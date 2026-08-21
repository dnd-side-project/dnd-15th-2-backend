/**
 * Created at: 2026-08-21T20:40:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-008 through INT-013
 */
package com.dnd.qello;

import java.util.EnumMap;
import java.util.Map;

import com.dnd.qello.notification.domain.NotificationType;

final class NotificationPreferenceIntegrationFixtures {

	private NotificationPreferenceIntegrationFixtures() {
	}

	static Map<NotificationType, Boolean> typePreferences(NotificationType targetType, boolean enabled) {
		EnumMap<NotificationType, Boolean> preferencesByType = new EnumMap<>(NotificationType.class);
		for (NotificationType notificationType : NotificationType.values()) {
			preferencesByType.put(notificationType, true);
		}
		preferencesByType.put(targetType, enabled);
		return preferencesByType;
	}
}
