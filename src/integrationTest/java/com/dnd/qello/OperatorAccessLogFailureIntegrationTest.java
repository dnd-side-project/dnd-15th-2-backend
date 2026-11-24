/*
 * Created at: 2026-11-22T22:14:54+09:00
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-INT-016 through INT-017
 */
package com.dnd.qello;

import java.sql.Timestamp;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.domain.OperatorAccessEvent;
import com.dnd.qello.auth.repository.OperatorAccessLogRepository;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #375 D4: 접근 기록 저장이 실패할 때의 동작을 실제 트랜잭션으로 검증한다. 로그인 결과 기록이 실패하면 판정 전체가
// 롤백되고 세션이 생기지 않는다(fail-closed). API 호출·로그아웃 기록이 실패해도 응답은 그대로다(fail-open).
// recorder가 아니라 저장소를 spy로 바꿔 recorder의 실제 전파·예외 처리를 거치게 한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
class OperatorAccessLogFailureIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final String LOGIN_ID = "qello-failure-admin";
	private static final String PASSWORD = "example-operator-password";
	private static final String REPORT_CASES = "/api/v1/operator/report-cases";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OperatorSeedService operatorSeedService;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoSpyBean
	private OperatorAccessLogRepository accessLogRepository;

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
				LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), "failure-admin", REGION_CODE, "ko-KR", "Asia/Seoul");
		csrf = issueCsrf();
	}

	@AfterEach
	void resetSpy() {
		reset(accessLogRepository);
	}

	@Test
	@DisplayName("INT-016: 로그인 실패 기록이 저장되지 않으면 500이고 실패 카운터 증가도 함께 롤백된다")
	void failedLoginRecordingRollsBackFailureCounter() throws Exception {
		failAppendFor(OperatorAccessEvent.LOGIN_FAILED);

		mockMvc.perform(login(PASSWORD + "-wrong").with(from("192.0.2.41")))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value("error"));

		assertThat(credentialInt("failed_attempt_count")).isZero();
		assertThat(accessLogRows()).isZero();
	}

	@Test
	@DisplayName("INT-016: 로그인 성공 기록이 저장되지 않으면 500이고 세션도 성공 갱신도 남지 않는다")
	void successfulLoginRecordingFailureCreatesNoSession() throws Exception {
		failAppendFor(OperatorAccessEvent.LOGIN_SUCCEEDED);

		MvcResult result = mockMvc.perform(login(PASSWORD).with(from("192.0.2.42")))
				.andExpect(status().isInternalServerError())
				.andReturn();

		assertThat(result.getResponse().getCookie("SESSION")).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT last_login_at FROM operator_credential WHERE login_id = ?", Timestamp.class, LOGIN_ID))
				.isNull();
		assertThat(accessLogRows()).isZero();
	}

	@Test
	@DisplayName("INT-017: API 호출 기록이 실패해도 응답은 200 그대로이고, 로그아웃 기록이 실패해도 세션은 무효화된다")
	void independentRecordingFailureKeepsResponses() throws Exception {
		Cookie session = mockMvc.perform(login(PASSWORD).with(from("192.0.2.43")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");
		failAppendFor(OperatorAccessEvent.API_CALL);
		failAppendFor(OperatorAccessEvent.LOGOUT);

		mockMvc.perform(get(REPORT_CASES).cookie(session).with(from("192.0.2.43")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("success"));
		mockMvc.perform(withCsrf(post("/admin/logout")).cookie(session).with(from("192.0.2.43")))
				.andExpect(status().isOk());
		mockMvc.perform(get(REPORT_CASES).cookie(session).with(from("192.0.2.43")))
				.andExpect(status().isUnauthorized());

		// 성공한 로그인 행만 남는다. 실패한 기록은 error 로그로만 남는다.
		assertThat(jdbcTemplate.queryForList("SELECT event_type FROM operator_access_log", String.class))
				.containsExactly("LOGIN_SUCCEEDED");
	}

	private void failAppendFor(OperatorAccessEvent eventType) {
		doThrow(new DataAccessResourceFailureException("simulated access log outage"))
				.when(accessLogRepository)
				.append(argThat(entry -> entry != null && entry.eventType() == eventType));
	}

	private MockHttpServletRequestBuilder login(String password) {
		return withCsrf(post("/admin/login"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, password));
	}

	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
		return request.header(csrf.headerName(), csrf.token()).cookie(csrf.cookies());
	}

	private Csrf issueCsrf() throws Exception {
		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andExpect(status().isOk()).andReturn();
		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		return new Csrf(data.get("headerName").asText(), data.get("token").asText(),
				issued.getResponse().getCookies());
	}

	private static RequestPostProcessor from(String remoteAddress) {
		return request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		};
	}

	private int credentialInt(String column) {
		return jdbcTemplate.queryForObject(
				"SELECT " + column + " FROM operator_credential WHERE login_id = ?", Integer.class, LOGIN_ID);
	}

	private int accessLogRows() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM operator_access_log", Integer.class);
	}

	private record Csrf(String headerName, String token, Cookie[] cookies) {
	}
}
