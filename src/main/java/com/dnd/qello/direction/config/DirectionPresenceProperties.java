package com.dnd.qello.direction.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qello.direction.presence")
public record DirectionPresenceProperties(
	Duration ttl,
	BigDecimal maxAccuracyMeters,
	Duration maxFutureSkew,
	Duration maxObservationAge
) {
	public DirectionPresenceProperties {
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("presence ttl은 양수여야 합니다");
		}
		if (maxAccuracyMeters == null || maxAccuracyMeters.signum() <= 0) {
			throw new IllegalArgumentException("presence 최대 정확도는 양수여야 합니다");
		}
		if (maxFutureSkew == null || maxFutureSkew.isNegative()) {
			throw new IllegalArgumentException("presence 미래 관측 오차는 음수일 수 없습니다");
		}
		if (maxObservationAge == null || maxObservationAge.isZero() || maxObservationAge.isNegative()) {
			throw new IllegalArgumentException("presence 최대 관측 나이는 양수여야 합니다");
		}
		if (maxObservationAge.compareTo(ttl) >= 0) {
			throw new IllegalArgumentException("presence 최대 관측 나이는 ttl보다 짧아야 합니다");
		}
	}
}
