package com.dnd.qello.notification.domain;

import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record Notification(Long id, long recipientId, long outboxEventId, NotificationType notificationType,
	String dedupKey, Long directionPostId, Long answerId, Long reportId, NotificationStatus status,
	Instant createdAt, Instant readAt) {

	public Notification {
		if (recipientId <= 0 || outboxEventId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, null, "ID가 유효하지 않습니다");
		}
		if (notificationType == null || dedupKey == null || dedupKey.isBlank() || status == null || createdAt == null) {
			throw new NotificationException(
				NotificationErrorCode.REQUIRED_VALUE_MISSING, null, "알림 필수 값이 유효하지 않습니다");
		}
		if (directionPostId != null && directionPostId <= 0 || answerId != null && answerId <= 0
			|| reportId != null && reportId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, null, "target ID가 유효하지 않습니다");
		}
		int targetCount = (directionPostId == null ? 0 : 1) + (answerId == null ? 0 : 1) + (reportId == null ? 0 : 1);
		if (targetCount > 1) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_TARGET, null, "알림 대상은 최대 하나입니다");
		}
		if (readAt != null && status != NotificationStatus.READ && status != NotificationStatus.DISMISSED) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATE, "readAt", "읽은 시각과 상태가 맞지 않습니다");
		}
	}

	public Notification markRead(Instant at) {
		if (status == NotificationStatus.REVOKED) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "취소된 알림은 읽음 처리할 수 없습니다");
		}
		return new Notification(id, recipientId, outboxEventId, notificationType, dedupKey, directionPostId, answerId,
			reportId, NotificationStatus.READ, createdAt, at);
	}

	/**
	 * 멱등하다 — 이미 REVOKED면 그대로 반환한다. 전역 숨김이 반복 호출돼도 안전해야
	 * 한다(#155). readAt은 항상 비운다 — ck_notification_read_at이 READ/DISMISSED
	 * 상태에만 read_at을 허용하기 때문이다.
	 */
	public Notification revoke() {
		if (status == NotificationStatus.REVOKED) {
			return this;
		}
		return new Notification(id, recipientId, outboxEventId, notificationType, dedupKey, directionPostId, answerId,
			reportId, NotificationStatus.REVOKED, createdAt, null);
	}
}
