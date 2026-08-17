package com.dnd.qello.feed.web.response;

import java.time.Instant;

import com.dnd.qello.feed.view.InboxDetail;

/** 수신함 카드 공개 모델과 열람·넘김 시각만 노출하는 상세 응답이다. */
public record InboxDetailResponse(
	InboxListingResponse.Card card,
	Instant openedAt,
	Instant skipRequestedAt
) {
	public static InboxDetailResponse from(InboxDetail detail) {
		return new InboxDetailResponse(
			InboxListingResponse.Card.from(detail.card()), detail.openedAt(), detail.skipRequestedAt());
	}
}
