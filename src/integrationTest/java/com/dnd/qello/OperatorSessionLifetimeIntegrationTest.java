/*
 * Created at: 2026-10-09T20:42:16+09:00
 * Source scenario: TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-INT-001 through INT-008
 */
package com.dnd.qello;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import jakarta.servlet.http.Cookie;

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

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 세션 시각은 SPRING_SESSION 행을 직접 고쳐 만든다. Spring Session은 생성 시각과 미사용 만료를 시스템
// 시각으로 기록·판정하므로 이 클래스는 고정 Clock을 들이지 않는다. 같은 운영자가 여러 번 로그인하면
// 행이 여러 개라 PRINCIPAL_NAME이 아니라 SESSION 쿠키에서 꺼낸 세션 ID로 행을 찾는다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class OperatorSessionLifetimeIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-GH342";
	private static final String LOGIN_ID = "qello-admin-gh342";
	private static final String PASSWORD = "example-operator-password";
	private static final String BACKOFFICE_PATH = "/admin/filtering/manual-review-cases";
	private static final String OPERATOR_API_PATH = "/api/v1/operator/report-cases";
	private static final String UNAUTHORIZED_CODE = "CMN-VAL-003";
	private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(12);
	private static final Duration IDLE_TIMEOUT = Duration.ofHours(8);
	// 요청 처리 중에 흐르는 시간 때문에 경계에서 판정이 뒤집히지 않게 둔 여유.
	private static final Duration MARGIN = Duration.ofMinutes(1);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OperatorSeedService operatorSeedService;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void seedOperator() {
		jdbcTemplate.update("DELETE FROM SPRING_SESSION");
		// operator_credential은 user_account를 ON DELETE CASCADE로 참조한다.
		jdbcTemplate.update("DELETE FROM user_account WHERE nickname = ?", LOGIN_ID);
		jdbcTemplate.update("""
				INSERT INTO region_code (code, display_name, level)
				VALUES (?, 'GH342 Test Country', 'COUNTRY')
				ON CONFLICT (code, level) DO NOTHING
				""", REGION_CODE);
		operatorSeedService.seedIfAbsent(
				LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), LOGIN_ID, REGION_CODE, "ko-KR", "Asia/Seoul");
	}

	@Test
	@DisplayName("INT-001: 로그인으로 만든 세션 행의 미사용 만료는 8시간(28800초)이다")
	void loginStoresEightHourIdleTimeout() throws Exception {
		OperatorLogin login = login();

		Integer maxInactiveInterval = jdbcTemplate.queryForObject(
				"SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE SESSION_ID = ?",
				Integer.class, login.sessionId());

		assertThat(maxInactiveInterval).isEqualTo((int) IDLE_TIMEOUT.toSeconds());
	}

	@Test
	@DisplayName("INT-002: 기본 설정의 SESSION 쿠키는 Secure, HttpOnly, SameSite=Lax다")
	void sessionCookieIsSecureHttpOnlyLax() throws Exception {
		mockMvc.perform(loginRequest(csrf(issueCsrf())))
				.andExpect(status().isOk())
				.andExpect(cookie().secure("SESSION", true))
				.andExpect(cookie().httpOnly("SESSION", true))
				.andExpect(cookie().sameSite("SESSION", "Lax"));
	}

	@Test
	@DisplayName("INT-003: 생성 후 12시간이 지난 세션으로 /admin을 호출하면 401이고 세션 행이 지워진다")
	void backofficeRejectsSessionPastAbsoluteTimeout() throws Exception {
		OperatorLogin login = login();
		ageSession(login, ABSOLUTE_TIMEOUT.plus(MARGIN));

		mockMvc.perform(backofficeRequest().cookie(login.sessionCookie()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value("error"))
				.andExpect(jsonPath("$.errorDetail.code").value(UNAUTHORIZED_CODE));

		assertThat(sessionRows(login)).isZero();
	}

	@Test
	@DisplayName("INT-004: 생성 후 12시간이 지난 세션으로 /api/v1/operator를 호출하면 401이고 세션 행이 지워진다")
	void operatorApiRejectsSessionPastAbsoluteTimeout() throws Exception {
		OperatorLogin login = login();
		ageSession(login, ABSOLUTE_TIMEOUT.plus(MARGIN));

		mockMvc.perform(get(OPERATOR_API_PATH).cookie(login.sessionCookie()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value("error"))
				.andExpect(jsonPath("$.errorDetail.code").value(UNAUTHORIZED_CODE));

		assertThat(sessionRows(login)).isZero();
	}

	@Test
	@DisplayName("INT-005: 생성 후 12시간이 되기 전의 세션은 두 경로 모두 통과한다")
	void sessionBeforeAbsoluteTimeoutPasses() throws Exception {
		OperatorLogin login = login();
		ageSession(login, ABSOLUTE_TIMEOUT.minus(MARGIN));

		mockMvc.perform(backofficeRequest().cookie(login.sessionCookie()))
				.andExpect(status().isOk());
		mockMvc.perform(get(OPERATOR_API_PATH).cookie(login.sessionCookie()))
				.andExpect(status().isOk());

		assertThat(sessionRows(login)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-006: 최대 수명이 지난 세션 쿠키를 가진 채로도 다시 로그인할 수 있다")
	void canLogInAgainWithExpiredSessionCookie() throws Exception {
		OperatorLogin expired = login();
		ageSession(expired, ABSOLUTE_TIMEOUT.plus(MARGIN));
		Instant beforeRelogin = Instant.now();

		// 로그인 요청 자체가 만료 세션 쿠키를 싣고 오는 경우를 본다. 필터가 여기서 401을 내면 다시 로그인할
		// 방법이 없다.
		MvcResult relogin = mockMvc.perform(loginRequest(csrf(issueCsrf())).cookie(expired.sessionCookie()))
				.andExpect(status().isOk())
				.andReturn();

		Cookie renewed = relogin.getResponse().getCookie("SESSION");
		assertThat(renewed).isNotNull();
		assertThat(renewed.getValue()).isNotEqualTo(expired.sessionCookie().getValue());
		Long creationTime = jdbcTemplate.queryForObject(
				"SELECT CREATION_TIME FROM SPRING_SESSION WHERE SESSION_ID = ?", Long.class,
				sessionIdOf(renewed));
		assertThat(creationTime).isGreaterThanOrEqualTo(beforeRelogin.minus(MARGIN).toEpochMilli());
	}

	@Test
	@DisplayName("INT-007: 8시간 넘게 쓰지 않은 세션은 401이고, 8시간 전이면 통과한다")
	void idleTimeoutIsEightHours() throws Exception {
		OperatorLogin active = login();
		idleSession(active, IDLE_TIMEOUT.minus(MARGIN));

		mockMvc.perform(get(OPERATOR_API_PATH).cookie(active.sessionCookie()))
				.andExpect(status().isOk());

		OperatorLogin idle = login();
		idleSession(idle, IDLE_TIMEOUT.plus(MARGIN));

		mockMvc.perform(get(OPERATOR_API_PATH).cookie(idle.sessionCookie()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorDetail.code").value(UNAUTHORIZED_CODE));
		assertThat(sessionRows(idle)).isZero();
	}

	@Test
	@DisplayName("INT-008: 앱 API 경로는 최대 수명이 지난 운영자 세션 행을 건드리지 않는다")
	void appApiChainDoesNotTouchOperatorSession() throws Exception {
		OperatorLogin login = login();
		ageSession(login, ABSOLUTE_TIMEOUT.plus(MARGIN));

		mockMvc.perform(get("/api/v1/anything").cookie(login.sessionCookie()))
				.andExpect(status().isUnauthorized());

		assertThat(sessionRows(login)).isEqualTo(1);
	}

	private OperatorLogin login() throws Exception {
		MvcResult result = mockMvc.perform(loginRequest(csrf(issueCsrf())))
				.andExpect(status().isOk())
				.andReturn();
		Cookie sessionCookie = result.getResponse().getCookie("SESSION");
		assertThat(sessionCookie).isNotNull();
		return new OperatorLogin(sessionCookie);
	}

	private MvcResult issueCsrf() throws Exception {
		return mockMvc.perform(get("/admin/csrf")).andExpect(status().isOk()).andReturn();
	}

	private Csrf csrf(MvcResult issued) throws Exception {
		JsonNode data = data(issued);
		return new Csrf(data.get("headerName").asText(), data.get("token").asText(), issued.getResponse().getCookies());
	}

	private JsonNode data(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	// spring-security-test의 csrf() post-processor를 쓰지 않는다. 설정된
	// CookieCsrfTokenRepository를
	// 덮어쓰기 때문이다(OperatorLoginIntegrationTest와 같은 이유). 실제 발급 흐름을 그대로 쓴다.
	private MockHttpServletRequestBuilder loginRequest(Csrf csrf) {
		return post("/admin/login")
				.header(csrf.headerName(), csrf.token())
				.cookie(csrf.cookies())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD));
	}

	private MockHttpServletRequestBuilder backofficeRequest() {
		return get(BACKOFFICE_PATH).param("agingThresholdSeconds", "60");
	}

	private void ageSession(OperatorLogin login, Duration age) {
		long createdAt = Instant.now().minus(age).toEpochMilli();
		int updated = jdbcTemplate.update(
				"UPDATE SPRING_SESSION SET CREATION_TIME = ? WHERE SESSION_ID = ?", createdAt,
				login.sessionId());
		assertThat(updated).isEqualTo(1);
	}

	// 생성 시각도 같이 옮겨 최대 수명(12시간)은 넘지 않게 한다. 이 시나리오는 미사용 만료만 본다.
	private void idleSession(OperatorLogin login, Duration idle) {
		long lastAccessedAt = Instant.now().minus(idle).toEpochMilli();
		int updated = jdbcTemplate.update("""
				UPDATE SPRING_SESSION
				SET CREATION_TIME = ?, LAST_ACCESS_TIME = ?, EXPIRY_TIME = ? + MAX_INACTIVE_INTERVAL * 1000
				WHERE SESSION_ID = ?
				""", lastAccessedAt, lastAccessedAt, lastAccessedAt, login.sessionId());
		assertThat(updated).isEqualTo(1);
	}

	private int sessionRows(OperatorLogin login) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, login.sessionId());
		return count == null ? 0 : count;
	}

	// DefaultCookieSerializer는 세션 ID를 Base64로 인코딩해 쿠키에 담는다.
	private static String sessionIdOf(Cookie sessionCookie) {
		return new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
	}

	private record OperatorLogin(Cookie sessionCookie) {

		String sessionId() {
			return sessionIdOf(sessionCookie);
		}
	}

	private record Csrf(String headerName, String token, Cookie[] cookies) {
	}
}
