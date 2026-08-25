/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-015,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-001
 */
package com.dnd.qello.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;

import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenKeyRing;
import com.dnd.qello.notification.push.security.PushTokenProtector;

class PushConfigurationTest {

	private static final String CURRENT_KEY_ID = "test-current";
	private static final String PREVIOUS_KEY_ID = "test-previous";
	private static final byte[] CURRENT_ENCRYPTION_KEY = fixedKey((byte) 0x11);
	private static final byte[] PREVIOUS_ENCRYPTION_KEY = fixedKey((byte) 0x22);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte) 0x33);
	private static final String[] POLICY_FIXTURE_PROPERTIES = {
		"qello.notification.push.policy.bundle-window=PT10M",
		"qello.notification.push.policy.max-delay=PT8H",
		"qello.notification.push.policy.daily-limit=5",
		"qello.notification.push.policy.direction-reserved=2",
		"qello.notification.push.policy.recommendation-min-interval=PT24H"
	};
	private static final String[] POLICY_PLACEHOLDERS = {
		"qello.notification.push.policy.bundle-window=${QELLO_NOTIFICATION_PUSH_BUNDLE_WINDOW}",
		"qello.notification.push.policy.max-delay=${QELLO_NOTIFICATION_PUSH_MAX_DELAY}",
		"qello.notification.push.policy.daily-limit=${QELLO_NOTIFICATION_PUSH_DAILY_LIMIT}",
		"qello.notification.push.policy.direction-reserved=${QELLO_NOTIFICATION_PUSH_DIRECTION_RESERVED}",
		"qello.notification.push.policy.recommendation-min-interval=${QELLO_NOTIFICATION_PUSH_RECOMMENDATION_MIN_INTERVAL}"
	};
	private static final String[] POLICY_ENV_FIXTURES = {
		"QELLO_NOTIFICATION_PUSH_BUNDLE_WINDOW=PT10M",
		"QELLO_NOTIFICATION_PUSH_MAX_DELAY=PT8H",
		"QELLO_NOTIFICATION_PUSH_DAILY_LIMIT=5",
		"QELLO_NOTIFICATION_PUSH_DIRECTION_RESERVED=2",
		"QELLO_NOTIFICATION_PUSH_RECOMMENDATION_MIN_INTERVAL=PT24H"
	};

	@Test
	@DisplayName("UNIT-015: PushProperties는 빈 credential이나 key 값으로 생성되면 즉시 실패해야 한다")
	void rejectsBlankSecretBackedValues() {
		assertThatThrownBy(() -> new PushProperties(" ", " ", java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-015: 운영 프로필은 실제 secret 없이 시작할 수 없고 fail-fast 해야 한다")
	void productionProfileFailsFastWithoutSecrets() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(PushConfiguration.class)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-015: production token-key properties는 필수 key가 없으면 binding 단계에서 즉시 실패해야 한다")
	void productionTokenKeyPropertiesFailFastWithoutRequiredKeys() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(TokenKeyPropertiesConfiguration.class)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-015: production PushConfiguration은 실제 PushTokenProtector를 만들고 current-write·previous-read를 보장한다")
	void productionConfigurationWiresTokenProtectorWithRotatingKeys() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(PushConfiguration.class)
			.withPropertyValues(productionSecretsAndPolicyFixtures())
			.run(context -> {
				assertThat(context).hasNotFailed();
				PushTokenProtector configuredProtector = context.getBean(PushTokenProtector.class);
				PushToken token = PushToken.of("production-wiring-test-token");
				PushTokenProtector previousProtector = new AesGcmPushTokenProtector(
					new PushTokenKeyRing(
						PREVIOUS_KEY_ID, Map.of(PREVIOUS_KEY_ID, PREVIOUS_ENCRYPTION_KEY), FINGERPRINT_KEY));

				ProtectedPushToken currentEnvelope = configuredProtector.protect(token);
				ProtectedPushToken previousEnvelope = previousProtector.protect(token);

				assertThat(envelopeKeyId(currentEnvelope.envelope())).isEqualTo(CURRENT_KEY_ID);
				assertThat(configuredProtector.decrypt(previousEnvelope.envelope())).isEqualTo(token);
			});
	}

	@Test
	@DisplayName("UNIT-015: production token-key properties는 Base64가 아니거나 32 bytes가 아닌 key를 거절한다")
	void productionTokenKeyPropertiesRejectInvalidEncodingAndLength() {
		assertThatThrownBy(() -> new PushTokenProperties(
			CURRENT_KEY_ID, "not-base64", null, null, base64(FINGERPRINT_KEY)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushTokenProperties(
			CURRENT_KEY_ID, base64(new byte[16]), null, null, base64(FINGERPRINT_KEY)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-015: token-key properties의 문자열 표현은 AES·HMAC key material을 노출하지 않는다")
	void tokenKeyPropertiesToStringRedactsKeyMaterial() {
		String currentEncryptionKey = base64(CURRENT_ENCRYPTION_KEY);
		String previousEncryptionKey = base64(PREVIOUS_ENCRYPTION_KEY);
		String fingerprintKey = base64(FINGERPRINT_KEY);
		PushTokenProperties properties = new PushTokenProperties(
			CURRENT_KEY_ID,
			currentEncryptionKey,
			PREVIOUS_KEY_ID,
			previousEncryptionKey,
			fingerprintKey);

		assertThat(properties.toString())
			.doesNotContain(currentEncryptionKey, previousEncryptionKey, fingerprintKey)
			.contains("[REDACTED]");
	}

	@Test
	@DisplayName("UNIT-015: FCM properties의 문자열 표현은 service-account credential을 노출하지 않는다")
	void fcmPropertiesToStringRedactsCredentialJson() {
		String credentialJson = "{\"type\":\"service_account\",\"private_key\":\"obvious-test-private-key\"}";
		PushProperties properties = new PushProperties(
			"test-project", credentialJson, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1));

		assertThat(properties.toString())
			.doesNotContain(credentialJson, "obvious-test-private-key")
			.contains("[REDACTED]");
	}

	@Test
	@DisplayName("UNIT-015: production key ring은 current로 쓰고 current와 previous를 읽는다")
	void productionKeyRingWritesCurrentAndReadsCurrentAndPrevious() {
		PushTokenProperties properties = new PushTokenProperties(
			CURRENT_KEY_ID,
			base64(CURRENT_ENCRYPTION_KEY),
			PREVIOUS_KEY_ID,
			base64(PREVIOUS_ENCRYPTION_KEY),
			base64(FINGERPRINT_KEY));
		PushTokenProtector configuredProtector = new AesGcmPushTokenProtector(properties.keyRing());
		PushTokenProtector previousProtector = new AesGcmPushTokenProtector(
			new PushTokenKeyRing(
				PREVIOUS_KEY_ID, Map.of(PREVIOUS_KEY_ID, PREVIOUS_ENCRYPTION_KEY), FINGERPRINT_KEY));
		PushToken token = PushToken.of("obvious-test-token");

		ProtectedPushToken currentEnvelope = configuredProtector.protect(token);
		ProtectedPushToken previousEnvelope = previousProtector.protect(token);

		assertThat(envelopeKeyId(currentEnvelope.envelope())).isEqualTo(CURRENT_KEY_ID);
		assertThat(configuredProtector.decrypt(currentEnvelope.envelope())).isEqualTo(token);
		assertThat(configuredProtector.decrypt(previousEnvelope.envelope())).isEqualTo(token);
	}

	@Test
	@DisplayName("UNIT-015: test 프로필은 실제 credential 없이 fake PushProvider로 기동해야 한다")
	void testProfileStartsWithFakeProviderWithoutRealSecrets() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(PushConfiguration.class)
			.withPropertyValues(testProfileWithPolicyFixtures())
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBeansOfType(com.dnd.qello.notification.push.PushProvider.class).values())
					.anySatisfy(bean -> assertThat(bean.getClass().getSimpleName().toLowerCase())
						.containsAnyOf("fake", "noop"));
			});
	}

	@Test
	@DisplayName("UNIT-015: integration 프로필은 실제 credential 없이 fake PushProvider로 기동해야 한다")
	void integrationProfileStartsWithFakeProviderWithoutRealSecrets() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(PushConfiguration.class)
			.withPropertyValues(profileWithPolicyFixtures("integration"))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBeansOfType(com.dnd.qello.notification.push.PushProvider.class).values())
					.anySatisfy(bean -> assertThat(bean.getClass().getSimpleName().toLowerCase())
						.containsAnyOf("fake", "noop"));
			});
	}

	@Test
	@DisplayName("UNIT-001: 다섯 fixture policy property가 있으면 수치만 바인딩하고 순수 정책 bean만 등록한다")
	void bindsFixturePolicyPropertiesAndRegistersPurePolicyBeans() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(PushConfiguration.class)
			.withPropertyValues(testProfileWithPolicyFixtures())
			.run(context -> {
				assertThat(context).hasNotFailed();
				PushPolicyProperties properties = context.getBean(PushPolicyProperties.class);
				assertThat(properties.bundleWindow()).isEqualTo(Duration.ofMinutes(10));
				assertThat(properties.maxDelay()).isEqualTo(Duration.ofHours(8));
				assertThat(properties.dailyLimit()).isEqualTo(5);
				assertThat(properties.directionReserved()).isEqualTo(2);
				assertThat(properties.recommendationMinInterval()).isEqualTo(Duration.ofHours(24));
				assertThat(properties.toString())
					.contains("10", "8", "5", "2", "24")
					.doesNotContain("credential", "token", "secret", "key-base64", "[REDACTED]");
				assertThat(context).hasSingleBean(PushGroupingPolicy.class);
				assertThat(context).hasSingleBean(PushSuppressionPolicy.class);
				assertThat(context).hasSingleBean(PushBudgetPolicy.class);
				assertThat(context).doesNotHaveBean(PushDeliveryDispatchWorker.class);
			});
	}

	@ParameterizedTest(name = "누락 키 {0}")
	@ValueSource(strings = {
		"QELLO_NOTIFICATION_PUSH_BUNDLE_WINDOW", "QELLO_NOTIFICATION_PUSH_MAX_DELAY",
		"QELLO_NOTIFICATION_PUSH_DAILY_LIMIT", "QELLO_NOTIFICATION_PUSH_DIRECTION_RESERVED",
		"QELLO_NOTIFICATION_PUSH_RECOMMENDATION_MIN_INTERVAL"})
	@DisplayName("UNIT-001: 정책 property를 하나라도 빼면 production-like context는 fail-fast 해야 한다")
	void omittingAnyPolicyPropertyFailsFast(String omittedEnv) {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(PushConfiguration.class)
			.withPropertyValues(productionLikePropertiesOmitting(omittedEnv))
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-015: PushProperties는 configuration-properties 접두사를 명시해야 한다")
	void declaresConfigurationPropertiesPrefix() {
		ConfigurationProperties annotation = PushProperties.class.getAnnotation(ConfigurationProperties.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.prefix()).isNotBlank();
	}

	private static String base64(byte[] value) {
		return Base64.getEncoder().encodeToString(value);
	}

	private static byte[] fixedKey(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}

	private static String envelopeKeyId(byte[] envelope) {
		int keyIdLength = Byte.toUnsignedInt(envelope[1]);
		return new String(envelope, 2, keyIdLength, StandardCharsets.UTF_8);
	}

	private static String[] testProfileWithPolicyFixtures() {
		return profileWithPolicyFixtures("test");
	}

	private static String[] profileWithPolicyFixtures(String profile) {
		List<String> values = new ArrayList<>();
		values.add("spring.profiles.active=" + profile);
		values.addAll(Arrays.asList(POLICY_FIXTURE_PROPERTIES));
		return values.toArray(String[]::new);
	}

	private static String[] productionSecretsAndPolicyFixtures() {
		List<String> values = new ArrayList<>();
		values.add("spring.profiles.active=production");
		values.add("qello.notification.push.fcm.project-id=test-project");
		values.add("qello.notification.push.fcm.credential-json={\"type\":\"authorized_user\",\"client_id\":\"test-client\",\"client_secret\":\"test-secret\",\"refresh_token\":\"test-refresh\"}");
		values.add("qello.notification.push.fcm.connect-timeout=PT1S");
		values.add("qello.notification.push.fcm.read-timeout=PT1S");
		values.add("qello.notification.push.token-protection.current-key-id=" + CURRENT_KEY_ID);
		values.add("qello.notification.push.token-protection.current-encryption-key-base64=" + base64(CURRENT_ENCRYPTION_KEY));
		values.add("qello.notification.push.token-protection.previous-key-id=" + PREVIOUS_KEY_ID);
		values.add("qello.notification.push.token-protection.previous-encryption-key-base64=" + base64(PREVIOUS_ENCRYPTION_KEY));
		values.add("qello.notification.push.token-protection.fingerprint-key-base64=" + base64(FINGERPRINT_KEY));
		values.addAll(Arrays.asList(POLICY_FIXTURE_PROPERTIES));
		return values.toArray(String[]::new);
	}

	private static String[] productionLikePropertiesOmitting(String omittedEnv) {
		List<String> values = new ArrayList<>();
		values.add("spring.profiles.active=production");
		values.add("qello.notification.push.fcm.project-id=test-project");
		values.add("qello.notification.push.fcm.credential-json={\"type\":\"authorized_user\",\"client_id\":\"test-client\",\"client_secret\":\"test-secret\",\"refresh_token\":\"test-refresh\"}");
		values.add("qello.notification.push.fcm.connect-timeout=PT1S");
		values.add("qello.notification.push.fcm.read-timeout=PT1S");
		values.add("qello.notification.push.token-protection.current-key-id=" + CURRENT_KEY_ID);
		values.add("qello.notification.push.token-protection.current-encryption-key-base64=" + base64(CURRENT_ENCRYPTION_KEY));
		values.add("qello.notification.push.token-protection.previous-key-id=" + PREVIOUS_KEY_ID);
		values.add("qello.notification.push.token-protection.previous-encryption-key-base64=" + base64(PREVIOUS_ENCRYPTION_KEY));
		values.add("qello.notification.push.token-protection.fingerprint-key-base64=" + base64(FINGERPRINT_KEY));
		values.addAll(Arrays.asList(POLICY_PLACEHOLDERS));
		for (String envFixture : POLICY_ENV_FIXTURES) {
			if (!envFixture.startsWith(omittedEnv + "=")) {
				values.add(envFixture);
			}
		}
		return values.toArray(String[]::new);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(PushTokenProperties.class)
	static class TokenKeyPropertiesConfiguration {
	}

}
