/**
 * Created at: 2026-08-14T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-107-ANSWER-MODERATION-JOB-UNIT-014 through UNIT-017
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class AnswerModerationDeadlineWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-14T00:10:00Z");
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 42L);

	private final FilterJobRepository filterJobRepository = mock(FilterJobRepository.class);
	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);

	@Test
	@DisplayName("deadline이 지난 미해결 job에 아직 신호가 없으면 MODERATION_DEADLINE_ELAPSED를 발행한다")
	void emitsDeadlineElapsedForDueUnresolvedJob() {
		FilterJob due = dueJob();
		when(filterJobRepository.findDeadlineElapsedCandidates(NOW, 50)).thenReturn(List.of(due));
		when(outboxEventRepository.findByDedupKey("filter-job:10:DEADLINE_ELAPSED")).thenReturn(Optional.empty());
		AnswerModerationDeadlineWorker worker = worker();

		AnswerModerationDeadlineWorker.BatchResult result = worker.processBatch(50, NOW);

		assertThat(result.outcomes()).containsExactly(AnswerModerationDeadlineWorker.Outcome.SIGNALED);
		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(OutboxEventType.MODERATION_DEADLINE_ELAPSED);
		assertThat(captor.getValue().dedupKey()).isEqualTo("filter-job:10:DEADLINE_ELAPSED");
	}

	@Test
	@DisplayName("같은 job에 이미 신호가 발행됐으면 다시 보내지 않는다(재스캔 멱등성)")
	void skipsAlreadySignaledJob() {
		FilterJob due = dueJob();
		when(filterJobRepository.findDeadlineElapsedCandidates(NOW, 50)).thenReturn(List.of(due));
		when(outboxEventRepository.findByDedupKey("filter-job:10:DEADLINE_ELAPSED"))
			.thenReturn(Optional.of(mock(OutboxEvent.class)));
		AnswerModerationDeadlineWorker worker = worker();

		AnswerModerationDeadlineWorker.BatchResult result = worker.processBatch(50, NOW);

		assertThat(result.outcomes()).containsExactly(AnswerModerationDeadlineWorker.Outcome.ALREADY_SIGNALED);
		verify(outboxEventRepository, never()).save(any());
	}

	@Test
	@DisplayName("확인과 삽입 사이 경쟁으로 unique 제약을 위반해도 예외를 전파하지 않고 이미 신호됨으로 처리한다")
	void absorbsUniqueConstraintRaceAsAlreadySignaled() {
		FilterJob due = dueJob();
		when(filterJobRepository.findDeadlineElapsedCandidates(NOW, 50)).thenReturn(List.of(due));
		when(outboxEventRepository.findByDedupKey("filter-job:10:DEADLINE_ELAPSED")).thenReturn(Optional.empty());
		when(outboxEventRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
		AnswerModerationDeadlineWorker worker = worker();

		AnswerModerationDeadlineWorker.BatchResult result = worker.processBatch(50, NOW);

		assertThat(result.outcomes()).containsExactly(AnswerModerationDeadlineWorker.Outcome.ALREADY_SIGNALED);
	}

	@Test
	@DisplayName("limit이 양수가 아니면 거부한다")
	void rejectsNonPositiveLimit() {
		AnswerModerationDeadlineWorker worker = worker();

		assertThatThrownBy(() -> worker.processBatch(0, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}

	private AnswerModerationDeadlineWorker worker() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return new AnswerModerationDeadlineWorker(filterJobRepository, outboxEventRepository, new ObjectMapper(),
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static FilterJob dueJob() {
		FilterJob created = FilterJob.create(TARGET, 5L, "idem-job", NOW.minusSeconds(1), NOW.minusSeconds(600));
		return FilterJob.restore(10L, created.target(), created.filterReleaseId(), created.status(),
			created.attemptGeneration(), created.manuallyResolved(), created.resolvedVerdict(),
			created.idempotencyKey(), created.deadlineAt(), created.createdAt(), created.updatedAt());
	}
}
