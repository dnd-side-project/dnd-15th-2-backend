/**
 * Created at: 2026-08-14T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-107-ANSWER-MODERATION-JOB-UNIT-007 through UNIT-013
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class AnswerModerationExecutionWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
	private static final Instant DEADLINE = NOW.plusSeconds(600);
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 42L);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final FilterJobRepository filterJobRepository = mock(FilterJobRepository.class);
	private final FilterReleaseRepository filterReleaseRepository = mock(FilterReleaseRepository.class);
	private final FilterJobStatusHistoryRepository historyRepository = mock(FilterJobStatusHistoryRepository.class);
	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		executor = Executors.newFixedThreadPool(2);
		when(filterReleaseRepository.findById(5L)).thenReturn(Optional.of(release()));
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("주 판정기가 ALLOW면 job을 RESOLVED로 전이하고 MODERATION_VERDICT_READY를 발행한 뒤 claim을 완료한다")
	void resolvesJobAndEmitsVerdictReadyOnAllow() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.complete(1L, "worker-1", 1L, NOW)).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(allowingPipeline());
		OutboxEvent claimed = claimedExecutionEvent(job);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimed));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.RESOLVED);
		ArgumentCaptor<FilterJob> jobCaptor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository).save(jobCaptor.capture());
		assertThat(jobCaptor.getValue().status()).isEqualTo(FilterJobStatus.RESOLVED);
		assertThat(jobCaptor.getValue().resolvedVerdict()).isEqualTo(FilterVerdict.ALLOW);

		ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(OutboxEventType.MODERATION_VERDICT_READY);
		assertThat(eventCaptor.getValue().dedupKey()).isEqualTo("filter-job:10:VERDICT_READY");
		verify(outboxEventRepository).complete(1L, "worker-1", 1L, NOW);
	}

	@Test
	@DisplayName("주 판정기가 BLOCK이면 job을 BLOCK으로 RESOLVED 전이한다")
	void resolvesJobWithBlockVerdict() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.complete(1L, "worker-1", 1L, NOW)).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(blockingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job)));

		worker.processBatch(command());

		ArgumentCaptor<FilterJob> jobCaptor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository).save(jobCaptor.capture());
		assertThat(jobCaptor.getValue().resolvedVerdict()).isEqualTo(FilterVerdict.BLOCK);
	}

	@Test
	@DisplayName("pipeline timeout이면 job은 바뀌지 않고 execution 이벤트만 DEAD로 종결된다")
	void marksEventDeadOnTimeoutWithoutResolvingJob() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), eq(NOW), eq(true))).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(hangingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.PIPELINE_UNAVAILABLE);
		verify(filterJobRepository, never()).save(any());
		verify(outboxEventRepository, never()).save(any());
		verify(outboxEventRepository).fail(1L, "worker-1", 1L, NOW, NOW, true);
	}

	@Test
	@DisplayName("공급자 오류도 timeout과 동일하게 job을 바꾸지 않고 execution 이벤트를 DEAD로 종결한다")
	void marksEventDeadOnProviderErrorWithoutResolvingJob() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), eq(NOW), eq(true))).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(failingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.PIPELINE_UNAVAILABLE);
		verify(filterJobRepository, never()).save(any());
	}

	@Test
	@DisplayName("job이 이미 RESOLVED 등 AUTOMATED가 아니면 pipeline을 호출하지 않고 claim만 완료한다")
	void skipsIneligibleJobWithoutCallingPipeline() {
		FilterJob resolved = automatedJob().applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW);
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(resolved));
		when(outboxEventRepository.complete(1L, "worker-1", 1L, NOW)).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(failingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(automatedJob())));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.SKIPPED_NOT_ELIGIBLE);
		verify(filterJobRepository, never()).save(any());
		verify(outboxEventRepository).complete(1L, "worker-1", 1L, NOW);
	}

	@Test
	@DisplayName("claim한 이벤트가 가리키는 job이 존재하지 않으면 JOB_NOT_FOUND로 이벤트를 종결한다")
	void marksEventDeadWhenJobMissing() {
		when(filterJobRepository.findById(10L)).thenReturn(Optional.empty());
		when(outboxEventRepository.fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), eq(NOW), eq(true))).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(allowingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(automatedJob())));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.JOB_NOT_FOUND);
		verify(filterJobRepository, never()).save(any());
	}

	@Test
	@DisplayName("payload의 attemptGeneration이 job의 현재 세대보다 낮으면 판정을 적용하지 않고 claim만 완료한다")
	void skipsStaleAttemptGenerationVerdict() {
		FilterJob migrated = automatedJob().advanceAttemptGeneration(NOW);
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(migrated));
		when(outboxEventRepository.complete(1L, "worker-1", 1L, NOW)).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(allowingPipeline());
		OutboxEvent staleEvent = claimedExecutionEvent(automatedJob());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(staleEvent));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.SKIPPED_NOT_ELIGIBLE);
		verify(filterJobRepository, never()).save(any());
		verify(outboxEventRepository, never()).save(any());
		verify(outboxEventRepository).complete(1L, "worker-1", 1L, NOW);
	}

	@Test
	@DisplayName("claim 완료 시점에 이미 다른 worker가 선점했다면 STALE_LEASE를 반환한다")
	void returnsStaleLeaseWhenCompleteFails() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.complete(1L, "worker-1", 1L, NOW)).thenReturn(false);
		AnswerModerationExecutionWorker worker = worker(allowingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.STALE_LEASE);
	}

	private AnswerModerationExecutionWorker worker(ModerationPipelineService pipeline) {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return new AnswerModerationExecutionWorker(pipeline, filterJobRepository, filterReleaseRepository,
			historyRepository, outboxEventRepository, MAPPER, executor, Duration.ofSeconds(1), transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private AnswerModerationExecutionWorker.BatchCommand command() {
		return new AnswerModerationExecutionWorker.BatchCommand(10, "worker-1", NOW, NOW.plusSeconds(30));
	}

	private OutboxEvent claimedExecutionEvent(FilterJob job) {
		AnswerModerationEventPayloads.ExecutionRequested payload = new AnswerModerationEventPayloads.ExecutionRequested(
			10L, FilterTargetType.ANSWER, 42L, 0L, "답변 내용", ModerationLanguage.KO, 5L, job.attemptGeneration());
		String json = AnswerModerationEventPayloads.toJson(MAPPER, payload);
		return withId(OutboxEvent.pending(com.dnd.qello.notification.domain.OutboxAggregateType.FILTER_JOB, 10L,
			OutboxEventType.MODERATION_EXECUTION_REQUESTED, "filter-job:10:EXECUTION_REQUESTED", json, NOW)
			.claimed("worker-1", NOW, NOW.plusSeconds(30)));
	}

	private static OutboxEvent withId(OutboxEvent event) {
		return new OutboxEvent(1L, event.aggregateType(), event.aggregateId(), event.eventType(), event.dedupKey(),
			event.payload(), event.status(), event.attemptCount(), event.nextAttemptAt(), event.createdAt(),
			event.processedAt(), event.matchRound(), event.leaseOwner(), event.leaseExpiresAt(),
			event.leaseGeneration());
	}

	private static FilterJob automatedJob() {
		FilterJob created = FilterJob.create(TARGET, 5L, "idem-job", DEADLINE, NOW);
		return FilterJob.restore(10L, created.target(), created.filterReleaseId(), created.status(),
			created.attemptGeneration(), created.manuallyResolved(), created.resolvedVerdict(),
			created.idempotencyKey(), created.deadlineAt(), created.createdAt(), created.updatedAt());
	}

	private static FilterRelease release() {
		return FilterRelease.restore(5L, "norm-v1", "ruleset-v1", "category-map-v1", "model-v1",
			FilterReleaseStatus.PROMOTED, NOW, NOW);
	}

	private static ModerationPipelineService allowingPipeline() {
		return pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.ALLOW, false, null);
	}

	private static ModerationPipelineService blockingPipeline() {
		return pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.BLOCK, false, null);
	}

	private static ModerationPipelineService failingPipeline() {
		return pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.ALLOW, true,
			new FilteringException(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "boom"));
	}

	private static ModerationPipelineService hangingPipeline() {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			(normalizedContent, modelSnapshot) -> {
				try {
					Thread.sleep(5_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return providerResult(false);
			},
			(providerResult, contentType, language, categoryMappingRef) -> FilterVerdict.ALLOW,
			mock(FilterDecisionRepository.class),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ModerationPipelineService pipeline(
		LocalRuleVerdict ruleVerdict, FilterVerdict policyVerdict, boolean providerFails, RuntimeException failure
	) {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> ruleVerdict,
			(normalizedContent, modelSnapshot) -> {
				if (providerFails) {
					throw failure;
				}
				return providerResult(false);
			},
			(providerResult, contentType, language, categoryMappingRef) -> policyVerdict,
			new AcceptingFilterDecisionRepository(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ModerationProviderResult providerResult(boolean flagged) {
		return new ModerationProviderResult(flagged, Map.of("harassment", flagged),
			Map.of("harassment", flagged ? 0.9 : 0.01), "omni-moderation-2024-09-26");
	}

	private static final class AcceptingFilterDecisionRepository implements FilterDecisionRepository {
		@Override
		public FilterDecision save(FilterDecision decision) {
			return decision;
		}

		@Override
		public Optional<FilterDecision> findById(long id) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}

		@Override
		public Optional<FilterDecision> findByFilterJobIdAndAttemptGeneration(long filterJobId, int attemptGeneration) {
			throw new AssertionError("이 테스트에서 호출되지 않아야 합니다");
		}
	}
}
