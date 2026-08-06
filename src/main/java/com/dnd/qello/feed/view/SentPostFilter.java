package com.dnd.qello.feed.view;

/** `내가 쓴 질문` 탭의 필터. 만료 여부는 조회 시각과 expires_at 비교로 판정한다. */
public enum SentPostFilter {
	ALL,
	IN_PROGRESS,
	EXPIRED
}
