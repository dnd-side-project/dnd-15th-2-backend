/*
 * Created at: 2026-10-06T14:26:52+09:00
 * Source scenario: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-001
 */
package com.dnd.qello.common.ratelimit;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPolicyTest {

	@Test
	@DisplayName("UNIT-001: 한도가 0 이하이면 정책을 만들 수 없다")
	void rejectsNonPositiveMaxRequests() {
		assertThatThrownBy(() -> new RateLimitPolicy(0, Duration.ofMinutes(1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RateLimitPolicy(-1, Duration.ofMinutes(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-001: window가 null이거나 0 또는 음수이면 정책을 만들 수 없다")
	void rejectsMissingOrNonPositiveWindow() {
		assertThatThrownBy(() -> new RateLimitPolicy(1, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RateLimitPolicy(1, Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RateLimitPolicy(1, Duration.ofSeconds(-1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-001: 양수 한도와 양수 window는 그대로 보존된다")
	void keepsValidValues() {
		RateLimitPolicy policy = new RateLimitPolicy(3, Duration.ofHours(1));

		assertThat(policy.maxRequests()).isEqualTo(3);
		assertThat(policy.window()).isEqualTo(Duration.ofHours(1));
	}
}
