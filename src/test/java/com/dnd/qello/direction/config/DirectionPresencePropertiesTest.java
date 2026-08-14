/**
 * Created at: 2026-08-14T00:51:11+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-003, UNIT-009
 */
package com.dnd.qello.direction.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

class DirectionPresencePropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
		.withUserConfiguration(PresencePropertiesConfiguration.class);

	@Test
	@DisplayName("MVP 정책값은 서로 독립적인 설정으로 보관된다")
	void acceptsMvpPolicyValues() {
		DirectionPresenceProperties properties = new DirectionPresenceProperties(
			Duration.ofHours(24), BigDecimal.valueOf(100), Duration.ofSeconds(30), Duration.ofMinutes(5));

		assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
		assertThat(properties.maxAccuracyMeters()).isEqualByComparingTo("100");
		assertThat(properties.maxFutureSkew()).isEqualTo(Duration.ofSeconds(30));
		assertThat(properties.maxObservationAge()).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	@DisplayName("양수가 아닌 TTL과 정확도 또는 음수 미래 오차는 시작 전에 거절한다")
	void rejectsNonPositiveOrNegativePolicyValues() {
		assertThatThrownBy(() -> properties(Duration.ZERO, BigDecimal.TEN, Duration.ZERO, Duration.ofSeconds(1)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE)
			.hasFieldOrPropertyWithValue("field", "ttl");
		assertThatThrownBy(() -> properties(Duration.ofHours(1), BigDecimal.ZERO, Duration.ZERO, Duration.ofSeconds(1)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE)
			.hasFieldOrPropertyWithValue("field", "maxAccuracyMeters");
		assertThatThrownBy(() -> properties(Duration.ofHours(1), BigDecimal.TEN, Duration.ofSeconds(-1), Duration.ofSeconds(1)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE)
			.hasFieldOrPropertyWithValue("field", "maxFutureSkew");
	}

	@Test
	@DisplayName("최대 관측 나이는 양수이고 TTL보다 짧아야 한다")
	void requiresObservationAgeShorterThanTtl() {
		assertThatThrownBy(() -> properties(Duration.ofMinutes(5), BigDecimal.TEN, Duration.ZERO, Duration.ofMinutes(5)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER)
			.hasFieldOrPropertyWithValue("field", "maxObservationAge");
		assertThatThrownBy(() -> properties(Duration.ofMinutes(5), BigDecimal.TEN, Duration.ZERO, Duration.ZERO))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE)
			.hasFieldOrPropertyWithValue("field", "maxObservationAge");
	}

	@Test
	@DisplayName("Spring 설정 override는 네 정책값에 독립적으로 binding된다")
	void bindsIndependentSpringConfigurationOverrides() {
		contextRunner.withPropertyValues(
			"qello.direction.presence.ttl=PT12H",
			"qello.direction.presence.max-accuracy-meters=75.5",
			"qello.direction.presence.max-future-skew=PT10S",
			"qello.direction.presence.max-observation-age=PT2M")
			.run(context -> {
				assertThat(context).hasNotFailed();
				DirectionPresenceProperties bound = context.getBean(DirectionPresenceProperties.class);
				assertThat(bound.ttl()).isEqualTo(Duration.ofHours(12));
				assertThat(bound.maxAccuracyMeters()).isEqualByComparingTo("75.5");
				assertThat(bound.maxFutureSkew()).isEqualTo(Duration.ofSeconds(10));
				assertThat(bound.maxObservationAge()).isEqualTo(Duration.ofMinutes(2));
			});
	}

	@Test
	@DisplayName("관측 나이가 TTL 이상인 Spring 설정은 context 시작에 실패한다")
	void failsFastWhenBoundRelationshipIsInvalid() {
		contextRunner.withPropertyValues(
			"qello.direction.presence.ttl=PT5M",
			"qello.direction.presence.max-accuracy-meters=100",
			"qello.direction.presence.max-future-skew=PT30S",
			"qello.direction.presence.max-observation-age=PT5M")
			.run(context -> assertThat(context).hasFailed());
	}

	private DirectionPresenceProperties properties(Duration ttl, BigDecimal accuracy, Duration future, Duration age) {
		return new DirectionPresenceProperties(ttl, accuracy, future, age);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(DirectionPresenceProperties.class)
	static class PresencePropertiesConfiguration {
	}
}
