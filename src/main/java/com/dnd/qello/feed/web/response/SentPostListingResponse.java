package com.dnd.qello.feed.web.response;

import java.time.Instant;
import java.util.List;

import com.dnd.qello.feed.view.SentPostCard;

/**
 * `내가 보낸 질문` 목록 공개 모델. nextCursor는 반환 건수가 요청 limit과 같을 때만
 * 채운다 — 그보다 적으면 마지막 페이지라는 뜻이라 null이다.
 */
public record SentPostListingResponse(
	List<Card> cards,
	Cursor nextCursor
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

	public record Card(
		long postId,
		String questionText,
		String bodyText,
		List<Long> mediaIds,
		String coarseRegionCode,
		Instant submittedAt,
		Instant expiresAt,
		long answerCount,
		long reactionCount,
		long unreadAnswerCount
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

	public record Cursor(Instant submittedAt, long postId) { }
}
