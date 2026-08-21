package com.dnd.qello.notification.web.request;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

import io.swagger.v3.oas.annotations.media.Schema;

public record QuietHoursRequest(
	@Schema(description = "방해 금지 시작 시각", example = "22:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
	String start,

	@Schema(description = "방해 금지 종료 시각", example = "07:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
	String end,

	@Schema(description = "IANA Zone ID", example = "Asia/Seoul", requiredMode = Schema.RequiredMode.REQUIRED)
	String zoneId
) {
	public NotificationQuietHours toDomain() {
		if (start == null || end == null || zoneId == null) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "quietHours",
				"quietHours는 start·end·zoneId를 모두 지정해야 합니다.");
		}
		try {
			return new NotificationQuietHours(
				LocalTime.parse(start),
				LocalTime.parse(end),
				ZoneId.of(zoneId));
		}
		catch (DateTimeParseException exception) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "quietHours",
				"quietHours 시각 형식이 올바르지 않습니다.");
		}
		catch (RuntimeException exception) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "quietHours",
				"quietHours 시간대가 올바르지 않습니다.");
		}
	}
}
