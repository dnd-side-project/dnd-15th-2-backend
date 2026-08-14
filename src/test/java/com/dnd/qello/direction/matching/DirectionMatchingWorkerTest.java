/**
 * Created at: 2026-08-13T17:35:00+09:00
 * Source scenario: TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-004 through UNIT-006
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-011
 */
package com.dnd.qello.direction.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.direction.config.DirectionReceiveProperties;
import com.dnd.qello.direction.config.DirectionPostProperties;
import com.dnd.qello.direction.config.DirectionRecipientSelectionProperties;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionPostModerationStatus;
import com.dnd.qello.direction.domain.DirectionPostStatus;
import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
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
	@DisplayName("GLOBAL worker는 작성자의 coarse region을 후보 필터로 전달하지 않는다")
	void doesNotFilterCandidatesByPostRegionInGlobalScope() {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		DirectionPostRepository posts = mock(DirectionPostRepository.class);
		PostAudienceRepository audiences = mock(PostAudienceRepository.class);
		ActiveUserPresenceRepository presence = mock(ActiveUserPresenceRepository.class);
		OutboxEvent claimed = matchingEvent().claimed("matching-worker", NOW, NOW.plusSeconds(30));
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(claimed));
		when(posts.findByIdForUpdate(1L)).thenReturn(Optional.of(post(DirectionPostModerationStatus.PASSED)));
		when(audiences.findByPostId(1L)).thenReturn(Optional.of(PostAudience.create(1L, 101L, "N",
			BigDecimal.ZERO, BigDecimal.valueOf(90), 0, 20_100_000,
			BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), "TEST-CELL", NOW)));
		when(presence.findCandidates(eq(11L), eq(37.5), eq(127.0), eq(0L), eq(20_100_000L),
			eq(315.0), eq(45.0), eq(NOW), org.mockito.ArgumentMatchers.isNull())).thenReturn(List.of());
		when(outbox.complete(1L, "matching-worker", 1L, NOW)).thenReturn(true);

		DirectionMatchingWorker worker = worker(outbox, posts, audiences, presence, transactionManager());

		assertThat(worker.processBatch(command()).outcomes())
			.containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		verify(presence).findCandidates(eq(11L), eq(37.5), eq(127.0), eq(0L), eq(20_100_000L),
			eq(315.0), eq(45.0), eq(NOW), org.mockito.ArgumentMatchers.isNull());
	}

	@Test
	@DisplayName("batch event는 claim 시각이 아니라 각 이벤트 처리 직전의 시각으로 만료를 판정한다")
	void readsProcessingTimeForEachClaimedEvent() {
		OutboxEventRepository outbox = mock(OutboxEventRepository.class);
		DirectionPostRepository posts = mock(DirectionPostRepository.class);
		PlatformTransactionManager transactionManager = transactionManager();
		OutboxEvent first = matchingEvent(1L).claimed("matching-worker", NOW, NOW.plusSeconds(30));
		OutboxEvent second = matchingEvent(2L).claimed("matching-worker", NOW, NOW.plusSeconds(30));
		when(outbox.claimDue(any(), any(Integer.class), any(String.class), any(Instant.class), any(Instant.class)))
			.thenReturn(List.of(first, second));
		when(posts.findByIdForUpdate(1L)).thenReturn(Optional.of(post(1L, DirectionPostStatus.MATCHING,
			DirectionPostModerationStatus.REJECTED, NOW.plusSeconds(3600))));
		when(posts.findByIdForUpdate(2L)).thenReturn(Optional.of(post(2L, DirectionPostStatus.MATCHING,
			DirectionPostModerationStatus.REJECTED, NOW.plusSeconds(2))));
		when(outbox.complete(anyLong(), eq("matching-worker"), eq(1L), any(Instant.class))).thenReturn(true);

		DirectionMatchingWorker worker = worker(outbox, posts, transactionManager,
			new SequenceClock(List.of(NOW, NOW.plusSeconds(1), NOW.plusSeconds(3))));
		DirectionMatchingWorker.BatchResult result = worker.processBatch(
			new DirectionMatchingWorker.BatchCommand(10, "matching-worker", null, NOW.plusSeconds(30), retryPolicy()));

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED,
			DirectionMatchingWorker.Outcome.PROCESSED);
		ArgumentCaptor<Instant> processingTimes = ArgumentCaptor.forClass(Instant.class);
		verify(outbox, org.mockito.Mockito.times(2)).complete(anyLong(), eq("matching-worker"), eq(1L),
			processingTimes.capture());
		assertThat(processingTimes.getAllValues()).containsExactly(NOW.plusSeconds(1), NOW.plusSeconds(3));
		ArgumentCaptor<DirectionPost> savedPosts = ArgumentCaptor.forClass(DirectionPost.class);
		verify(posts).save(savedPosts.capture());
		assertThat(savedPosts.getValue().getId()).isEqualTo(2L);
		assertThat(savedPosts.getValue().getStatus()).isEqualTo(DirectionPostStatus.EXPIRED);
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
	@DisplayName("방향 매칭 경계의 잘못된 입력은 DirectionException과 방향 오류 코드로 분류한다")
	void classifiesDirectionBoundaryValidation() {
		assertThatThrownBy(() -> new DirectionMatchingWorker.BatchResult(-1, List.of()))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new DirectionMatchingWorker.BatchResult(0, null))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> new RecipientReceiveStateRepository.LockCandidate(0, BigDecimal.ONE))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_ID);
		assertThatThrownBy(() -> new RecipientReceiveStateRepository.LockCandidate(1, null))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.REQUIRED_VALUE_MISSING);
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
		return worker(outbox, posts, mock(PostAudienceRepository.class), mock(ActiveUserPresenceRepository.class),
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private DirectionMatchingWorker worker(OutboxEventRepository outbox, DirectionPostRepository posts,
		PlatformTransactionManager transactionManager, Clock clock) {
		return worker(outbox, posts, mock(PostAudienceRepository.class), mock(ActiveUserPresenceRepository.class),
			transactionManager, clock);
	}

	private DirectionMatchingWorker worker(OutboxEventRepository outbox, DirectionPostRepository posts,
		PostAudienceRepository audiences, ActiveUserPresenceRepository presence,
		PlatformTransactionManager transactionManager) {
		return worker(outbox, posts, audiences, presence, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private DirectionMatchingWorker worker(OutboxEventRepository outbox, DirectionPostRepository posts,
		PostAudienceRepository audiences, ActiveUserPresenceRepository presence,
		PlatformTransactionManager transactionManager, Clock clock) {
		return new DirectionMatchingWorker(outbox, posts, audiences,
			presence, mock(PostRecipientRepository.class),
			mock(RecipientReceiveStateRepository.class), new DirectionRecipientSelectionProperties(10),
			new DirectionPostProperties(DirectionPostProperties.DeliveryScope.GLOBAL, 0, 20_100_000,
				Duration.ofHours(12), 300, 1),
			new DirectionReceiveProperties(5), new DistanceBandPolicy(new FeedDistanceProperties(10_000)),
			transactionManager, clock);
	}

	private PlatformTransactionManager transactionManager() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return transactionManager;
	}

	private OutboxEvent matchingEvent() {
		return matchingEvent(1L);
	}

	private OutboxEvent matchingEvent(long postId) {
		return withId(postId, OutboxEvent.matchingPending(postId, 1, "matching-event-" + postId,
			"{\"postId\":" + postId + "}", NOW));
	}

	private OutboxEvent withId(OutboxEvent event) {
		return withId(1L, event);
	}

	private OutboxEvent withId(long id, OutboxEvent event) {
		return new OutboxEvent(id, event.aggregateType(), event.aggregateId(), event.eventType(), event.dedupKey(), event.payload(),
			event.status(), event.attemptCount(), event.nextAttemptAt(), event.createdAt(), event.processedAt(), event.matchRound(),
			event.leaseOwner(), event.leaseExpiresAt(), event.leaseGeneration());
	}

	private DirectionPost post(DirectionPostModerationStatus moderationStatus) {
		return post(1L, moderationStatus);
	}

	private DirectionPost post(long postId, DirectionPostModerationStatus moderationStatus) {
		return post(postId, DirectionPostStatus.MATCHING, moderationStatus, NOW.plusSeconds(3600));
	}

	private DirectionPost post(long postId, DirectionPostStatus status,
		DirectionPostModerationStatus moderationStatus, Instant expiresAt) {
		return DirectionPost.restore(postId, 11L, 101L, status, "matching-key-" + postId, "matching body",
			"TEST-REGION", moderationStatus, NOW.minusSeconds(60), null, expiresAt, null, null);
	}

	private DirectionMatchingWorker.BatchCommand command() {
		return new DirectionMatchingWorker.BatchCommand(10, "matching-worker", NOW, NOW.plusSeconds(30), retryPolicy());
	}

	private OutboxRetryPolicy retryPolicy() {
		return new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1));
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
