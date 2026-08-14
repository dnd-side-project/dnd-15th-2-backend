package com.dnd.qello.direction.web.response;

import java.util.List;

import com.dnd.qello.direction.service.DirectionPreviewResult;

/** 사용자 식별자와 정확 위치를 포함하지 않는 방향 preview 공개 모델. */
public record DirectionPreviewResponse(
	long schemeId,
	String schemeCode,
	int schemeVersion,
	List<SegmentCount> segments
) {
	public DirectionPreviewResponse {
		segments = List.copyOf(segments);
	}

	public static DirectionPreviewResponse from(DirectionPreviewResult result) {
		return new DirectionPreviewResponse(result.schemeId(), result.schemeCode(), result.schemeVersion(),
			result.segments().stream()
				.map(segment -> new SegmentCount(segment.segmentKey(), segment.displayName(), segment.sortOrder(), segment.count()))
				.toList());
	}

	public record SegmentCount(String segmentKey, String displayName, int sortOrder, long count) {
	}
}
