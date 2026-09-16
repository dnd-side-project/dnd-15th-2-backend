/**
 * Created at: 2026-09-17T00:58:11+09:00
 * Source scenario: TEST-PLAN-GH-230-CORS-ACTUATOR-DEV-PROFILE-INT-001..004
 */
package com.dnd.qello;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.auth.config.SecurityConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #230: 프론트가 브라우저에서 #229 EC2 테스트 서버의 /api/**를 호출하려면
// CORS가 필요하다. 허용 origin은 qello.web.allowed-origins로 이 테스트에서만
// 주입하고, /admin/** 세션 체인은 CORS 설정과 무관하게 그대로임을 함께 확인한다
// (docs/adr/0006-split-operator-and-device-authentication.md 체인 분리 원칙).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String ALLOWED_ORIGIN = "https://allowed.qello-test.example";
	private static final String DISALLOWED_ORIGIN = "https://not-allowed.example";

	@DynamicPropertySource
	static void corsProperties(DynamicPropertyRegistry registry) {
		registry.add("qello.web.allowed-origins", () -> ALLOWED_ORIGIN);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("INT-001: 허용 origin의 preflight 요청은 200과 Access-Control-Allow-Origin을 반환한다")
	void allowedOriginPreflightSucceeds() throws Exception {
		mockMvc.perform(options(SecurityConfiguration.DEVICE_REGISTRATION_PATH)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
	}

	@Test
	@DisplayName("INT-002: 허용하지 않은 origin의 preflight 요청은 Access-Control-Allow-Origin 없이 끝난다")
	void disallowedOriginPreflightHasNoCorsHeader() throws Exception {
		mockMvc.perform(options(SecurityConfiguration.DEVICE_REGISTRATION_PATH)
				.header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("INT-003: 인증이 필요한 /api/** 응답에도 허용 origin의 CORS 헤더가 붙는다")
	void authenticatedPathStillCarriesCorsHeaderForAllowedOrigin() throws Exception {
		// 토큰 없이 보내 401을 받더라도, CorsFilter는 인가 결과와 무관하게
		// 먼저 실행되어 헤더를 붙인다.
		mockMvc.perform(get(SecurityConfiguration.DEVICE_TOKEN_PATH)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
	}

	@Test
	@DisplayName("INT-004: /admin/** 체인은 CORS 설정과 무관하게 기존 세션 동작을 유지한다")
	void backofficeChainIsUnaffectedByCors() throws Exception {
		// CSRF 토큰 없이 보내 403이 나더라도, 이 체인에는 CORS를 연결하지
		// 않았으므로 Origin 헤더를 보내도 응답에 CORS 헤더가 없어야 한다.
		mockMvc.perform(post(SecurityConfiguration.LOGIN_PATH)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"unused\",\"password\":\"unused\"}"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

}
