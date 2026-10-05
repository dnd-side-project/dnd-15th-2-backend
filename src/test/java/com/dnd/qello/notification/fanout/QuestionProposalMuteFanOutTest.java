/**
 * Created at: 2026-10-05T18:10:06+09:00
 * Source scenario: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-012, UNIT-013
 */
package com.dnd.qello.notification.fanout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.dnd.qello.safety.repository.SafetyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionProposalMuteFanOutTest {

	private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
	private static final long PROPOSER_ID = 42L;
	private static final long PROPOSAL_ID = 12L;
	private static final String DEDUP_KEY = "question-proposal-reviewed:" + PROPOSAL_ID;

	@Mock
	private OutboxEventRepository outbox;
	@Mock
	private NotificationRepository notifications;
	@Mock
	private NotificationPreferenceRepository preferences;
	@Mock
	private AccountRepository accounts;
	@Mock
	private SafetyRepository safety;
	@Mock
	private PlatformTransactionManager transactionManager;
	@Mock
	private QuestionProposalRepository proposals;

	private NotificationFanOutWorker worker;

	@BeforeEach
	void setUp() {
		lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		worker = new NotificationFanOutWorker(outbox, notifications, preferences,
				List.of(new QuestionProposalReviewedNotificationResolver(proposals, new ObjectMapper())),
				accounts, safety, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
		OutboxEvent event = claimedReviewedEvent();
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
				.thenReturn(List.of(event));
		when(accounts.findById(PROPOSER_ID)).thenReturn(Optional.of(Account.restore(PROPOSER_ID, AccountRole.USER,
				AccountStatus.ACTIVE, "KR", "KR-TEST", "ko-KR", "Asia/Seoul", "proposer", null)));
		when(notifications.saveIfAbsent(any(Notification.class))).thenReturn(new Notification(900L, PROPOSER_ID,
				1L, NotificationType.QUESTION_PROPOSAL_REVIEWED, DEDUP_KEY, null, null, null,
				NotificationStatus.UNREAD, NOW, null));
		when(outbox.complete(anyLong(), any(String.class), anyLong(), any(Instant.class))).thenReturn(true);
	}

	@Test
	@DisplayName("알림을 끈 제안의 검토 결과는 notification만 남기고 push delivery를 만들지 않는다")
	void mutedProposalKeepsInboxWithoutDelivery() {
		when(proposals.findById(PROPOSAL_ID)).thenReturn(Optional.of(approvedProposal(true)));

		var result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		verify(notifications).saveIfAbsent(any(Notification.class));
		verify(notifications, never()).findActiveDeviceIdsByUserId(anyLong());
		verify(notifications, never()).saveDeliveryIfAbsent(any(NotificationDelivery.class));
		verify(preferences, never()).isPushEnabled(anyLong(), any());
	}

	@Test
	@DisplayName("알림을 켠 제안의 검토 결과는 기존처럼 활성 기기 수만큼 delivery를 만든다")
	void unmutedProposalCreatesDeliveries() {
		when(proposals.findById(PROPOSAL_ID)).thenReturn(Optional.of(approvedProposal(false)));
		when(preferences.isPushEnabled(PROPOSER_ID, NotificationType.QUESTION_PROPOSAL_REVIEWED)).thenReturn(true);
		when(notifications.findActiveDeviceIdsByUserId(PROPOSER_ID)).thenReturn(List.of(1001L, 1002L));

		var result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		verify(notifications).saveIfAbsent(any(Notification.class));
		verify(notifications, times(2)).saveDeliveryIfAbsent(any(NotificationDelivery.class));
	}

	private static QuestionProposal approvedProposal(boolean muted) {
		return QuestionProposal.restore(PROPOSAL_ID, PROPOSER_ID, QuestionProposalStatus.APPROVED, "제안 문구",
				null, NOW, NOW, NOW, null, muted);
	}

	private static OutboxEvent claimedReviewedEvent() {
		OutboxEvent pending = OutboxEvent.pending(OutboxAggregateType.QUESTION_PROPOSAL, PROPOSAL_ID,
				OutboxEventType.QUESTION_PROPOSAL_REVIEWED, DEDUP_KEY,
				"{\"proposalId\":12,\"proposerId\":42,\"decision\":\"APPROVED\"}", NOW);
		OutboxEvent stored = new OutboxEvent(1L, pending.aggregateType(), pending.aggregateId(), pending.eventType(),
				pending.dedupKey(), pending.payload(), pending.status(), pending.attemptCount(),
				pending.nextAttemptAt(),
				pending.createdAt(), pending.processedAt());
		return stored.claimed("notification-fanout-expansion", NOW, NOW.plusSeconds(30));
	}

	private static NotificationFanOutWorker.BatchCommand command() {
		return new NotificationFanOutWorker.BatchCommand(10, "notification-fanout-expansion", NOW,
				NOW.plusSeconds(30), new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1)));
	}
}
