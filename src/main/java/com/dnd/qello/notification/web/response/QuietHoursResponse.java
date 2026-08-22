package com.dnd.qello.notification.web.response;

import com.dnd.qello.notification.domain.NotificationQuietHours;

public record QuietHoursResponse(
	String start,
	String end,
	String zoneId
) {
	public static QuietHoursResponse from(NotificationQuietHours quietHours) {
		return new QuietHoursResponse(
			quietHours.start().toString(),
			quietHours.end().toString(),
			quietHours.zoneId().getId());
	}
}
