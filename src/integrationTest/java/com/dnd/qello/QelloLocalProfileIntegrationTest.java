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
 * Source scenario: TEST-PLAN-GH-30-LOCAL-PROFILE-INTEGRATION
 * Extended at: 2026-08-03T16:09:35+09:00
 * Extension scenario: TEST-PLAN-GH-31-LOCAL-PROFILE-CONTAINER-INTEGRATION
 */
@SpringBootTest(properties = {
	"qello.notification.push.policy.bundle-window=PT10M",
	"qello.notification.push.policy.max-delay=PT8H",
	"qello.notification.push.policy.daily-limit=5",
	"qello.notification.push.policy.direction-reserved=2",
	"qello.notification.push.policy.recommendation-min-interval=PT24H"
})
@ActiveProfiles("local")
class QelloLocalProfileIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private Environment environment;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("local 프로필이 추적되지 않는 설정 대신 Testcontainers PostgreSQL에 연결된다")
	void contextLoadsWithLocalProfile() {
		assertThat(environment.matchesProfiles("local")).isTrue();
		assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
			.isEqualTo("qello_test");
	}

}
