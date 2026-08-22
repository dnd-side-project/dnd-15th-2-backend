package com.dnd.qello.notification.domain;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record NotificationPreference(NotificationType notificationType, long userId, boolean enabled) {

	public NotificationPreference {
		if (notificationType == null || userId <= 0) {
			throw new NotificationException(
				NotificationErrorCode.REQUIRED_VALUE_MISSING, null, "알림 설정 값이 유효하지 않습니다");
		}
	}
}
