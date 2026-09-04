/**
 * Created at: 2026-09-05T01:24:42+09:00
 * Source scenario: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-001
 */
package com.dnd.qello;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
@SpringBootTest
@ActiveProfiles("test")
class PgStatStatementsPerformanceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeAll
	static void installExtension(@Autowired JdbcTemplate jdbc) {
		jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
	}

	@Test
	@DisplayName("PERF-001: performanceTest는 pg_stat_statements를 preload하고 안전한 요약을 조회한다")
	void preloadsAndQueriesPgStatStatements() {
		String preload = jdbc.queryForObject("SHOW shared_preload_libraries", String.class);
		assertThat(preload).contains("pg_stat_statements");

		jdbc.queryForObject("SELECT 1", Integer.class);
		jdbc.execute("SELECT pg_stat_statements_reset()");

		Integer rows = jdbc.queryForObject("SELECT count(*) FROM pg_stat_statements", Integer.class);
		assertThat(rows).isNotNull().isGreaterThanOrEqualTo(0);
	}

}
