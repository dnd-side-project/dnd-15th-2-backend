package com.dnd.qello.direction.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

@ConfigurationProperties(prefix = "qello.direction.presence")
public record DirectionPresenceProperties(
	Duration ttl,
	BigDecimal maxAccuracyMeters,
	Duration maxFutureSkew,
	Duration maxObservationAge
) {
	public DirectionPresenceProperties {
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "ttl", "presence ttl은 양수여야 합니다");
		}
		if (maxAccuracyMeters == null || maxAccuracyMeters.signum() <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "maxAccuracyMeters",
				"presence 최대 정확도는 양수여야 합니다");
		}
		if (maxFutureSkew == null || maxFutureSkew.isNegative()) {
			throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "maxFutureSkew",
				"presence 미래 관측 오차는 음수일 수 없습니다");
		}
		if (maxObservationAge == null || maxObservationAge.isZero() || maxObservationAge.isNegative()) {
			throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "maxObservationAge",
				"presence 최대 관측 나이는 양수여야 합니다");
		}
		if (maxObservationAge.compareTo(ttl) >= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_TIME_ORDER, "maxObservationAge",
				"presence 최대 관측 나이는 ttl보다 짧아야 합니다");
		}
	}
}
