/**
 * Created at: 2026-08-25T13:14:21+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-001
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.config.PushPolicyProperties;

class PushPolicyPropertiesTest {

	@Test
	@DisplayName("UNIT-001: 명시한 정책 fixture 값은 기본값 없이 그대로 보존한다")
	void preservesExplicitPolicyValues() {
		PushPolicyProperties properties = validProperties();

		assertThat(properties.bundleWindow()).isEqualTo(Duration.ofMinutes(10));
		assertThat(properties.maxDelay()).isEqualTo(Duration.ofHours(8));
		assertThat(properties.dailyLimit()).isEqualTo(5);
		assertThat(properties.directionReserved()).isEqualTo(2);
		assertThat(properties.recommendationMinInterval()).isEqualTo(Duration.ofHours(24));
	}

	@Test
	@DisplayName("UNIT-001: max delay와 bundle window가 같은 경계값은 유효하다")
	void acceptsEqualMaxDelayAndBundleWindow() {
		PushPolicyProperties properties = new PushPolicyProperties(Duration.ofMinutes(10), Duration.ofMinutes(10),
			5, 2, Duration.ofHours(24));

		assertThat(properties.maxDelay()).isEqualTo(properties.bundleWindow());
	}

	@Test
	@DisplayName("UNIT-001: null, 0, 음수 Duration과 max delay 역전은 시작 전에 거절한다")
	void rejectsInvalidDurationPolicy() {
		assertThatThrownBy(() -> new PushPolicyProperties(null, Duration.ofHours(8), 5, 2, Duration.ofHours(24)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushPolicyProperties(Duration.ZERO, Duration.ofHours(8), 5, 2,
			Duration.ofHours(24))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushPolicyProperties(Duration.ofMinutes(10), Duration.ofMinutes(9), 5, 2,
			Duration.ofHours(24))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushPolicyProperties(Duration.ofMinutes(10), Duration.ofHours(8), 5, 2,
			Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-001: daily limit과 질문글 예약량의 허용 범위를 검증한다")
	void rejectsInvalidDailyBudgetPolicy() {
		assertThatThrownBy(() -> new PushPolicyProperties(Duration.ofMinutes(10), Duration.ofHours(8), 0, 0,
			Duration.ofHours(24))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushPolicyProperties(Duration.ofMinutes(10), Duration.ofHours(8), 5, -1,
			Duration.ofHours(24))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushPolicyProperties(Duration.ofMinutes(10), Duration.ofHours(8), 5, 6,
			Duration.ofHours(24))).isInstanceOf(IllegalArgumentException.class);
	}

	static PushPolicyProperties validProperties() {
		return new PushPolicyProperties(Duration.ofMinutes(10), Duration.ofHours(8), 5, 2, Duration.ofHours(24));
	}
}
