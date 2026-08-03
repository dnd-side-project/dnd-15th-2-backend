package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Created at: 2026-08-03T15:39:58+09:00
 * Source scenario: TEST-PLAN-GH-30-TEST-PROFILE-INTEGRATION
 * Extended at: 2026-08-03T16:09:35+09:00
 * Extension scenario: TEST-PLAN-GH-31-POSTGIS-TEST-PROFILE-INTEGRATION
 */
@SpringBootTest
@ActiveProfiles("test")
class QelloApplicationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private Environment environment;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("test 프로필에서 Spring 애플리케이션 컨텍스트가 로드된다")
	void contextLoadsWithTestProfile() {
		assertThat(environment.matchesProfiles("test")).isTrue();
	}

	@Test
	@DisplayName("test 프로필의 격리된 PostgreSQL에서 PostGIS 확장을 사용할 수 있다")
	void postgisExtensionIsAvailableInTestProfile() {
		String postgisVersion = jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class);

		assertThat(postgisVersion).isNotBlank();
	}

}
