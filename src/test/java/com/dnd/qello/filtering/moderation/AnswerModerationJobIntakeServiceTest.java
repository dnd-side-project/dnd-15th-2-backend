/**
 * Created at: 2026-08-14T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-107-ANSWER-MODERATION-JOB-UNIT-001 through UNIT-006
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class AnswerModerationJobIntakeServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
	private static final Duration DEADLINE_WINDOW = Duration.ofMinutes(10);
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 42L);

	private final FilterJobRepository filterJobRepository = mock(FilterJobRepository.class);
	private final FilterReleaseRepository filterReleaseRepository = mock(FilterReleaseRepository.class);
	private final FilterJobStatusHistoryRepository historyRepository = mock(FilterJobStatusHistoryRepository.class);
	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);

	@Test
	@DisplayName("같은 idempotencyKey로 이미 접수된 job이 있으면 그 job을 그대로 반환하고 새로 만들지 않는다")
	void returnsExistingJobForDuplicateIdempotencyKey() {
		FilterJob existing = restoredJob();
		when(filterJobRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));
		AnswerModerationJobIntakeService service = service();

		FilterJob result = service.submit(TARGET, "답변 내용", ModerationLanguage.KO, "idem-1");

		assertThat(result).isEqualTo(existing);
		verify(filterJobRepository, never()).save(any());
		verify(outboxEventRepository, never()).save(any());
		verify(historyRepository, never()).save(any());
	}

	@Test
	@DisplayName("현재 승격된 release가 없으면 fail-closed로 거부하고 job을 만들지 않는다")
	void rejectsWhenNoActiveRelease() {
		when(filterJobRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
		when(filterReleaseRepository.findCurrentlyPromoted()).thenReturn(Optional.empty());
		AnswerModerationJobIntakeService service = service();

		assertThatThrownBy(() -> service.submit(TARGET, "답변 내용", ModerationLanguage.KO, "idem-2"))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.NO_ACTIVE_RELEASE);
		verify(filterJobRepository, never()).save(any());
	}

	@Test
	@DisplayName("job 생성 시 deadlineAt을 now + deadlineWindow로 한 번만 고정한다")
	void fixesDeadlineAtCreationTime() {
		when(filterJobRepository.findByIdempotencyKey("idem-3")).thenReturn(Optional.empty());
		when(filterReleaseRepository.findCurrentlyPromoted()).thenReturn(Optional.of(release()));
		when(filterJobRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));
		AnswerModerationJobIntakeService service = service();

		service.submit(TARGET, "답변 내용", ModerationLanguage.KO, "idem-3");

		ArgumentCaptor<FilterJob> captor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository).save(captor.capture());
		assertThat(captor.getValue().deadlineAt()).isEqualTo(NOW.plus(DEADLINE_WINDOW));
	}

	@Test
	@DisplayName("job 생성과 함께 상태 이력과 MODERATION_EXECUTION_REQUESTED outbox 이벤트를 같은 흐름에서 남긴다")
	void emitsHistoryAndExecutionRequestedEvent() {
		when(filterJobRepository.findByIdempotencyKey("idem-4")).thenReturn(Optional.empty());
		when(filterReleaseRepository.findCurrentlyPromoted()).thenReturn(Optional.of(release()));
		when(filterJobRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 200L));
		AnswerModerationJobIntakeService service = service();

		service.submit(TARGET, "답변 내용", ModerationLanguage.KO, "idem-4");

		ArgumentCaptor<FilterJobStatusHistoryEntry> historyCaptor =
			ArgumentCaptor.forClass(FilterJobStatusHistoryEntry.class);
		verify(historyRepository).save(historyCaptor.capture());
		assertThat(historyCaptor.getValue().filterJobId()).isEqualTo(200L);
		assertThat(historyCaptor.getValue().fromStatus()).isNull();

		ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(eventCaptor.capture());
		OutboxEvent event = eventCaptor.getValue();
		assertThat(event.aggregateType()).isEqualTo(OutboxAggregateType.FILTER_JOB);
		assertThat(event.aggregateId()).isEqualTo(200L);
		assertThat(event.eventType()).isEqualTo(OutboxEventType.MODERATION_EXECUTION_REQUESTED);
		assertThat(event.dedupKey()).isEqualTo("filter-job:200:EXECUTION_REQUESTED");
		assertThat(event.payload()).contains("답변 내용").contains("\"targetId\":42");
	}

	@Test
	@DisplayName("조회 후 삽입 사이 경쟁으로 idempotencyKey unique 제약을 위반하면 예외를 전파하지 않고 먼저 커밋된 job을 다시 조회해 반환한다")
	void returnsExistingJobWhenConcurrentInsertRaces() {
		FilterJob existing = restoredJob();
		when(filterJobRepository.findByIdempotencyKey("idem-1"))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(existing));
		when(filterReleaseRepository.findCurrentlyPromoted()).thenReturn(Optional.of(release()));
		when(filterJobRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
		AnswerModerationJobIntakeService service = service();

		FilterJob result = service.submit(TARGET, "답변 내용", ModerationLanguage.KO, "idem-1");

		assertThat(result).isEqualTo(existing);
		verify(outboxEventRepository, never()).save(any());
		verify(historyRepository, never()).save(any());
	}

	private AnswerModerationJobIntakeService service() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return new AnswerModerationJobIntakeService(filterJobRepository, filterReleaseRepository, historyRepository,
			outboxEventRepository, new ObjectMapper(), DEADLINE_WINDOW, transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static FilterJob withId(FilterJob job, long id) {
		return FilterJob.restore(id, job.target(), job.filterReleaseId(), job.status(), job.attemptGeneration(),
			job.manuallyResolved(), job.resolvedVerdict(), job.idempotencyKey(), job.deadlineAt(), job.createdAt(),
			job.updatedAt());
	}

	private static FilterJob restoredJob() {
		return FilterJob.restore(1L, TARGET, 5L, com.dnd.qello.filtering.domain.FilterJobStatus.AUTOMATED, 1, false,
			null, "idem-1", NOW.plus(DEADLINE_WINDOW), NOW, NOW);
	}

	private static FilterRelease release() {
		return FilterRelease.restore(5L, "norm-v1", "ruleset-v1", "category-map-v1", "model-v1",
			FilterReleaseStatus.PROMOTED, NOW, NOW);
	}
}
