/**
 * Created at: 2026-08-28T16:10:00+09:00
 * Source scenario: TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-001 through PERF-006
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.service.DirectionPostPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("performance")
@SpringBootTest
@ActiveProfiles("test")
class DirectionMatchingIndexPlanPerformanceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-PERF-163";
	private static final String ACCOUNT_PREFIX = "perf-163-account-";
	private static final String EXCLUDED_NICKNAME = "perf-163-excluded";
	private static final Instant NOW = Instant.parse("2026-08-28T06:00:00Z");
	private static final double ORIGIN_LATITUDE = 37.5000;
	private static final double ORIGIN_LONGITUDE = 127.0000;
	private static final int SYNTHETIC_ACCOUNT_COUNT = 100_000;
	private static final int SYNTHETIC_PRESENCE_COUNT = 10_000;
	private static final long SELECTIVITY_PROBE_MAX_DISTANCE_METERS = 5_000L;
	private static final String PARTIAL_GIST_INDEX = "active_user_presence_position_gix";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private DirectionPostPolicy policy;

	@BeforeAll
	static void seedFixture(@Autowired JdbcTemplate jdbc) {
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code) DO NOTHING");
		jdbc.update("DELETE FROM active_user_presence WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Perf 163', 'REGION')", REGION);
		jdbc.update("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			SELECT 'USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ? || lpad(gs::text, 6, '0')
			FROM generate_series(1, ?) AS gs
			""", REGION, ACCOUNT_PREFIX, SYNTHETIC_ACCOUNT_COUNT);
		jdbc.update("""
			INSERT INTO active_user_presence
				(user_id, position, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
			SELECT ua.id,
			       ST_Project(
			           ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
			           sqrt((gs - 0.5) / ?) * 100000,
			           radians(mod((gs - 1) * 137.50776405003785, 360))
			       ),
			       ?, 5, TRUE, ?, ?
			FROM generate_series(1, ?) AS gs
			JOIN user_account ua ON ua.nickname = ? || lpad(gs::text, 6, '0')
			""", ORIGIN_LONGITUDE, ORIGIN_LATITUDE, SYNTHETIC_PRESENCE_COUNT, REGION,
			Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW.plusSeconds(3600)),
			SYNTHETIC_PRESENCE_COUNT, ACCOUNT_PREFIX);
		jdbc.update("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			""", REGION, EXCLUDED_NICKNAME);
		jdbc.execute("ANALYZE user_account, active_user_presence");
	}

	@AfterAll
	static void cleanupFixture(@Autowired JdbcTemplate jdbc) {
		jdbc.update("DELETE FROM active_user_presence WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}

	@BeforeEach
	void verifyFixtureBeforeObservation() {
		assertFixtureShape();
	}

	@Test
	@DisplayName("PERF-001: 결정적 합성 데이터는 100,000개 계정과 10,000개 presence를 만든다")
	void seedsApprovedCandidateDataShape() {
		assertFixtureShape();

		System.out.println("PERF-001 evidence: synthetic_accounts=" + syntheticAccountCount()
			+ " synthetic_presence=" + validPresenceCount()
			+ " policy_baseline_candidates=" + countWithin(policy.maxDistanceMeters())
			+ " selectivity_probe_candidates=" + countWithin(SELECTIVITY_PROBE_MAX_DISTANCE_METERS));
	}

	private void assertFixtureShape() {
		long accountCount = syntheticAccountCount();
		long presenceCount = validPresenceCount();
		long probeCount = countWithin(SELECTIVITY_PROBE_MAX_DISTANCE_METERS);

		assertThat(policy.isGlobal()).isTrue();
		assertThat(policy.minDistanceMeters()).isZero();
		assertThat(policy.maxDistanceMeters()).isEqualTo(DirectionPostProperties.GLOBAL_MAX_DISTANCE_METERS);
		assertThat(accountCount).isEqualTo(SYNTHETIC_ACCOUNT_COUNT);
		assertThat(presenceCount).isEqualTo(SYNTHETIC_PRESENCE_COUNT);
		assertThat(countWithin(policy.maxDistanceMeters())).isEqualTo(SYNTHETIC_PRESENCE_COUNT);
		assertThat(probeCount).isEqualTo(25);
		assertThat(presenceCount - probeCount).isEqualTo(9_975);
	}

	private long syntheticAccountCount() {
		return count("""
			SELECT COUNT(*) FROM user_account
			WHERE coarse_region_code = ? AND nickname LIKE ?
			""", REGION, ACCOUNT_PREFIX + "%");
	}

	private long validPresenceCount() {
		return count("""
			SELECT COUNT(*) FROM active_user_presence
			WHERE coarse_region_code = ? AND receive_allowed = TRUE AND position IS NOT NULL
			  AND location_at <= ? AND expires_at > ?
			""", REGION, Timestamp.from(NOW), Timestamp.from(NOW));
	}

	@Test
	@DisplayName("PERF-002: preview 집계는 POLICY_BASELINE 실행계획의 relation 접근 경로를 기록한다")
	void explainsPreviewAtPolicyBaseline() {
		assertCompletePlan(explain("PREVIEW", "POLICY_BASELINE", ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL,
			previewParameters(policy.minDistanceMeters(), policy.maxDistanceMeters())));
	}

	@Test
	@DisplayName("PERF-003: matching 후보 조회는 POLICY_BASELINE 실행계획의 relation 접근 경로를 기록한다")
	void explainsMatchingAtPolicyBaseline() {
		assertCompletePlan(explain("MATCHING", "POLICY_BASELINE", ActiveUserPresenceSql.FIND_CANDIDATES_SQL,
			matchingParameters(policy.minDistanceMeters(), policy.maxDistanceMeters())));
	}

	@Test
	@DisplayName("PERF-004: preview 집계는 SELECTIVITY_PROBE 실행계획의 relation 접근 경로를 기록한다")
	void explainsPreviewAtSelectivityProbe() {
		assertCompletePlan(explain("PREVIEW", "SELECTIVITY_PROBE", ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL,
			previewParameters(policy.minDistanceMeters(), SELECTIVITY_PROBE_MAX_DISTANCE_METERS)));
	}

	@Test
	@DisplayName("PERF-005: matching 후보 조회는 SELECTIVITY_PROBE에서 북쪽 후보와 relation 접근 경로를 기록한다")
	void explainsMatchingAtSelectivityProbe() {
		MapSqlParameterSource parameters = matchingParameters(policy.minDistanceMeters(), SELECTIVITY_PROBE_MAX_DISTANCE_METERS);
		List<Long> northCandidateIds = namedJdbc.query(ActiveUserPresenceSql.FIND_CANDIDATES_SQL, parameters,
			(rs, rowNum) -> rs.getLong("user_id"));
		assertThat(northCandidateIds).as("5km 북쪽 섹터의 matching 후보").isNotEmpty();
		assertCompletePlan(explain("MATCHING", "SELECTIVITY_PROBE", ActiveUserPresenceSql.FIND_CANDIDATES_SQL, parameters));
	}

	private long count(String sql, Object... arguments) {
		Long value = jdbc.queryForObject(sql, Long.class, arguments);
		return value == null ? 0 : value;
	}

	private long countWithin(long maxDistanceMeters) {
		return count("""
			SELECT COUNT(*)
			FROM active_user_presence p
			WHERE p.coarse_region_code = ?
			  AND ST_DWithin(p.position,
			      ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
			""", REGION, ORIGIN_LONGITUDE, ORIGIN_LATITUDE, maxDistanceMeters);
	}

	private MapSqlParameterSource previewParameters(long minDistanceMeters, long maxDistanceMeters) {
		return commonParameters(minDistanceMeters, maxDistanceMeters)
			.addValue("schemeId", octantSchemeId());
	}

	private MapSqlParameterSource matchingParameters(long minDistanceMeters, long maxDistanceMeters) {
		return commonParameters(minDistanceMeters, maxDistanceMeters)
			.addValue("sectorStartDegrees", 337.5)
			.addValue("sectorEndDegrees", 22.5);
	}

	private MapSqlParameterSource commonParameters(long minDistanceMeters, long maxDistanceMeters) {
		return new MapSqlParameterSource()
			.addValue("excludedUserId", excludedUserId())
			.addValue("originLatitude", ORIGIN_LATITUDE)
			.addValue("originLongitude", ORIGIN_LONGITUDE)
			.addValue("minDistanceMeters", minDistanceMeters)
			.addValue("maxDistanceMeters", maxDistanceMeters)
			.addValue("at", Timestamp.from(NOW))
			.addValue("regionCode", null);
	}

	private long excludedUserId() {
		Long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE coarse_region_code = ? AND nickname = ?",
			Long.class, REGION, EXCLUDED_NICKNAME);
		assertThat(userId).as("별도 제외 사용자").isNotNull();
		return userId;
	}

	private long octantSchemeId() {
		Long schemeId = jdbc.queryForObject("SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'", Long.class);
		assertThat(schemeId).as("활성 OCTANT scheme").isNotNull();
		return schemeId;
	}

	private ExplainObservation explain(String query, String radiusLabel, String sql, MapSqlParameterSource parameters) {
		String json = namedJdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql, parameters, String.class);
		try {
			JsonNode root = objectMapper.readTree(json);
			JsonNode explain = root.path(0);
			JsonNode plan = explain.path("Plan");
			if (!root.isArray() || root.size() != 1 || plan.isMissingNode()) {
				throw new IllegalStateException("EXPLAIN JSON 결과 구조가 유효하지 않습니다");
			}
			List<PlanNodeObservation> nodes = new ArrayList<>();
			collectTargetRelations(plan, nodes);
			ExplainObservation observation = new ExplainObservation(query, radiusLabel, nodes,
				number(explain, "Planning Time"), number(explain, "Execution Time"), containsPartialGist(plan));
			printEvidence(observation);
			return observation;
		} catch (Exception exception) {
			throw new IllegalStateException("EXPLAIN JSON 결과 파싱에 실패했습니다", exception);
		}
	}

	private void collectTargetRelations(JsonNode node, List<PlanNodeObservation> observations) {
		String relation = text(node, "Relation Name");
		if ("active_user_presence".equals(relation) || "user_account".equals(relation)) {
			observations.add(new PlanNodeObservation(relation, text(node, "Node Type"), findIndexName(node),
				number(node, "Plan Rows"), number(node, "Actual Rows"), number(node, "Actual Loops"),
				number(node, "Rows Removed by Filter"), number(node, "Shared Hit Blocks"), number(node, "Shared Read Blocks")));
		}
		JsonNode children = node.path("Plans");
		if (children.isArray()) {
			for (JsonNode child : children) {
				collectTargetRelations(child, observations);
			}
		}
	}

	private String findIndexName(JsonNode node) {
		String indexName = text(node, "Index Name");
		if (indexName != null) return indexName;
		JsonNode children = node.path("Plans");
		if (children.isArray()) {
			for (JsonNode child : children) {
				String nestedIndexName = findIndexName(child);
				if (nestedIndexName != null) return nestedIndexName;
			}
		}
		return null;
	}

	private boolean containsPartialGist(JsonNode node) {
		if (PARTIAL_GIST_INDEX.equals(text(node, "Index Name"))) {
			return true;
		}
		JsonNode children = node.path("Plans");
		if (children.isArray()) {
			for (JsonNode child : children) {
				if (containsPartialGist(child)) return true;
			}
		}
		return false;
	}

	private void assertCompletePlan(ExplainObservation observation) {
		assertThat(observation.planningTimeMs()).as("planning time").isNotNull();
		assertThat(observation.executionTimeMs()).as("execution time").isNotNull();
		assertThat(observation.nodes()).extracting(PlanNodeObservation::relation)
			.contains("active_user_presence", "user_account");
		assertThat(observation.nodes()).allSatisfy(node -> {
			assertThat(node.nodeType()).as("%s node type", node.relation()).isNotBlank();
			assertThat(node.planRows()).as("%s plan rows", node.relation()).isNotNull();
			assertThat(node.actualRows()).as("%s actual rows", node.relation()).isNotNull();
			assertThat(node.actualLoops()).as("%s actual loops", node.relation()).isNotNull();
			assertThat(node.sharedHitBlocks()).as("%s shared hit blocks", node.relation()).isNotNull();
			assertThat(node.sharedReadBlocks()).as("%s shared read blocks", node.relation()).isNotNull();
		});
	}

	private void printEvidence(ExplainObservation observation) {
		List<PlanNodeObservation> orderedNodes = observation.nodes().stream()
			.sorted(Comparator.comparing(PlanNodeObservation::relation).thenComparing(PlanNodeObservation::nodeType))
			.toList();
		String accessPaths = orderedNodes.stream().map(node -> node.relation() + ":" + node.nodeType()
			+ "(" + (node.indexName() == null ? "none" : node.indexName()) + ")"
			+ " plan_rows=" + node.planRows() + " actual_rows=" + node.actualRows()
			+ " loops=" + node.actualLoops() + " removed=" + node.rowsRemovedByFilter()
			+ " hit=" + node.sharedHitBlocks() + " read=" + node.sharedReadBlocks())
			.reduce((left, right) -> left + "; " + right).orElse("none");
		System.out.println("PERF-163 evidence: query=" + observation.query() + " radius=" + observation.radiusLabel()
			+ " partial_gist=" + (observation.usesPartialGist() ? "USED" : "NOT_USED")
			+ " planning_ms=" + observation.planningTimeMs() + " execution_ms=" + observation.executionTimeMs()
			+ " access_paths=[" + accessPaths + "]");
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private static Double number(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asDouble();
	}

	private record ExplainObservation(
		String query,
		String radiusLabel,
		List<PlanNodeObservation> nodes,
		Double planningTimeMs,
		Double executionTimeMs,
		boolean usesPartialGist
	) {
	}

	private record PlanNodeObservation(
		String relation,
		String nodeType,
		String indexName,
		Double planRows,
		Double actualRows,
		Double actualLoops,
		Double rowsRemovedByFilter,
		Double sharedHitBlocks,
		Double sharedReadBlocks
	) {
	}
}
