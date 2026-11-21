/**
 * Created at: 2026-09-17T00:58:11+09:00
 * Source scenario: TEST-PLAN-GH-230-CORS-ACTUATOR-DEV-PROFILE-INT-005..008
 * Extended at: 2026-11-21T08:52:10Z
 * Source scenario: TEST-PLAN-GH-365-PUSH-FID-PROVIDER-INT-006
 */
package com.dnd.qello;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.notification.config.PushProperties;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.PushPayloadFactory;
import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.PushSendCommand;
import com.dnd.qello.notification.push.fcm.FcmAccessTokenProvider;
import com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenProtector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileBootIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String FID = "test-only-gh365-dev-fid-sentinel";

	@DynamicPropertySource
	static void devProfileRequiredProperties(DynamicPropertyRegistry registry) {
		registry.add("qello.media.bucket", () -> "qello-dev-boot-test-placeholder");
		registry.add("qello.auth.access-token.secret", () -> "dev-profile-boot-test-signing-key-32-bytes-min");
		registry.add("qello.notification.push.token-protection.current-key-id", () -> "test-only-gh365-current");
		registry.add("qello.notification.push.token-protection.current-encryption-key-base64",
				() -> fakeKey((byte) 0x11));
		registry.add("qello.notification.push.token-protection.fingerprint-key-base64", () -> fakeKey((byte) 0x33));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private PushTokenProtector protector;

	@Autowired
	private PushProvider provider;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("INT-006: dev는 가짜 보호 키만으로 실제 AES와 기본 noop을 등록하고 FCM 빈을 만들지 않는다")
	void devProfileContextLoads() {
		assertThat(context.getBeansOfType(PushTokenProtector.class).values()).containsExactly(protector);
		assertThat(protector).isInstanceOf(AesGcmPushTokenProtector.class);
		assertThat(context.getBeansOfType(PushProvider.class).values()).containsExactly(provider);
		assertThat(context.getBeansOfType(PushProperties.class)).isEmpty();
		assertThat(context.getBeansOfType(FcmAccessTokenProvider.class)).isEmpty();
		assertThat(context.getBeansOfType(FcmHttpV1PushProvider.class)).isEmpty();
		assertThat(provider.send(new PushSendCommand(PushToken.of(FID),
				new PushPayloadFactory().create(NotificationType.QUESTION_PROPOSAL_REVIEWED, 1, false))))
				.isInstanceOf(PushProviderResult.Accepted.class);
	}

	@Test
	@DisplayName("INT-006: dev의 실제 자동 AES 보호기로 FID를 등록하고 같은 FID를 해지한다")
	void registersAndRevokesFidWithAutoConfiguredProtector() throws Exception {
		long userId = jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, coarse_region_code, status, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'KR', 'ACTIVE', 'ko-KR', 'Asia/Seoul', 'gh365-dev-boot')
				RETURNING id
				""", Long.class);
		String body = "{\"platform\":\"ANDROID\",\"fid\":\"" + FID + "\"}";
		try {
			mockMvc.perform(post("/api/v1/notifications/devices")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(userId))))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isNoContent()).andExpect(content().string(""));

			byte[] ciphertext = jdbc.queryForObject(
					"SELECT token_ciphertext FROM push_device WHERE user_id = ?", byte[].class, userId);
			assertThat(ciphertext).isNotEqualTo(FID.getBytes(StandardCharsets.UTF_8));
			assertThat(protector.decrypt(ciphertext)).isEqualTo(PushToken.of(FID));
			assertThat(jdbc.queryForObject("SELECT token_fingerprint FROM push_device WHERE user_id = ?",
					String.class, userId)).isEqualTo(protector.fingerprint(PushToken.of(FID)));
			assertThat(jdbc.queryForObject("SELECT device_status FROM push_device WHERE user_id = ?",
					String.class, userId)).isEqualTo("ACTIVE");

			mockMvc.perform(post("/api/v1/notifications/devices/revoke")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
							.jwt(token -> token.subject(Long.toString(userId))))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isNoContent()).andExpect(content().string(""));

			assertThat(jdbc.queryForObject("SELECT device_status FROM push_device WHERE user_id = ?",
					String.class, userId)).isEqualTo("REVOKED");
		} finally {
			jdbc.update("DELETE FROM push_device WHERE user_id = ?", userId);
			jdbc.update("DELETE FROM user_account WHERE id = ?", userId);
		}
	}

	@Test
	@DisplayName("INT-005: GET /actuator/health는 인증 없이 200과 UP 상태를 반환한다")
	void actuatorHealthIsOpenAndUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("INT-006: health 외 actuator endpoint는 dev 프로파일에서도 노출되지 않는다")
	void onlyHealthIsExposed() throws Exception {
		for (String path : new String[]{"/actuator", "/actuator/metrics", "/actuator/env"}) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
	}

	private static String fakeKey(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return Base64.getEncoder().encodeToString(key);
	}

}
