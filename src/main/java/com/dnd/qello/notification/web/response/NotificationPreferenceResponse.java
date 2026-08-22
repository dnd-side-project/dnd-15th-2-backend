package com.dnd.qello.notification.web.response;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationType;

public record NotificationPreferenceResponse(
	boolean pushEnabled,
	QuietHoursResponse quietHours,
	List<NotificationTypePreferenceResponse> preferences,
	InboxRecordingPolicy inboxRecordingPolicy
) {
	public static NotificationPreferenceResponse from(NotificationPreferenceSnapshot snapshot) {
		return new NotificationPreferenceResponse(
			snapshot.pushEnabled(),
			snapshot.quietHours() == null ? null : QuietHoursResponse.from(snapshot.quietHours()),
			toResponses(snapshot.typeEnabled()),
			InboxRecordingPolicy.ALWAYS_RECORD);
	}

	private static List<NotificationTypePreferenceResponse> toResponses(Map<NotificationType, Boolean> typeEnabled) {
		return Arrays.stream(NotificationType.values())
			.map(type -> new NotificationTypePreferenceResponse(type.name(), typeEnabled.get(type)))
			.toList();
	}
}
