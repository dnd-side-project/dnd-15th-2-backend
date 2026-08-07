/**
 * Created at: 2026-08-07T15:55:00+09:00
 * Source scenario: TEST-PLAN-GH-72-OPERATOR-LOGIN-INT-001 through INT-007
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorCredential;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class OperatorLoginIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final String LOGIN_ID = "qello-admin";
	private static final String PASSWORD = "example-operator-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OperatorSeedService operatorSeedService;

	@Autowired
	private ObjectMapper objectMapper;

	private String csrfHeaderName;
	private String csrfToken;
	private Cookie[] csrfCookies;

	@BeforeEach
	void seedOperator() throws Exception {
		jdbcTemplate.update("DELETE FROM operator_credential");
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("""
			INSERT INTO region_code (code, display_name, level)
			VALUES (?, 'Test Country', 'COUNTRY')
			""", REGION_CODE);
		operatorSeedService.seedIfAbsent(
			LoginId.of(LOGIN_ID), new RawPassword(PASSWORD),
			"qello-admin", REGION_CODE, "ko-KR", "Asia/Seoul");

		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		csrfHeaderName = data.get("headerName").asText();
		csrfToken = data.get("token").asText();
		csrfCookies = issued.getResponse().getCookies();
	}

	@Test
	@DisplayName("로그인 성공은 200과 ApiResponse 형식으로 응답하고 세션 쿠키를 내린다")
	void loginReturnsSessionCookieWithResponseContract() throws Exception {
		mockMvc.perform(login(LOGIN_ID, PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("success"))
			.andExpect(jsonPath("$.data.userId").isNumber())
			.andExpect(jsonPath("$.timestamp").exists())
			.andExpect(cookie().exists("SESSION"));
	}

	@Test
	@DisplayName("로그인 성공 시 세션 ID가 재발급된다")
	void loginRotatesSessionId() throws Exception {
		// 세션은 spring-session-jdbc가 관리하므로 MockHttpSession이 아니라 SESSION 쿠키로 본다.
		Cookie first = mockMvc.perform(login(LOGIN_ID, PASSWORD))
			.andExpect(status().isOk())
			.andReturn().getResponse().getCookie("SESSION");
		assertThat(first).isNotNull();

		Cookie second = mockMvc.perform(login(LOGIN_ID, PASSWORD).cookie(first))
			.andExpect(status().isOk())
			.andReturn().getResponse().getCookie("SESSION");

		assertThat(second).isNotNull();
		assertThat(second.getValue()).isNotEqualTo(first.getValue());
	}

	@Test
	@DisplayName("존재하지 않는 loginId와 잘못된 비밀번호는 같은 401 본문으로 응답한다")
	void failedLoginsAreIndistinguishable() throws Exception {
		String unknown = mockMvc.perform(login("no-such-operator", PASSWORD))
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		String wrongPassword = mockMvc.perform(login(LOGIN_ID, "wrong-password"))
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();

		assertThat(withoutTimestamp(unknown)).isEqualTo(withoutTimestamp(wrongPassword));
		assertThat(unknown).contains("AUT-APP-001");
	}

	@Test
	@DisplayName("실패 5회 후에는 올바른 비밀번호도 423으로 거절된다")
	void locksAfterFiveFailures() throws Exception {
		for (int attempt = 0; attempt < OperatorCredential.MAX_FAILED_ATTEMPTS; attempt++) {
			mockMvc.perform(login(LOGIN_ID, "wrong-password"))
				.andExpect(status().isUnauthorized());
		}

		mockMvc.perform(login(LOGIN_ID, PASSWORD))
			.andExpect(status().isLocked())
			.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-002"));
	}

	@Test
	@DisplayName("응답과 저장소 어디에도 평문 비밀번호가 남지 않는다")
	void neverExposesRawPassword() throws Exception {
		String body = mockMvc.perform(login(LOGIN_ID, PASSWORD))
			.andReturn().getResponse().getContentAsString();
		String storedHash = jdbcTemplate.queryForObject(
			"SELECT password_hash FROM operator_credential WHERE login_id = ?", String.class, LOGIN_ID);

		assertThat(body).doesNotContain(PASSWORD);
		assertThat(storedHash).doesNotContain(PASSWORD).startsWith("$2");
	}

	@Test
	@DisplayName("/admin은 CSRF 토큰 없는 상태 변경 요청을 거절한다")
	void backofficeRequiresCsrfToken() throws Exception {
		// 로그인은 permitAll이지만 CSRF 필터는 체인에 남아 있어야 한다.
		mockMvc.perform(post("/admin/logout"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD)))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("로그인 전에 받은 CSRF 토큰으로 상태 변경 요청을 통과시킬 수 있다")
	void issuedCsrfTokenUnlocksStateChangingRequests() throws Exception {
		MvcResult issued = mockMvc.perform(get("/admin/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("success"))
			.andExpect(jsonPath("$.data.headerName").isNotEmpty())
			.andExpect(jsonPath("$.data.token").isNotEmpty())
			.andReturn();

		// SPA가 쿠키에서 토큰을 읽어 헤더로 돌려보낼 수 있어야 한다.
		// 이 쿠키가 없으면 CookieCsrfTokenRepository 설정이 적용되지 않은 것이다.
		assertThat(issued.getResponse().getCookie("XSRF-TOKEN")).isNotNull();

		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		// 브라우저는 응답으로 받은 쿠키를 모두 되돌려 보낸다.
		Cookie[] cookies = issued.getResponse().getCookies();

		mockMvc.perform(post("/admin/login")
				.header(data.get("headerName").asText(), data.get("token").asText())
				.cookie(cookies)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("운영자 계정을 만드는 엔드포인트는 존재하지 않는다")
	void hasNoOperatorCreationEndpoint() throws Exception {
		mockMvc.perform(withCsrf(post("/admin/operators"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
	}

	@Test
	@DisplayName("/api는 인증 없이 접근할 수 없고 세션을 만들지 않는다")
	void appApiChainIsStatelessAndSecured() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/anything"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value("error"))
			.andReturn();

		assertThat(result.getRequest().getSession(false)).isNull();
	}

	private MockHttpServletRequestBuilder login(String loginId, String password) {
		return withCsrf(post("/admin/login"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password));
	}

	// spring-security-test의 csrf() post-processor를 쓰지 않는다. 그 구현이
	// TestCsrfTokenRepository를 설치해 설정된 CookieCsrfTokenRepository를 덮어쓰고,
	// 효과가 같은 클래스의 뒤 요청까지 샌다. 실제 발급 흐름을 그대로 쓴다.
	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
		return request
			.header(csrfHeaderName, csrfToken)
			.cookie(csrfCookies);
	}

	private String withoutTimestamp(String body) {
		return body.replaceAll("\"timestamp\":\"[^\"]+\"", "\"timestamp\":\"FIXED\"");
	}
}
