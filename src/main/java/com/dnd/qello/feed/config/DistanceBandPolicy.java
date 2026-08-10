package com.dnd.qello.feed.config;

import org.springframework.stereotype.Component;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;

/**
 * 거리 스냅샷을 저장할 때 사용할 내부 band를 결정한다.
 * 근거리 표시는 제품 문구를 그대로 저장하고, 하한 이상은 정확 거리 표시 경로임을
 * 나타내는 내부 표식을 저장한다. 실제 응답 노출은 각 조회자의 거리와 하한으로 판정한다.
 */
@Component
public record DistanceBandPolicy(FeedDistanceProperties properties) {

	public static final String EXACT_DISTANCE_STORAGE_BAND = "EXACT_DISTANCE";

	public String forDistance(long distanceM) {
		if (distanceM < 0) {
			throw new FeedException(FeedErrorCode.INVALID_DISTANCE);
		}
		return distanceM < properties.nearDistanceFloorM()
			? properties.nearDistanceLabel()
			: EXACT_DISTANCE_STORAGE_BAND;
	}
}
