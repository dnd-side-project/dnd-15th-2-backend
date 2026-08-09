package com.dnd.qello.feed.view;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;

/**
 * 방향 구간 하나의 집계. 구간 키·표시명·정렬 순서는 조회 시점의 ACTIVE
 * direction_scheme에서 파생되며 저장되지 않는다 — 스킴이 8방향에서 16방향으로
 * 바뀌어도 데이터 마이그레이션 없이 재분류된다.
 * count가 0인 방향은 애초에 이 타입의 인스턴스로 만들어지지 않는다(SQL의
 * INNER JOIN이 자연히 제외한다) — 여기서는 그 판단을 하지 않는다.
 */
public record DirectionChip(String segmentKey, String displayName, int sortOrder, long count) {

	public DirectionChip {
		if (segmentKey == null || segmentKey.isBlank()) {
			throw new FeedException(FeedErrorCode.INVALID_TEXT, "segmentKey", "segmentKey는 필수입니다");
		}
		if (displayName == null || displayName.isBlank()) {
			throw new FeedException(FeedErrorCode.INVALID_TEXT, "displayName", "displayName은 필수입니다");
		}
		if (sortOrder < 0) {
			throw new FeedException(
				FeedErrorCode.INVALID_VALUE_RANGE, "sortOrder", "sortOrder는 0 이상이어야 합니다: " + sortOrder);
		}
		if (count < 0) {
			throw new FeedException(FeedErrorCode.INVALID_VALUE_RANGE, "count", "count는 0 이상이어야 합니다: " + count);
		}
	}
}
