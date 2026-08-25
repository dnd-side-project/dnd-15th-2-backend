package com.dnd.qello.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qello.notification.push.policy")
public record PushPolicyProperties(
	Duration bundleWindow,
	Duration maxDelay,
	int dailyLimit,
	int directionReserved,
	Duration recommendationMinInterval
) {

	public PushPolicyProperties {
		requirePositive(bundleWindow, "bundleWindow");
		requirePositive(maxDelay, "maxDelay");
		requirePositive(recommendationMinInterval, "recommendationMinInterval");
		if (maxDelay.compareTo(bundleWindow) < 0) {
			throw new IllegalArgumentException("maxDelay는 bundleWindow 이상이어야 합니다");
		}
		if (dailyLimit <= 0 || directionReserved < 0 || directionReserved > dailyLimit) {
			throw new IllegalArgumentException("push daily budget 설정이 올바르지 않습니다");
		}
	}

	private static void requirePositive(Duration duration, String name) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + "은 양수여야 합니다");
		}
	}
}
