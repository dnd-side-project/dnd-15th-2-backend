package com.dnd.qello.notification.fanout;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

record FanOutInstruction(
	NotificationType notificationType,
	long recipientId,
	Long actorId,
	Long answerId,
	String dedupKey,
	boolean suppressed
) {

	FanOutInstruction {
		if (!suppressed) {
			if (notificationType == null || recipientId <= 0 || dedupKey == null || dedupKey.isBlank()) {
				throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, null,
					"fan-out instruction이 유효하지 않습니다");
			}
			if (hasInvalidId(actorId) || hasInvalidId(answerId)) {
				throw new NotificationException(NotificationErrorCode.INVALID_ID, null,
					"fan-out instruction ID가 유효하지 않습니다");
			}
		}
	}

	static FanOutInstruction notification(NotificationType notificationType, long recipientId,
		Long actorId, Long answerId, String dedupKey) {
		return new FanOutInstruction(notificationType, recipientId, actorId, answerId, dedupKey, false);
	}

	static FanOutInstruction suppress() {
		return new FanOutInstruction(null, 0, null, null, "suppressed", true);
	}

	private static boolean hasInvalidId(Long id) {
		return id != null && id <= 0;
	}
}
