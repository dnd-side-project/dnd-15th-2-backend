/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-015
 */
package com.dnd.qello.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PushConfigurationTest {

	private static final String CONFIGURATION = "com.dnd.qello.notification.config.PushConfiguration";
	private static final String PROPERTIES = "com.dnd.qello.notification.config.PushProperties";
	private static final String PROVIDER = "com.dnd.qello.notification.push.PushProvider";

	@Test
	@DisplayName("UNIT-015: PushProperties는 빈 credential이나 key 값으로 생성되면 즉시 실패해야 한다")
	void rejectsBlankSecretBackedValues() {
		Class<?> propertiesType = requiredClass(PROPERTIES);

		assertThatThrownBy(() -> instantiateWithBlankStrings(propertiesType))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	@DisplayName("UNIT-015: 운영 프로필은 실제 secret 없이 시작할 수 없고 fail-fast 해야 한다")
	void productionProfileFailsFastWithoutSecrets() {
		Class<?> configurationType = requiredClass(CONFIGURATION);

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(configurationType)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-015: test 프로필은 실제 credential 없이 fake PushProvider로 기동해야 한다")
	void testProfileStartsWithFakeProviderWithoutRealSecrets() {
		Class<?> configurationType = requiredClass(CONFIGURATION);
		Class<?> providerType = requiredClass(PROVIDER);

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(configurationType)
			.withPropertyValues("spring.profiles.active=test")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBeansOfType(providerType).values())
					.anySatisfy(bean -> assertThat(bean.getClass().getSimpleName().toLowerCase())
						.containsAnyOf("fake", "noop"));
			});
	}

	@Test
	@DisplayName("UNIT-015: PushProperties는 configuration-properties 접두사를 명시해야 한다")
	void declaresConfigurationPropertiesPrefix() {
		Class<?> propertiesType = requiredClass(PROPERTIES);
		ConfigurationProperties annotation = propertiesType.getAnnotation(ConfigurationProperties.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.prefix()).isNotBlank();
	}

	private static Class<?> requiredClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Missing production API: " + className, e);
		}
	}

	private static Object instantiateWithBlankStrings(Class<?> type) {
		try {
			Constructor<?> constructor = type.getDeclaredConstructors()[0];
			constructor.setAccessible(true);
			Object[] arguments = Arrays.stream(constructor.getParameterTypes())
				.map(PushConfigurationTest::blankValue)
				.toArray();
			return constructor.newInstance(arguments);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static Object blankValue(Class<?> type) {
		if (type == String.class) {
			return " ";
		}
		if (type == boolean.class || type == Boolean.class) {
			return false;
		}
		if (type == int.class || type == Integer.class) {
			return 0;
		}
		if (type == long.class || type == Long.class) {
			return 0L;
		}
		if (type == java.time.Duration.class) {
			return java.time.Duration.ZERO;
		}
		if (type.isEnum()) {
			return type.getEnumConstants()[0];
		}
		if (type.isRecord()) {
			return instantiateNestedRecord(type);
		}
		return null;
	}

	private static Object instantiateNestedRecord(Class<?> type) {
		try {
			Constructor<?> constructor = type.getDeclaredConstructor(
				Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class[]::new));
			constructor.setAccessible(true);
			Object[] arguments = Arrays.stream(type.getRecordComponents())
				.map(RecordComponent::getType)
				.map(PushConfigurationTest::blankValue)
				.toArray();
			return constructor.newInstance(arguments);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
