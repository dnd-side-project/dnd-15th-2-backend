package com.dnd.qello.direction.web.response;

import java.util.List;

import com.dnd.qello.direction.service.DirectionPreviewResult;

import io.swagger.v3.oas.annotations.media.Schema;

/** 사용자 식별자와 정확 위치를 포함하지 않는 방향 preview 공개 모델. */
public record DirectionPreviewResponse(
	@Schema(description = "이 미리보기를 계산한 방향 구획 체계의 식별자") long schemeId,
	@Schema(description = "방향 구획 체계의 코드") String schemeCode,
	@Schema(description = "방향 구획 체계의 판 번호") int schemeVersion,
	@Schema(description = "방향별 후보 수 목록") List<SegmentCount> segments
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

	public record SegmentCount(
		@Schema(description = "질문글을 보낼 때 지정할 방향 키") String segmentKey,
		@Schema(description = "화면에 표시할 방향 이름") String displayName,
		@Schema(description = "화면에 늘어놓을 순서") int sortOrder,
		@Schema(description = "이 방향에서 질문을 받을 수 있는 사람 수. 참고값입니다") long count
	) {
	}
}
