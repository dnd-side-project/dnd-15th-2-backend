/**
 * Created at: 2026-08-21T21:25:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-010
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationPreferenceApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH178-PREF-API";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transactions;
	@Autowired
	private NotificationPreferenceRepository preferences;

	private long userA;
	private long userB;
	private long blockedUser;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM notification_user_setting");
		jdbc.update("DELETE FROM notification_preference");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH178 Preference API', 'REGION')
			""", REGION);
		userA = account("pref-api-a", "ACTIVE");
		userB = account("pref-api-b", "ACTIVE");
		blockedUser = account("pref-api-blocked", "BLOCKED");
		persist(snapshot(userB, false, mixedTypesB(), quietHoursB()));
	}

	@Test
	@DisplayName("인증 사용자 A는 B용 body 필드를 보내도 A 설정만 조회·변경하고 B는 건드리지 않는다")
	void authenticatedUserCanOnlyAccessOwnPreferences() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/preferences")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userA)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pushEnabled").value(true))
			.andExpect(jsonPath("$.data.quietHours").doesNotExist())
			.andExpect(jsonPath("$.data.preferences.length()").value(6))
			.andExpect(jsonPath("$.data.inboxRecordingPolicy").value("ALWAYS_RECORD"));

		String putResponse = mockMvc.perform(put("/api/v1/notifications/preferences")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userA))))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestForUserA().replace("}", ",\"userId\":" + userB + "}")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pushEnabled").value(false))
			.andExpect(jsonPath("$.data.quietHours.zoneId").value("Asia/Seoul"))
			.andExpect(jsonPath("$.data.preferences.length()").value(6))
			.andExpect(jsonPath("$.data.inboxRecordingPolicy").value("ALWAYS_RECORD"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(putResponse).doesNotContain("\"userId\"");
		assertThat(preferences.findByUserId(userA)).isEqualTo(snapshot(userA, false, mixedTypesA(), quietHoursA()));
		assertThat(preferences.findByUserId(userB)).isEqualTo(snapshot(userB, false, mixedTypesB(), quietHoursB()));
	}

	@Test
	@DisplayName("비활성 사용자는 preference GET·PUT 모두 403이다")
	void inactiveUserIsForbidden() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/preferences")
				.with(jwt().jwt(token -> token.subject(String.valueOf(blockedUser)))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-APP-002"));

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.with(jwt().jwt(token -> token.subject(String.valueOf(blockedUser))))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestForUserA()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorDetail.code").value("NOT-APP-002"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_user_setting WHERE user_id = ?",
			Integer.class, blockedUser)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preference WHERE user_id = ?",
			Integer.class, blockedUser)).isZero();
	}

	private long account(String nickname, String status) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, status, REGION, nickname);
	}

	private void persist(NotificationPreferenceSnapshot snapshot) {
		transactions.executeWithoutResult(status -> {
			preferences.lockUser(snapshot.userId());
			preferences.saveUserSetting(snapshot.userId(), snapshot.pushEnabled(), snapshot.quietHours());
			preferences.replaceTypePreferences(snapshot.userId(), snapshot.typeEnabled());
		});
	}

	private NotificationPreferenceSnapshot snapshot(
		long accountId,
		boolean pushEnabled,
		Map<NotificationType, Boolean> typeEnabled,
		NotificationQuietHours quietHours) {
		return new NotificationPreferenceSnapshot(accountId, pushEnabled, quietHours, typeEnabled);
	}

	private Map<NotificationType, Boolean> mixedTypesA() {
		return Map.ofEntries(
			Map.entry(NotificationType.ANSWER_RECEIVED, true),
			Map.entry(NotificationType.ANSWER_REACTED, false),
			Map.entry(NotificationType.DIRECTION_POST_RECEIVED, true),
			Map.entry(NotificationType.REPORT_RESOLVED, false),
			Map.entry(NotificationType.QUESTION_PROPOSAL_REVIEWED, true),
			Map.entry(NotificationType.QUESTION_RECOMMENDED, false));
	}

	private Map<NotificationType, Boolean> mixedTypesB() {
		return Map.ofEntries(
			Map.entry(NotificationType.ANSWER_RECEIVED, false),
			Map.entry(NotificationType.ANSWER_REACTED, true),
			Map.entry(NotificationType.DIRECTION_POST_RECEIVED, false),
			Map.entry(NotificationType.REPORT_RESOLVED, true),
			Map.entry(NotificationType.QUESTION_PROPOSAL_REVIEWED, false),
			Map.entry(NotificationType.QUESTION_RECOMMENDED, true));
	}

	private NotificationQuietHours quietHoursA() {
		return new NotificationQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Asia/Seoul"));
	}

	private NotificationQuietHours quietHoursB() {
		return new NotificationQuietHours(LocalTime.of(1, 30), LocalTime.of(8, 45), ZoneId.of("Asia/Tokyo"));
	}

	private String requestForUserA() {
		return """
			{
			  "pushEnabled": false,
			  "quietHours": {
			    "start": "22:00:00",
			    "end": "07:00:00",
			    "zoneId": "Asia/Seoul"
			  },
			  "preferences": [
			    {"type": "ANSWER_RECEIVED", "enabled": true},
			    {"type": "ANSWER_REACTED", "enabled": false},
			    {"type": "DIRECTION_POST_RECEIVED", "enabled": true},
			    {"type": "REPORT_RESOLVED", "enabled": false},
			    {"type": "QUESTION_PROPOSAL_REVIEWED", "enabled": true},
			    {"type": "QUESTION_RECOMMENDED", "enabled": false}
			  ]
			}
			""";
	}
}
