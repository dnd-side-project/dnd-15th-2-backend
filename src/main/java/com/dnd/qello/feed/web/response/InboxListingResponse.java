package com.dnd.qello.feed.web.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxListing;

/** 정확 위치와 내부 사용자 식별자를 제외한 수신함 목록 공개 모델이다. */
public record InboxListingResponse(
	@Schema(description = "받은 질문 카드 목록") List<Card> cards,
	@Schema(description = "방향 구간별 집계. directionSegmentKey와 무관하게 category 전체 기준입니다") List<Chip> chips
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

	@Schema(name = "InboxCard")
	public record Card(
		@Schema(description = "이 수신 항목의 식별자") long postRecipientId,
		@Schema(description = "이 카드가 속한 질문글 식별자") long postId,
		@Schema(description = "이 수신 항목의 현재 상태") String status,
		@Schema(description = "이 질문글이 사용한 질문 문항의 텍스트") String questionText,
		@Schema(description = "발신자가 추가로 쓴 본문") String bodyText,
		@Schema(description = "첨부된 이미지 식별자 목록") List<Long> mediaIds,
		@Schema(description = "발신자의 대략적인 지역 코드. 정확한 위치는 포함하지 않습니다") String senderCoarseRegionCode,
		@Schema(description = "이 카드를 보는 수신자 기준으로 계산한 도착 방위각(도 단위). 화면의 방향 표시는 이 값을 씁니다") BigDecimal inboundBearingDegrees,
		@Schema(description = "발신자와의 거리(미터). 근거리 구간에서는 null이고 대신 distanceBand가 채워집니다") Long distanceM,
		@Schema(description = "근거리 구간일 때만 채워지는 거리 표시 문구. 그 외에는 null이고 대신 distanceM이 채워집니다") String distanceBand,
		@Schema(description = "이 수신 항목이 매칭된 시각") Instant matchedAt,
		@Schema(description = "이 질문글이 만료되는 시각") Instant expiresAt,
		@Schema(description = "이 질문글에 달린 답변 수") long answerCount,
		@Schema(description = "조회하는 본인이 이 질문글에 공감했는지 여부") boolean reactedByMe,
		@Schema(description = "이 질문글이 받은 공감 총수") long reactionCount,
		@Schema(description = "마지막 답변 열람 이후 새로 공개된 답변 수") long unreadAnswerCount
	) {
		public Card {
			mediaIds = List.copyOf(mediaIds);
		}

		public static Card from(InboxCard card) {
			return new Card(
				card.postRecipientId(), card.postId(), card.status().name(), card.questionText(), card.bodyText(),
				card.mediaIds(), card.senderCoarseRegionCode(), card.inboundBearingDegrees(), card.distanceM(),
				card.distanceBand(), card.matchedAt(), card.expiresAt(), card.answerCount(), card.reactedByMe(),
				card.reactionCount(), card.unreadAnswerCount());
		}
	}

	@Schema(name = "DirectionChip")
	public record Chip(
		@Schema(description = "방향 구간을 식별하는 키") String segmentKey,
		@Schema(description = "그 방향 구간의 화면 표시명") String displayName,
		@Schema(description = "화면에 표시할 때 쓰는 정렬 순서") int sortOrder,
		@Schema(description = "그 방향 구간에 속한 수신 항목 수. 0인 방향은 나타나지 않습니다") long count
	) {
		public static Chip from(DirectionChip chip) {
			return new Chip(chip.segmentKey(), chip.displayName(), chip.sortOrder(), chip.count());
		}
	}
}
