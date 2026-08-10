package com.dnd.qello.feed.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 정확 거리를 노출할 수 있는 근거리 하한. 이 값 미만이면 정확 거리를 감추고
 * distance_band만 노출한다.
 * 고정 상수가 아니라 운영 설정값이므로 코드에 박지 않는다 —
 * DirectionReceiveProperties가 수신 상한을 다루는 방식과 같다.
 */
@ConfigurationProperties(prefix = "qello.feed")
public record FeedDistanceProperties(long nearDistanceFloorM) {

	public FeedDistanceProperties {
		if (nearDistanceFloorM <= 0) {
			throw new IllegalArgumentException(
				"qello.feed.near-distance-floor-m은 양수여야 합니다: " + nearDistanceFloorM);
		}
	}

	/** 하한 설정과 저장 정책·조회 projection이 함께 사용하는 사용자 표시 문구다. */
	public String nearDistanceLabel() {
		BigDecimal kilometers = BigDecimal.valueOf(nearDistanceFloorM)
			.divide(BigDecimal.valueOf(1_000L), 3, RoundingMode.HALF_UP)
			.stripTrailingZeros();
		return kilometers.toPlainString() + "km 이내";
	}
}
