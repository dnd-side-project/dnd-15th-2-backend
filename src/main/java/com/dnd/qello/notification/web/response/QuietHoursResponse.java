package com.dnd.qello.notification.web.response;

import com.dnd.qello.notification.domain.NotificationQuietHours;

import io.swagger.v3.oas.annotations.media.Schema;

public record QuietHoursResponse(
	@Schema(description = "알림을 받지 않기 시작하는 시각", example = "22:00") String start,
	@Schema(description = "알림을 다시 받기 시작하는 시각", example = "07:00") String end,
	@Schema(description = "위 두 시각을 해석할 시간대", example = "Asia/Seoul") String zoneId
) {
	public static QuietHoursResponse from(NotificationQuietHours quietHours) {
		return new QuietHoursResponse(
			quietHours.start().toString(),
			quietHours.end().toString(),
			quietHours.zoneId().getId());
	}
}
