package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Created at: 2026-08-03T15:39:58+09:00
 * Source scenario: TEST-PLAN-GH-30-TEST-PROFILE-INTEGRATION
 */
@SpringBootTest
@ActiveProfiles("test")
class QelloApplicationIntegrationTest {

	@Autowired
	private Environment environment;

	@Test
	@DisplayName("test 프로필에서 Spring 애플리케이션 컨텍스트가 로드된다")
	void contextLoadsWithTestProfile() {
		assertThat(environment.matchesProfiles("test")).isTrue();
	}

}
