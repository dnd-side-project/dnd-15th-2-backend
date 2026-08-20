/**
 * Created at: 2026-08-20T19:07:10+09:00
 * Source scenario: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-UNIT-001,
 * UNIT-002, UNIT-003, UNIT-004, UNIT-005, UNIT-006, UNIT-007, UNIT-008,
 * UNIT-009, UNIT-010, UNIT-011
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
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.repository.SafetyRepository;

@ExtendWith(MockitoExtension.class)
class NotificationFanOutWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");

	@Mock private OutboxEventRepository outbox;
	@Mock private NotificationRepository notifications;
	@Mock private NotificationFanOutResolver resolver;
	@Mock private com.dnd.qello.account.repository.AccountRepository accounts;
	@Mock private SafetyRepository safety;
	@Mock private PlatformTransactionManager transactionManager;

	@Test
	@DisplayName("네 가지 notification fan-out event만 claim한다")
	void claimsOnlyNotificationExpansionEvents() {
		when(resolver.eventTypes()).thenReturn(Set.of(
			OutboxEventType.ANSWER_PUBLISHED,
			OutboxEventType.ANSWER_REACTED,
			OutboxEventType.QUESTION_PROPOSAL_REVIEWED,
			OutboxEventType.QUESTION_RECOMMENDED));
		NotificationFanOutWorker worker = worker();
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of());

		NotificationFanOutWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.claimed()).isZero();
		assertThat(result.outcomes()).isEmpty();
		verify(outbox).claimDue(
			Set.of(OutboxEventType.ANSWER_PUBLISHED, OutboxEventType.ANSWER_REACTED,
				OutboxEventType.QUESTION_PROPOSAL_REVIEWED, OutboxEventType.QUESTION_RECOMMENDED),
			10, "notification-fanout-expansion", NOW, NOW.plusSeconds(30));
	}

	@Test
	@DisplayName("lease identity가 없는 event는 STALE_LEASE로 격리한다")
	void isolatesEventWithoutLeaseIdentity() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.ANSWER_PUBLISHED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = OutboxEvent.pending(OutboxAggregateType.ANSWER, 7L,
			OutboxEventType.ANSWER_PUBLISHED, "answer-published:7", "{\"answerId\":7}", NOW);
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.STALE_LEASE);
		verify(resolver, never()).resolve(any());
		verify(notifications, never()).saveIfAbsent(any(Notification.class));
	}

	@Test
	@DisplayName("preference enabled면 notification 저장 후 active device별 delivery를 만든다")
	void persistsNotificationAndDeliveriesAfterPreferenceCheck() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.QUESTION_RECOMMENDED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent(OutboxEventType.QUESTION_RECOMMENDED, 30L,
			"{\"assignmentId\":30}");
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(resolver.resolve(event)).thenReturn(FanOutInstruction.notification(
			NotificationType.QUESTION_RECOMMENDED, 42L, null, null, "question-recommended:30"));
		when(accounts.findById(42L)).thenReturn(Optional.of(account(42L)));
		when(notifications.saveIfAbsent(any(Notification.class))).thenReturn(notification(900L, 42L,
			NotificationType.QUESTION_RECOMMENDED, "question-recommended:30"));
		when(notifications.isPreferenceEnabled(42L, NotificationType.QUESTION_RECOMMENDED)).thenReturn(true);
		when(notifications.findActiveDeviceIdsByUserId(42L)).thenReturn(List.of(1001L, 1002L));
		when(outbox.complete(anyLong(), any(String.class), anyLong(), any(Instant.class))).thenReturn(true);

		var result = worker.processBatch(command());
		verify(resolver).resolve(event);
		verify(accounts).findById(42L);
		verify(notifications).saveIfAbsent(any(Notification.class));
		verify(outbox).complete(anyLong(), any(String.class), anyLong(), any(Instant.class));
		assertThat(result.outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		verify(notifications).saveIfAbsent(any(Notification.class));
		verify(notifications, times(2)).saveDeliveryIfAbsent(any(NotificationDelivery.class));
	}

	@Test
	@DisplayName("preference disabled면 notification은 남기고 delivery는 만들지 않는다")
	void keepsInboxRowWhenPreferenceIsDisabled() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.QUESTION_RECOMMENDED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent(OutboxEventType.QUESTION_RECOMMENDED, 30L,
			"{\"assignmentId\":30}");
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(resolver.resolve(event)).thenReturn(FanOutInstruction.notification(
			NotificationType.QUESTION_RECOMMENDED, 42L, null, null, "question-recommended:30"));
		when(accounts.findById(42L)).thenReturn(Optional.of(account(42L)));
		when(notifications.saveIfAbsent(any(Notification.class))).thenReturn(notification(900L, 42L,
			NotificationType.QUESTION_RECOMMENDED, "question-recommended:30"));
		when(notifications.isPreferenceEnabled(42L, NotificationType.QUESTION_RECOMMENDED)).thenReturn(false);
		when(outbox.complete(anyLong(), any(String.class), anyLong(), any(Instant.class))).thenReturn(true);

		var result = worker.processBatch(command());
		verify(outbox).complete(anyLong(), any(String.class), anyLong(), any(Instant.class));
		assertThat(result.outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		verify(notifications).saveIfAbsent(any(Notification.class));
		verify(notifications, never()).findActiveDeviceIdsByUserId(anyLong());
		verify(notifications, never()).saveDeliveryIfAbsent(any(NotificationDelivery.class));
	}

	@Test
	@DisplayName("actor와 수신자 사이에 활성 차단이 있으면 notification 자체를 만들지 않는다")
	void suppressesNotificationWhenUsersAreBlocked() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.ANSWER_REACTED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent(OutboxEventType.ANSWER_REACTED, 91L,
			"{\"answerId\":91,\"reactorId\":9}");
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(resolver.resolve(event)).thenReturn(FanOutInstruction.notification(
			NotificationType.ANSWER_REACTED, 3L, 9L, 91L, "answer-reacted:event:91"));
		when(accounts.findById(3L)).thenReturn(Optional.of(account(3L)));
		when(accounts.findById(9L)).thenReturn(Optional.of(account(9L)));
		when(safety.findBlock(3L, 9L)).thenReturn(Optional.empty());
		when(safety.findBlock(9L, 3L)).thenReturn(Optional.of(
			com.dnd.qello.safety.domain.UserBlock.create(9L, 3L, NOW.minusSeconds(10))));
		when(outbox.complete(anyLong(), any(String.class), anyLong(), any(Instant.class))).thenReturn(true);

		var result = worker.processBatch(command());
		verify(outbox).complete(anyLong(), any(String.class), anyLong(), any(Instant.class));
		assertThat(result.outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		verify(notifications, never()).saveIfAbsent(any(Notification.class));
	}

	@Test
	@DisplayName("resolver의 malformed event는 notification을 만들지 않고 permanent DEAD로 분류한다")
	void classifiesMalformedEventAsDead() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.ANSWER_PUBLISHED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent(OutboxEventType.ANSWER_PUBLISHED, 91L, "{}");
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(resolver.resolve(event)).thenThrow(new com.dnd.qello.notification.error.NotificationException(
			com.dnd.qello.notification.error.NotificationErrorCode.INVALID_PAYLOAD, "payload", "bad payload"));
		when(outbox.fail(anyLong(), any(String.class), anyLong(), any(Instant.class), any())).thenReturn(true);

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.DEAD);
		verify(notifications, never()).saveIfAbsent(any(Notification.class));
	}

	@Test
	@DisplayName("transient notification 저장 실패는 retry policy를 적용해 RETRYABLE로 분류한다")
	void classifiesTransientStorageFailureAsRetryable() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.QUESTION_RECOMMENDED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent(OutboxEventType.QUESTION_RECOMMENDED, 30L,
			"{\"assignmentId\":30}");
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(resolver.resolve(event)).thenReturn(FanOutInstruction.notification(
			NotificationType.QUESTION_RECOMMENDED, 42L, null, null, "question-recommended:30"));
		when(accounts.findById(42L)).thenReturn(Optional.of(account(42L)));
		when(notifications.saveIfAbsent(any(Notification.class)))
			.thenThrow(new TransientDataAccessResourceException("connection reset"));
		when(outbox.fail(anyLong(), any(String.class), anyLong(), any(Instant.class), any())).thenReturn(true);

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.RETRYABLE);
	}

	@Test
	@DisplayName("recoverable data access 실패는 retry policy를 적용해 RETRYABLE로 분류한다")
	void classifiesRecoverableStorageFailureAsRetryable() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.QUESTION_RECOMMENDED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent(OutboxEventType.QUESTION_RECOMMENDED, 30L,
			"{\"assignmentId\":30}");
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(resolver.resolve(event)).thenReturn(FanOutInstruction.notification(
			NotificationType.QUESTION_RECOMMENDED, 42L, null, null, "question-recommended:30"));
		when(accounts.findById(42L)).thenReturn(Optional.of(account(42L)));
		when(notifications.saveIfAbsent(any(Notification.class)))
			.thenThrow(new RecoverableDataAccessException("recoverable connection"));
		when(outbox.fail(anyLong(), any(String.class), anyLong(), any(Instant.class), any())).thenReturn(true);

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.RETRYABLE);
	}

	@Test
	@DisplayName("실패 기록 자체가 예외여도 FAILURE_RECORDING_FAILED로 격리한다")
	void isolatesFailureRecordingFailure() {
		when(resolver.eventTypes()).thenReturn(Set.of(OutboxEventType.QUESTION_RECOMMENDED));
		NotificationFanOutWorker worker = worker();
		OutboxEvent event = claimedEvent(OutboxEventType.QUESTION_RECOMMENDED, 30L,
			"{\"assignmentId\":30}");
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(event));
		when(resolver.resolve(event)).thenThrow(new TransientDataAccessResourceException("worker failure"));
		when(outbox.fail(anyLong(), any(String.class), anyLong(), any(Instant.class), any()))
			.thenThrow(new TransientDataAccessResourceException("failure recording"));

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(NotificationFanOutWorker.Outcome.FAILURE_RECORDING_FAILED);
	}

	private NotificationFanOutWorker worker() {
		givenTransactionRunsInline();
		return new NotificationFanOutWorker(outbox, notifications, List.of(resolver),
			accounts, safety, transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void givenTransactionRunsInline() {
		lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
	}

	private static Account account(long id) {
		return Account.restore(id, AccountRole.USER, AccountStatus.ACTIVE, "KR", "KR-TEST",
			"ko-KR", "Asia/Seoul", "user-" + id, null);
	}

	private static Notification notification(long id, long recipientId, NotificationType type, String dedupKey) {
		return new Notification(id, recipientId, 1L, type, dedupKey, null, null,
			com.dnd.qello.notification.domain.NotificationStatus.UNREAD, NOW, null);
	}

	private static OutboxEvent claimedEvent(OutboxEventType type, long aggregateId, String payload) {
		OutboxEvent pending = OutboxEvent.pending(OutboxAggregateType.ANSWER, aggregateId, type,
			"event:" + aggregateId + ":" + type, payload, NOW);
		OutboxEvent stored = new OutboxEvent(1L, pending.aggregateType(), pending.aggregateId(), pending.eventType(),
			pending.dedupKey(), pending.payload(), pending.status(), pending.attemptCount(), pending.nextAttemptAt(),
			pending.createdAt(), pending.processedAt());
		return stored.claimed("notification-fanout-expansion",
			NOW, NOW.plusSeconds(30));
	}

	private NotificationFanOutWorker.BatchCommand command() {
		return new NotificationFanOutWorker.BatchCommand(10, "notification-fanout-expansion", NOW,
			NOW.plusSeconds(30), new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1)));
	}
}
