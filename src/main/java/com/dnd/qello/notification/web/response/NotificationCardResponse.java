package com.dnd.qello.notification.web.response;

import java.time.Instant;

import com.dnd.qello.notification.view.NotificationCard;

/**
 * 알림함 한 줄의 공개 모델이다. 질문·답변 본문, 닉네임, 계정 식별자, 위치는
 * 싣지 않는다 — 알림 문구는 6종 모두 익명이므로 {@code type}만으로 클라이언트가
 * 조립한다.
 */
public record NotificationCardResponse(
	long notificationId,
	String type,
	Instant createdAt,
	Instant readAt,
	boolean unread,
	NotificationTargetSummaryResponse target,
	Instant expiresAt
) {
	public static NotificationCardResponse from(NotificationCard card) {
		return new NotificationCardResponse(
			card.notificationId(), card.type().name(), card.createdAt(), card.readAt(), card.unread(),
			NotificationTargetSummaryResponse.of(card.targetKind(), card.targetId(), card.targetState()),
			card.expiresAt());
	}
}
