/**
 * Created at: 2026-10-08T02:53:18+09:00
 * Source scenario: TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-001
 */
package com.dnd.qello.notification;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationDismissTest {

	private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");
	private static final Instant EARLIER_READ_AT = CREATED_AT.plusSeconds(60);
	private static final Instant NOW = CREATED_AT.plusSeconds(3600);

	@Test
	@DisplayName("UNIT-001 read_at이 없는 DISMISSED 알림을 읽음 처리해도 DISMISSED와 null read_at을 유지한다")
	void markReadKeepsDismissedWithoutReadAt() {
		Notification dismissed = notification(NotificationStatus.DISMISSED, null);

		Notification result = dismissed.markRead(NOW);

		assertThat(result.status()).isEqualTo(NotificationStatus.DISMISSED);
		assertThat(result.readAt()).isNull();
		assertThat(result).isEqualTo(dismissed);
	}

	@Test
	@DisplayName("UNIT-001 read_at이 있는 DISMISSED 알림을 읽음 처리해도 기존 read_at을 바꾸지 않는다")
	void markReadKeepsDismissedReadAt() {
		Notification dismissed = notification(NotificationStatus.DISMISSED, EARLIER_READ_AT);

		Notification result = dismissed.markRead(NOW);

		assertThat(result.status()).isEqualTo(NotificationStatus.DISMISSED);
		assertThat(result.readAt()).isEqualTo(EARLIER_READ_AT);
	}

	@Test
	@DisplayName("UNIT-001 UNREAD 알림은 READ가 되고 read_at은 요청 시각이다")
	void markReadTransitionsUnreadToRead() {
		Notification unread = notification(NotificationStatus.UNREAD, null);

		Notification result = unread.markRead(NOW);

		assertThat(result.status()).isEqualTo(NotificationStatus.READ);
		assertThat(result.readAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("UNIT-001 REVOKED 알림의 읽음 처리는 계속 NOT-DOM-003으로 거부한다")
	void markReadStillRejectsRevoked() {
		Notification revoked = notification(NotificationStatus.REVOKED, null);

		assertThatThrownBy(() -> revoked.markRead(NOW))
				.isInstanceOf(NotificationException.class)
				.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_NOTIFICATION_STATUS);
	}

	private static Notification notification(NotificationStatus status, Instant readAt) {
		return new Notification(1L, 2L, 3L, NotificationType.DIRECTION_POST_RECEIVED, "gh332-unit-dedup",
				4L, null, null, status, CREATED_AT, readAt);
	}
}
