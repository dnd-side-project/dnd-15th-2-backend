/**
 * Created at: 2026-08-14T00:51:11+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-001 through INT-005, INT-009
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.direction.config.DirectionPresenceProperties;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ActiveUserPresenceApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_A = "TEST-PRESENCE-API-A";
	private static final String REGION_B = "TEST-PRESENCE-API-B";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DirectionPresenceProperties presenceProperties;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code IN (?, ?)", REGION_A, REGION_B);
		jdbc.update("DELETE FROM region_code WHERE code IN (?, ?)", REGION_A, REGION_B);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Presence A', 'REGION'), (?, 'KR', 'Presence B', 'REGION')
			""", REGION_A, REGION_B);
	}

	@Test
	@DisplayName("JWT subject 본인의 서버 지역으로 presence를 저장하고 applied만 응답한다")
	void updatesAuthenticatedUsersPresenceWithoutExposingLocation() throws Exception {
		long userA = createUser(REGION_A, "user-a", "ACTIVE", "USER");
		long userB = createUser(REGION_B, "user-b", "ACTIVE", "USER");
		Instant observedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		String sentinelLatitude = "37.512345";
		String sentinelLongitude = "127.098765";

		String response = mockMvc.perform(put("/api/v1/direction/presence")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userA))))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(sentinelLatitude, sentinelLongitude, "100", true, observedAt)
					.replace("}", ",\"userId\":" + userB + ",\"coarseRegionCode\":\"" + REGION_B + "\"}")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.applied").value(true))
			.andReturn().getResponse().getContentAsString();

		assertThat(response).doesNotContain(sentinelLatitude, sentinelLongitude, REGION_A, REGION_B,
			"\"userId\"", "\"coarseRegionCode\"", "\"latitude\"", "\"longitude\"");
		assertThat(jdbc.queryForObject("SELECT coarse_region_code FROM active_user_presence WHERE user_id = ?",
			String.class, userA)).isEqualTo(REGION_A);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence WHERE user_id = ?",
			Integer.class, userB)).isZero();
		assertThat(jdbc.queryForObject("SELECT expires_at FROM active_user_presence WHERE user_id = ?",
			Timestamp.class, userA).toInstant()).isEqualTo(observedAt.plus(presenceProperties.ttl()));
	}

	@Test
	@DisplayName("같거나 오래된 API 재시도는 200 applied false이고 최신 위치를 보존한다")
	void returnsAppliedFalseForEqualOrOlderObservation() throws Exception {
		long userId = createUser(REGION_A, "stale", "ACTIVE", "USER");
		Instant observedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		mockMvc.perform(update(userId, request("37.5000", "127.0000", "10", true, observedAt)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(true));
		mockMvc.perform(update(userId, request("37.6000", "127.1000", "10", false, observedAt)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(false));

		assertThat(jdbc.queryForObject("SELECT ST_Y(position::geometry) FROM active_user_presence WHERE user_id = ?",
			Double.class, userId)).isEqualTo(37.5);
		assertThat(jdbc.queryForObject("SELECT receive_allowed FROM active_user_presence WHERE user_id = ?",
			Boolean.class, userId)).isTrue();
	}

	@Test
	@DisplayName("토큰이 없으면 presence API는 401이고 행을 만들지 않는다")
	void requiresAppAccessToken() throws Exception {
		mockMvc.perform(put("/api/v1/direction/presence")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("37.5", "127", "1", true, Instant.now())))
			.andExpect(status().isUnauthorized());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence", Integer.class)).isZero();
	}

	@Test
	@DisplayName("양수가 아닌 숫자·비숫자·빈 JWT subject는 401이고 service를 호출하지 않는다")
	void rejectsInvalidJwtSubject() throws Exception {
		String body = request("37.5", "127", "1", true, Instant.now());
		for (String subject : new String[] {"0", "-1", "not-a-number", ""}) {
			mockMvc.perform(put("/api/v1/direction/presence")
					.with(jwt().jwt(token -> token.subject(subject)))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isUnauthorized());
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence", Integer.class)).isZero();
	}

	@Test
	@DisplayName("누락 값과 정책 범위 밖 정확도는 400이고 기존 행을 변경하지 않는다")
	void rejectsInvalidRequestBeforePersistence(CapturedOutput output) throws Exception {
		long userId = createUser(REGION_A, "invalid", "ACTIVE", "USER");
		Instant observedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		String sentinelLatitude = "91.123456";

		mockMvc.perform(update(userId, "{\"latitude\":37.5,\"longitude\":127,\"accuracyMeters\":1,\"observedAt\":\""
			+ observedAt + "\"}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(update(userId, request("37.5", "127", "100.01", true, observedAt)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("DIR-VAL-008"));
		String errorResponse = mockMvc.perform(update(userId,
				request(sentinelLatitude, "127.098765", "1", true, observedAt)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorDetail.code").value("CMN-VAL-001"))
			.andReturn().getResponse().getContentAsString();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence", Integer.class)).isZero();
		assertThat(errorResponse).doesNotContain(sentinelLatitude, "127.098765");
		assertThat(output.getOut()).doesNotContain(sentinelLatitude, "127.098765");
	}

	@Test
	@DisplayName("없는 계정은 404이고 차단·삭제·운영자 계정은 403이다")
	void rejectsMissingOrIneligibleAccount() throws Exception {
		Instant observedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		String request = request("37.5", "127", "1", true, observedAt);
		long missingId = 9_999_999L;
		long blockedId = createUser(REGION_A, "blocked", "BLOCKED", "USER");
		long deletedId = createUser(REGION_A, "deleted", "DELETED", "USER");
		long operatorId = createUser(REGION_A, "operator", "ACTIVE", "OPERATOR");

		mockMvc.perform(update(missingId, request)).andExpect(status().isNotFound());
		mockMvc.perform(update(blockedId, request)).andExpect(status().isForbidden());
		mockMvc.perform(update(deletedId, request)).andExpect(status().isForbidden());
		mockMvc.perform(update(operatorId, request)).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence", Integer.class)).isZero();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder update(long userId, String body) {
		return put("/api/v1/direction/presence")
			.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private String request(String latitude, String longitude, String accuracy, boolean allowed, Instant observedAt) {
		return """
			{"latitude":%s,"longitude":%s,"accuracyMeters":%s,"receiveAllowed":%s,"observedAt":"%s"}
			""".formatted(latitude, longitude, accuracy, allowed, observedAt);
	}

	private long createUser(String region, String nickname, String status, String role) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname, deleted_at)
			VALUES (?, CASE WHEN ? = 'USER' THEN 'KR' ELSE NULL END, ?, ?, 'ko-KR', 'Asia/Seoul', ?,
				CASE WHEN ? = 'DELETED' THEN clock_timestamp() ELSE NULL END)
			RETURNING id
			""", Long.class, role, role, status, region, nickname, status);
	}
}
