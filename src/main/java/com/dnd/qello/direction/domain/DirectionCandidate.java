package com.dnd.qello.direction.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** 후보 조회 결과. 정확 위치는 의도적으로 포함하지 않는다. */
public record DirectionCandidate(
	Long userId,
	BigDecimal distanceMeters,
	BigDecimal bearingDegrees,
	String matchedRegionCode
) {
	public DirectionCandidate {
		if (userId == null || userId <= 0) throw new IllegalArgumentException("userId는 양수여야 합니다");
		Objects.requireNonNull(distanceMeters, "distanceMeters는 필수입니다");
		Objects.requireNonNull(bearingDegrees, "bearingDegrees는 필수입니다");
		if (distanceMeters.signum() < 0) throw new IllegalArgumentException("distanceMeters는 음수일 수 없습니다");
		if (bearingDegrees.signum() < 0 || bearingDegrees.doubleValue() >= 360) {
			throw new IllegalArgumentException("bearingDegrees는 [0, 360)이어야 합니다");
		}
		if (matchedRegionCode == null || matchedRegionCode.isBlank()) {
			throw new IllegalArgumentException("matchedRegionCode는 필수입니다");
		}
	}
}
