/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Extended at: 2026-08-27T15:13:23+09:00
 * Extended at: 2026-11-21T08:43:08.515Z
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-015,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-001,
 * TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-018, UNIT-019
 * Source scenario: TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-006,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-007,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-008,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-009,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-010,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-011,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-012,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-013,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-014,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-015,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-017,
 * TEST-PLAN-GH-365-PUSH-FID-PROVIDER-UNIT-018
 */
package com.dnd.qello.notification.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import com.dnd.qello.notification.push.PushDeliveryRetryPolicy;
import com.dnd.qello.notification.push.PushDispatchEligibility;
import com.dnd.qello.notification.push.PushPayload;
import com.dnd.qello.notification.push.PushPayloadFactory;
import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.PushSendCommand;
import com.dnd.qello.notification.push.fcm.FcmAccessTokenProvider;
import com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider;
import com.dnd.qello.notification.push.fcm.GoogleCredentialsFcmAccessTokenProvider;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenKeyRing;
import com.dnd.qello.notification.push.security.PushTokenProtectionException;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.repository.PushDispatchGroupRepository;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.notification.service.PushDispatchGroupClaimService;
import com.dnd.qello.notification.service.PushDispatchGroupPlanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PushConfigurationTest {

	private static final String CURRENT_KEY_ID = "test-current";
	private static final String PREVIOUS_KEY_ID = "test-previous";
	private static final byte[] CURRENT_ENCRYPTION_KEY = fixedKey((byte) 0x11);
	private static final byte[] PREVIOUS_ENCRYPTION_KEY = fixedKey((byte) 0x22);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte) 0x33);
	private static final String PROVIDER_PROPERTY = "qello.notification.push.provider";
	private static final String TOKEN_PREFIX = "qello.notification.push.token-protection.";
	private static final String FCM_PREFIX = "qello.notification.push.fcm.";
	private static final String FAKE_CREDENTIAL = "{\"type\":\"authorized_user\","
			+ "\"client_id\":\"test-client\",\"client_secret\":\"test-secret\",\"refresh_token\":\"test-refresh\"}";
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
	private static final String[] PUSH_SCHEDULING_ENABLED_PROPERTIES = {
			"qello.worker.scheduling.enabled=true",
			"qello.worker.scheduling.pool-size=1",
			"qello.worker.scheduling.push-delivery-dispatch.enabled=true",
			"qello.worker.scheduling.push-delivery-dispatch.fixed-delay=PT0.05S",
			"qello.worker.scheduling.push-delivery-dispatch.batch-size=7",
			"qello.worker.scheduling.push-delivery-dispatch.lease-duration=PT30S",
			"qello.worker.scheduling.push-delivery-dispatch.retry.max-attempts=3",
			"qello.worker.scheduling.push-delivery-dispatch.retry.base-backoff=PT1S",
			"qello.worker.scheduling.push-delivery-dispatch.retry.backoff-cap=PT30S"
	};

	@Test
	@DisplayName("UNIT-015: PushProperties는 빈 credential이나 key 값으로 생성되면 즉시 실패해야 한다")
	void rejectsBlankSecretBackedValues() {
		assertThatThrownBy(() -> new PushProperties(" ", " ", Duration.ofSeconds(1), Duration.ofSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@MethodSource("invalidFcmSettings")
	@DisplayName("UNIT-011: fcm은 실제 config data의 FCM 누락·빈 값·파싱·timeout 오류로 실패한다")
	void productionProfileFailsFastWithoutSecrets(String setting, String value, String failureMarker) {
		List<String> values = new ArrayList<>(Arrays.asList(fcmEnvironmentFixtures()));
		values.removeIf(property -> property.startsWith(setting + "="));
		if (value != null) {
			values.add(setting + "=" + value);
		}

		applicationConfigContext("production")
				.withPropertyValues(tokenKeyFixtures())
				.withPropertyValues(PROVIDER_PROPERTY + "=fcm")
				.withPropertyValues(values.toArray(String[]::new))
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasStackTraceContaining(failureMarker)
							.hasMessageNotContaining(FAKE_CREDENTIAL);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"default", "prod", "production"})
	@DisplayName("UNIT-007: 기본·운영 프로필도 필수 key가 없으면 token 설정 binding 때문에 실패한다")
	void productionTokenKeyPropertiesFailFastWithoutRequiredKeys(String profile) {
		pushContext(profile)
				.withPropertyValues(PROVIDER_PROPERTY + "=noop")
				.run(PushConfigurationTest::assertTokenConfigurationFailure);
	}

	@Test
	@DisplayName("UNIT-013: production noop은 실제 AES로 current-write·previous-read하고 폐기 key를 거부한다")
	void productionConfigurationWiresTokenProtectorWithRotatingKeys() {
		pushContext("production")
				.withPropertyValues(productionSecretsAndPolicyFixtures())
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(AesGcmPushTokenProtector.class);
					assertNoFcmBeans(context);
					PushTokenProtector configuredProtector = context.getBean(PushTokenProtector.class);
					PushToken token = PushToken.of("production-wiring-test-token");
					PushTokenProtector previousProtector = new AesGcmPushTokenProtector(
							new PushTokenKeyRing(
									PREVIOUS_KEY_ID, Map.of(PREVIOUS_KEY_ID, PREVIOUS_ENCRYPTION_KEY),
									FINGERPRINT_KEY));

					ProtectedPushToken currentEnvelope = configuredProtector.protect(token);
					ProtectedPushToken previousEnvelope = previousProtector.protect(token);
					PushTokenProtector retiredPreviousProtector = new AesGcmPushTokenProtector(
							new PushTokenKeyRing(
									CURRENT_KEY_ID, Map.of(CURRENT_KEY_ID, CURRENT_ENCRYPTION_KEY), FINGERPRINT_KEY));

					assertThat(envelopeKeyId(currentEnvelope.envelope())).isEqualTo(CURRENT_KEY_ID);
					assertThat(configuredProtector.decrypt(previousEnvelope.envelope())).isEqualTo(token);
					assertThatThrownBy(() -> retiredPreviousProtector.decrypt(previousEnvelope.envelope()))
							.isInstanceOf(PushTokenProtectionException.class);
				});
	}

	@Test
	@DisplayName("UNIT-006: dev 기본 noop은 실제 AES 단일 bean으로 FID를 보호하고 복호화한다")
	void devDefaultsToNoOpAndWiresRealTokenProtector() {
		applicationConfigContext("dev")
				.withPropertyValues(tokenKeyFixtures())
				.run(context -> {
					assertNoOpProvider(context);
					assertThat(context).hasSingleBean(AesGcmPushTokenProtector.class);
					assertThat(context).hasSingleBean(PushTokenProtector.class);
					PushToken token = PushToken.of("test-only-dev-fid");
					PushTokenProtector protector = context.getBean(PushTokenProtector.class);

					assertThat(protector.decrypt(protector.protect(token).envelope())).isEqualTo(token);
				});
	}

	@ParameterizedTest
	@MethodSource("missingOrBlankDevKeys")
	@DisplayName("UNIT-007: dev는 필수 ID·AES·HMAC 한 항목의 누락이나 빈 값 때문에 실패한다")
	void devRejectsMissingOrBlankRequiredKeys(String setting, String value) {
		List<String> values = new ArrayList<>(Arrays.asList(tokenKeyFixtures()));
		values.removeIf(property -> property.startsWith(TOKEN_PREFIX + setting + "="));
		if (value != null) {
			values.add(TOKEN_PREFIX + setting + "=" + value);
		}

		pushContext("dev")
				.withPropertyValues(PROVIDER_PROPERTY + "=noop")
				.withPropertyValues(values.toArray(String[]::new))
				.run(PushConfigurationTest::assertTokenConfigurationFailure);
	}

	@ParameterizedTest
	@MethodSource("invalidDevKeys")
	@DisplayName("UNIT-008: dev는 key 인코딩·길이·previous 쌍·ID 중복 오류를 안전하게 거부한다")
	void devRejectsInvalidKeyConfiguration(String setting, String value) {
		pushContext("dev")
				.withPropertyValues(PROVIDER_PROPERTY + "=noop")
				.withPropertyValues(tokenKeyFixtures())
				.withPropertyValues(TOKEN_PREFIX + setting + "=" + value)
				.run(PushConfigurationTest::assertTokenConfigurationFailure);
	}

	@ParameterizedTest
	@ValueSource(strings = {"dev", "default", "prod", "production"})
	@DisplayName("UNIT-009: 명시적 noop은 실제 config data에서 FCM 값 없이 AES와 provider 하나만 등록한다")
	void explicitNoOpStartsWithoutFcmPlaceholders(String profile) {
		applicationConfigContext(profile)
				.withPropertyValues(PROVIDER_PROPERTY + "=noop")
				.withPropertyValues(tokenKeyFixtures())
				.run(context -> {
					assertNoOpProvider(context);
					assertThat(context).hasSingleBean(AesGcmPushTokenProtector.class);
					assertThat(context).hasSingleBean(PushTokenProperties.class);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"dev", "production"})
	@DisplayName("UNIT-010: 명시적 fcm은 credential 파싱만 하고 단일 FCM provider와 실제 AES를 등록한다")
	void explicitFcmWiresOnlyFcmProvider(String profile) {
		applicationConfigContext(profile)
				.withPropertyValues(PROVIDER_PROPERTY + "=fcm")
				.withPropertyValues(tokenKeyFixtures())
				.withPropertyValues(fcmEnvironmentFixtures())
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PushProvider.class);
					assertThat(context).hasSingleBean(FcmHttpV1PushProvider.class);
					assertThat(context).hasSingleBean(FcmAccessTokenProvider.class);
					assertThat(context).hasSingleBean(GoogleCredentialsFcmAccessTokenProvider.class);
					assertThat(context).hasSingleBean(PushProperties.class);
					assertThat(context).hasBean("fcmRestClient");
					assertThat(context).hasSingleBean(AesGcmPushTokenProtector.class);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"default", "prod", "production"})
	@DisplayName("UNIT-017: 기본·운영 프로필은 다른 모든 설정이 유효해도 provider 누락 때문에 실패한다")
	void nonDevelopmentProfilesRequireExplicitProvider(String profile) {
		applicationConfigContext(profile)
				.withPropertyValues(tokenKeyFixtures())
				.withPropertyValues(fcmEnvironmentFixtures())
				.run(PushConfigurationTest::assertProviderConfigurationFailure);
	}

	@ParameterizedTest
	@MethodSource("invalidProvidersByProfile")
	@DisplayName("UNIT-018: 모든 프로필에서 미지원·빈 값·공백 provider는 fallback 없이 실패한다")
	void rejectsInvalidProviderInEveryProfile(String profile, String provider) {
		pushContext(profile)
				.withPropertyValues(tokenKeyFixtures())
				.withPropertyValues(fcmEnvironmentFixtures())
				.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
						new MapPropertySource("literal-provider", Map.of(PROVIDER_PROPERTY, provider))))
				.run(PushConfigurationTest::assertProviderConfigurationFailure);
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

	@ParameterizedTest
	@ValueSource(strings = {"test", "local", "integration"})
	@DisplayName("UNIT-012: 제외 프로필은 key·credential 없이 noop 하나만 쓰고 자동 protector는 없다")
	void testProfileStartsWithFakeProviderWithoutRealSecrets(String profile) {
		pushContext(profile)
				.run(context -> {
					assertNoOpProvider(context);
					assertThat(context).doesNotHaveBean(PushTokenProtector.class);
					assertThat(context).doesNotHaveBean(PushTokenProperties.class);
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

	@Test
	@DisplayName("UNIT-018: push scheduling enabled는 planner·payload·retry·worker를 각각 하나만 등록하고 retry 값이 일치한다")
	void enabledPushSchedulingWiresSinglePlannerPayloadRetryAndWorkerBeans() {
		enabledPushSchedulingContext("test")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PushDispatchGroupPlanner.class);
					assertThat(context).hasSingleBean(PushPayloadFactory.class);
					assertThat(context).hasSingleBean(PushDeliveryRetryPolicy.class);
					assertThat(context).hasSingleBean(PushDeliveryDispatchWorker.class);
					assertThat(context.getBean(PushDeliveryRetryPolicy.class))
							.extracting(
									PushDeliveryRetryPolicy::maxAttempts,
									PushDeliveryRetryPolicy::baseBackoff,
									PushDeliveryRetryPolicy::backoffCap)
							.containsExactly(3, Duration.ofSeconds(1), Duration.ofSeconds(30));
				});
	}

	@ParameterizedTest(name = "프로필 {0}")
	@ValueSource(strings = {"test", "local"})
	@DisplayName("UNIT-019: push scheduling enabled여도 test/local은 NoOp provider만 쓰고 실제 FCM client를 만들지 않는다")
	void enabledPushSchedulingKeepsNoOpProviderAndDoesNotBuildFcmClient(String profile) {
		enabledPushSchedulingContext(profile)
				.run(context -> {
					assertNoOpProvider(context);
					assertThat(context).hasSingleBean(PushTokenProtector.class);
					assertThat(context).doesNotHaveBean(AesGcmPushTokenProtector.class);
					assertThat(context).doesNotHaveBean(PushTokenProperties.class);
				});
	}

	@ParameterizedTest(name = "누락 키 {0}")
	@ValueSource(strings = {
			"QELLO_NOTIFICATION_PUSH_BUNDLE_WINDOW", "QELLO_NOTIFICATION_PUSH_MAX_DELAY",
			"QELLO_NOTIFICATION_PUSH_DAILY_LIMIT", "QELLO_NOTIFICATION_PUSH_DIRECTION_RESERVED",
			"QELLO_NOTIFICATION_PUSH_RECOMMENDATION_MIN_INTERVAL"})
	@DisplayName("UNIT-014: 나머지 설정이 유효해도 정책 property 하나의 누락 때문에 기동에 실패한다")
	void omittingAnyPolicyPropertyFailsFast(String omittedEnv) {
		pushContext("production")
				.withPropertyValues(productionLikePropertiesOmitting(omittedEnv))
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasStackTraceContaining(PushPolicyProperties.class.getName())
							.hasStackTraceContaining(omittedEnv);
				});
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

	private static ApplicationContextRunner pushContext(String profile) {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
				.withUserConfiguration(PushConfiguration.class, ScannedPushPropertiesConfiguration.class)
				.withInitializer(context -> {
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
				})
				.withPropertyValues(POLICY_FIXTURE_PROPERTIES);
		return "default".equals(profile) ? runner : runner.withPropertyValues("spring.profiles.active=" + profile);
	}

	private static ApplicationContextRunner applicationConfigContext(String profile) {
		// local YAML은 .env를 import하므로 config data 검증은 dev/default/운영 프로필로 한정한다.
		return pushContext(profile)
				.withPropertyValues("spring.config.location=classpath:/application.yml")
				.withInitializer(new ConfigDataApplicationContextInitializer());
	}

	private static void assertNoOpProvider(AssertableApplicationContext context) {
		assertThat(context).hasNotFailed();
		assertThat(context).hasSingleBean(PushProvider.class);
		assertThat(context.getBean(PushProvider.class).send(
				new PushSendCommand(
						PushToken.of("test-only-noop-fid"), new PushPayload("ANSWER_RECEIVED", "1", "false"))))
				.isInstanceOf(PushProviderResult.Accepted.class);
		assertNoFcmBeans(context);
	}

	private static void assertNoFcmBeans(AssertableApplicationContext context) {
		assertThat(context).doesNotHaveBean(PushProperties.class);
		assertThat(context).doesNotHaveBean(FcmHttpV1PushProvider.class);
		assertThat(context).doesNotHaveBean(FcmAccessTokenProvider.class);
		assertThat(context).doesNotHaveBean(GoogleCredentialsFcmAccessTokenProvider.class);
		assertThat(context).doesNotHaveBean("fcmRestClient");
		assertThat(context).doesNotHaveBean(RestClient.class);
	}

	private static void assertTokenConfigurationFailure(AssertableApplicationContext context) {
		assertThat(context).hasFailed();
		assertThat(context.getStartupFailure()).hasStackTraceContaining(PushTokenProperties.class.getName());
		assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("push token key")
				.hasMessageNotContaining(base64(CURRENT_ENCRYPTION_KEY))
				.hasMessageNotContaining(base64(PREVIOUS_ENCRYPTION_KEY))
				.hasMessageNotContaining(base64(FINGERPRINT_KEY));
	}

	private static void assertProviderConfigurationFailure(AssertableApplicationContext context) {
		assertThat(context).hasFailed();
		assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(PROVIDER_PROPERTY);
	}

	private static Stream<Arguments> missingOrBlankDevKeys() {
		return Stream.of("current-key-id", "current-encryption-key-base64", "fingerprint-key-base64")
				.flatMap(setting -> Stream.of(
						Arguments.of(setting, null), Arguments.of(setting, ""), Arguments.of(setting, " ")));
	}

	private static Stream<Arguments> invalidDevKeys() {
		return Stream.of(
				Arguments.of("current-encryption-key-base64", "not-base64"),
				Arguments.of("current-encryption-key-base64", base64(new byte[16])),
				Arguments.of("fingerprint-key-base64", "not-base64"),
				Arguments.of("fingerprint-key-base64", base64(new byte[16])),
				Arguments.of("previous-key-id", ""),
				Arguments.of("previous-encryption-key-base64", ""),
				Arguments.of("previous-encryption-key-base64", "not-base64"),
				Arguments.of("previous-encryption-key-base64", base64(new byte[16])),
				Arguments.of("previous-key-id", CURRENT_KEY_ID));
	}

	private static Stream<Arguments> invalidFcmSettings() {
		return Stream.of(
				Arguments.of("QELLO_NOTIFICATION_PUSH_FCM_PROJECT_ID", null, "projectId"),
				Arguments.of("QELLO_NOTIFICATION_PUSH_FCM_PROJECT_ID", "", "projectId"),
				Arguments.of("QELLO_NOTIFICATION_PUSH_FCM_PROJECT_ID", " ", "projectId"),
				Arguments.of("QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON", null, "credential"),
				Arguments.of("QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON", "", "credential"),
				Arguments.of("QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON", " ", "credential"),
				Arguments.of("QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON", "not-json-test-credential",
						"FCM credential"),
				Arguments.of(FCM_PREFIX + "connect-timeout", "PT0S", "connectTimeout"),
				Arguments.of(FCM_PREFIX + "connect-timeout", "PT-1S", "connectTimeout"),
				Arguments.of(FCM_PREFIX + "read-timeout", "PT0S", "readTimeout"),
				Arguments.of(FCM_PREFIX + "read-timeout", "PT-1S", "readTimeout"));
	}

	private static Stream<Arguments> invalidProvidersByProfile() {
		return Stream.of("test", "local", "integration", "dev", "default", "prod", "production")
				.flatMap(profile -> Stream.of("apns", "", " ", "\t")
						.map(provider -> Arguments.of(profile, provider)));
	}

	private ApplicationContextRunner enabledPushSchedulingContext(String profile) {
		return new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
				.withUserConfiguration(PushConfiguration.class)
				.withBean(PushDispatchGroupRepository.class, () -> mock(PushDispatchGroupRepository.class))
				.withBean(PushDispatchGroupClaimService.class, () -> mock(PushDispatchGroupClaimService.class))
				.withBean(PushDispatchEligibility.class, PushDispatchEligibility::new)
				.withBean(PushTokenProtector.class, () -> mock(PushTokenProtector.class))
				.withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
				.withBean(Clock.class, Clock::systemUTC)
				.withPropertyValues(enabledPushSchedulingProperties(profile));
	}

	private static String[] enabledPushSchedulingProperties(String profile) {
		List<String> values = new ArrayList<>();
		values.add("spring.profiles.active=" + profile);
		values.addAll(Arrays.asList(POLICY_FIXTURE_PROPERTIES));
		values.addAll(Arrays.asList(PUSH_SCHEDULING_ENABLED_PROPERTIES));
		return values.toArray(String[]::new);
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
		values.add(PROVIDER_PROPERTY + "=noop");
		values.add(FCM_PREFIX + "project-id=test-project");
		values.add(FCM_PREFIX + "credential-json=" + FAKE_CREDENTIAL);
		values.add(FCM_PREFIX + "connect-timeout=PT1S");
		values.add(FCM_PREFIX + "read-timeout=PT1S");
		values.addAll(Arrays.asList(tokenKeyFixtures()));
		values.addAll(Arrays.asList(POLICY_FIXTURE_PROPERTIES));
		return values.toArray(String[]::new);
	}

	private static String[] tokenKeyFixtures() {
		return new String[]{
				TOKEN_PREFIX + "current-key-id=" + CURRENT_KEY_ID,
				TOKEN_PREFIX + "current-encryption-key-base64=" + base64(CURRENT_ENCRYPTION_KEY),
				TOKEN_PREFIX + "previous-key-id=" + PREVIOUS_KEY_ID,
				TOKEN_PREFIX + "previous-encryption-key-base64=" + base64(PREVIOUS_ENCRYPTION_KEY),
				TOKEN_PREFIX + "fingerprint-key-base64=" + base64(FINGERPRINT_KEY)
		};
	}

	private static String[] fcmEnvironmentFixtures() {
		return new String[]{
				"QELLO_NOTIFICATION_PUSH_FCM_PROJECT_ID=test-project",
				"QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON=" + FAKE_CREDENTIAL,
				FCM_PREFIX + "connect-timeout=PT1S",
				FCM_PREFIX + "read-timeout=PT1S"
		};
	}

	private static String[] productionLikePropertiesOmitting(String omittedEnv) {
		List<String> values = new ArrayList<>(Arrays.asList(productionSecretsAndPolicyFixtures()));
		values.removeAll(Arrays.asList(POLICY_FIXTURE_PROPERTIES));
		values.addAll(Arrays.asList(POLICY_PLACEHOLDERS));
		for (String envFixture : POLICY_ENV_FIXTURES) {
			if (!envFixture.startsWith(omittedEnv + "=")) {
				values.add(envFixture);
			}
		}
		return values.toArray(String[]::new);
	}

	@Configuration(proxyBeanMethods = false)
	@ConfigurationPropertiesScan(basePackageClasses = PushProperties.class)
	static class ScannedPushPropertiesConfiguration {
	}

}
