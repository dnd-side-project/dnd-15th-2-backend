package com.dnd.qello.notification.domain;

import java.time.LocalTime;
import java.time.ZoneId;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record NotificationQuietHours(LocalTime start, LocalTime end, ZoneId zoneId) {

	public NotificationQuietHours {
		if (start == null || end == null || zoneId == null || start.equals(end)) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "quietHours",
				"시작·종료·시간대를 모두 지정하고 시작과 종료를 다르게 설정해야 합니다.");
		}
		if (!ZoneId.getAvailableZoneIds().contains(zoneId.getId())) {
			throw new NotificationException(NotificationErrorCode.INVALID_PREFERENCE, "quietHours",
				"IANA 지역 기반 시간대를 지정해야 합니다.");
		}
	}
}
