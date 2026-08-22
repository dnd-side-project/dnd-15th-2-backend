/**
 * Created at: 2026-08-14T17:52:07+09:00
 * Source scenario: TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-UNIT-001 through UNIT-010
 */
package com.dnd.qello.notification.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.repository.SafetyRepository;

class RecipientNotificationFanOutWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");
	private static final long POST_ID = 101L;
	private static final long SENDER_ID = 11L;
	private static final long RECIPIENT_ID = 22L;
	private static final long POST_RECIPIENT_ID = 301L;

	@Test
	@DisplayName("batch worker는 RECIPIENTS_CONFIRMED만 claim한다")
	void claimsOnlyRecipientsConfirmedEvents() {
		Context context = context();
		when(context.outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of());

		context.worker.processBatch(command());

		ArgumentCaptor<Set<OutboxEventType>> types = ArgumentCaptor.forClass(Set.class);
		verify(context.outbox).claimDue(types.capture(), eq(10), eq("notification-worker"), eq(NOW),
			eq(NOW.plusSeconds(30)));
		assertThat(types.getValue()).containsExactly(OutboxEventType.RECIPIENTS_CONFIRMED);
	}

	@Test
	@DisplayName("손상된 aggregate event는 payload를 사용하지 않고 permanent 실패로 분류한다")
	void rejectsMalformedAggregateBeforeNotificationWrite() {
		Context context = context();
		OutboxEvent malformed = claimedEvent(1L, OutboxAggregateType.ANSWER, POST_RECIPIENT_ID,
			OutboxEventType.RECIPIENTS_CONFIRMED, "{\"postRecipientId\":999,\"recipientId\":999}");
		givenClaimed(context, malformed);
		when(context.outbox.fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW),
			any(OutboxRetryDecision.class))).thenReturn(true);

		RecipientNotificationFanOutWorker.BatchResult result = context.worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(RecipientNotificationFanOutWorker.Outcome.DEAD);
		verifyNoInteractions(context.notifications, context.recipients, context.posts, context.accounts, context.safety);
	}

	@Test
	@DisplayName("lease identity가 없는 event는 source 갱신 없이 STALE_LEASE로 격리한다")
	void classifiesMissingLeaseIdentityAsStaleLease() {
		Context context = context();
		OutboxEvent pendingWithoutLease = storedEvent(1L, OutboxEvent.pending(
			OutboxAggregateType.POST_RECIPIENT, POST_RECIPIENT_ID,
			OutboxEventType.RECIPIENTS_CONFIRMED, "malformed-claim", "{}", NOW));
		givenClaimed(context, pendingWithoutLease);

		RecipientNotificationFanOutWorker.BatchResult result = context.worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(RecipientNotificationFanOutWorker.Outcome.STALE_LEASE);
		verify(context.outbox, never()).fail(anyLong(), any(String.class), anyLong(), any(Instant.class),
			any(OutboxRetryDecision.class));
		verify(context.outbox, never()).complete(anyLong(), any(String.class), anyLong(), any(Instant.class));
		verifyNoInteractions(context.notifications, context.recipients, context.posts, context.accounts, context.safety);
	}

	@Test
	@DisplayName("유효 lease identity의 잘못된 event status는 fenced permanent DEAD로 처리한다")
	void classifiesMalformedStatusWithLeaseAsPermanent() {
		Context context = context();
		OutboxEvent malformed = mock(OutboxEvent.class);
		when(malformed.id()).thenReturn(1L);
		when(malformed.aggregateType()).thenReturn(OutboxAggregateType.POST_RECIPIENT);
		when(malformed.eventType()).thenReturn(OutboxEventType.RECIPIENTS_CONFIRMED);
		when(malformed.status()).thenReturn(com.dnd.qello.notification.domain.OutboxStatus.PENDING);
		when(malformed.leaseOwner()).thenReturn("notification-worker");
		when(malformed.leaseGeneration()).thenReturn(1L);
		givenClaimed(context, malformed);
		when(context.outbox.fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW),
			any(OutboxRetryDecision.class))).thenReturn(true);

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.DEAD);
		ArgumentCaptor<OutboxRetryDecision> decision = ArgumentCaptor.forClass(OutboxRetryDecision.class);
		verify(context.outbox).fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW), decision.capture());
		assertThat(decision.getValue().dead()).isTrue();
		assertThat(decision.getValue().nextAttemptAt()).isEqualTo(NOW);
		verify(context.outbox, never()).complete(anyLong(), any(String.class), anyLong(), any(Instant.class));
	}

	@Test
	@DisplayName("locked PostRecipient ID가 aggregate ID와 다르면 payload를 보지 않고 permanent 처리한다")
	void rejectsMismatchedLockedAggregateIdentity() {
		Context context = context();
		givenClaimed(context, confirmedEvent(1L, POST_RECIPIENT_ID,
			"{\"postRecipientId\":" + POST_RECIPIENT_ID + "}"));
		when(context.recipients.findByIdForUpdate(POST_RECIPIENT_ID))
			.thenReturn(Optional.of(recipient(999L, POST_ID, RECIPIENT_ID, PostRecipientStatus.AVAILABLE)));
		when(context.outbox.fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW),
			any(OutboxRetryDecision.class))).thenReturn(true);

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.DEAD);
		verifyNoInteractions(context.notifications, context.posts, context.accounts, context.safety);
	}

	@Test
	@DisplayName("정상 event는 aggregate PostRecipient로 인앱 알림과 ACTIVE 기기 전달 작업을 구성한다")
	void createsNotificationAndPendingDeviceDeliveriesFromAggregate() {
		Context context = eligibleContext();
		OutboxEvent event = confirmedEvent(1L, POST_RECIPIENT_ID,
			"{\"postRecipientId\":999,\"recipientId\":999,\"postId\":999}");
		givenClaimed(context, event);
		when(context.notifications.findActiveDeviceIdsByUserId(RECIPIENT_ID)).thenReturn(List.of(701L, 702L));

		RecipientNotificationFanOutWorker.BatchResult result = context.worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
		verify(context.notifications).saveIfAbsent(notification.capture());
		assertThat(notification.getValue()).satisfies(saved -> {
			assertThat(saved.id()).isNull();
			assertThat(saved.recipientId()).isEqualTo(RECIPIENT_ID);
			assertThat(saved.outboxEventId()).isEqualTo(1L);
			assertThat(saved.notificationType()).isEqualTo(NotificationType.DIRECTION_POST_RECEIVED);
			assertThat(saved.dedupKey()).isEqualTo("direction-post-received:" + POST_RECIPIENT_ID);
			assertThat(saved.directionPostId()).isEqualTo(POST_ID);
			assertThat(saved.answerId()).isNull();
			assertThat(saved.status()).isEqualTo(NotificationStatus.UNREAD);
			assertThat(saved.createdAt()).isEqualTo(NOW);
			assertThat(saved.readAt()).isNull();
		});
		ArgumentCaptor<NotificationDelivery> deliveries = ArgumentCaptor.forClass(NotificationDelivery.class);
		verify(context.notifications, times(2)).saveDeliveryIfAbsent(deliveries.capture());
		assertThat(deliveries.getAllValues())
			.extracting(NotificationDelivery::pushDeviceId)
			.containsExactly(701L, 702L);
		assertThat(deliveries.getAllValues())
			.allSatisfy(delivery -> {
				assertThat(delivery.notificationId()).isEqualTo(RECIPIENT_ID + 500L);
				assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
				assertThat(delivery.nextAttemptAt()).isEqualTo(NOW);
				assertThat(delivery.createdAt()).isEqualTo(NOW);
			});
	}

	@Test
	@DisplayName("설정 행 없음과 활성 설정은 delivery까지 만들고, 비활성 설정은 알림함 행은 만들되 delivery는 억제한다(#176)")
	void appliesPreferenceDefaultAndSuppression() {
		Context enabled = eligibleContext();
		givenClaimed(enabled, confirmedEvent(1L, POST_RECIPIENT_ID, "{}"));
		assertThat(enabled.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		verify(enabled.notifications).saveIfAbsent(any(Notification.class));
		verify(enabled.notifications).findActiveDeviceIdsByUserId(RECIPIENT_ID);

		Context disabled = eligibleContext();
		givenClaimed(disabled, confirmedEvent(2L, POST_RECIPIENT_ID, "{}"));
			when(disabled.preferences.isPushEnabled(RECIPIENT_ID,
				NotificationType.DIRECTION_POST_RECEIVED)).thenReturn(false);
			assertThat(disabled.worker.processBatch(command()).outcomes())
				.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		// #176 결정 10: preference는 delivery만 막는다. 차단·계정·만료와 달리 알림함
		// 기록 자체는 preference와 무관하게 남아야 한다.
		verify(disabled.notifications).saveIfAbsent(any(Notification.class));
		verify(disabled.notifications, never()).findActiveDeviceIdsByUserId(RECIPIENT_ID);
		verify(disabled.notifications, never()).saveDeliveryIfAbsent(any(NotificationDelivery.class));
		verify(disabled.outbox).complete(2L, "notification-worker", 1L, NOW);
	}

	@ParameterizedTest(name = "{0} -> fanOut={1}")
	@MethodSource("recipientStatusCases")
	@DisplayName("수신 상태 판정표는 미처리 네 상태만 fan-out한다")
	void appliesRecipientStatusEligibilityTable(PostRecipientStatus status, boolean allowed) {
		Context context = eligibleContext(status);
		givenClaimed(context, confirmedEvent(1L, POST_RECIPIENT_ID, "{}"));

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);

		if (allowed) verify(context.notifications).saveIfAbsent(any(Notification.class));
		else verify(context.notifications, never()).saveIfAbsent(any(Notification.class));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("eligibilitySuppressions")
	@DisplayName("비활성 계정과 post, deadline 경계와 양방향 차단은 성공 억제한다")
	void suppressesLostEligibility(EligibilitySuppression suppression) {
		Context context = eligibleContext();
		givenClaimed(context, confirmedEvent(1L, POST_RECIPIENT_ID, "{}"));
		suppression.apply(context);

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		verify(context.notifications, never()).saveIfAbsent(any(Notification.class));
		verify(context.outbox).complete(1L, "notification-worker", 1L, NOW);
	}

	@Test
	@DisplayName("기존 logical Notification은 dedup 성공으로 사용하고 누락된 Delivery를 보충한다")
	void reconcilesRecognizedNotificationDedup() {
		Context context = eligibleContext();
		givenClaimed(context, confirmedEvent(1L, POST_RECIPIENT_ID, "{}"));
		when(context.notifications.saveIfAbsent(any(Notification.class))).thenReturn(notification(900L, 77L));
		when(context.notifications.findActiveDeviceIdsByUserId(RECIPIENT_ID)).thenReturn(List.of(701L));

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
		verify(context.notifications).saveDeliveryIfAbsent(delivery.capture());
		assertThat(delivery.getValue().notificationId()).isEqualTo(900L);
	}

	@Test
	@DisplayName("transient 저장 실패는 retry policy를 적용하고 해당 event만 RETRYABLE 처리한다")
	void classifiesTransientFailureAsRetryable() {
		Context context = eligibleContext();
		givenClaimed(context, confirmedEvent(1L, POST_RECIPIENT_ID, "{}"));
		when(context.notifications.saveIfAbsent(any(Notification.class)))
			.thenThrow(new TransientDataAccessResourceException("retry"));
		when(context.outbox.fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW),
			any(OutboxRetryDecision.class))).thenReturn(true);

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.RETRYABLE);
		ArgumentCaptor<OutboxRetryDecision> decision = ArgumentCaptor.forClass(OutboxRetryDecision.class);
		verify(context.outbox).fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW), decision.capture());
		assertThat(decision.getValue().dead()).isFalse();
		assertThat(decision.getValue().nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	@DisplayName("non-dedup 무결성 실패는 permanent DEAD로 처리한다")
	void classifiesIntegrityFailureAsPermanent() {
		Context context = eligibleContext();
		givenClaimed(context, confirmedEvent(1L, POST_RECIPIENT_ID, "{}"));
		when(context.notifications.saveIfAbsent(any(Notification.class)))
			.thenThrow(new DataIntegrityViolationException("constraint"));
		when(context.outbox.fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW),
			any(OutboxRetryDecision.class))).thenReturn(true);

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.DEAD);
	}

	@Test
	@DisplayName("complete fencing 실패는 과거 worker가 fail 상태를 덮어쓰지 않고 STALE_LEASE로 종료한다")
	void leavesStaleLeaseUntouched() {
		Context context = eligibleContext();
		givenClaimed(context, confirmedEvent(1L, POST_RECIPIENT_ID, "{}"));
		when(context.outbox.complete(1L, "notification-worker", 1L, NOW)).thenReturn(false);

		assertThat(context.worker.processBatch(command()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.STALE_LEASE);
		verify(context.outbox, never()).fail(anyLong(), any(String.class), anyLong(), any(Instant.class),
			any(OutboxRetryDecision.class));
	}

	@Test
	@DisplayName("고정 at이 없으면 claim과 각 event 처리 시각을 Clock에서 각각 읽는다")
	void readsClockForClaimAndEveryEvent() {
		SequenceClock clock = new SequenceClock(List.of(NOW, NOW.plusSeconds(1), NOW.plusSeconds(2)));
		Context context = context(clock);
		OutboxEvent first = confirmedEvent(1L, 301L, "{}");
		OutboxEvent second = confirmedEvent(2L, 302L, "{}");
		givenClaimed(context, first, second);
		givenEligible(context, 301L, 101L, 22L, PostRecipientStatus.AVAILABLE);
		givenEligible(context, 302L, 102L, 23L, PostRecipientStatus.AVAILABLE);
		when(context.preferences.isPushEnabled(anyLong(), eq(NotificationType.DIRECTION_POST_RECEIVED)))
			.thenReturn(false);
		when(context.outbox.complete(anyLong(), eq("notification-worker"), eq(1L), any(Instant.class)))
			.thenReturn(true);

		RecipientNotificationFanOutWorker.BatchCommand command = new RecipientNotificationFanOutWorker.BatchCommand(
			10, "notification-worker", null, NOW.plusSeconds(30), retryPolicy());
		assertThat(context.worker.processBatch(command).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED);

		verify(context.outbox).claimDue(any(), eq(10), eq("notification-worker"), eq(NOW), eq(NOW.plusSeconds(30)));
		ArgumentCaptor<Instant> processingTimes = ArgumentCaptor.forClass(Instant.class);
		verify(context.outbox, times(2)).complete(anyLong(), eq("notification-worker"), eq(1L),
			processingTimes.capture());
		assertThat(processingTimes.getAllValues()).containsExactly(NOW.plusSeconds(1), NOW.plusSeconds(2));
	}

	@Test
	@DisplayName("정상 retryable permanent event가 섞여도 claimed 순서와 event별 결과를 보존한다")
	void isolatesMixedBatchOutcomes() {
		Context context = context();
		OutboxEvent normal = confirmedEvent(1L, 301L, "{}");
		OutboxEvent transientEvent = confirmedEvent(2L, 302L, "{}");
		OutboxEvent permanent = claimedEvent(3L, OutboxAggregateType.ANSWER, 303L,
			OutboxEventType.RECIPIENTS_CONFIRMED, "{}");
		givenClaimed(context, normal, transientEvent, permanent);
		givenEligible(context, 301L, 101L, 22L, PostRecipientStatus.AVAILABLE);
		givenEligible(context, 302L, 102L, 23L, PostRecipientStatus.AVAILABLE);
		when(context.notifications.saveIfAbsent(any(Notification.class))).thenAnswer(invocation -> {
			Notification candidate = invocation.getArgument(0);
			if (candidate.recipientId() == 23L) throw new TransientDataAccessResourceException("retry");
			return withId(candidate, candidate.recipientId() + 500L);
		});
		when(context.outbox.complete(1L, "notification-worker", 1L, NOW)).thenReturn(true);
		when(context.outbox.fail(anyLong(), eq("notification-worker"), eq(1L), eq(NOW),
			any(OutboxRetryDecision.class))).thenReturn(true);

		assertThat(context.worker.processBatch(command()).outcomes()).containsExactly(
			RecipientNotificationFanOutWorker.Outcome.PROCESSED,
			RecipientNotificationFanOutWorker.Outcome.RETRYABLE,
			RecipientNotificationFanOutWorker.Outcome.DEAD);
	}

	@Test
	@DisplayName("실패 기록 예외가 발생해도 후속 claimed event를 계속 처리하고 별도 outcome을 반환한다")
	void continuesAfterFailureRecordingException() {
		Context context = context();
		OutboxEvent failedEvent = confirmedEvent(1L, 301L, "{}");
		OutboxEvent followingEvent = confirmedEvent(2L, 302L, "{}");
		givenClaimed(context, failedEvent, followingEvent);
		givenEligible(context, 301L, 101L, 22L, PostRecipientStatus.AVAILABLE);
		givenEligible(context, 302L, 102L, 23L, PostRecipientStatus.AVAILABLE);
		when(context.notifications.saveIfAbsent(any(Notification.class))).thenAnswer(invocation -> {
			Notification candidate = invocation.getArgument(0);
			if (candidate.outboxEventId() == 1L) {
				throw new TransientDataAccessResourceException("domain failure");
			}
			return withId(candidate, candidate.recipientId() + 500L);
		});
		when(context.outbox.fail(eq(1L), eq("notification-worker"), eq(1L), eq(NOW),
			any(OutboxRetryDecision.class))).thenThrow(new TransientDataAccessResourceException("failure recording"));

		assertThat(context.worker.processBatch(command()).outcomes()).containsExactly(
			RecipientNotificationFanOutWorker.Outcome.FAILURE_RECORDING_FAILED,
			RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		verify(context.outbox).complete(2L, "notification-worker", 1L, NOW);
	}

	@Test
	@DisplayName("잘못된 batch 입력은 Notification 오류 코드로 fail-fast하고 repository를 호출하지 않는다")
	void rejectsInvalidBatchInputAtFeatureBoundary() {
		Context context = context();
		assertNotificationError(() -> context.worker.processBatch(null), NotificationErrorCode.REQUIRED_VALUE_MISSING);
		assertNotificationError(() -> new RecipientNotificationFanOutWorker.BatchCommand(0, "worker", NOW,
			NOW.plusSeconds(1), retryPolicy()), NotificationErrorCode.INVALID_VALUE_RANGE);
		assertNotificationError(() -> new RecipientNotificationFanOutWorker.BatchCommand(1, " ", NOW,
			NOW.plusSeconds(1), retryPolicy()), NotificationErrorCode.INVALID_TEXT);
		assertNotificationError(() -> new RecipientNotificationFanOutWorker.BatchCommand(1, "worker", NOW,
			NOW, retryPolicy()), NotificationErrorCode.INVALID_VALUE_RANGE);
		assertNotificationError(() -> new RecipientNotificationFanOutWorker.BatchCommand(1, "worker", NOW,
			NOW.plusSeconds(1), null), NotificationErrorCode.REQUIRED_VALUE_MISSING);
		verifyNoInteractions(context.outbox);
	}

	private static Stream<Arguments> recipientStatusCases() {
		return Stream.of(
			Arguments.of(PostRecipientStatus.AVAILABLE, true),
			Arguments.of(PostRecipientStatus.DISCOVERED, true),
			Arguments.of(PostRecipientStatus.OPENED, true),
			Arguments.of(PostRecipientStatus.SKIP_PENDING, true),
			Arguments.of(PostRecipientStatus.ANSWERED, false),
			Arguments.of(PostRecipientStatus.SKIPPED, false),
			Arguments.of(PostRecipientStatus.EXPIRED, false),
			Arguments.of(PostRecipientStatus.BLOCKED, false));
	}

	private static Stream<EligibilitySuppression> eligibilitySuppressions() {
		return Stream.of(
			context -> when(context.accounts.findById(SENDER_ID)).thenReturn(Optional.of(account(SENDER_ID, AccountStatus.BLOCKED))),
			context -> when(context.accounts.findById(RECIPIENT_ID)).thenReturn(Optional.of(account(RECIPIENT_ID, AccountStatus.DELETED))),
			context -> when(context.posts.findById(POST_ID)).thenReturn(Optional.of(post(POST_ID, SENDER_ID,
				DirectionPostStatus.EXPIRED, NOW.plusSeconds(3600)))),
			context -> when(context.posts.findById(POST_ID)).thenReturn(Optional.of(deletedPost(POST_ID, SENDER_ID))),
			context -> when(context.posts.findById(POST_ID)).thenReturn(Optional.of(post(POST_ID, SENDER_ID,
				DirectionPostStatus.ACTIVE, NOW))),
			context -> when(context.safety.findBlock(RECIPIENT_ID, SENDER_ID))
				.thenReturn(Optional.of(UserBlock.create(RECIPIENT_ID, SENDER_ID, NOW.minusSeconds(60)))),
			context -> when(context.safety.findBlock(SENDER_ID, RECIPIENT_ID))
				.thenReturn(Optional.of(UserBlock.create(SENDER_ID, RECIPIENT_ID, NOW.minusSeconds(60)))));
	}

	private Context eligibleContext() {
		return eligibleContext(PostRecipientStatus.AVAILABLE);
	}

	private Context eligibleContext(PostRecipientStatus status) {
		Context context = context();
		givenEligible(context, POST_RECIPIENT_ID, POST_ID, RECIPIENT_ID, status);
		return context;
	}

	private void givenEligible(Context context, long postRecipientId, long postId, long recipientId,
		PostRecipientStatus status) {
		when(context.recipients.findByIdForUpdate(postRecipientId))
			.thenReturn(Optional.of(recipient(postRecipientId, postId, recipientId, status)));
		when(context.posts.findById(postId)).thenReturn(Optional.of(post(postId, SENDER_ID,
			DirectionPostStatus.ACTIVE, NOW.plusSeconds(3600))));
		when(context.accounts.findById(SENDER_ID)).thenReturn(Optional.of(account(SENDER_ID, AccountStatus.ACTIVE)));
		when(context.accounts.findById(recipientId)).thenReturn(Optional.of(account(recipientId, AccountStatus.ACTIVE)));
		when(context.safety.findBlock(recipientId, SENDER_ID)).thenReturn(Optional.empty());
		when(context.safety.findBlock(SENDER_ID, recipientId)).thenReturn(Optional.empty());
		when(context.preferences.isPushEnabled(recipientId, NotificationType.DIRECTION_POST_RECEIVED))
			.thenReturn(true);
		when(context.notifications.findActiveDeviceIdsByUserId(recipientId)).thenReturn(List.of());
		when(context.notifications.saveIfAbsent(any(Notification.class))).thenAnswer(invocation -> {
			Notification candidate = invocation.getArgument(0);
			return withId(candidate, candidate.recipientId() + 500L);
		});
		when(context.notifications.saveDeliveryIfAbsent(any(NotificationDelivery.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(context.outbox.complete(anyLong(), eq("notification-worker"), eq(1L), any(Instant.class)))
			.thenReturn(true);
	}

	private Context context() {
		return context(Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Context context(Clock clock) {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		NotificationRepository notifications = mock(NotificationRepository.class);
		NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
		PostRecipientRepository recipients = mock(PostRecipientRepository.class);
		DirectionPostRepository posts = mock(DirectionPostRepository.class);
		AccountRepository accounts = mock(AccountRepository.class);
		SafetyRepository safety = mock(SafetyRepository.class);
		PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
		when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		RecipientNotificationFanOutWorker worker = new RecipientNotificationFanOutWorker(outbox, notifications,
			preferences, recipients, posts, accounts, safety, transactions, clock);
		return new Context(worker, outbox, notifications, preferences, recipients, posts, accounts, safety);
	}

	private void givenClaimed(Context context, OutboxEvent... events) {
		when(context.outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(events));
	}

	private RecipientNotificationFanOutWorker.BatchCommand command() {
		return new RecipientNotificationFanOutWorker.BatchCommand(10, "notification-worker", NOW,
			NOW.plusSeconds(30), retryPolicy());
	}

	private OutboxRetryPolicy retryPolicy() {
		return new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1));
	}

	private static OutboxEvent confirmedEvent(long eventId, long postRecipientId, String payload) {
		return claimedEvent(eventId, OutboxAggregateType.POST_RECIPIENT, postRecipientId,
			OutboxEventType.RECIPIENTS_CONFIRMED, payload);
	}

	private static OutboxEvent claimedEvent(long eventId, OutboxAggregateType aggregateType, long aggregateId,
		OutboxEventType eventType, String payload) {
		OutboxEvent pending = OutboxEvent.pending(aggregateType, aggregateId, eventType,
			"notification-fanout-event-" + eventId, payload, NOW);
		return storedEvent(eventId, pending).claimed("notification-worker", NOW, NOW.plusSeconds(30));
	}

	private static OutboxEvent storedEvent(long eventId, OutboxEvent pending) {
		return new OutboxEvent(eventId, pending.aggregateType(), pending.aggregateId(), pending.eventType(),
			pending.dedupKey(), pending.payload(), pending.status(), pending.attemptCount(), pending.nextAttemptAt(),
			pending.createdAt(), pending.processedAt(), pending.matchRound(), pending.leaseOwner(),
			pending.leaseExpiresAt(), pending.leaseGeneration());
	}

	private static PostRecipient recipient(long id, long postId, long recipientId, PostRecipientStatus status) {
		Instant matched = NOW.minusSeconds(300);
		Instant discovered = switch (status) {
			case DISCOVERED, OPENED, ANSWERED -> NOW.minusSeconds(240);
			default -> null;
		};
		Instant opened = switch (status) {
			case OPENED, ANSWERED -> NOW.minusSeconds(180);
			default -> null;
		};
		Instant skipRequested = switch (status) {
			case SKIP_PENDING, SKIPPED -> NOW.minusSeconds(120);
			default -> null;
		};
		Instant terminalAt = NOW.minusSeconds(60);
		return PostRecipient.restore(id, postId, recipientId, status, "NEAR", BigDecimal.valueOf(10),
			"TEST-REGION", matched, discovered, opened, skipRequested,
			status == PostRecipientStatus.SKIPPED ? terminalAt : null,
			isTerminal(status) ? terminalAt : null,
			status == PostRecipientStatus.EXPIRED ? terminalAt : null,
			status == PostRecipientStatus.BLOCKED ? terminalAt : null,
			BigDecimal.valueOf(190), 100, null);
	}

	private static boolean isTerminal(PostRecipientStatus status) {
		return status == PostRecipientStatus.ANSWERED || status == PostRecipientStatus.SKIPPED
			|| status == PostRecipientStatus.EXPIRED || status == PostRecipientStatus.BLOCKED;
	}

	private static DirectionPost post(long id, long senderId, DirectionPostStatus status, Instant expiresAt) {
		return DirectionPost.restore(id, senderId, 1L, status, "post-key-" + id, "body", "TEST-REGION",
			DirectionPostModerationStatus.PASSED, NOW.minusSeconds(3600), NOW.minusSeconds(1800), expiresAt,
			null, null);
	}

	private static DirectionPost deletedPost(long id, long senderId) {
		return DirectionPost.restore(id, senderId, 1L, DirectionPostStatus.DELETED, "post-key-" + id, "body",
			"TEST-REGION", DirectionPostModerationStatus.PASSED, NOW.minusSeconds(3600), NOW.minusSeconds(1800),
			NOW.plusSeconds(3600), null, NOW.minusSeconds(60));
	}

	private static Account account(long id, AccountStatus status) {
		return Account.restore(id, AccountRole.USER, status, "KR", "TEST-REGION", "ko-KR", "Asia/Seoul",
			"user-" + id, status == AccountStatus.DELETED ? NOW.minusSeconds(60) : null);
	}

	private static Notification notification(long id, long sourceEventId) {
		return new Notification(id, RECIPIENT_ID, sourceEventId, NotificationType.DIRECTION_POST_RECEIVED,
			"direction-post-received:" + POST_RECIPIENT_ID, POST_ID, null, null, NotificationStatus.UNREAD, NOW, null);
	}

	private static Notification withId(Notification notification, long id) {
		return new Notification(id, notification.recipientId(), notification.outboxEventId(),
			notification.notificationType(), notification.dedupKey(), notification.directionPostId(),
			notification.answerId(), notification.reportId(), notification.status(), notification.createdAt(),
			notification.readAt());
	}

	private static void assertNotificationError(Runnable action, NotificationErrorCode errorCode) {
		assertThatThrownBy(action::run)
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", errorCode);
	}

	@FunctionalInterface
	private interface EligibilitySuppression {
		void apply(Context context);
	}

	private record Context(RecipientNotificationFanOutWorker worker, OutboxEventRepository outbox,
		NotificationRepository notifications, NotificationPreferenceRepository preferences,
		PostRecipientRepository recipients,
		DirectionPostRepository posts, AccountRepository accounts, SafetyRepository safety) {
	}

	private static final class SequenceClock extends Clock {
		private final List<Instant> instants;
		private int index;

		private SequenceClock(List<Instant> instants) {
			this.instants = List.copyOf(instants);
		}

		@Override
		public Instant instant() {
			return instants.get(index++);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
