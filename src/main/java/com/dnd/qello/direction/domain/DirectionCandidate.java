package com.dnd.qello.direction.domain;

import java.math.BigDecimal;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/** 후보 조회 결과. 정확 위치는 의도적으로 포함하지 않는다. */
public record DirectionCandidate(
	Long userId,
	BigDecimal distanceMeters,
	BigDecimal bearingDegrees,
	String matchedRegionCode
) {
	public DirectionCandidate {
		if (userId == null || userId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다");
		}
		requireValue(distanceMeters, "distanceMeters");
		requireValue(bearingDegrees, "bearingDegrees");
		if (distanceMeters.signum() < 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "distanceMeters", "distanceMeters는 음수일 수 없습니다");
		}
		if (bearingDegrees.signum() < 0 || bearingDegrees.doubleValue() >= 360) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_BEARING, "bearingDegrees", "bearingDegrees는 [0, 360)이어야 합니다");
		}
		if (matchedRegionCode == null || matchedRegionCode.isBlank()) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, "matchedRegionCode", "matchedRegionCode는 필수입니다");
		}
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "는 필수입니다");
		}
		return value;
	}
}
