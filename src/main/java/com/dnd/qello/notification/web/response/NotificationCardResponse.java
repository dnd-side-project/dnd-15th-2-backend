package com.dnd.qello.notification.web.response;

import java.time.Instant;

import com.dnd.qello.notification.view.NotificationCard;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림함 한 줄의 공개 모델이다. 질문·답변 본문, 닉네임, 계정 식별자, 위치는
 * 싣지 않는다 — 알림 문구는 6종 모두 익명이므로 {@code type}만으로 클라이언트가
 * 조립한다.
 */
public record NotificationCardResponse(
	@Schema(description = "알림 식별자") long notificationId,
	@Schema(description = "알림 종류", allowableValues = {
		"ANSWER_RECEIVED",
		"ANSWER_REACTED",
		"DIRECTION_POST_RECEIVED",
		"REPORT_RESOLVED",
		"QUESTION_PROPOSAL_REVIEWED",
		"QUESTION_RECOMMENDED"
	})
	String type,
	@Schema(description = "알림이 도착한 시각") Instant createdAt,
	@Schema(description = "이 알림을 읽은 시각. 아직 읽지 않았으면 null입니다") Instant readAt,
	@Schema(description = "아직 읽지 않은 알림인지 여부") boolean unread,
	@Schema(description = "이 알림이 가리키는 글의 요약") NotificationTargetSummaryResponse target,
	@Schema(description = "이 알림이 가리키는 질문글이 만료되는 시각. 아직 볼 수 있는 질문글 알림에만 채워지고 그 밖에는 null입니다") Instant expiresAt
) {
	public static NotificationCardResponse from(NotificationCard card) {
		return new NotificationCardResponse(
			card.notificationId(), card.type().name(), card.createdAt(), card.readAt(), card.unread(),
			NotificationTargetSummaryResponse.of(card.targetKind(), card.targetId(), card.targetState()),
			card.expiresAt());
	}
}
