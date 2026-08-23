package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.SentPostDetail;

/** `내가 보낸 질문` 상세 공개 모델이다. */
public record SentPostDetailResponse(
	@Schema(description = "내가 보낸 질문글 카드. list 응답의 카드와 같은 구조입니다") SentPostListingResponse.Card card,
	@Schema(description = "이 질문글의 답변을 마지막으로 읽은 시각") Instant answersReadAt
) {
	public static SentPostDetailResponse from(SentPostDetail detail) {
		return new SentPostDetailResponse(SentPostListingResponse.Card.from(detail.card()), detail.answersReadAt());
	}
}
