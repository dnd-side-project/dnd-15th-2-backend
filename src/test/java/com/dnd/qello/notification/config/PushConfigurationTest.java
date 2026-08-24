/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-015
 */
package com.dnd.qello.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PushConfigurationTest {

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

}
