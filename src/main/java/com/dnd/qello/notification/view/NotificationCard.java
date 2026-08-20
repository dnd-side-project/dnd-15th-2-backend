package com.dnd.qello.notification.view;

import java.time.Instant;

import com.dnd.qello.notification.domain.NotificationType;

/**
 * 알림함 목록의 줄 1개. {@code targetKind}·{@code targetId}·{@code targetState}는
 * 대상 생존 판정(§7.2)의 결과이고, {@code expiresAt}은 살아 있는 질문글 알림에만
 * 채운다 — 만료된 줄에 만료 시각을 실어 보내면 클라이언트가 살아 있는 줄로
 * 오해한다.
 */
public record NotificationCard(
	long notificationId,
	NotificationType type,
	Instant createdAt,
	Instant readAt,
	boolean unread,
	NotificationTargetKind targetKind,
	Long targetId,
	NotificationTargetState targetState,
	Instant expiresAt
) {

	public NotificationCard {
		if (targetKind == null) {
			throw new IllegalArgumentException("targetKind must not be null");
		}
		if (targetKind == NotificationTargetKind.NONE) {
			if (targetId != null) {
				throw new IllegalArgumentException("NONE target must not carry an id");
			}
		} else if (targetId == null) {
			throw new IllegalArgumentException("non-NONE target requires an id");
		}
		if (targetKind == NotificationTargetKind.NONE && targetState != null) {
			throw new IllegalArgumentException("NONE target must not carry a state");
		}
		if (targetKind != NotificationTargetKind.NONE && targetState == null) {
			throw new IllegalArgumentException("non-NONE target requires a state");
		}
		boolean expirableTarget =
			targetKind == NotificationTargetKind.DIRECTION_POST && targetState == NotificationTargetState.AVAILABLE;
		if (expiresAt != null && !expirableTarget) {
			throw new IllegalArgumentException(
				"expiresAt is only allowed for an AVAILABLE DIRECTION_POST target");
		}
	}
}
