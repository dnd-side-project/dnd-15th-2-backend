package com.dnd.qello.direction.domain;

import java.math.BigDecimal;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/** 후보 조회 결과. 정확 위치는 의도적으로 포함하지 않는다. */
public record DirectionCandidate(
	Long userId,
	BigDecimal distanceMeters,
	BigDecimal bearingDegrees,
	String matchedRegionCode,
	BigDecimal inboundBearingDegrees
) {
	public DirectionCandidate {
		if (userId == null || userId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다");
		}
		requireValue(distanceMeters, "distanceMeters");
		requireValue(bearingDegrees, "bearingDegrees");
		requireValue(inboundBearingDegrees, "inboundBearingDegrees");
		if (distanceMeters.signum() < 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "distanceMeters", "distanceMeters는 음수일 수 없습니다");
		}
		if (bearingDegrees.signum() < 0 || bearingDegrees.doubleValue() >= 360) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_BEARING, "bearingDegrees", "bearingDegrees는 [0, 360)이어야 합니다");
		}
		// 수신자 위치를 원점으로 계산한 역방위다. bearingDegrees(발송자→후보)를 그대로 쓰거나
		// +180도로 근사하면 방향이 뒤집혀 보인다 — 구면 역방위는 +180이 아니다. 매칭 시점에
		// 후보 위치 기준으로 별도 계산해 스냅샷으로 박는 이유는 direction_communication.dbml의
		// "2026-08-07 스키마 변경" 절 참고.
		if (inboundBearingDegrees.signum() < 0 || inboundBearingDegrees.doubleValue() >= 360) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_BEARING, "inboundBearingDegrees", "inboundBearingDegrees는 [0, 360)이어야 합니다");
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
