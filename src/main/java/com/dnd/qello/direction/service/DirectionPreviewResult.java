package com.dnd.qello.direction.service;

import java.util.List;

/**
 * 방향 preview의 공개 결과 모델이다. 후보 사용자와 정확 위치는 preview 경계를 넘지 않는다.
 */
public record DirectionPreviewResult(long schemeId, String schemeCode, int schemeVersion,
	List<SegmentCount> segments) {

	public DirectionPreviewResult {
		if (schemeId <= 0) throw new IllegalArgumentException("schemeId must be positive");
		if (schemeCode == null || schemeCode.isBlank()) throw new IllegalArgumentException("schemeCode must not be blank");
		if (schemeVersion <= 0) throw new IllegalArgumentException("schemeVersion must be positive");
		segments = List.copyOf(segments);
	}

	public record SegmentCount(String segmentKey, String displayName, int sortOrder, long count) {
		public SegmentCount {
			if (segmentKey == null || segmentKey.isBlank()) throw new IllegalArgumentException("segmentKey must not be blank");
			if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
			if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must not be negative");
			if (count < 0) throw new IllegalArgumentException("count must not be negative");
		}
	}
}
