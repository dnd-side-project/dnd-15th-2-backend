/**
 * Created at: 2026-08-14T13:00:00+09:00
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-001
 */
package com.dnd.qello.direction.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

class DirectionPostPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
		.withUserConfiguration(PostPropertiesConfiguration.class);

	@Test
	@DisplayName("승인된 GLOBAL 질문글 정책값을 보관한다")
	void acceptsApprovedGlobalPolicy() {
		var properties = approved();

		assertThat(properties.deliveryScope()).isEqualTo(DirectionPostProperties.DeliveryScope.GLOBAL);
		assertThat(properties.minDistanceMeters()).isZero();
		assertThat(properties.maxDistanceMeters()).isEqualTo(20_100_000L);
		assertThat(properties.ttl()).isEqualTo(Duration.ofHours(12));
		assertThat(properties.maxBodyCodePoints()).isEqualTo(300);
		assertThat(properties.maxMediaCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("GLOBAL 거리 상한과 역전된 범위는 시작 전에 거절한다")
	void rejectsInvalidDistancePolicy() {
		assertThatThrownBy(() -> new DirectionPostProperties(DirectionPostProperties.DeliveryScope.GLOBAL,
			0, 20_100_001L, Duration.ofHours(12), 300, 1))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_DISTANCE_RANGE);
		assertThatThrownBy(() -> new DirectionPostProperties(DirectionPostProperties.DeliveryScope.GLOBAL,
			1, 1, Duration.ofHours(12), 300, 1))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_DISTANCE_RANGE);
	}

	@Test
	@DisplayName("본문 300 code point와 미디어 1장 이외의 정책은 거절한다")
	void rejectsUnapprovedContentLimits() {
		assertThatThrownBy(() -> new DirectionPostProperties(DirectionPostProperties.DeliveryScope.GLOBAL,
			0, 20_100_000L, Duration.ofHours(12), 301, 1))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("field", "maxBodyCodePoints");
		assertThatThrownBy(() -> new DirectionPostProperties(DirectionPostProperties.DeliveryScope.GLOBAL,
			0, 20_100_000L, Duration.ofHours(12), 300, 2))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("field", "maxMediaCount");
	}

	@Test
	@DisplayName("Spring 설정은 승인된 질문글 정책 필드에 독립적으로 binding된다")
	void bindsApprovedPolicyFromSpringConfiguration() {
		contextRunner.withPropertyValues(
			"qello.direction.post.delivery-scope=GLOBAL",
			"qello.direction.post.min-distance-meters=0",
			"qello.direction.post.max-distance-meters=20100000",
			"qello.direction.post.ttl=PT12H",
			"qello.direction.post.max-body-code-points=300",
			"qello.direction.post.max-media-count=1")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(DirectionPostProperties.class).isGlobal()).isTrue();
			});
	}

	@Test
	@DisplayName("Spring 설정의 GLOBAL 거리 상한 초과는 context 시작에 실패한다")
	void failsFastWhenBoundDistanceExceedsGlobalLimit() {
		contextRunner.withPropertyValues(
			"qello.direction.post.delivery-scope=GLOBAL",
			"qello.direction.post.min-distance-meters=0",
			"qello.direction.post.max-distance-meters=20100001",
			"qello.direction.post.ttl=PT12H",
			"qello.direction.post.max-body-code-points=300",
			"qello.direction.post.max-media-count=1")
			.run(context -> assertThat(context).hasFailed());
	}

	private static DirectionPostProperties approved() {
		return new DirectionPostProperties(DirectionPostProperties.DeliveryScope.GLOBAL,
			0, 20_100_000L, Duration.ofHours(12), 300, 1);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(DirectionPostProperties.class)
	static class PostPropertiesConfiguration {
	}
}
