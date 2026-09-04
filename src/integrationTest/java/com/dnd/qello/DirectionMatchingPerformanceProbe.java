/**
 * Created at: 2026-09-05T01:42:19+09:00
 * Source scenario: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-010
 *
 * GH-214 E3 실험(Task 4/5)이 재사용할 sanitized 측정 도구다. pg_stat_statements
 * 호출 수·실행시간과 EXPLAIN(ANALYZE, BUFFERS, FORMAT JSON) 결과에서
 * active_user_presence·user_account 접근 경로만 비식별로 추출한다. 원문 SQL,
 * query 컬럼, 사용자 식별자나 좌표는 어떤 필드에도 담지 않는다.
 */
package com.dnd.qello;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class DirectionMatchingPerformanceProbe {

	static final int MEASURED_CALLS = 20;

	private static final String PARTIAL_GIST_INDEX = "active_user_presence_position_gix";
	private static final String MISSING_TEXT = "N/A";
	private static final String PG_STAT_SUMMARY_SQL = """
			SELECT calls,
			       total_exec_time,
			       mean_exec_time,
			       rows,
			       shared_blks_hit,
			       shared_blks_read,
			       temp_blks_read,
			       temp_blks_written
			FROM pg_stat_statements
			WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
			  AND query NOT LIKE 'EXPLAIN%'
			  AND query LIKE ?
			ORDER BY total_exec_time DESC
			""";

	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate namedJdbc;
	private final ObjectMapper objectMapper;

	DirectionMatchingPerformanceProbe(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc,
			ObjectMapper objectMapper) {
		this.jdbc = jdbc;
		this.namedJdbc = namedJdbc;
		this.objectMapper = objectMapper;
	}

	<T> Measurement<T> measure(String experimentId, String condition, QueryFingerprint fingerprint, Supplier<T> query) {
		query.get();
		jdbc.execute("SELECT pg_stat_statements_reset()");

		List<T> results = new ArrayList<>(MEASURED_CALLS);
		List<Long> elapsedNanos = new ArrayList<>(MEASURED_CALLS);
		for (int i = 0; i < MEASURED_CALLS; i++) {
			long start = System.nanoTime();
			T result = query.get();
			long elapsed = System.nanoTime() - start;
			results.add(result);
			elapsedNanos.add(elapsed);
		}

		PgStatObservation pgStat = readPgStat(fingerprint);

		List<Long> sortedNanos = new ArrayList<>(elapsedNanos);
		Collections.sort(sortedNanos);
		LatencyObservation latency = new LatencyObservation(
				percentileMillis(sortedNanos, 0.50),
				percentileMillis(sortedNanos, 0.95),
				percentileMillis(sortedNanos, 0.99));

		String sanitizedLine = String.format(Locale.ROOT,
				"experiment=%s condition=%s calls=%d rows=%d total_exec_ms=%.3f "
						+ "mean_exec_ms=%.3f client_p50_ms=%.3f client_p95_ms=%.3f "
						+ "client_p99_ms=%.3f shared_hit=%d shared_read=%d temp_read=%d temp_written=%d",
				experimentId, condition, pgStat.calls(), pgStat.rows(), pgStat.totalExecTimeMs(),
				pgStat.meanExecTimeMs(), latency.p50Ms(), latency.p95Ms(), latency.p99Ms(),
				pgStat.sharedBlocksHit(), pgStat.sharedBlocksRead(), pgStat.tempBlocksRead(),
				pgStat.tempBlocksWritten());

		return new Measurement<>(List.copyOf(results), pgStat, latency, sanitizedLine);
	}

	PlanObservation explain(String queryKind, String radius, String sql, MapSqlParameterSource parameters) {
		String json = namedJdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql, parameters,
				String.class);
		JsonNode root;
		try {
			root = objectMapper.readTree(json);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("EXPLAIN JSON 결과 파싱에 실패했습니다", exception);
		}
		JsonNode explainRoot = root.path(0);
		JsonNode plan = explainRoot.path("Plan");
		if (!root.isArray() || root.size() != 1 || plan.isMissingNode()) {
			throw new IllegalStateException("EXPLAIN JSON 결과 구조가 유효하지 않습니다");
		}

		List<PlanNodeObservation> targetNodes = new ArrayList<>();
		List<SortObservation> sorts = new ArrayList<>();
		collectNodes(plan, targetNodes, sorts);

		boolean usesPartialGist = targetNodes.stream()
				.anyMatch(node -> PARTIAL_GIST_INDEX.equals(node.indexName()));

		return new PlanObservation(queryKind, radius, List.copyOf(targetNodes), List.copyOf(sorts),
				number(explainRoot, "Planning Time"), number(explainRoot, "Execution Time"), usesPartialGist);
	}

	private PgStatObservation readPgStat(QueryFingerprint fingerprint) {
		List<PgStatObservation> rows = jdbc.query(PG_STAT_SUMMARY_SQL,
				(resultSet, rowNumber) -> new PgStatObservation(
						resultSet.getLong("calls"),
						resultSet.getDouble("total_exec_time"),
						resultSet.getDouble("mean_exec_time"),
						resultSet.getLong("rows"),
						resultSet.getLong("shared_blks_hit"),
						resultSet.getLong("shared_blks_read"),
						resultSet.getLong("temp_blks_read"),
						resultSet.getLong("temp_blks_written")),
				fingerprint.likePattern);
		if (rows.size() != 1) {
			throw new IllegalStateException(
					"pg_stat_statements fingerprint expected exactly one row but found " + rows.size());
		}
		return rows.get(0);
	}

	private void collectNodes(JsonNode node, List<PlanNodeObservation> targetNodes, List<SortObservation> sorts) {
		String relation = text(node, "Relation Name");
		if ("active_user_presence".equals(relation) || "user_account".equals(relation)) {
			targetNodes.add(new PlanNodeObservation(relation, text(node, "Node Type"), findIndexName(node),
					number(node, "Plan Rows"), number(node, "Actual Rows"), number(node, "Actual Loops"),
					number(node, "Rows Removed by Filter"), number(node, "Shared Hit Blocks"),
					number(node, "Shared Read Blocks"),
					number(node, "Temp Read Blocks"), number(node, "Temp Written Blocks")));
		}
		if ("Sort".equals(text(node, "Node Type"))) {
			sorts.add(new SortObservation(text(node, "Sort Method"), text(node, "Sort Space Type"),
					number(node, "Sort Space Used"), number(node, "Actual Rows")));
		}
		JsonNode children = node.path("Plans");
		if (children.isArray()) {
			for (JsonNode child : children) {
				collectNodes(child, targetNodes, sorts);
			}
		}
	}

	private String findIndexName(JsonNode node) {
		String indexName = text(node, "Index Name");
		if (!MISSING_TEXT.equals(indexName)) {
			return indexName;
		}
		JsonNode children = node.path("Plans");
		if (children.isArray()) {
			for (JsonNode child : children) {
				// SubPlan·InitPlan 자식은 상관 서브쿼리(예: NOT EXISTS user_block)처럼 이
				// 노드의 접근 경로가 아닌 별도 계획이다. 이 자식으로 내려가면 대상
				// relation과 무관한 인덱스를 그 relation의 인덱스로 잘못 귀속시킬 수
				// 있어 제외한다. Bitmap Heap Scan의 Bitmap Index Scan 자식처럼 같은
				// 접근 경로에 속한 자식(Outer/Inner 등)만 계속 내려간다.
				String parentRelationship = text(child, "Parent Relationship");
				if ("SubPlan".equals(parentRelationship) || "InitPlan".equals(parentRelationship)) {
					continue;
				}
				String nestedIndexName = findIndexName(child);
				if (!MISSING_TEXT.equals(nestedIndexName)) {
					return nestedIndexName;
				}
			}
		}
		return MISSING_TEXT;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? MISSING_TEXT : value.asText();
	}

	private static double number(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? 0.0 : value.asDouble();
	}

	private static double percentileMillis(List<Long> sortedNanos, double percentile) {
		int index = Math.max(0, (int) Math.ceil(percentile * sortedNanos.size()) - 1);
		return sortedNanos.get(index) / 1_000_000.0;
	}

	enum QueryFingerprint {
		PREVIEW("%candidate_bearings AS (%"), MATCHING("%LEFT JOIN recipient_receive_state%"), PROBE_CONTRACT(
				"%e3_probe_contract%");

		private final String likePattern;

		QueryFingerprint(String likePattern) {
			this.likePattern = likePattern;
		}
	}

	record Measurement<T>(
			List<T> results,
			PgStatObservation pgStat,
			LatencyObservation latency,
			String sanitizedLine) {
	}

	record PgStatObservation(
			long calls,
			double totalExecTimeMs,
			double meanExecTimeMs,
			long rows,
			long sharedBlocksHit,
			long sharedBlocksRead,
			long tempBlocksRead,
			long tempBlocksWritten) {
	}

	record LatencyObservation(double p50Ms, double p95Ms, double p99Ms) {
	}

	record PlanObservation(
			String queryKind,
			String radius,
			List<PlanNodeObservation> targetNodes,
			List<SortObservation> sorts,
			double planningTimeMs,
			double executionTimeMs,
			boolean usesPartialGist) {
	}

	record PlanNodeObservation(
			String relation,
			String nodeType,
			String indexName,
			double planRows,
			double actualRows,
			double actualLoops,
			double rowsRemovedByFilter,
			double sharedBlocksHit,
			double sharedBlocksRead,
			double tempBlocksRead,
			double tempBlocksWritten) {
	}

	record SortObservation(
			String method,
			String spaceType,
			double spaceUsedKb,
			double actualRows) {
	}

}
