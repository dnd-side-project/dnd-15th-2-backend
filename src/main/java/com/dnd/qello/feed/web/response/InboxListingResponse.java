package com.dnd.qello.feed.web.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxListing;

/** 정확 위치와 내부 사용자 식별자를 제외한 수신함 목록 공개 모델이다. */
public record InboxListingResponse(
	List<Card> cards,
	List<Chip> chips
) {
	public InboxListingResponse {
		cards = List.copyOf(cards);
		chips = List.copyOf(chips);
	}

	public static InboxListingResponse from(InboxListing listing) {
		return new InboxListingResponse(
			listing.cards().stream().map(Card::from).toList(),
			listing.chips().stream().map(Chip::from).toList());
	}

	public record Card(
		long postRecipientId,
		long postId,
		String status,
		String questionText,
		String bodyText,
		List<Long> mediaIds,
		String senderCoarseRegionCode,
		BigDecimal inboundBearingDegrees,
		Long distanceM,
		String distanceBand,
		Instant matchedAt,
		Instant expiresAt,
		long answerCount,
		long reactionCount,
		long unreadAnswerCount
	) {
		public Card {
			mediaIds = List.copyOf(mediaIds);
		}

		public static Card from(InboxCard card) {
			return new Card(
				card.postRecipientId(), card.postId(), card.status().name(), card.questionText(), card.bodyText(),
				card.mediaIds(), card.senderCoarseRegionCode(), card.inboundBearingDegrees(), card.distanceM(),
				card.distanceBand(), card.matchedAt(), card.expiresAt(), card.answerCount(), card.reactionCount(),
				card.unreadAnswerCount());
		}
	}

	public record Chip(String segmentKey, String displayName, int sortOrder, long count) {
		public static Chip from(DirectionChip chip) {
			return new Chip(chip.segmentKey(), chip.displayName(), chip.sortOrder(), chip.count());
		}
	}
}
