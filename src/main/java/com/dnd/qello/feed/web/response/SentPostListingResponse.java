package com.dnd.qello.feed.web.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.SentPostCard;

/**
 * `내가 보낸 질문` 목록 공개 모델. nextCursor는 반환 건수가 요청 limit과 같을 때만
 * 채운다 — 그보다 적으면 마지막 페이지라는 뜻이라 null이다.
 */
public record SentPostListingResponse(
	@Schema(description = "내가 보낸 질문글 카드 목록") List<Card> cards,
	@Schema(description = "다음 페이지 커서. 마지막 페이지면 null입니다") Cursor nextCursor
) {
	public SentPostListingResponse {
		cards = List.copyOf(cards);
	}

	public static SentPostListingResponse from(List<SentPostCard> cards, int limit) {
		List<Card> mapped = cards.stream().map(Card::from).toList();
		Cursor next = cards.size() == limit
			? new Cursor(cards.get(cards.size() - 1).submittedAt(), cards.get(cards.size() - 1).postId())
			: null;
		return new SentPostListingResponse(mapped, next);
	}

	@Schema(name = "SentPostCard")
	public record Card(
		@Schema(description = "질문글 식별자") long postId,
		@Schema(description = "이 질문글이 사용한 질문 문항의 텍스트") String questionText,
		@Schema(description = "발신자가 추가로 쓴 본문") String bodyText,
		@Schema(description = "첨부된 이미지 식별자 목록") List<Long> mediaIds,
		@Schema(description = "이 질문글을 보낼 때 기록된 발신자의 대략적 지역 코드") String coarseRegionCode,
		@Schema(description = "이 질문글을 제출한 시각") Instant submittedAt,
		@Schema(description = "이 질문글이 만료되는 시각") Instant expiresAt,
		@Schema(description = "이 질문글에 달린 답변 수") long answerCount,
		@Schema(description = "이 질문글이 받은 공감 총수") long reactionCount,
		@Schema(description = "답변 열람 시각 이후 새로 공개된 답변 수") long unreadAnswerCount
	) {
		public Card {
			mediaIds = List.copyOf(mediaIds);
		}

		public static Card from(SentPostCard card) {
			return new Card(
				card.postId(), card.questionText(), card.bodyText(), card.mediaIds(), card.coarseRegionCode(),
				card.submittedAt(), card.expiresAt(), card.answerCount(), card.reactionCount(),
				card.unreadAnswerCount());
		}
	}

	@Schema(name = "SentPostCursor")
	public record Cursor(
		@Schema(description = "다음 페이지 조회에 쓸 제출 시각") Instant submittedAt,
		@Schema(description = "다음 페이지 조회에 쓸 질문글 식별자") long postId
	) { }
}
