package com.dnd.qello.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.domain.UserBlock;

/**
 * Created at: 2026-08-03T21:10:00+09:00
 * Source scenario: TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-UNIT-003 through UNIT-006
 */
class SafetyNotificationBoundaryTest {

	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

	@Test
	@DisplayName("UserBlock은 자기 차단과 시간 역전을 거절한다")
	void validatesBlockInvariant() {
		assertThat(UserBlock.create(1L, 2L, NOW).active()).isTrue();
		assertThatThrownBy(() -> UserBlock.create(1L, 1L, NOW)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> UserBlock.create(1L, 2L, NOW).release(NOW.minusSeconds(1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Report는 신고 대상을 정확히 하나만 허용하고 종결 시각을 기록한다")
	void validatesReportTargetAndResolution() {
		Report report = Report.forAnswer(1L, 9L, "ABUSE", null, NOW);
		assertThat(report.status()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(report.resolve(ReportStatus.ACTIONED, NOW.plusSeconds(1)).resolvedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThatThrownBy(() -> new Report(null, 1L, 2L, 3L, null, "ABUSE", null,
			ReportStatus.RECEIVED, NOW, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Outbox는 JSON object와 명시적인 상태 전이만 허용한다")
	void transitionsOutboxIdempotently() {
		OutboxEvent event = OutboxEvent.pending(OutboxAggregateType.ANSWER, 7L,
			OutboxEventType.ANSWER_PUBLISHED, "answer-published:7", "{\"answerId\":7}", NOW);
		OutboxEvent processing = event.claimed(NOW.plusSeconds(1));
		OutboxEvent processed = processing.processed(NOW.plusSeconds(2));

		assertThat(processing.status()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(processing.attemptCount()).isEqualTo(1);
		assertThat(processed.status()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(processed.processedAt()).isEqualTo(NOW.plusSeconds(2));
		assertThatThrownBy(() -> OutboxEvent.pending(OutboxAggregateType.ANSWER, 7L,
			OutboxEventType.ANSWER_PUBLISHED, "bad", "[]", NOW)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Notification과 Delivery는 대상 중복과 terminal 재처리를 막는다")
	void enforcesNotificationAndDeliveryStates() {
		Notification notification = new Notification(null, 2L, 3L, NotificationType.ANSWER_RECEIVED,
			"answer:7", null, 7L, NotificationStatus.UNREAD, NOW, null);
		assertThat(notification.markRead(NOW.plusSeconds(1)).status()).isEqualTo(NotificationStatus.READ);
		NotificationDelivery delivery = NotificationDelivery.pending(4L, 5L, NOW).claimed(NOW.plusSeconds(1));
		assertThat(delivery.sent(NOW.plusSeconds(2), "provider-id").status()).isEqualTo(DeliveryStatus.SENT);
		assertThatThrownBy(() -> new Notification(null, 2L, 3L, NotificationType.ANSWER_RECEIVED,
			"bad", 1L, 7L, NotificationStatus.UNREAD, NOW, null)).isInstanceOf(IllegalArgumentException.class);
	}
}
