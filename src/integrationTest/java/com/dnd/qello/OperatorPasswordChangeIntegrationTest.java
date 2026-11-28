/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-002 through INT-007, INT-009
 */
package com.dnd.qello;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #376: 초기 비밀번호 세션의 운영자 API 제한, 비밀번호 변경 API의 규칙·실패 카운터·세션 재발급, 접근 기록을 실제 보안
// 체인과 PostgreSQL로 검증한다. OperatorLoginIntegrationTest와 같은 설정이라 컨텍스트를 공유한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class OperatorPasswordChangeIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final String LOGIN_ID = "qello-password-admin";
	private static final String SEED_PASSWORD = "example-operator-password";
	private static final String NEW_PASSWORD = "Changed-Passw0rd-376";
	private static final String REPORT_CASES = "/api/v1/operator/report-cases";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OperatorSeedService operatorSeedService;

	@Autowired
	private ObjectMapper objectMapper;

	private long operatorUserId;
	private Csrf csrf;

	@BeforeEach
	void seedOperator() throws Exception {
		jdbcTemplate.update("DELETE FROM operator_access_log");
		jdbcTemplate.update("DELETE FROM operator_credential");
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("""
				INSERT INTO region_code (code, display_name, level)
				VALUES (?, 'Test Country', 'COUNTRY')
				""", REGION_CODE);
		operatorSeedService.seedIfAbsent(LoginId.of(LOGIN_ID), new RawPassword(SEED_PASSWORD), "password-admin",
				REGION_CODE, "ko-KR", "Asia/Seoul");
		operatorUserId = jdbcTemplate.queryForObject(
				"SELECT user_id FROM operator_credential WHERE login_id = ?", Long.class, LOGIN_ID);
		csrf = issueCsrf();
	}

	@Test
	@DisplayName("INT-002: 시드 운영자는 변경 필요로 로그인되고 운영자 API는 403 AUT-APP-008, 로그아웃은 된다")
	void initialPasswordSessionIsRestricted() throws Exception {
		MvcResult loggedIn = mockMvc.perform(login(SEED_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(operatorUserId))
				.andExpect(jsonPath("$.data.passwordChangeRequired").value(true))
				.andReturn();
		Cookie session = sessionCookie(loggedIn);

		mockMvc.perform(get(REPORT_CASES).cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-008"));
		mockMvc.perform(get("/admin/filtering/manual-review-cases").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-008"));
		mockMvc.perform(withCsrf(post("/admin/logout")).cookie(session))
				.andExpect(status().isOk());
		mockMvc.perform(get(REPORT_CASES).cookie(session))
				.andExpect(status().isUnauthorized());

		// 제한 세션의 로그아웃도 운영자 id와 함께 남는다(UNIT-017).
		List<Map<String, Object>> logouts = jdbcTemplate.queryForList(
				"SELECT * FROM operator_access_log WHERE event_type = 'LOGOUT'");
		assertThat(logouts).singleElement().satisfies(row -> assertThat(row)
				.containsEntry("operator_user_id", operatorUserId)
				.containsEntry("http_status", 200));
	}

	@Test
	@DisplayName("INT-003: 변경하면 세션 ID가 바뀌고 이전 쿠키는 401, 새 쿠키는 운영자 API 200이며 이전 비밀번호로는 로그인할 수 없다")
	void changingPasswordRotatesSessionAndUnlocksOperatorApi() throws Exception {
		Cookie restricted = sessionCookie(mockMvc.perform(login(SEED_PASSWORD)).andExpect(status().isOk()).andReturn());
		Credential before = credential();
		long sessionCreatedBefore = sessionCreationTime(restricted);

		MvcResult changed = mockMvc.perform(changePassword(restricted, SEED_PASSWORD, NEW_PASSWORD))
				.andExpect(status().isOk())
				.andReturn();
		Cookie rotated = sessionCookie(changed);

		assertThat(rotated.getValue()).isNotEqualTo(restricted.getValue());
		mockMvc.perform(get(REPORT_CASES).cookie(restricted)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(REPORT_CASES).cookie(rotated)).andExpect(status().isOk());
		Credential after = credential();
		assertThat(after.passwordHash()).isNotEqualTo(before.passwordHash());
		assertThat(after.passwordUpdatedAt()).isAfter(before.passwordUpdatedAt());
		assertThat(after.changeRequired()).isFalse();
		// 세션 ID만 바뀌고 절대 만료의 기준인 생성 시각은 그대로다.
		assertThat(sessionCreationTime(rotated)).isEqualTo(sessionCreatedBefore);

		mockMvc.perform(withCsrf(post("/admin/logout")).cookie(rotated)).andExpect(status().isOk());
		mockMvc.perform(login(SEED_PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-001"));
		mockMvc.perform(login(NEW_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.passwordChangeRequired").value(false));
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("INT-004: 길이 미달·종류 부족·login_id 동일·ASCII 밖 문자·73자는 400 AUT-VAL-005이고 자격증명은 그대로다")
	@ValueSource(strings = {"Short1!", "onlylowercaseletters", "QELLO-PASSWORD-ADMIN", "Abcdef1!한글",
			"Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!x"})
	void rejectsPolicyViolations(String newPassword) throws Exception {
		Cookie session = sessionCookie(mockMvc.perform(login(SEED_PASSWORD)).andExpect(status().isOk()).andReturn());
		Credential before = credential();

		mockMvc.perform(changePassword(session, SEED_PASSWORD, newPassword))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-VAL-005"))
				.andExpect(jsonPath("$.errorDetail.field").value("newPassword"));

		assertThat(credential()).isEqualTo(before);
	}

	@Test
	@DisplayName("INT-004: 새 비밀번호가 현재 비밀번호와 같으면 400 AUT-APP-010이고 자격증명은 그대로다")
	void rejectsUnchangedPassword() throws Exception {
		Cookie session = sessionCookie(mockMvc.perform(login(SEED_PASSWORD)).andExpect(status().isOk()).andReturn());
		Credential before = credential();

		mockMvc.perform(changePassword(session, SEED_PASSWORD, SEED_PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-010"));

		assertThat(credential()).isEqualTo(before);
	}

	@Test
	@DisplayName("INT-005: 현재 비밀번호를 5회 틀리면 잠기고 여섯 번째 변경과 로그인은 423 AUT-APP-002다")
	void locksAfterFiveWrongCurrentPasswords() throws Exception {
		Cookie session = sessionCookie(mockMvc.perform(login(SEED_PASSWORD)).andExpect(status().isOk()).andReturn());

		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc.perform(changePassword(session, "wrong-current-password", NEW_PASSWORD))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-009"));
		}

		assertThat(credential().failedAttemptCount()).isEqualTo(5);
		assertThat(credential().lockedUntil()).isNotNull();
		mockMvc.perform(changePassword(session, SEED_PASSWORD, NEW_PASSWORD))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-002"));
		mockMvc.perform(login(SEED_PASSWORD))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-002"));
	}

	@Test
	@DisplayName("INT-006: CSRF 토큰 없는 변경은 공통 403이고, 세션 없는 변경은 401이며 둘 다 자격증명을 바꾸지 않는다")
	void csrfAndSessionFailuresDoNotUseChangeRequiredCode() throws Exception {
		Cookie session = sessionCookie(mockMvc.perform(login(SEED_PASSWORD)).andExpect(status().isOk()).andReturn());
		Credential before = credential();

		mockMvc.perform(post("/admin/password").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(SEED_PASSWORD, NEW_PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("CMN-DOM-001"));
		mockMvc.perform(withCsrf(post("/admin/password"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(SEED_PASSWORD, NEW_PASSWORD)))
				.andExpect(status().isUnauthorized());

		assertThat(credential()).isEqualTo(before);
	}

	@Test
	@DisplayName("INT-007: 변경 성공·현재 비밀번호 불일치·제한 세션의 거부가 운영자 id와 함께 API_CALL로 남고 비밀번호는 없다")
	void recordsPasswordChangeCallsWithoutPasswords() throws Exception {
		Cookie session = sessionCookie(mockMvc.perform(login(SEED_PASSWORD)).andExpect(status().isOk()).andReturn());

		mockMvc.perform(get(REPORT_CASES).cookie(session)).andExpect(status().isForbidden());
		mockMvc.perform(changePassword(session, "wrong-current-password", NEW_PASSWORD))
				.andExpect(status().isBadRequest());
		mockMvc.perform(changePassword(session, SEED_PASSWORD, NEW_PASSWORD)).andExpect(status().isOk());

		List<Map<String, Object>> apiCalls = jdbcTemplate.queryForList(
				"SELECT * FROM operator_access_log WHERE event_type = 'API_CALL' ORDER BY id");
		assertThat(apiCalls).extracting(row -> row.get("http_method") + " " + row.get("request_path") + " "
				+ row.get("http_status"))
				.containsExactly("GET " + REPORT_CASES + " 403", "POST /admin/password 400",
						"POST /admin/password 200");
		assertThat(apiCalls).allSatisfy(row -> assertThat(row).containsEntry("operator_user_id", operatorUserId));
		String allRows = objectMapper.writeValueAsString(
				jdbcTemplate.queryForList("SELECT * FROM operator_access_log ORDER BY id"));
		assertThat(allRows).doesNotContain(SEED_PASSWORD, NEW_PASSWORD, "wrong-current-password");
	}

	@Test
	@DisplayName("INT-009: 변경이 필요 없는 정상 세션도 스스로 비밀번호를 바꿀 수 있고 세션 ID가 바뀌며 운영자 API는 계속 200이다")
	void fullSessionCanChangePasswordVoluntarily() throws Exception {
		OperatorTestAccounts.completePasswordChange(jdbcTemplate, LOGIN_ID);
		MvcResult loggedIn = mockMvc.perform(login(SEED_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.passwordChangeRequired").value(false))
				.andReturn();
		Cookie session = sessionCookie(loggedIn);
		mockMvc.perform(get(REPORT_CASES).cookie(session)).andExpect(status().isOk());

		Cookie rotated = sessionCookie(mockMvc.perform(changePassword(session, SEED_PASSWORD, NEW_PASSWORD))
				.andExpect(status().isOk())
				.andReturn());

		assertThat(rotated.getValue()).isNotEqualTo(session.getValue());
		mockMvc.perform(get(REPORT_CASES).cookie(rotated)).andExpect(status().isOk());
		assertThat(credential().changeRequired()).isFalse();
	}

	private MockHttpServletRequestBuilder login(String password) {
		return withCsrf(post("/admin/login"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, password));
	}

	private MockHttpServletRequestBuilder changePassword(Cookie session, String currentPassword, String newPassword)
			throws Exception {
		return withCsrf(post("/admin/password"))
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(currentPassword, newPassword));
	}

	private String body(String currentPassword, String newPassword) throws Exception {
		return objectMapper.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword));
	}

	// spring-security-test의 csrf() post-processor를 쓰지
	// 않는다(OperatorLoginIntegrationTest 주석과 같은 이유).
	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
		return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
	}

	private Csrf issueCsrf() throws Exception {
		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andExpect(status().isOk()).andReturn();
		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		return new Csrf(data.get("headerName").asText(), data.get("token").asText(),
				issued.getResponse().getCookies());
	}

	private static Cookie sessionCookie(MvcResult result) {
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).as("SESSION 쿠키").isNotNull();
		return session;
	}

	private Credential credential() {
		return jdbcTemplate.queryForObject("""
				SELECT password_hash, password_updated_at, password_change_required, failed_attempt_count, locked_until
				FROM operator_credential
				WHERE user_id = ?
				""", (resultSet, rowNumber) -> new Credential(
				resultSet.getString(1),
				resultSet.getTimestamp(2).toInstant(),
				resultSet.getBoolean(3),
				resultSet.getInt(4),
				resultSet.getTimestamp(5)), operatorUserId);
	}

	// SESSION 쿠키 값은 세션 ID를 Base64로 인코딩한 값이다(Spring Session 기본 쿠키 직렬화).
	private long sessionCreationTime(Cookie session) {
		String sessionId = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8);
		return jdbcTemplate.queryForObject(
				"SELECT CREATION_TIME FROM SPRING_SESSION WHERE SESSION_ID = ?", Long.class, sessionId);
	}

	private record Csrf(String headerName, String token, Cookie[] cookies) {
	}

	private record Credential(String passwordHash, Instant passwordUpdatedAt, boolean changeRequired,
			int failedAttemptCount, Timestamp lockedUntil) {
	}
}
