package com.dnd.qello.notification.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSeenResponse(
	@Schema(description = "새로 기록된, 알림함을 마지막으로 연 시각") Instant seenAt
) {

	public static NotificationSeenResponse from(Instant seenAt) {
		return new NotificationSeenResponse(seenAt);
	}
}
