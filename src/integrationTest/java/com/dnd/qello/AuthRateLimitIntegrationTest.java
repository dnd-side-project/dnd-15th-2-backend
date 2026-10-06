/**
 * Created at: 2026-10-06T14:26:52+09:00
 * Source scenario: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-001 through INT-005
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

// 인증 없이 열린 세 경로의 IP 단위 한도를 낮은 값(2회)으로 주입해 검증한다(#315).
//
// 카운터는 컨텍스트에 하나라 테스트끼리 이어진다. 테스트마다 다른 문서용 주소(192.0.2.0/24)를 써서 격리한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "account-persistence"})
@TestPropertySource(properties = {
		"qello.auth.rate-limit.device-registration.max-requests=2",
		"qello.auth.rate-limit.token-reissue.max-requests=2",
		"qello.auth.rate-limit.operator-login.max-requests=2"
})
@Import(AuthRateLimitIntegrationTest.ClockConfiguration.class)
class AuthRateLimitIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String COUNTRY_CODE = "KR";
	private static final String REGION_CODE = "TEST-RATE-LIMIT-REGION";
	private static final String LOGIN_ID = "qello-rate-admin";
	private static final String PASSWORD = "example-operator-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OperatorSeedService operatorSeedService;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetFixtures() {
		clock.reset();
		jdbcTemplate.update("DELETE FROM device_credential");
		jdbcTemplate.update("DELETE FROM operator_credential");
		jdbcTemplate.update("DELETE FROM user_account");
		jdbcTemplate.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, NULL, 'Korea', 'COUNTRY')
				ON CONFLICT (code) DO NOTHING
				""", COUNTRY_CODE);
		jdbcTemplate.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, ?, 'Rate Limit Region', 'REGION')
				ON CONFLICT (code) DO NOTHING
				""", REGION_CODE, COUNTRY_CODE);
	}

	@Test
	@DisplayName("INT-001: 같은 IP의 기기 등록이 한도를 넘으면 429 AUT-APP-007이고 계정과 자격증명을 만들지 않는다")
	void rejectsDeviceRegistrationOverLimit() throws Exception {
		mockMvc.perform(register("install-a1").with(from("192.0.2.1"))).andExpect(status().isCreated());
		mockMvc.perform(register("install-a2").with(from("192.0.2.1"))).andExpect(status().isCreated());

		mockMvc.perform(register("install-a3").with(from("192.0.2.1")))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-007"));

		assertThat(count("SELECT count(*) FROM user_account")).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM device_credential")).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM device_credential WHERE installation_id = 'install-a3'")).isZero();
	}

	@Test
	@DisplayName("INT-002: 다른 IP는 따로 세고, X-Forwarded-For 헤더를 바꿔도 연결 주소 기준으로 거절한다")
	void countsByConnectionAddressOnly() throws Exception {
		mockMvc.perform(register("install-b1").with(from("192.0.2.2"))).andExpect(status().isCreated());
		mockMvc.perform(register("install-b2").with(from("192.0.2.2"))).andExpect(status().isCreated());

		mockMvc.perform(register("install-b3").with(from("192.0.2.3")))
				.andExpect(status().isCreated());
		mockMvc.perform(register("install-b4").with(from("192.0.2.2"))
				.header("X-Forwarded-For", "198.51.100.10"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-007"));
	}

	@Test
	@DisplayName("INT-003: 같은 IP의 토큰 재발급이 한도를 넘으면 429 AUT-APP-007이고 last_used_at을 갱신하지 않는다")
	void rejectsTokenReissueOverLimit() throws Exception {
		MvcResult registered = mockMvc.perform(register("install-r").with(from("192.0.2.4")))
				.andExpect(status().isCreated())
				.andReturn();
		String secret = data(registered).get("deviceSecret").asText();
		mockMvc.perform(reissue("install-r", secret).with(from("192.0.2.5"))).andExpect(status().isOk());
		mockMvc.perform(reissue("install-r", secret).with(from("192.0.2.5"))).andExpect(status().isOk());
		Timestamp lastUsedBefore = lastUsedAt("install-r");
		clock.advance(Duration.ofMinutes(1));

		mockMvc.perform(reissue("install-r", secret).with(from("192.0.2.5")))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-007"));

		assertThat(lastUsedAt("install-r")).isEqualTo(lastUsedBefore);
	}

	@Test
	@DisplayName("INT-004: 같은 IP의 운영자 로그인이 한도를 넘으면 올바른 비밀번호도 429이고 세션과 실패 횟수가 바뀌지 않는다")
	void rejectsOperatorLoginOverLimitWithoutTouchingCredential() throws Exception {
		operatorSeedService.seedIfAbsent(
				LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), "rate-admin", COUNTRY_CODE, "ko-KR", "Asia/Seoul");
		Csrf csrf = issueCsrf();
		mockMvc.perform(login(csrf, "wrong-password").with(from("192.0.2.6"))).andExpect(status().isUnauthorized());
		mockMvc.perform(login(csrf, "wrong-password").with(from("192.0.2.6"))).andExpect(status().isUnauthorized());
		int sessionsBefore = count("SELECT count(*) FROM spring_session");

		MvcResult rejected = mockMvc.perform(login(csrf, PASSWORD).with(from("192.0.2.6")))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-007"))
				.andReturn();

		assertThat(rejected.getResponse().getCookie("SESSION")).isNull();
		assertThat(count("SELECT count(*) FROM spring_session")).isEqualTo(sessionsBefore);
		assertThat(count("SELECT failed_attempt_count FROM operator_credential WHERE login_id = '" + LOGIN_ID + "'"))
				.isEqualTo(2);
	}

	@Test
	@DisplayName("INT-005: 한도를 넘긴 IP도 window가 지나면 다시 등록할 수 있다")
	void allowsAgainAfterWindowElapses() throws Exception {
		mockMvc.perform(register("install-w1").with(from("192.0.2.7"))).andExpect(status().isCreated());
		mockMvc.perform(register("install-w2").with(from("192.0.2.7"))).andExpect(status().isCreated());
		mockMvc.perform(register("install-w3").with(from("192.0.2.7"))).andExpect(status().isTooManyRequests());

		clock.advance(Duration.ofHours(1));

		mockMvc.perform(register("install-w3").with(from("192.0.2.7"))).andExpect(status().isCreated());
	}

	private MockHttpServletRequestBuilder register(String installationId) {
		return post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "installationId": "%s",
						  "platform": "IOS",
						  "countryCode": "%s",
						  "coarseRegionCode": "%s",
						  "locale": "ko-KR",
						  "timezone": "Asia/Seoul"
						}
						""".formatted(installationId, COUNTRY_CODE, REGION_CODE));
	}

	private MockHttpServletRequestBuilder reissue(String installationId, String deviceSecret) {
		return post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"installationId\":\"%s\",\"deviceSecret\":\"%s\"}".formatted(installationId, deviceSecret));
	}

	private MockHttpServletRequestBuilder login(Csrf csrf, String password) {
		return post("/admin/login")
				.header(csrf.headerName(), csrf.token())
				.cookie(csrf.cookies())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, password));
	}

	// 실제 발급 흐름으로 CSRF 토큰을 받는다. OperatorLoginIntegrationTest와 같은 이유로 csrf()
	// post-processor를 쓰지 않는다.
	private Csrf issueCsrf() throws Exception {
		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode payload = data(issued);
		return new Csrf(payload.get("headerName").asText(), payload.get("token").asText(),
				issued.getResponse().getCookies());
	}

	private static RequestPostProcessor from(String remoteAddress) {
		return request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		};
	}

	private JsonNode data(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private Timestamp lastUsedAt(String installationId) {
		return jdbcTemplate.queryForObject(
				"SELECT last_used_at FROM device_credential WHERE installation_id = ?", Timestamp.class,
				installationId);
	}

	private int count(String sql) {
		Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
		return value == null ? 0 : value;
	}

	private record Csrf(String headerName, String token, Cookie[] cookies) {
	}

	@TestConfiguration
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock authRateLimitTestClock() {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		}
	}

	// 실제 현재 시각에서 시작한다. 등록 응답의 액세스 토큰과 DB 기본 시각이 시스템 시계와 어긋나지 않게 한다.
	static final class MutableClock extends Clock {

		private final Instant base;
		private final AtomicReference<Instant> current;

		MutableClock(Instant base) {
			this.base = base;
			this.current = new AtomicReference<>(base);
		}

		void reset() {
			current.set(base);
		}

		void advance(Duration duration) {
			current.updateAndGet(instant -> instant.plus(duration));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current.get();
		}
	}
}
