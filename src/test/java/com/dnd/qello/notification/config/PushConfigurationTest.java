/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-015
 */
package com.dnd.qello.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

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
			.withPropertyValues("spring.profiles.active=test")
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
			.withPropertyValues("spring.profiles.active=integration")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBeansOfType(com.dnd.qello.notification.push.PushProvider.class).values())
					.anySatisfy(bean -> assertThat(bean.getClass().getSimpleName().toLowerCase())
						.containsAnyOf("fake", "noop"));
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

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(PushTokenProperties.class)
	static class TokenKeyPropertiesConfiguration {
	}

}
