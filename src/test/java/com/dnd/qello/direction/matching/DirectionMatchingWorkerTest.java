/**
 * Created at: 2026-08-13T17:35:00+09:00
 * Source scenario: TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-004 through UNIT-006
 */
package com.dnd.qello.direction.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.direction.config.DirectionReceiveProperties;
import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.feed.config.DistanceBandPolicy;
import com.dnd.qello.feed.config.FeedDistanceProperties;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import org.springframework.transaction.TransactionStatus;

class DirectionMatchingWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-13T08:30:00Z");

	@Test
	@DisplayName("batch worker는 RECIPIENT_MATCH_REQUESTED만 claim한다")
	void claimsOnlyMatchingEvents() {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of());
		DirectionMatchingWorker worker = worker(outbox);

		worker.processBatch(command());

		ArgumentCaptor<Set<OutboxEventType>> types = ArgumentCaptor.forClass(Set.class);
		verify(outbox).claimDue(types.capture(), eq(10), eq("matching-worker"), eq(NOW), eq(NOW.plusSeconds(30)));
		org.assertj.core.api.Assertions.assertThat(types.getValue())
			.containsExactly(OutboxEventType.RECIPIENT_MATCH_REQUESTED);
	}

	@Test
	@DisplayName("batch command는 lease와 retry policy가 없으면 거절한다")
	void rejectsInvalidBatchCommand() {
		assertThatThrownBy(() -> new DirectionMatchingWorker.BatchCommand(0, "worker", NOW, NOW.plusSeconds(30), retryPolicy()))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> new DirectionMatchingWorker.BatchCommand(10, "worker", NOW, NOW, retryPolicy()))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> new DirectionMatchingWorker.BatchCommand(10, "worker", NOW, NOW.plusSeconds(30), null))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	@DisplayName("moderation not-ready는 retryable 실패로 분류하고 domain write를 수행하지 않는다")
	void classifiesModerationNotReadyAsRetryable() {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		DirectionPostRepository posts = mock(DirectionPostRepository.class);
		PlatformTransactionManager transactionManager = transactionManager();
		OutboxEvent claimed = matchingEvent().claimed("matching-worker", NOW, NOW.plusSeconds(30));
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(claimed));
		when(posts.findByIdForUpdate(1L)).thenReturn(Optional.of(post(DirectionPostModerationStatus.PENDING)));
		when(outbox.fail(eq(1L), eq("matching-worker"), eq(1L), eq(NOW), any(OutboxRetryDecision.class))).thenReturn(true);

		DirectionMatchingWorker.BatchResult result = worker(outbox, posts, transactionManager).processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.RETRYABLE);
		verify(outbox).fail(eq(1L), eq("matching-worker"), eq(1L), eq(NOW), any(OutboxRetryDecision.class));
	}

	@Test
	@DisplayName("손상된 matching event는 permanent 실패로 분류하고 DEAD 처리한다")
	void classifiesInvalidEventAsPermanent() {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		PlatformTransactionManager transactionManager = transactionManager();
		OutboxEvent claimed = withId(OutboxEvent.pending(com.dnd.qello.notification.domain.OutboxAggregateType.ANSWER, 1L,
			OutboxEventType.ANSWER_PUBLISHED, "invalid-event", "{\"answerId\":1}", NOW)
		).claimed("matching-worker", NOW, NOW.plusSeconds(30));
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(claimed));
		when(outbox.fail(eq(1L), eq("matching-worker"), eq(1L), eq(NOW), any(OutboxRetryDecision.class))).thenReturn(true);

		DirectionMatchingWorker.BatchResult result = worker(outbox, mock(DirectionPostRepository.class), transactionManager).processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.DEAD);
		verify(outbox).fail(eq(1L), eq("matching-worker"), eq(1L), eq(NOW), any(OutboxRetryDecision.class));
	}

	@Test
	@DisplayName("stale lease complete 실패는 과거 worker의 fail 갱신 없이 STALE_LEASE로 종료한다")
	void leavesStaleLeaseForReclaim() {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		DirectionPostRepository posts = mock(DirectionPostRepository.class);
		PlatformTransactionManager transactionManager = transactionManager();
		OutboxEvent claimed = matchingEvent().claimed("matching-worker", NOW, NOW.plusSeconds(30));
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(claimed));
		when(posts.findByIdForUpdate(1L)).thenReturn(Optional.of(post(DirectionPostModerationStatus.REJECTED)));
		when(outbox.complete(1L, "matching-worker", 1L, NOW)).thenReturn(false);

		DirectionMatchingWorker.BatchResult result = worker(outbox, posts, transactionManager).processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.STALE_LEASE);
		verify(outbox).complete(1L, "matching-worker", 1L, NOW);
		org.mockito.Mockito.verify(outbox, org.mockito.Mockito.never()).fail(any(Long.class), any(String.class), any(Long.class), any(Instant.class), any(), org.mockito.ArgumentMatchers.anyBoolean());
	}

	private DirectionMatchingWorker worker(OutboxEventRepository outbox) {
		return worker(outbox, mock(DirectionPostRepository.class), mock(PlatformTransactionManager.class));
	}

	private DirectionMatchingWorker worker(OutboxEventRepository outbox, DirectionPostRepository posts,
		PlatformTransactionManager transactionManager) {
		return new DirectionMatchingWorker(outbox, posts, mock(PostAudienceRepository.class),
			mock(ActiveUserPresenceRepository.class), mock(PostRecipientRepository.class),
			mock(RecipientReceiveStateRepository.class), new DirectionRecipientSelectionProperties(10),
			new DirectionReceiveProperties(5), new DistanceBandPolicy(new FeedDistanceProperties(10_000)),
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private PlatformTransactionManager transactionManager() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return transactionManager;
	}

	private OutboxEvent matchingEvent() {
		return withId(OutboxEvent.matchingPending(1L, 1, "matching-event", "{\"postId\":1}", NOW));
	}

	private OutboxEvent withId(OutboxEvent event) {
		return new OutboxEvent(1L, event.aggregateType(), event.aggregateId(), event.eventType(), event.dedupKey(), event.payload(),
			event.status(), event.attemptCount(), event.nextAttemptAt(), event.createdAt(), event.processedAt(), event.matchRound(),
			event.leaseOwner(), event.leaseExpiresAt(), event.leaseGeneration());
	}

	private DirectionPost post(DirectionPostModerationStatus moderationStatus) {
		return DirectionPost.restore(1L, 11L, 101L, DirectionPostStatus.MATCHING, "matching-key", "matching body",
			"TEST-REGION", moderationStatus, NOW.minusSeconds(60), null, NOW.plusSeconds(3600), null, null);
	}

	private DirectionMatchingWorker.BatchCommand command() {
		return new DirectionMatchingWorker.BatchCommand(10, "matching-worker", NOW, NOW.plusSeconds(30), retryPolicy());
	}

	private OutboxRetryPolicy retryPolicy() {
		return new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1));
	}
}
