package com.dnd.qello.notification.service;

import java.util.Collections;
import java.util.Map;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;

public record UpdateNotificationPreferences(
	boolean pushEnabled,
	NotificationQuietHours quietHours,
	Map<NotificationType, Boolean> typeEnabled) {

	public UpdateNotificationPreferences {
		typeEnabled = Collections.unmodifiableMap(NotificationPreferenceSnapshot.requireNonNullEntries(typeEnabled));
	}

	public void requireCompleteTypeSet() {
		NotificationPreferenceSnapshot.requireComplete(typeEnabled);
	}
}
