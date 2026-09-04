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
	}

	@Test
	@DisplayName("PERF-010: EXPLAIN 파서는 대상 relation이 없어도 원문 JSON 없이 sort 노드를 추출한다")
	void parsesExplainPlanWithoutTargetRelationsOrRawJson() {
		DirectionMatchingPerformanceProbe probe = new DirectionMatchingPerformanceProbe(jdbc, namedJdbc, objectMapper);

		DirectionMatchingPerformanceProbe.PlanObservation observation = probe.explain(
				"GENERATE_SERIES", "N/A", "SELECT * FROM generate_series(1, 3) ORDER BY 1",
				new MapSqlParameterSource());

		assertThat(observation.planningTimeMs()).isGreaterThanOrEqualTo(0.0);
		assertThat(observation.executionTimeMs()).isGreaterThanOrEqualTo(0.0);
		assertThat(observation.targetNodes()).isEmpty();
		assertThat(observation.sorts()).hasSize(1);
	}

}
