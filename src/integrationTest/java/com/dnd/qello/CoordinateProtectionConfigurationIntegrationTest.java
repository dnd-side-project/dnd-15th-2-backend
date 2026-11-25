/**
 * Created at: 2026-11-24T17:42:01Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-018
 */
package com.dnd.qello;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import com.dnd.qello.direction.config.CoordinateProtectionProperties;
import com.dnd.qello.direction.security.AesGcmCoordinateProtector;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinateProtectionConfigurationIntegrationTest {

	private static final String FIXTURE = "Y2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2M=";

	@ParameterizedTest
	@ValueSource(strings = {"local", "test"})
	@DisplayName("INT-018: 실제 local 및 통합 test 프로필 YAML의 전용 fixture로 암호화한다")
	void bindsOnlyExplicitFixtureProfiles(String profile) {
		runner(profile).run(context -> {
			assertThat(context).hasNotFailed();
			var properties = context.getBean(CoordinateProtectionProperties.class);
			assertThat(properties.currentKeyId()).isEqualTo("coordinate-fixture-current");
			var protector = context.getBean(CoordinateProtector.class);
			Coordinate coordinate = new Coordinate(BigDecimal.ONE, BigDecimal.TEN);
			byte[] envelope = protector.protect(coordinate, CoordinatePurpose.ACTIVE_USER_PRESENCE, 1L);
			Coordinate restored = protector.decrypt(envelope, CoordinatePurpose.ACTIVE_USER_PRESENCE, 1L);
			assertThat(restored.latitude()).isEqualByComparingTo(coordinate.latitude());
			assertThat(restored.longitude()).isEqualByComparingTo(coordinate.longitude());
			assertThat(properties.currentKeyBase64()).isNotEqualTo(
					context.getEnvironment().getProperty(
							"qello.notification.push.token-protection.current-encryption-key-base64"));
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"dev", "production"})
	@DisplayName("INT-018: dev와 운영은 좌표 key 주입이 없으면 fixture fallback 없이 기동을 거부한다")
	void refusesNonFixtureProfilesWithoutSecrets(String profile) {
		runner(profile).run(context -> assertThat(context).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = {"dev", "production"})
	@DisplayName("INT-018: 비테스트 프로필의 명시적 합성 key를 바인딩하고 잘못된 길이는 거절한다")
	void bindsExplicitSecretsAndRejectsInvalidMaterial(String profile) {
		runner(profile).withPropertyValues(
				"QELLO_COORDINATE_CURRENT_KEY_ID=explicit-synthetic",
				"QELLO_COORDINATE_CURRENT_KEY_BASE64=" + FIXTURE).run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(CoordinateProtectionProperties.class).currentKeyId())
							.isEqualTo("explicit-synthetic");
				});
		runner(profile).withPropertyValues(
				"QELLO_COORDINATE_CURRENT_KEY_ID=explicit-synthetic",
				"QELLO_COORDINATE_CURRENT_KEY_BASE64=c2hvcnQ=").run(context -> assertThat(context).hasFailed());
	}

	private static ApplicationContextRunner runner(String profile) {
		return new ApplicationContextRunner().withUserConfiguration(CoordinateConfiguration.class)
				.withPropertyValues("QELLO_NOTIFICATION_PUSH_TOKEN_CURRENT_ENCRYPTION_KEY_BASE64="
						+ "cHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHA=")
				.withInitializer(context -> {
					var sources = context.getEnvironment().getPropertySources();
					sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
					var loader = new YamlPropertySourceLoader();
					for (String name : new String[]{"application.yml", "application-" + profile + ".yml"}) {
						var resource = new ClassPathResource(name);
						if (resource.exists()) {
							try {
								for (var source : loader.load(name, resource)) {
									sources.addFirst(source);
								}
							} catch (IOException exception) {
								throw new IllegalStateException("Cannot load test configuration", exception);
							}
						}
					}
				});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(CoordinateProtectionProperties.class)
	@Import(AesGcmCoordinateProtector.class)
	static class CoordinateConfiguration {
	}
}
