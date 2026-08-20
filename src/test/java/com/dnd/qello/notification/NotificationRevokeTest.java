package com.dnd.qello.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

/**
 * Created at: 2026-08-19T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS-UNIT-006 through UNIT-008
 */
class NotificationRevokeTest {

	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

	@Test
	@DisplayName("UNREAD 알림을 revoke하면 REVOKED가 된다")
	void revokesUnreadNotification() {
		Notification unread = notification(NotificationStatus.UNREAD, null);

		assertThat(unread.revoke().status()).isEqualTo(NotificationStatus.REVOKED);
	}

	@Test
	@DisplayName("READ 알림을 revoke하면 REVOKED가 되고 readAt은 비운다")
	void revokesReadNotificationAndClearsReadAt() {
		Notification read = notification(NotificationStatus.READ, NOW);

		Notification revoked = read.revoke();

		assertThat(revoked.status()).isEqualTo(NotificationStatus.REVOKED);
		assertThat(revoked.readAt()).isNull();
	}

	@Test
	@DisplayName("이미 REVOKED인 알림을 다시 revoke해도 예외 없이 같은 상태를 유지한다(멱등)")
	void revokeIsIdempotent() {
		Notification revoked = notification(NotificationStatus.REVOKED, null);

		assertThat(revoked.revoke().status()).isEqualTo(NotificationStatus.REVOKED);
	}

	@Test
	@DisplayName("REVOKED 알림은 읽음 처리할 수 없다(기존 가드 회귀 확인)")
	void revokedNotificationCannotBeMarkedRead() {
		Notification revoked = notification(NotificationStatus.REVOKED, null);

		assertThatThrownBy(() -> revoked.markRead(NOW))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_NOTIFICATION_STATUS);
	}

	private static Notification notification(NotificationStatus status, Instant readAt) {
		return new Notification(1L, 2L, 3L, NotificationType.REPORT_RESOLVED, "report-resolved:9",
			null, null, 9L, status, NOW, readAt);
	}
}
