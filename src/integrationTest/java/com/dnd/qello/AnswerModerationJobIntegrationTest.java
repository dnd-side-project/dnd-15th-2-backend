/**
 * Created at: 2026-08-14T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-107-ANSWER-MODERATION-JOB-INT-001 through INT-004
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.AnswerModerationDeadlineWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationJobIntakeService;
import com.dnd.qello.filtering.moderation.LocalRuleVerdict;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.moderation.ModerationProviderResult;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class AnswerModerationJobIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2024-09-26";
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 900L);

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
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private AnswerModerationDeadlineWorker deadlineWorker;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ObjectMapper objectMapper;

	private long releaseId;
	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'FILTER_JOB'");
		jdbc.update("DELETE FROM filter_decision");
		jdbc.update("DELETE FROM filter_job_status_history");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM release_promotion_history");
		jdbc.update("DELETE FROM filter_release");
		releaseId = promotedRelease();
		executor = Executors.newFixedThreadPool(4);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("같은 idempotencyKey로 동시에 접수해도 filter_job과 실행 요청 outbox는 정확히 하나만 커밋된다")
	void concurrentDuplicateSubmissionCreatesExactlyOneJob() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<FilterJob>> futures = List.of(
			executor.submit(submitAfterSignal(intake, ready, start)),
			executor.submit(submitAfterSignal(intake, ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		awaitIgnoringDuplicateFailure(futures.get(0));
		awaitIgnoringDuplicateFailure(futures.get(1));

		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM filter_job WHERE idempotency_key = ?", Long.class, "dup-key-int-001")).isEqualTo(1L);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'FILTER_JOB' AND event_type = 'MODERATION_EXECUTION_REQUESTED'",
			Long.class)).isEqualTo(1L);
	}

	@Test
	@DisplayName("같은 실행 요청 이벤트를 두 worker가 동시에 claim해도 job은 한 번만 RESOLVED된다")
	void concurrentExecutionWorkersResolveJobExactlyOnce() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob job = intake.submit(TARGET, "동시성 답변 내용", ModerationLanguage.KO, "concurrent-exec-001");
		AnswerModerationExecutionWorker workerA = executionWorker(allowingPipeline());
		AnswerModerationExecutionWorker workerB = executionWorker(allowingPipeline());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<AnswerModerationExecutionWorker.BatchResult>> futures = List.of(
			executor.submit(processAfterSignal(workerA, "exec-worker-a", ready, start)),
			executor.submit(processAfterSignal(workerB, "exec-worker-b", ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		AnswerModerationExecutionWorker.BatchResult first = futures.get(0).get(10, TimeUnit.SECONDS);
		AnswerModerationExecutionWorker.BatchResult second = futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(first.claimed() + second.claimed()).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT status FROM filter_job WHERE id = ?", String.class, job.id())).isEqualTo("RESOLVED");
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'FILTER_JOB' AND event_type = 'MODERATION_VERDICT_READY' AND aggregate_id = ?",
			Long.class, job.id())).isEqualTo(1L);
	}

	@Test
	@DisplayName("deadline worker를 동시에 두 번 돌려도 같은 job에 신호를 두 번 보내지 않는다")
	void concurrentDeadlineScansSignalExactlyOnce() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService(Duration.ofSeconds(-1));
		FilterJob job = intake.submit(TARGET, "deadline 답변 내용", ModerationLanguage.KO, "deadline-race-001");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<AnswerModerationDeadlineWorker.BatchResult>> futures = List.of(
			executor.submit(scanAfterSignal(ready, start)),
			executor.submit(scanAfterSignal(ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		futures.get(0).get(10, TimeUnit.SECONDS);
		futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'FILTER_JOB' AND event_type = 'MODERATION_DEADLINE_ELAPSED' AND aggregate_id = ?",
			Long.class, job.id())).isEqualTo(1L);
	}

	@Test
	@DisplayName("deadline 경과 신호가 이미 나간 뒤 도착한 판정도 MODERATION_VERDICT_READY로 전달된다")
	void lateVerdictAfterDeadlineElapsedStillEmitsVerdictReady() {
		AnswerModerationJobIntakeService intake = intakeService(Duration.ofSeconds(-1));
		FilterJob job = intake.submit(TARGET, "늦은 판정 답변", ModerationLanguage.KO, "late-verdict-001");

		deadlineWorker.processBatch(50, NOW);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'FILTER_JOB' AND event_type = 'MODERATION_DEADLINE_ELAPSED' AND aggregate_id = ?",
			Long.class, job.id())).isEqualTo(1L);

		AnswerModerationExecutionWorker worker = executionWorker(allowingPipeline());
		worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(
			10, "late-worker", NOW, NOW.plusSeconds(30)));

		assertThat(jdbc.queryForObject(
			"SELECT status FROM filter_job WHERE id = ?", String.class, job.id())).isEqualTo("RESOLVED");
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'FILTER_JOB' AND event_type = 'MODERATION_VERDICT_READY' AND aggregate_id = ?",
			Long.class, job.id())).isEqualTo(1L);
	}

	private Callable<FilterJob> submitAfterSignal(
		AnswerModerationJobIntakeService intake, CountDownLatch ready, CountDownLatch start
	) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("submit start barrier timed out");
			}
			return intake.submit(TARGET, "동시 접수 답변", ModerationLanguage.KO, "dup-key-int-001");
		};
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

	private Callable<AnswerModerationDeadlineWorker.BatchResult> scanAfterSignal(
		CountDownLatch ready, CountDownLatch start
	) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("deadline scan start barrier timed out");
			}
			return deadlineWorker.processBatch(50, NOW);
		};
	}

	private void awaitIgnoringDuplicateFailure(Future<FilterJob> future) throws Exception {
		try {
			future.get(10, TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException raceLoser) {
			// 동시 접수의 패자는 idempotency_key unique 제약 위반으로 끝날 수 있다 —
			// 그 자체가 "job이 두 개 만들어지지 않는다"는 보장의 증거다.
		}
	}

	private AnswerModerationJobIntakeService intakeService() {
		return intakeService(Duration.ofMinutes(10));
	}

	private AnswerModerationJobIntakeService intakeService(Duration deadlineWindow) {
		return new AnswerModerationJobIntakeService(filterJobRepository, filterReleaseRepository, historyRepository,
			outboxEventRepository, objectMapper, deadlineWindow, transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private AnswerModerationExecutionWorker executionWorker(ModerationPipelineService pipeline) {
		return new AnswerModerationExecutionWorker(pipeline, filterJobRepository, filterReleaseRepository,
			historyRepository, outboxEventRepository, objectMapper, executor, Duration.ofSeconds(5),
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private ModerationPipelineService allowingPipeline() {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			(normalizedContent, modelSnapshot) -> providerResult(false),
			(providerResult, contentType, language, categoryMappingRef) -> FilterVerdict.ALLOW,
			filterDecisionRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ModerationProviderResult providerResult(boolean flagged) {
		return new ModerationProviderResult(flagged, Map.of("harassment", flagged),
			Map.of("harassment", flagged ? 0.9 : 0.01), MODEL_SNAPSHOT);
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
