/**
 * Created at: 2026-08-14T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-107-ANSWER-MODERATION-JOB-UNIT-007 through UNIT-013,
 * TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY-UNIT-014 through UNIT-017
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
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
import com.dnd.qello.filtering.domain.FilterReleaseGateState;
import com.dnd.qello.filtering.domain.FilterReleaseRetryGate;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class AnswerModerationExecutionWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
	private static final Instant DEADLINE = NOW.plusSeconds(600);
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 42L);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final RetryGateConfig GATE_CONFIG = new RetryGateConfig(3, 2, 2, 2, 6);
	private static final OutboxBackoffStrategy FAST_BACKOFF = attempt -> Duration.ofSeconds(1);
	private static final OutboxBackoffStrategy SLOW_BACKOFF = attempt -> Duration.ofSeconds(60);

	private final FilterJobRepository filterJobRepository = mock(FilterJobRepository.class);
	private final FilterReleaseRepository filterReleaseRepository = mock(FilterReleaseRepository.class);
	private final FilterJobStatusHistoryRepository historyRepository = mock(FilterJobStatusHistoryRepository.class);
	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
	private final FilterReleaseRetryGateRepository filterReleaseRetryGateRepository =
		mock(FilterReleaseRetryGateRepository.class);
	private final ManualReviewCaseRepository manualReviewCaseRepository = mock(ManualReviewCaseRepository.class);
	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		executor = Executors.newFixedThreadPool(2);
		when(filterReleaseRepository.findById(5L)).thenReturn(Optional.of(release()));
		when(filterReleaseRetryGateRepository.findOrCreateForUpdate(anyLong(), any()))
			.thenAnswer(inv -> FilterReleaseRetryGate.healthy(inv.getArgument(0), inv.getArgument(1)));
		when(filterReleaseRetryGateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(manualReviewCaseRepository.findByTargetAndFilterReleaseId(any(), anyLong())).thenReturn(Optional.empty());
		when(manualReviewCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
		OutboxEvent claimed = claimedExecutionEvent(job, 10L, 1L);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimed));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.RESOLVED);
		ArgumentCaptor<FilterJob> jobCaptor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository).save(jobCaptor.capture());
		assertThat(jobCaptor.getValue().status()).isEqualTo(FilterJobStatus.RESOLVED);
		assertThat(jobCaptor.getValue().resolvedVerdict()).isEqualTo(FilterVerdict.ALLOW);
		assertThat(jobCaptor.getValue().logicalAttemptCount()).isEqualTo(1);

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
			.thenReturn(List.of(claimedExecutionEvent(job, 10L, 1L)));

		worker.processBatch(command());

		ArgumentCaptor<FilterJob> jobCaptor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository).save(jobCaptor.capture());
		assertThat(jobCaptor.getValue().resolvedVerdict()).isEqualTo(FilterVerdict.BLOCK);
	}

	@Test
	@DisplayName("pipeline timeout이면 예산 안에서 backoff 뒤 같은 이벤트를 재시도로 예약한다")
	void schedulesRetryOnTimeoutWithinBudget() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), any(OutboxRetryDecision.class)))
			.thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(hangingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job, 10L, 1L)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.RETRY_SCHEDULED);
		ArgumentCaptor<FilterJob> jobCaptor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository).save(jobCaptor.capture());
		assertThat(jobCaptor.getValue().status()).isEqualTo(FilterJobStatus.AUTOMATED);
		assertThat(jobCaptor.getValue().logicalAttemptCount()).isEqualTo(1);
		verify(outboxEventRepository, never()).save(any());
		ArgumentCaptor<OutboxRetryDecision> decisionCaptor = ArgumentCaptor.forClass(OutboxRetryDecision.class);
		verify(outboxEventRepository).fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().dead()).isFalse();
		assertThat(decisionCaptor.getValue().nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	@DisplayName("공급자 오류도 timeout과 동일하게 예산 안에서 재시도로 예약한다")
	void schedulesRetryOnProviderErrorWithinBudget() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), any(OutboxRetryDecision.class)))
			.thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(failingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job, 10L, 1L)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.RETRY_SCHEDULED);
	}

	@Test
	@DisplayName("공급자가 429 Retry-After 힌트를 주면 계산된 backoff보다 커도 그 값을 하한으로 사용한다")
	void schedulesRetryHonoringRetryAfterHint() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), any(OutboxRetryDecision.class)))
			.thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(
			pipeline(LocalRuleVerdict.noMatch(), FilterVerdict.ALLOW, true, new ModerationRateLimitedException(Duration.ofSeconds(45))),
			retryPolicy(100, Duration.ofHours(1)));
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job, 10L, 1L)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.RETRY_SCHEDULED);
		ArgumentCaptor<OutboxRetryDecision> decisionCaptor = ArgumentCaptor.forClass(OutboxRetryDecision.class);
		verify(outboxEventRepository).fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().nextAttemptAt()).isEqualTo(NOW.plusSeconds(45));
	}

	@Test
	@DisplayName("재시도 예산을 소진하면 job을 MANUAL_REVIEW_REQUIRED로 넘기고 case를 만들되 VERDICT_READY는 발행하지 않는다")
	void exhaustsRetriesAndOpensManualReviewWithoutPublishingVerdict() {
		FilterJob job = automatedJob();
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(job));
		when(outboxEventRepository.fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), any(OutboxRetryDecision.class)))
			.thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(failingPipeline(), retryPolicy(1, Duration.ofHours(1)));
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(job, 10L, 1L)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.RETRY_EXHAUSTED);
		ArgumentCaptor<FilterJob> jobCaptor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository).save(jobCaptor.capture());
		assertThat(jobCaptor.getValue().status()).isEqualTo(FilterJobStatus.MANUAL_REVIEW_REQUIRED);
		assertThat(jobCaptor.getValue().logicalAttemptCount()).isEqualTo(1);
		verify(manualReviewCaseRepository).save(any());
		verify(outboxEventRepository, never()).save(any());
		ArgumentCaptor<OutboxRetryDecision> decisionCaptor = ArgumentCaptor.forClass(OutboxRetryDecision.class);
		verify(outboxEventRepository).fail(eq(1L), eq("worker-1"), eq(1L), eq(NOW), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().dead()).isTrue();
	}

	@Test
	@DisplayName("같은 release에서 게이트 한도를 초과하는 이후 이벤트는 pipeline을 호출하지 않고 짧게 미룬다")
	void defersSecondEventInSameBatchWhenGateLimitExceeded() {
		FilterJob jobA = automatedJob();
		FilterJob jobB = jobWithId(20L);
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(jobA));
		when(filterJobRepository.findById(20L)).thenReturn(Optional.of(jobB));
		// DEGRADED + currentLimit=1: 이번 배치에서 이미 1개를 admit했으면 그다음은 막힌다.
		// setUp()의 범용 HEALTHY 기본 스텁을 먼저 지운다 — eq(5L)로 더 구체적인 스텁을
		// 추가로 등록하면 그 등록 과정(when(...) 레코딩 호출) 자체가 기존 anyLong()
		// 스텁의 answer를 실행시켜, healthy()가 레코딩용 더미 인자로 검증 예외를 던진다.
		reset(filterReleaseRetryGateRepository);
		when(filterReleaseRetryGateRepository.findOrCreateForUpdate(eq(5L), any()))
			.thenReturn(FilterReleaseRetryGate.restore(5L, FilterReleaseGateState.DEGRADED, 1, 0, 0, NOW));
		when(filterReleaseRetryGateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(outboxEventRepository.complete(1L, "worker-1", 1L, NOW)).thenReturn(true);
		when(outboxEventRepository.fail(eq(2L), eq("worker-1"), eq(1L), eq(NOW), any(OutboxRetryDecision.class)))
			.thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(allowingPipeline());
		OutboxEvent eventA = claimedExecutionEvent(jobA, 10L, 1L);
		OutboxEvent eventB = claimedExecutionEvent(jobB, 20L, 2L);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(eventA, eventB));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(
			AnswerModerationExecutionWorker.Outcome.RESOLVED, AnswerModerationExecutionWorker.Outcome.RETRY_DEFERRED_BY_GATE);
		ArgumentCaptor<FilterJob> jobCaptor = ArgumentCaptor.forClass(FilterJob.class);
		verify(filterJobRepository, times(1)).save(jobCaptor.capture());
		assertThat(jobCaptor.getValue().id()).isEqualTo(10L);
		ArgumentCaptor<OutboxRetryDecision> decisionCaptor = ArgumentCaptor.forClass(OutboxRetryDecision.class);
		verify(outboxEventRepository).fail(eq(2L), eq("worker-1"), eq(1L), eq(NOW), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().dead()).isFalse();
	}

	@Test
	@DisplayName("job이 이미 RESOLVED 등 AUTOMATED가 아니면 pipeline을 호출하지 않고 claim만 완료한다")
	void skipsIneligibleJobWithoutCallingPipeline() {
		FilterJob resolved = automatedJob().applyAutomatedDecision(1, FilterVerdict.ALLOW, NOW);
		when(filterJobRepository.findById(10L)).thenReturn(Optional.of(resolved));
		when(outboxEventRepository.complete(1L, "worker-1", 1L, NOW)).thenReturn(true);
		AnswerModerationExecutionWorker worker = worker(failingPipeline());
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(claimedExecutionEvent(automatedJob(), 10L, 1L)));

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
			.thenReturn(List.of(claimedExecutionEvent(automatedJob(), 10L, 1L)));

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
		OutboxEvent staleEvent = claimedExecutionEvent(automatedJob(), 10L, 1L);
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
			.thenReturn(List.of(claimedExecutionEvent(job, 10L, 1L)));

		AnswerModerationExecutionWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationExecutionWorker.Outcome.STALE_LEASE);
	}

	private AnswerModerationExecutionWorker worker(ModerationPipelineService pipeline) {
		return worker(pipeline, retryPolicy(100, Duration.ofHours(1)));
	}

	private AnswerModerationExecutionWorker worker(
		ModerationPipelineService pipeline, AnswerModerationRetryPolicy retryPolicy
	) {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return new AnswerModerationExecutionWorker(pipeline, filterJobRepository, filterReleaseRepository,
			historyRepository, outboxEventRepository, retryPolicy, filterReleaseRetryGateRepository, GATE_CONFIG,
			manualReviewCaseRepository, Duration.ofSeconds(5), MAPPER, executor, Duration.ofSeconds(1),
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static AnswerModerationRetryPolicy retryPolicy(int maxAttempts, Duration maxRetryLifetime) {
		return new AnswerModerationRetryPolicy(FAST_BACKOFF, SLOW_BACKOFF, maxAttempts, maxRetryLifetime);
	}

	private AnswerModerationExecutionWorker.BatchCommand command() {
		return new AnswerModerationExecutionWorker.BatchCommand(10, "worker-1", NOW, NOW.plusSeconds(30));
	}

	private OutboxEvent claimedExecutionEvent(FilterJob job, long filterJobId, long eventId) {
		AnswerModerationEventPayloads.ExecutionRequested payload = new AnswerModerationEventPayloads.ExecutionRequested(
			filterJobId, FilterTargetType.ANSWER, 42L, 0L, "답변 내용", ModerationLanguage.KO, 5L,
			job.attemptGeneration());
		String json = AnswerModerationEventPayloads.toJson(MAPPER, payload);
		return withId(eventId, OutboxEvent.pending(com.dnd.qello.notification.domain.OutboxAggregateType.FILTER_JOB,
			filterJobId, OutboxEventType.MODERATION_EXECUTION_REQUESTED,
			"filter-job:" + filterJobId + ":EXECUTION_REQUESTED", json, NOW)
			.claimed("worker-1", NOW, NOW.plusSeconds(30)));
	}

	private static OutboxEvent withId(long id, OutboxEvent event) {
		return new OutboxEvent(id, event.aggregateType(), event.aggregateId(), event.eventType(), event.dedupKey(),
			event.payload(), event.status(), event.attemptCount(), event.nextAttemptAt(), event.createdAt(),
			event.processedAt(), event.matchRound(), event.leaseOwner(), event.leaseExpiresAt(),
			event.leaseGeneration());
	}

	private static FilterJob automatedJob() {
		return jobWithId(10L);
	}

	private static FilterJob jobWithId(long id) {
		FilterJob created = FilterJob.create(TARGET, 5L, "idem-job-" + id, DEADLINE, NOW);
		return FilterJob.restore(id, created.target(), created.filterReleaseId(), created.status(),
			created.attemptGeneration(), created.logicalAttemptCount(), created.manuallyResolved(),
			created.resolvedVerdict(), created.idempotencyKey(), created.deadlineAt(), created.createdAt(),
			created.updatedAt());
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
