package com.dnd.qello.notification.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record NotificationPreferenceSnapshot(
	long userId,
	boolean pushEnabled,
	NotificationQuietHours quietHours,
	Map<NotificationType, Boolean> typeEnabled) {

	public NotificationPreferenceSnapshot {
		if (userId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "userId",
				"알림 설정 대상 사용자가 올바르지 않습니다.");
		}
		EnumMap<NotificationType, Boolean> copy = requireNonNullEntries(typeEnabled);
		requireComplete(copy);
		typeEnabled = Collections.unmodifiableMap(copy);
	}

	public static EnumMap<NotificationType, Boolean> requireNonNullEntries(Map<NotificationType, Boolean> typeEnabled) {
		if (typeEnabled == null) {
			throw invalidPreference();
		}
		EnumMap<NotificationType, Boolean> copy = new EnumMap<>(NotificationType.class);
		for (Map.Entry<NotificationType, Boolean> entry : typeEnabled.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				throw invalidPreference();
			}
			copy.put(entry.getKey(), entry.getValue());
		}
		return copy;
	}

	public static void requireComplete(Map<NotificationType, Boolean> typeEnabled) {
		if (typeEnabled.size() != NotificationType.values().length
			|| !typeEnabled.keySet().equals(EnumSet.allOf(NotificationType.class))) {
			throw invalidPreference();
		}
	}

	private static NotificationException invalidPreference() {
		return new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "preferences",
			"알림 6종 설정을 정확히 한 번씩 지정해야 합니다.");
	}
}
