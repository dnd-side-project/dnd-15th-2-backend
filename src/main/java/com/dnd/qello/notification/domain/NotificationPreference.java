package com.dnd.qello.notification.domain;

import java.time.LocalTime;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record NotificationPreference(NotificationType notificationType, long userId, boolean enabled,
	LocalTime quietStart, LocalTime quietEnd) {

	public NotificationPreference {
		if (notificationType == null || userId <= 0) {
			throw new NotificationException(
				NotificationErrorCode.REQUIRED_VALUE_MISSING, null, "알림 설정 값이 유효하지 않습니다");
		}
		if ((quietStart == null) != (quietEnd == null)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATE, "quietStart", "quiet 시간은 함께 지정해야 합니다");
		}
	}
}
