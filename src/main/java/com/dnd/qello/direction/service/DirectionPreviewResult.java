package com.dnd.qello.direction.service;

import java.util.List;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/**
 * 방향 preview의 공개 결과 모델이다. 후보 사용자와 정확 위치는 preview 경계를 넘지 않는다.
 */
public record DirectionPreviewResult(long schemeId, String schemeCode, int schemeVersion,
	List<SegmentCount> segments) {

	public DirectionPreviewResult {
		requirePositiveId(schemeId, "schemeId");
		requireText(schemeCode, "schemeCode");
		requireNonNull(segments, "segments");
		requirePositiveRange(schemeVersion, "schemeVersion");
		segments = List.copyOf(segments);
	}

	public record SegmentCount(String segmentKey, String displayName, int sortOrder, long count) {
		public SegmentCount {
			requireText(segmentKey, "segmentKey");
			requireText(displayName, "displayName");
			requireNonNegativeRange(sortOrder, "sortOrder");
			requireNonNegativeRange(count, "count");
		}
	}

	private static void requirePositiveId(long value, String field) {
		if (value <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
	}

	private static void requireText(String value, String field) {
		if (value == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "는 필수입니다");
		}
		if (value.isBlank()) {
			throw new DirectionException(DirectionErrorCode.INVALID_TEXT, field, field + "이 유효하지 않습니다");
		}
	}

	private static <T> void requireNonNull(T value, String field) {
		if (value == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, field, field + "는 필수입니다");
		}
	}

	private static void requirePositiveRange(int value, String field) {
		if (value <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, field, field + "는 양수여야 합니다");
		}
	}

	private static void requireNonNegativeRange(long value, String field) {
		if (value < 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, field, field + "는 음수일 수 없습니다");
		}
	}
}
