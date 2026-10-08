package com.dnd.qello.notification.web.response;

import java.time.Instant;

import com.dnd.qello.notification.view.NotificationDismissal;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationDismissResponse(
		@Schema(description = "이번 요청으로 지운 알림 수. 지울 알림이 없었으면 0입니다") int dismissedCount,
		@Schema(description = "지우기 기준이 된 서버 시각. 이 시각 이후에 도착한 알림은 지워지지 않습니다") Instant dismissedAt) {

	public static NotificationDismissResponse from(NotificationDismissal dismissal) {
		return new NotificationDismissResponse(dismissal.dismissedCount(), dismissal.dismissedAt());
	}
}
