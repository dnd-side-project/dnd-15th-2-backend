/**
 * Created at: 2026-09-17T00:58:11+09:00
 * Source scenario: TEST-PLAN-GH-230-CORS-ACTUATOR-DEV-PROFILE-INT-005..008
 */
package com.dnd.qello;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #230: dev는 #229 EC2 테스트 서버 프로필이다. SPRING_PROFILES_ACTIVE=dev와
// 이슈가 명시한 필수 환경변수(DB·media bucket·auth secret)만으로 컨텍스트가
// 로드되어야 한다. FCM/push 관련 값은 일부러 주지 않는다 — PushProperties와
// PushTokenProperties가 dev에서도 바인딩을 시도하면 이 테스트가 컨텍스트 로드
// 실패로 드러낸다(PushConfiguration의 @Profile 예외 목록에 dev가 없으면 재발).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileBootIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@DynamicPropertySource
	static void devProfileRequiredProperties(DynamicPropertyRegistry registry) {
		// FCM/push 관련 값은 의도적으로 주지 않는다.
		registry.add("qello.media.bucket", () -> "qello-dev-boot-test-placeholder");
		registry.add("qello.auth.access-token.secret", () -> "dev-profile-boot-test-signing-key-32-bytes-min");
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("INT-008: dev 프로파일 컨텍스트가 FCM/push 필수값 없이 로드된다")
	void devProfileContextLoads() {
		// @SpringBootTest가 컨텍스트를 이미 띄웠다는 사실 자체가 검증이다.
		// PushProperties/PushTokenProperties가 dev에서 바인딩을 시도했다면
		// 이 클래스 로드 시점에 ApplicationContextException으로 실패한다.
	}

	@Test
	@DisplayName("INT-005: GET /actuator/health는 인증 없이 200과 UP 상태를 반환한다")
	void actuatorHealthIsOpenAndUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("INT-006: health 외 actuator endpoint는 dev 프로파일에서도 노출되지 않는다")
	void onlyHealthIsExposed() throws Exception {
		for (String path : new String[]{"/actuator", "/actuator/metrics", "/actuator/env"}) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
	}

}
