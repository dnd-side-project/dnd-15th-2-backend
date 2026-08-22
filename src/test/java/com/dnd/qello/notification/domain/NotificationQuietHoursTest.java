/**
 * Created at: 2026-08-21T18:05:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-001,
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-003,
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-004,
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-005,
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-006
 */
package com.dnd.qello.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

class NotificationQuietHoursTest {

	@Test
	@DisplayName("자정을 통과하는 quiet 시간은 유효하다")
	void acceptsOvernightQuietHours() {
		NotificationQuietHours quietHours = new NotificationQuietHours(
			LocalTime.of(22, 0),
			LocalTime.of(7, 0),
			ZoneId.of("Asia/Seoul"));

		assertThat(quietHours.start()).isEqualTo(LocalTime.of(22, 0));
		assertThat(quietHours.end()).isEqualTo(LocalTime.of(7, 0));
		assertThat(quietHours.zoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

	@Test
	@DisplayName("시작 시간이 없으면 거부한다")
	void rejectsMissingStart() {
		assertThatThrownBy(() -> new NotificationQuietHours(
			null,
			LocalTime.of(7, 0),
			ZoneId.of("Asia/Seoul")))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PREFERENCE);
	}

	@Test
	@DisplayName("종료 시간이 없으면 거부한다")
	void rejectsMissingEnd() {
		assertThatThrownBy(() -> new NotificationQuietHours(
			LocalTime.of(22, 0),
			null,
			ZoneId.of("Asia/Seoul")))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PREFERENCE);
	}

	@Test
	@DisplayName("시간대가 없으면 거부한다")
	void rejectsMissingZoneId() {
		assertThatThrownBy(() -> new NotificationQuietHours(
			LocalTime.of(22, 0),
			LocalTime.of(7, 0),
			null))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PREFERENCE);
	}

	@Test
	@DisplayName("시작과 종료가 같으면 거부한다")
	void rejectsEqualTimes() {
		assertThatThrownBy(() -> new NotificationQuietHours(
			LocalTime.NOON,
			LocalTime.NOON,
			ZoneId.of("Asia/Seoul")))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PREFERENCE);
	}

	@Test
	@DisplayName("고정 offset 시간대는 IANA 지역 기반 Zone ID가 아니므로 거부한다")
	void rejectsFixedOffsetZoneId() {
		assertThatThrownBy(() -> new NotificationQuietHours(
			LocalTime.of(22, 0),
			LocalTime.of(7, 0),
			ZoneId.of("+09:00")))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_PREFERENCE);
	}
}
