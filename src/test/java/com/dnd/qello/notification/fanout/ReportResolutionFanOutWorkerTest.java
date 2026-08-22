/**
 * Created at: 2026-08-21T17:40:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-017,
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-018
 */
package com.dnd.qello.notification.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.repository.SafetyRepository;

@ExtendWith(MockitoExtension.class)
class ReportResolutionFanOutWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-21T08:00:00Z");
	private static final long REPORT_ID = 55L;
	private static final long REPORTER_ID = 42L;

	@Mock private OutboxEventRepository outbox;
	@Mock private NotificationRepository notifications;
	@Mock private NotificationPreferenceRepository preferences;
	@Mock private SafetyRepository safety;
	@Mock private PlatformTransactionManager transactionManager;

	@Test
	@DisplayName("global/type ON이면 notification 저장 후 active device별 delivery를 만든다")
	void persistsNotificationAndDeliveriesWhenPushEnabled() {
		ReportResolutionFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent();
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(safety.findReportById(REPORT_ID)).thenReturn(Optional.of(report(REPORTER_ID)));
		when(notifications.saveIfAbsent(any(Notification.class))).thenReturn(notification(900L));
		when(preferences.isPushEnabled(REPORTER_ID, NotificationType.REPORT_RESOLVED)).thenReturn(true);
		when(notifications.findActiveDeviceIdsByUserId(REPORTER_ID)).thenReturn(List.of(1001L, 1002L));
		when(outbox.complete(anyLong(), any(String.class), anyLong(), any(Instant.class))).thenReturn(true);

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(ReportResolutionFanOutWorker.Outcome.PROCESSED);
		verify(notifications).saveIfAbsent(any(Notification.class));
		verify(notifications, times(2)).saveDeliveryIfAbsent(any(NotificationDelivery.class));
	}

	@Test
	@DisplayName("global OFF 또는 type OFF면 notification은 남기고 device/delivery 조회를 생략한다")
	void keepsInboxRowWhenPushDisabled() {
		ReportResolutionFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent();
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(safety.findReportById(REPORT_ID)).thenReturn(Optional.of(report(REPORTER_ID)));
		when(notifications.saveIfAbsent(any(Notification.class))).thenReturn(notification(900L));
		when(preferences.isPushEnabled(REPORTER_ID, NotificationType.REPORT_RESOLVED)).thenReturn(false);
		when(outbox.complete(anyLong(), any(String.class), anyLong(), any(Instant.class))).thenReturn(true);

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(ReportResolutionFanOutWorker.Outcome.PROCESSED);
		verify(notifications).saveIfAbsent(any(Notification.class));
		verify(notifications, never()).findActiveDeviceIdsByUserId(anyLong());
		verify(notifications, never()).saveDeliveryIfAbsent(any(NotificationDelivery.class));
	}

	private ReportResolutionFanOutWorker worker() {
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return new ReportResolutionFanOutWorker(outbox, notifications, preferences, safety,
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static Report report(long reporterId) {
		return new Report(REPORT_ID, reporterId, null, null, 99L, "SPAM", "detail",
			ReportStatus.NO_VIOLATION, NOW.minusSeconds(60), NOW, 1L, null);
	}

	private static Notification notification(long id) {
		return new Notification(id, REPORTER_ID, 1L, NotificationType.REPORT_RESOLVED,
			"report-resolved:" + REPORT_ID, null, null, REPORT_ID,
			com.dnd.qello.notification.domain.NotificationStatus.UNREAD, NOW, null);
	}

	private static OutboxEvent claimedEvent() {
		OutboxEvent pending = OutboxEvent.pending(OutboxAggregateType.REPORT, REPORT_ID,
			OutboxEventType.REPORT_RESOLVED, "report-resolved:" + REPORT_ID, "{\"reportId\":55}", NOW);
		return new OutboxEvent(1L, pending.aggregateType(), pending.aggregateId(), pending.eventType(),
			pending.dedupKey(), pending.payload(), pending.status(), pending.attemptCount(),
			pending.nextAttemptAt(), pending.createdAt(), pending.processedAt()).claimed(
				"report-resolution-worker", NOW, NOW.plusSeconds(30));
	}

	private static ReportResolutionFanOutWorker.BatchCommand command() {
		return new ReportResolutionFanOutWorker.BatchCommand(10, "report-resolution-worker", NOW,
			NOW.plusSeconds(30), new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1)));
	}
}
