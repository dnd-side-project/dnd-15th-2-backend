package com.dnd.qello.notification.domain;

import java.time.Instant;

public record Notification(Long id, long recipientId, long outboxEventId, NotificationType notificationType,
	String dedupKey, Long directionPostId, Long answerId, NotificationStatus status,
	Instant createdAt, Instant readAt) {

	public Notification {
		if (recipientId <= 0 || outboxEventId <= 0) throw new IllegalArgumentException("ID가 유효하지 않습니다");
		if (notificationType == null || dedupKey == null || dedupKey.isBlank() || status == null || createdAt == null) throw new IllegalArgumentException("알림 필수 값이 유효하지 않습니다");
		if (directionPostId != null && directionPostId <= 0 || answerId != null && answerId <= 0) throw new IllegalArgumentException("target ID가 유효하지 않습니다");
		if (directionPostId != null && answerId != null) throw new IllegalArgumentException("알림 대상은 최대 하나입니다");
		if (readAt != null && status != NotificationStatus.READ && status != NotificationStatus.DISMISSED) throw new IllegalArgumentException("읽은 시각과 상태가 맞지 않습니다");
	}

	public Notification markRead(Instant at) {
		if (status == NotificationStatus.REVOKED) throw new IllegalStateException("취소된 알림은 읽음 처리할 수 없습니다");
		return new Notification(id, recipientId, outboxEventId, notificationType, dedupKey, directionPostId, answerId,
			NotificationStatus.READ, createdAt, at);
	}
}
