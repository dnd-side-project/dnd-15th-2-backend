package com.dnd.qello.notification.repository;

import java.util.Map;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;

public interface NotificationPreferenceRepository {

	NotificationPreferenceSnapshot findByUserId(long userId);

	void lockUser(long userId);

	void saveUserSetting(long userId, boolean pushEnabled, NotificationQuietHours quietHours);

	void replaceTypePreferences(long userId, Map<NotificationType, Boolean> typeEnabled);

	boolean isPushEnabled(long userId, NotificationType notificationType);
}
