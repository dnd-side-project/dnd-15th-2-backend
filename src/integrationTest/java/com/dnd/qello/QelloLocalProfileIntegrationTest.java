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
 * Source scenario: TEST-PLAN-GH-30-LOCAL-PROFILE-INTEGRATION
 */
@SpringBootTest
@ActiveProfiles("local")
class QelloLocalProfileIntegrationTest {

	@Autowired
	private Environment environment;

	@Test
	@DisplayName("local 프로필에서 외부 인프라 없이 Spring 애플리케이션 컨텍스트가 로드된다")
	void contextLoadsWithLocalProfile() {
		assertThat(environment.matchesProfiles("local")).isTrue();
	}

}
