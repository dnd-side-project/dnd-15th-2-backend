/**
 * Created at: 2026-08-15T00:30:00+09:00
 * Source scenario: TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY-INT-001 through INT-004
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.AnswerModerationDeadlineWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationJobIntakeService;
import com.dnd.qello.filtering.moderation.AnswerModerationRetryPolicy;
import com.dnd.qello.filtering.moderation.LocalRuleVerdict;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.filtering.repository.ManualReviewPriorityEvaluationRepository;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// #108: durable retry가 실제 PostgreSQL 동시성 아래에서도 logical attempt budget,
// manual review case 유일성, release 게이트 행 갱신을 정확히 지키는지 검증한다.
// AnswerModerationJobIntegrationTest(#107)와 같은 latch-barrier 패턴을 재사용한다.
@SpringBootTest
@ActiveProfiles("test")
class AnswerModerationRetryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2024-09-26";
	private static final RetryGateConfig GATE_CONFIG = new RetryGateConfig(3, 2, 2, 2, 6);
	private static final OutboxBackoffStrategy FAST_BACKOFF = attempt -> Duration.ofSeconds(60);
	private static final OutboxBackoffStrategy SLOW_BACKOFF = attempt -> Duration.ofSeconds(120);
	private static final ManualReviewPriorityPolicy MANUAL_REVIEW_PRIORITY_POLICY =
		new ManualReviewPriorityPolicy(3, Duration.ofHours(24), "test-v1");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private FilterReleaseRepository filterReleaseRepository;
	@Autowired
	private FilterJobRepository filterJobRepository;
	@Autowired
	private FilterJobStatusHistoryRepository historyRepository;
	@Autowired
	private FilterDecisionRepository filterDecisionRepository;
	@Autowired
	private OutboxEventRepository outboxEventRepository;
	@Autowired
	private FilterReleaseRetryGateRepository filterReleaseRetryGateRepository;
	@Autowired
	private ManualReviewCaseRepository manualReviewCaseRepository;
	@Autowired
	private ManualReviewPriorityEvaluationRepository manualReviewPriorityEvaluationRepository;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private AnswerModerationDeadlineWorker deadlineWorker;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ObjectMapper objectMapper;

	private long releaseId;
	private ExecutorService executor;
	private ExecutorService pipelineExecutor;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'FILTER_JOB'");
		jdbc.update("DELETE FROM filter_decision");
		jdbc.update("DELETE FROM filter_job_status_history");
		jdbc.update("DELETE FROM manual_review_priority_evaluation");
		jdbc.update("DELETE FROM manual_review_case");
		jdbc.update("DELETE FROM filter_release_retry_gate");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM release_promotion_history");
		jdbc.update("DELETE FROM filter_release");
		releaseId = promotedRelease();
		executor = Executors.newFixedThreadPool(4);
		pipelineExecutor = Executors.newFixedThreadPool(4);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		pipelineExecutor.shutdownNow();
	}

	@Test
	@DisplayName("동시에 재시도 claim이 경쟁해도 logicalAttemptCount는 정확히 한 번만 증가한다")
	void concurrentRetryClaimsDoNotDoubleCountLogicalAttempts() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob job = intake.submit(target(1L), "재시도 경쟁 답변", ModerationLanguage.KO, "retry-race-001");
		AnswerModerationExecutionWorker workerA = executionWorker(failingPipeline(), 100);
		AnswerModerationExecutionWorker workerB = executionWorker(failingPipeline(), 100);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<AnswerModerationExecutionWorker.BatchResult>> futures = List.of(
			executor.submit(processAfterSignal(workerA, "retry-worker-a", ready, start)),
			executor.submit(processAfterSignal(workerB, "retry-worker-b", ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		AnswerModerationExecutionWorker.BatchResult first = futures.get(0).get(10, TimeUnit.SECONDS);
		AnswerModerationExecutionWorker.BatchResult second = futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(first.claimed() + second.claimed()).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT logical_attempt_count FROM filter_job WHERE id = ?", Long.class, job.id())).isEqualTo(1L);
		assertThat(jdbc.queryForObject(
			"SELECT status FROM filter_job WHERE id = ?", String.class, job.id())).isEqualTo("AUTOMATED");
	}

	@Test
	@DisplayName("같은 대상·release로 소진되는 두 job이 동시에 처리돼도 ManualReviewCase는 하나만 생긴다")
	void concurrentExhaustionDoesNotDuplicateManualReviewCase() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterTarget sharedTarget = target(2L);
		FilterJob jobA = intake.submit(sharedTarget, "동시 소진 답변 A", ModerationLanguage.KO, "exhaust-race-a");
		FilterJob jobB = intake.submit(sharedTarget, "동시 소진 답변 B", ModerationLanguage.KO, "exhaust-race-b");
		AnswerModerationExecutionWorker workerA = executionWorker(failingPipeline(), 1);
		AnswerModerationExecutionWorker workerB = executionWorker(failingPipeline(), 1);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<AnswerModerationExecutionWorker.BatchResult>> futures = List.of(
			executor.submit(processAfterSignal(workerA, "exhaust-worker-a", ready, start)),
			executor.submit(processAfterSignal(workerB, "exhaust-worker-b", ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		futures.get(0).get(10, TimeUnit.SECONDS);
		futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(jdbc.queryForObject(
			"SELECT status FROM filter_job WHERE id = ?", String.class, jobA.id())).isEqualTo("MANUAL_REVIEW_REQUIRED");
		assertThat(jdbc.queryForObject(
			"SELECT status FROM filter_job WHERE id = ?", String.class, jobB.id())).isEqualTo("MANUAL_REVIEW_REQUIRED");
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM manual_review_case
			WHERE target_type = 'ANSWER' AND target_id = ? AND target_version = 0 AND filter_release_id = ?
			""", Long.class, sharedTarget.targetId(), releaseId)).isEqualTo(1L);
	}

	@Test
	@DisplayName("같은 release의 동시 실패가 게이트 행 갱신을 유실하지 않는다(FOR UPDATE 직렬화)")
	void concurrentFailuresDoNotLoseGateUpdates() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob jobA = intake.submit(target(3L), "게이트 경쟁 답변 A", ModerationLanguage.KO, "gate-race-a");
		FilterJob jobB = intake.submit(target(4L), "게이트 경쟁 답변 B", ModerationLanguage.KO, "gate-race-b");
		AnswerModerationExecutionWorker workerA = executionWorker(failingPipeline(), 100);
		AnswerModerationExecutionWorker workerB = executionWorker(failingPipeline(), 100);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<AnswerModerationExecutionWorker.BatchResult>> futures = List.of(
			executor.submit(processAfterSignal(workerA, "gate-worker-a", ready, start)),
			executor.submit(processAfterSignal(workerB, "gate-worker-b", ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		futures.get(0).get(10, TimeUnit.SECONDS);
		futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(jdbc.queryForObject(
			"SELECT consecutive_failures FROM filter_release_retry_gate WHERE filter_release_id = ?",
			Long.class, releaseId)).isEqualTo(2L);
		assertThat(jdbc.queryForObject(
			"SELECT state FROM filter_release_retry_gate WHERE filter_release_id = ?",
			String.class, releaseId)).isEqualTo("HEALTHY");
	}

	@Test
	@DisplayName("deadline 경과 신호와 retry 소진 handoff가 동시에 일어나도 서로 충돌하지 않는다")
	void deadlineElapsedAndRetryExhaustionCoexist() {
		AnswerModerationJobIntakeService intake = intakeService(Duration.ofSeconds(-1));
		FilterJob job = intake.submit(target(5L), "deadline 동시 소진 답변", ModerationLanguage.KO, "deadline-exhaust-001");

		deadlineWorker.processBatch(50, NOW);
		AnswerModerationExecutionWorker worker = executionWorker(failingPipeline(), 1);
		worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(10, "deadline-exhaust-worker", NOW,
			NOW.plusSeconds(30)));

		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'FILTER_JOB' AND event_type = 'MODERATION_DEADLINE_ELAPSED' AND aggregate_id = ?",
			Long.class, job.id())).isEqualTo(1L);
		assertThat(jdbc.queryForObject(
			"SELECT status FROM filter_job WHERE id = ?", String.class, job.id())).isEqualTo("MANUAL_REVIEW_REQUIRED");
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM manual_review_case WHERE target_type = 'ANSWER' AND target_id = ? AND filter_release_id = ?",
			Long.class, 5L, releaseId)).isEqualTo(1L);
	}

	private Callable<AnswerModerationExecutionWorker.BatchResult> processAfterSignal(
		AnswerModerationExecutionWorker worker, String owner, CountDownLatch ready, CountDownLatch start
	) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("execution start barrier timed out");
			}
			return worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(
				10, owner, NOW, NOW.plusSeconds(30)));
		};
	}

	private AnswerModerationJobIntakeService intakeService() {
		return intakeService(Duration.ofMinutes(10));
	}

	private AnswerModerationJobIntakeService intakeService(Duration deadlineWindow) {
		return new AnswerModerationJobIntakeService(filterJobRepository, filterReleaseRepository, historyRepository,
			outboxEventRepository, objectMapper, deadlineWindow, transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private AnswerModerationExecutionWorker executionWorker(ModerationPipelineService pipeline, int maxAttempts) {
		AnswerModerationRetryPolicy retryPolicy =
			new AnswerModerationRetryPolicy(FAST_BACKOFF, SLOW_BACKOFF, maxAttempts, Duration.ofHours(1));
		return new AnswerModerationExecutionWorker(pipeline, filterJobRepository, filterReleaseRepository,
			historyRepository, outboxEventRepository, retryPolicy, filterReleaseRetryGateRepository, GATE_CONFIG,
			manualReviewCaseRepository, manualReviewPriorityEvaluationRepository, MANUAL_REVIEW_PRIORITY_POLICY,
			Duration.ofSeconds(5), objectMapper, pipelineExecutor, Duration.ofSeconds(5),
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private ModerationPipelineService failingPipeline() {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			(normalizedContent, modelSnapshot) -> {
				throw new FilteringException(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "boom");
			},
			(providerResult, contentType, language, categoryMappingRef) -> {
				throw new AssertionError("판정까지 도달하면 안 됩니다");
			},
			filterDecisionRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static FilterTarget target(long targetId) {
		return FilterTarget.of(FilterTargetType.ANSWER, targetId);
	}

	private long promotedRelease() {
		FilterRelease candidate = releaseRegistryService.createCandidate(
			"norm-v1", "ruleset-v1", "category-map-v1", MODEL_SNAPSHOT);
		releaseRegistryService.markOfflineEvaluated(candidate.id());
		releaseRegistryService.designateShadow(candidate.id());
		releaseRegistryService.designateCanary(candidate.id());
		FilterRelease promoted = releaseRegistryService.promote(candidate.id(), 1L);
		return promoted.id();
	}
}
