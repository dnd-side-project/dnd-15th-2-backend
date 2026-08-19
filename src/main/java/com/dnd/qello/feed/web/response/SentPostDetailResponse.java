package com.dnd.qello.feed.web.response;

import java.time.Instant;

import com.dnd.qello.feed.view.SentPostDetail;

/** `내가 보낸 질문` 상세 공개 모델이다. */
public record SentPostDetailResponse(
	SentPostListingResponse.Card card,
	Instant answersReadAt
) {
	public static SentPostDetailResponse from(SentPostDetail detail) {
		return new SentPostDetailResponse(SentPostListingResponse.Card.from(detail.card()), detail.answersReadAt());
	}
}
