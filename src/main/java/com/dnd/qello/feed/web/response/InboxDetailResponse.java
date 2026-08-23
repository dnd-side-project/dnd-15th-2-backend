package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.InboxDetail;

/** 수신함 카드 공개 모델과 열람·넘김 시각만 노출하는 상세 응답이다. */
public record InboxDetailResponse(
	@Schema(description = "받은 질문 카드. list 응답의 카드와 같은 구조입니다") InboxListingResponse.Card card,
	@Schema(description = "이 항목을 처음 연 시각") Instant openedAt,
	@Schema(description = "넘김을 요청한 시각. 넘김을 요청하지 않았으면 null입니다") Instant skipRequestedAt
) {
	public static InboxDetailResponse from(InboxDetail detail) {
		return new InboxDetailResponse(
			InboxListingResponse.Card.from(detail.card()), detail.openedAt(), detail.skipRequestedAt());
	}
}
