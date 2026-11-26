/*
 * Created at: 2026-11-22T22:14:54+09:00
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-002 through INT-006, INT-008 through INT-015, INT-018
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-010 (seed helper, added 2026-11-24T23:50:07+09:00)
 */
package com.dnd.qello;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.IllegalTransactionStateException;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorAccessLogEntry;
import com.dnd.qello.auth.domain.OperatorAccessRequest;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorAccessLogRecorder;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #375: 운영자 로그인 결과·로그아웃·운영자 API 호출이 operator_access_log에 한 행씩 남는지 실제 보안 체인과
// PostgreSQL로 검증한다. OperatorLoginIntegrationTest와 같은 설정이라 컨텍스트를 공유한다. 요청 제한 카운터도
// 공유되므로 테스트마다 다른 문서용 주소(192.0.2.0/24)를 쓴다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class OperatorAccessLogIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final String LOGIN_ID = "qello-access-admin";
	private static final String PASSWORD = "example-operator-password";
	private static final String REPORT_CASES = "/api/v1/operator/report-cases";
	private static final String MANUAL_REVIEW_CASES = "/admin/filtering/manual-review-cases";
	private static final String SPOOFED_FORWARDED_FOR = "198.51.100.7";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OperatorSeedService operatorSeedService;

	@Autowired
	private OperatorAccessLogRecorder accessLogRecorder;

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
		operatorSeedService.seedIfAbsent(
				LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), "access-admin", REGION_CODE, "ko-KR", "Asia/Seoul");
		OperatorTestAccounts.completePasswordChange(jdbcTemplate, LOGIN_ID);
		operatorUserId = jdbcTemplate.queryForObject(
				"SELECT user_id FROM operator_credential WHERE login_id = ?", Long.class, LOGIN_ID);
		csrf = issueCsrf();
	}

	@Test
	@DisplayName("INT-002: 로그인 성공은 연결 주소와 판정 결과로 한 행 남고 X-Forwarded-For는 무시된다")
	void recordsSuccessfulLogin() throws Exception {
		Instant before = Instant.now();
		MvcResult result = mockMvc.perform(login(LOGIN_ID, PASSWORD).with(from("192.0.2.21"))
				.header("X-Forwarded-For", SPOOFED_FORWARDED_FOR))
				.andExpect(status().isOk())
				.andReturn();
		Instant after = Instant.now();
		long responseUserId = data(result).get("userId").asLong();

		assertThat(rows()).singleElement().satisfies(row -> {
			assertThat(row).containsEntry("event_type", "LOGIN_SUCCEEDED")
					.containsEntry("operator_user_id", responseUserId)
					.containsEntry("client_address", "192.0.2.21")
					.containsEntry("http_method", "POST")
					.containsEntry("request_path", "/admin/login")
					.containsEntry("http_status", 200)
					.containsEntry("result_code", null);
			Instant occurredAt = ((Timestamp) row.get("occurred_at")).toInstant();
			assertThat(occurredAt).isBetween(before.minusSeconds(1), after.plusSeconds(1));
		});
	}

	@Test
	@DisplayName("INT-003: 존재하지 않는 loginId는 운영자 id 없이 401·AUT-APP-001로 한 행 남는다")
	void recordsUnknownLoginIdWithoutOperator() throws Exception {
		mockMvc.perform(login("no-such-operator", PASSWORD).with(from("192.0.2.22")))
				.andExpect(status().isUnauthorized());

		assertThat(rows()).singleElement().satisfies(row -> assertThat(row)
				.containsEntry("event_type", "LOGIN_FAILED")
				.containsEntry("operator_user_id", null)
				.containsEntry("http_status", 401)
				.containsEntry("result_code", "AUT-APP-001"));
	}

	@Test
	@DisplayName("INT-004: 잘못된 비밀번호는 실패 카운터와 같은 커밋으로 운영자 id와 함께 한 행 남는다")
	void recordsWrongPasswordTogetherWithFailureCounter() throws Exception {
		mockMvc.perform(login(LOGIN_ID, "wrong-password").with(from("192.0.2.23")))
				.andExpect(status().isUnauthorized());

		assertThat(rows()).singleElement().satisfies(row -> assertThat(row)
				.containsEntry("event_type", "LOGIN_FAILED")
				.containsEntry("operator_user_id", operatorUserId)
				.containsEntry("http_status", 401)
				.containsEntry("result_code", "AUT-APP-001"));
		assertThat(failedAttemptCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-005: 실패 5회 뒤 올바른 비밀번호는 423이고 여섯 번째 행이 423·AUT-APP-002로 남는다")
	void recordsLockedAttemptAfterFiveFailures() throws Exception {
		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc.perform(login(LOGIN_ID, "wrong-password").with(from("192.0.2.24")))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(login(LOGIN_ID, PASSWORD).with(from("192.0.2.24")))
				.andExpect(status().isLocked());

		List<Map<String, Object>> rows = rows();
		assertThat(rows).hasSize(6)
				.allSatisfy(row -> assertThat(row).containsEntry("operator_user_id", operatorUserId));
		assertThat(rows.getLast()).containsEntry("http_status", 423)
				.containsEntry("result_code", "AUT-APP-002");
	}

	@Test
	@DisplayName("INT-006: 비활성 계정은 세션 없이 403·AUT-APP-003으로 운영자 id와 함께 한 행 남는다")
	void recordsInactiveAccount() throws Exception {
		jdbcTemplate.update("UPDATE user_account SET status = 'BLOCKED' WHERE id = ?", operatorUserId);

		MvcResult result = mockMvc.perform(login(LOGIN_ID, PASSWORD).with(from("192.0.2.25")))
				.andExpect(status().isForbidden())
				.andReturn();

		assertThat(result.getResponse().getCookie("SESSION")).isNull();
		assertThat(rows()).singleElement().satisfies(row -> assertThat(row)
				.containsEntry("event_type", "LOGIN_FAILED")
				.containsEntry("operator_user_id", operatorUserId)
				.containsEntry("http_status", 403)
				.containsEntry("result_code", "AUT-APP-003"));
	}

	@Test
	@DisplayName("INT-008: 로그아웃은 한 행 남고, 무효화된 세션으로 다시 부른 요청은 401이며 행을 남기지 않는다")
	void recordsLogoutAndNotTheRejectedFollowUp() throws Exception {
		Cookie session = loginSession("192.0.2.26");

		mockMvc.perform(withCsrf(post("/admin/logout")).cookie(session).with(from("192.0.2.26")))
				.andExpect(status().isOk());
		mockMvc.perform(get(REPORT_CASES).cookie(session).with(from("192.0.2.26")))
				.andExpect(status().isUnauthorized());

		assertThat(eventTypes()).containsExactly("LOGIN_SUCCEEDED", "LOGOUT");
		assertThat(rows().getLast()).containsEntry("operator_user_id", operatorUserId)
				.containsEntry("request_path", "/admin/logout")
				.containsEntry("http_status", 200);
	}

	@Test
	@DisplayName("INT-009: 두 세션 체인의 운영자 API 호출이 query 없는 경로로 각각 한 행 남는다")
	void recordsOperatorApiCallsOnBothChains() throws Exception {
		Cookie session = loginSession("192.0.2.27");

		mockMvc.perform(get(REPORT_CASES).cookie(session).with(from("192.0.2.27")))
				.andExpect(status().isOk());
		mockMvc.perform(get(MANUAL_REVIEW_CASES).param("agingThresholdSeconds", "60").cookie(session)
				.with(from("192.0.2.27")))
				.andExpect(status().isOk());

		List<Map<String, Object>> apiCalls = rows("API_CALL");
		assertThat(apiCalls).extracting(row -> row.get("request_path"))
				.containsExactly(REPORT_CASES, MANUAL_REVIEW_CASES);
		assertThat(apiCalls).allSatisfy(row -> assertThat(row)
				.containsEntry("operator_user_id", operatorUserId)
				.containsEntry("http_method", "GET")
				.containsEntry("http_status", 200)
				.containsEntry("client_address", "192.0.2.27"));
	}

	@Test
	@DisplayName("INT-010: 실패한 운영자 API 호출도 실제 응답 상태와 사건 id가 들어간 경로로 남는다")
	void recordsFailedOperatorApiCallWithActualStatus() throws Exception {
		Cookie session = loginSession("192.0.2.28");

		MvcResult result = mockMvc.perform(withCsrf(post(REPORT_CASES + "/999999/review")).cookie(session)
				.with(from("192.0.2.28"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"BODY-MARKER-375\"}"))
				.andReturn();
		int responseStatus = result.getResponse().getStatus();

		assertThat(responseStatus).isBetween(400, 499);
		assertThat(rows("API_CALL")).singleElement().satisfies(row -> assertThat(row)
				.containsEntry("http_method", "POST")
				.containsEntry("request_path", REPORT_CASES + "/999999/review")
				.containsEntry("http_status", responseStatus));
	}

	@Test
	@DisplayName("INT-011: CSRF 토큰 없이 보안 필터에서 거절된 인증된 요청도 403으로 남는다")
	void recordsAuthenticatedRequestRejectedByCsrfFilter() throws Exception {
		Cookie session = loginSession("192.0.2.29");

		mockMvc.perform(post(REPORT_CASES + "/999999/review").cookie(session).with(from("192.0.2.29")))
				.andExpect(status().isForbidden());

		assertThat(rows("API_CALL")).singleElement().satisfies(row -> assertThat(row)
				.containsEntry("operator_user_id", operatorUserId)
				.containsEntry("http_status", 403));
	}

	@Test
	@DisplayName("INT-012: CSRF 발급·로그인·로그아웃은 필터가 따로 기록하지 않아 결과 행만 남는다")
	void excludedPathsLeaveOnlyOutcomeRows() throws Exception {
		Cookie session = loginSession("192.0.2.30");
		mockMvc.perform(get("/admin/csrf").cookie(session).with(from("192.0.2.30")))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(post("/admin/logout")).cookie(session).with(from("192.0.2.30")))
				.andExpect(status().isOk());

		assertThat(eventTypes()).containsExactly("LOGIN_SUCCEEDED", "LOGOUT");
	}

	@Test
	@DisplayName("INT-013: 세션 없는 세션 체인 요청과 앱 API 요청은 401이고 행을 남기지 않는다")
	void anonymousRequestsLeaveNoRows() throws Exception {
		mockMvc.perform(get(REPORT_CASES).with(from("192.0.2.31")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(MANUAL_REVIEW_CASES).param("agingThresholdSeconds", "60").with(from("192.0.2.31")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/anything").with(from("192.0.2.31")))
				.andExpect(status().isUnauthorized());

		assertThat(rows()).isEmpty();
	}

	@Test
	@DisplayName("INT-014: 어떤 행에도 비밀번호·세션 ID·CSRF 토큰·요청 본문·query string이 없다")
	void rowsContainNoSecrets() throws Exception {
		Cookie session = loginSession("192.0.2.32");
		mockMvc.perform(get(MANUAL_REVIEW_CASES).param("agingThresholdSeconds", "60").cookie(session)
				.with(from("192.0.2.32")))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(REPORT_CASES + "/999999/review")).cookie(session).with(from("192.0.2.32"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"BODY-MARKER-375\"}"));
		String decodedSessionId = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8);
		// 응답의 token은 XOR로 가린 값이고 실제 비밀값은 XSRF-TOKEN 쿠키 원문이다. 둘 다 검사한다.
		String rawCsrfCookie = Arrays.stream(csrf.cookies())
				.filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElseThrow();

		List<String> serializedRows = jdbcTemplate.queryForList(
				"SELECT row_to_json(l)::text FROM operator_access_log l ORDER BY id", String.class);

		assertThat(serializedRows).hasSize(3).allSatisfy(row -> assertThat(row).doesNotContain(
				PASSWORD, session.getValue(), decodedSessionId, csrf.token(), rawCsrfCookie, "BODY-MARKER-375",
				"agingThresholdSeconds", "?"));
	}

	@Test
	@DisplayName("INT-015: 트랜잭션 안의 기록 메서드는 트랜잭션 없이 호출하면 실패하고 행을 남기지 않는다")
	void withinTransactionRecordingRequiresCallerTransaction() {
		OperatorAccessLogEntry entry = OperatorAccessLogEntry.loginSucceeded(
				operatorUserId, new OperatorAccessRequest("192.0.2.33", "POST", "/admin/login"), Instant.now());

		assertThatThrownBy(() -> accessLogRecorder.recordWithinTransaction(entry))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(rows()).isEmpty();
	}

	@Test
	@DisplayName("INT-018: 같은 세션의 동시 API 호출 10건이 모두 한 행씩 남는다")
	void concurrentCallsAreAllRecorded() throws Exception {
		Cookie session = loginSession("192.0.2.34");
		List<Callable<Integer>> calls = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			calls.add(() -> mockMvc.perform(get(REPORT_CASES).cookie(session).with(from("192.0.2.34")))
					.andReturn().getResponse().getStatus());
		}

		ExecutorService executor = Executors.newFixedThreadPool(10);
		List<Integer> statuses = new ArrayList<>();
		try {
			for (Future<Integer> future : executor.invokeAll(calls)) {
				statuses.add(future.get());
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(statuses).hasSize(10).containsOnly(200);
		assertThat(rows("API_CALL")).hasSize(10);
	}

	private Cookie loginSession(String remoteAddress) throws Exception {
		Cookie session = mockMvc.perform(login(LOGIN_ID, PASSWORD).with(from(remoteAddress)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return session;
	}

	private MockHttpServletRequestBuilder login(String loginId, String password) {
		return withCsrf(post("/admin/login"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password));
	}

	// spring-security-test의 csrf() post-processor를 쓰지
	// 않는다(OperatorLoginIntegrationTest 주석과 같은 이유).
	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
		return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
	}

	private Csrf issueCsrf() throws Exception {
		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andExpect(status().isOk()).andReturn();
		JsonNode data = data(issued);
		return new Csrf(data.get("headerName").asText(), data.get("token").asText(),
				issued.getResponse().getCookies());
	}

	private JsonNode data(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private static RequestPostProcessor from(String remoteAddress) {
		return request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		};
	}

	private List<Map<String, Object>> rows() {
		return jdbcTemplate.queryForList("SELECT * FROM operator_access_log ORDER BY id");
	}

	private List<Map<String, Object>> rows(String eventType) {
		return jdbcTemplate.queryForList(
				"SELECT * FROM operator_access_log WHERE event_type = ? ORDER BY id", eventType);
	}

	private List<String> eventTypes() {
		return jdbcTemplate.queryForList("SELECT event_type FROM operator_access_log ORDER BY id", String.class);
	}

	private int failedAttemptCount() {
		return jdbcTemplate.queryForObject(
				"SELECT failed_attempt_count FROM operator_credential WHERE login_id = ?", Integer.class, LOGIN_ID);
	}

	private record Csrf(String headerName, String token, Cookie[] cookies) {
	}
}
