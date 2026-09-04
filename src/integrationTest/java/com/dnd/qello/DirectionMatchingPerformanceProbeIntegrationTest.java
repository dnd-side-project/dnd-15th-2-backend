/**
 * Created at: 2026-09-05T01:42:19+09:00
 * Source scenario: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-010
 */
package com.dnd.qello;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("performance")
@SpringBootTest
@ActiveProfiles("test")
class DirectionMatchingPerformanceProbeIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private ObjectMapper objectMapper;

	@BeforeAll
	static void installExtension(@Autowired JdbcTemplate jdbc) {
		jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
	}

	@Test
	@DisplayName("PERF-010: PROBE-CONTRACT 쿼리 20회 측정은 허용된 aggregate field만 담은 sanitized 한 줄을 만든다")
	void measuresProbeContractQueryAndSanitizesOutput() {
		DirectionMatchingPerformanceProbe probe = new DirectionMatchingPerformanceProbe(jdbc, namedJdbc, objectMapper);

		DirectionMatchingPerformanceProbe.Measurement<Integer> measurement = probe.measure(
				"PROBE-CONTRACT", "CONTRACT", DirectionMatchingPerformanceProbe.QueryFingerprint.PROBE_CONTRACT,
				() -> jdbc.queryForObject("SELECT 1 AS e3_probe_contract", Integer.class));

		assertThat(measurement.results()).hasSize(20).containsOnly(1);
		assertThat(measurement.pgStat().calls()).isEqualTo(20L);
		assertThat(measurement.pgStat().totalExecTimeMs()).isGreaterThanOrEqualTo(0.0);
		assertThat(measurement.pgStat().meanExecTimeMs()).isGreaterThanOrEqualTo(0.0);
		assertThat(measurement.latency().p50Ms()).isGreaterThanOrEqualTo(0.0);
		assertThat(measurement.latency().p95Ms()).isGreaterThanOrEqualTo(measurement.latency().p50Ms());
		assertThat(measurement.latency().p99Ms()).isGreaterThanOrEqualTo(measurement.latency().p95Ms());
		assertThat(measurement.sanitizedLine())
				.contains("experiment=PROBE-CONTRACT", "calls=20")
				.doesNotContain("SELECT", "query=", "userId", "nickname", "latitude", "longitude");

		// 20회 모두 단일 행 SELECT였으므로 pg_stat_statements의 누적 rows도 정확히
		// 20이어야 한다. 이 검증은 rows 필드가 실제 aggregate 값을 담는지 확인한다.
		assertThat(measurement.pgStat().rows()).isEqualTo(20L);
		// sanitized 한 줄이 규정된 필드 순서를 그대로 담는지 확인한다.
		assertThat(measurement.sanitizedLine())
				.startsWith("experiment=PROBE-CONTRACT condition=")
				.contains("calls=20 rows=20");
		// Measurement.results()는 List.copyOf로 만들어져 변경을 시도하면 실패해야 한다.
		assertThatThrownBy(() -> measurement.results().add(1))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("PERF-010: EXPLAIN 파서는 대상 relation이 없어도 원문 JSON 없이 sort 노드를 추출한다")
	void parsesExplainPlanWithoutTargetRelationsOrRawJson() {
		DirectionMatchingPerformanceProbe probe = new DirectionMatchingPerformanceProbe(jdbc, namedJdbc, objectMapper);

		DirectionMatchingPerformanceProbe.PlanObservation observation = probe.explain(
				"GENERATE_SERIES", "N/A", "SELECT * FROM generate_series(1, 3) ORDER BY 1",
				new MapSqlParameterSource());

		// 0.0 이상은 "Planning Time"/"Execution Time" 키가 없을 때도 통과하므로
		// (number()의 기본값이 0.0이라서), 실제로 root[0]에서 읽었는지를 검증하려면
		// 0보다 크다고 단언해야 한다.
		assertThat(observation.planningTimeMs()).isGreaterThan(0.0);
		assertThat(observation.executionTimeMs()).isGreaterThan(0.0);
		assertThat(observation.targetNodes()).isEmpty();
		assertThat(observation.sorts()).hasSize(1);
	}

	@Test
	@DisplayName("PERF-010: EXPLAIN 파서는 실제 대상 relation의 접근 경로와 GiST 사용 여부를 수집한다")
	void collectsRealTargetRelationNode() {
		DirectionMatchingPerformanceProbe probe = new DirectionMatchingPerformanceProbe(jdbc, namedJdbc, objectMapper);

		DirectionMatchingPerformanceProbe.PlanObservation observation = probe.explain(
				"USER_ACCOUNT_SCAN", "N/A", "SELECT id FROM user_account", new MapSqlParameterSource());

		assertThat(observation.targetNodes()).hasSize(1);
		DirectionMatchingPerformanceProbe.PlanNodeObservation node = observation.targetNodes().get(0);
		assertThat(node.relation()).isEqualTo("user_account");
		assertThat(node.nodeType()).isNotBlank();
		assertThat(node.sharedBlocksHit()).isGreaterThanOrEqualTo(0.0);
		assertThat(node.sharedBlocksRead()).isGreaterThanOrEqualTo(0.0);
		assertThat(observation.usesPartialGist()).isFalse();
	}

}
