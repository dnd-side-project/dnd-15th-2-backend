package com.dnd.qello.notification.web.response;

import java.time.Instant;

public record NotificationSeenResponse(Instant seenAt) {

	public static NotificationSeenResponse from(Instant seenAt) {
		return new NotificationSeenResponse(seenAt);
	}
}
