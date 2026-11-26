/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-008
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

// #376: 변경 주기(기본 90일) 경계를 고정 Clock으로 검증한다. 마지막 변경 시각 + 90일이 되는 순간부터 변경이 필요하다.
// 고정 Clock 빈 때문에 컨텍스트를 따로 쓴다. Clock은 실제 현재 시각에서 고정해 세션 절대 만료(로그인 시각 + 12시간)가
// 테스트 중에 걸리지 않게 한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
@Import(OperatorPasswordExpiryIntegrationTest.FixedClockConfiguration.class)
class OperatorPasswordExpiryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-COUNTRY";
	private static final String EXPIRED_LOGIN_ID = "qello-expired-admin";
	private static final String VALID_LOGIN_ID = "qello-valid-admin";
	private static final String PASSWORD = "example-operator-password";
	private static final String NEW_PASSWORD = "Changed-Passw0rd-376";
	private static final String REPORT_CASES = "/api/v1/operator/report-cases";
	private static final Duration MAX_AGE = Duration.ofDays(90);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OperatorSeedService operatorSeedService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Clock clock;

	private Csrf csrf;

	@BeforeEach
	void seedOperators() throws Exception {
		jdbcTemplate.update("DELETE FROM operator_access_log");
		jdbcTemplate.update("DELETE FROM operator_credential");
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("""
				INSERT INTO region_code (code, display_name, level)
				VALUES (?, 'Test Country', 'COUNTRY')
				""", REGION_CODE);
		Instant now = clock.instant();
		seedChangedOperator(EXPIRED_LOGIN_ID, "expired-admin", now.minus(MAX_AGE));
		seedChangedOperator(VALID_LOGIN_ID, "valid-admin", now.minus(MAX_AGE).plusSeconds(1));
		csrf = issueCsrf();
	}

	@Test
	@DisplayName("INT-008: 마지막 변경 후 정확히 90일이면 변경이 필요하고 운영자 API는 403 AUT-APP-008, 변경 API는 200이다")
	void passwordExpiresAtExactMaxAge() throws Exception {
		MvcResult loggedIn = mockMvc.perform(login(EXPIRED_LOGIN_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.passwordChangeRequired").value(true))
				.andReturn();
		Cookie session = sessionCookie(loggedIn);

		mockMvc.perform(get(REPORT_CASES).cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-008"));
		Cookie rotated = sessionCookie(mockMvc.perform(withCsrf(post("/admin/password"))
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
				.andExpect(status().isOk())
				.andReturn());

		mockMvc.perform(get(REPORT_CASES).cookie(rotated)).andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT password_updated_at FROM operator_credential WHERE login_id = ?", Timestamp.class,
				EXPIRED_LOGIN_ID).toInstant()).isEqualTo(clock.instant());
	}

	@Test
	@DisplayName("INT-008: 마지막 변경 후 90일에서 1초 모자라면 변경이 필요 없고 운영자 API는 200이다")
	void passwordIsValidOneSecondBeforeMaxAge() throws Exception {
		MvcResult loggedIn = mockMvc.perform(login(VALID_LOGIN_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.passwordChangeRequired").value(false))
				.andReturn();

		mockMvc.perform(get(REPORT_CASES).cookie(sessionCookie(loggedIn))).andExpect(status().isOk());
	}

	// 시드 후 변경을 마친 운영자로 만들고 마지막 변경 시각을 JDBC로 직접 넣는다.
	private void seedChangedOperator(String loginId, String nickname, Instant passwordUpdatedAt) {
		operatorSeedService.seedIfAbsent(
				LoginId.of(loginId), new RawPassword(PASSWORD), nickname, REGION_CODE, "ko-KR", "Asia/Seoul");
		OperatorTestAccounts.completePasswordChange(jdbcTemplate, loginId);
		jdbcTemplate.update("UPDATE operator_credential SET password_updated_at = ? WHERE login_id = ?",
				Timestamp.from(passwordUpdatedAt), loginId);
	}

	private MockHttpServletRequestBuilder login(String loginId) {
		return withCsrf(post("/admin/login"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, PASSWORD));
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

	private static Cookie sessionCookie(MvcResult result) {
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).as("SESSION 쿠키").isNotNull();
		return session;
	}

	private record Csrf(String headerName, String token, Cookie[] cookies) {
	}

	@TestConfiguration
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock operatorPasswordExpiryTestClock() {
			return Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
		}
	}
}
