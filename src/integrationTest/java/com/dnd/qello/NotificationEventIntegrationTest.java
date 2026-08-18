/**
 * Created at: 2026-08-17T17:00:00+09:00
 * Source scenario: TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-INT-001 through INT-006
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
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewPriorityDecision;
import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
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
import com.dnd.qello.notification.domain.NotificationEvent;
import com.dnd.qello.notification.domain.NotificationEventStatus;
import com.dnd.qello.notification.domain.NotificationRetryPolicy;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;
import com.dnd.qello.notification.repository.NotificationEventRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.slack.SlackDeliveryException;
import com.dnd.qello.notification.slack.SlackManualReviewNotificationDispatchWorker;
import com.dnd.qello.notification.slack.SlackNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;

// #111: case 생성과 notification_event 발행의 원자성, 생성·전송 두 지점의 중복
// 방지(INV-SLK-005), Slack 전송 실패가 manual_review_case/filter_job을
// 건드리지 않음(INV-SLK-002)을 실제 PostgreSQL 위에서 검증한다.
@SpringBootTest
@ActiveProfiles("test")
class NotificationEventIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2026-08-01";
	private static final RetryGateConfig GATE_CONFIG = new RetryGateConfig(3, 2, 2, 2, 6);
	private static final OutboxBackoffStrategy FAST_BACKOFF = attempt -> Duration.ofSeconds(60);
	private static final OutboxBackoffStrategy SLOW_BACKOFF = attempt -> Duration.ofSeconds(120);
	private static final ManualReviewPriorityPolicy MANUAL_REVIEW_PRIORITY_POLICY =
		new ManualReviewPriorityPolicy(3, Duration.ofHours(24), "test-v1");
	private static final SlackNotifier SUCCEEDING_NOTIFIER = notification -> { };

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
	private NotificationEventRepository notificationEventRepository;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
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
		jdbc.update("DELETE FROM notification_event");
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
	@DisplayName("case 생성 성공과 같은 트랜잭션으로 notification_event가 PENDING으로 원자적으로 생성된다")
	void producerCreatesNotificationEventAtomicallyWithCase() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob submitted = intake.submit(target(1L), "본문", ModerationLanguage.KO, "int-001");
		AnswerModerationExecutionWorker worker = executionWorker(failingPipeline(), 1);

		worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(10, "int-001-worker", NOW,
			NOW.plusSeconds(30)));

		ManualReviewCase opened = manualReviewCaseRepository.findByTargetAndFilterReleaseId(target(1L), releaseId)
			.orElseThrow();
		NotificationEvent event = notificationEventRepository.findByCaseId(opened.id()).orElseThrow();
		assertThat(event.status()).isEqualTo(NotificationEventStatus.PENDING);
		assertThat(event.attemptCount()).isZero();
		assertThat(event.adminLinkPath()).isEqualTo("/admin/filtering/manual-review-cases/" + opened.id());
		assertThat(submitted.id()).isNotNull();
	}

	@Test
	@DisplayName("동일 대상·release로 동시에 소진되는 두 job은 case와 notification_event를 정확히 하나씩만 만든다(INV-SLK-005 생성 시점 dedup)")
	void concurrentCaseCreationRaceProducesExactlyOneNotificationEvent() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterTarget sharedTarget = target(2L);
		intake.submit(sharedTarget, "동시 소진 답변 A", ModerationLanguage.KO, "int-002-a");
		intake.submit(sharedTarget, "동시 소진 답변 B", ModerationLanguage.KO, "int-002-b");
		AnswerModerationExecutionWorker workerA = executionWorker(failingPipeline(), 1);
		AnswerModerationExecutionWorker workerB = executionWorker(failingPipeline(), 1);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<AnswerModerationExecutionWorker.BatchResult>> futures = List.of(
			executor.submit(processAfterSignal(workerA, "int-002-worker-a", ready, start)),
			executor.submit(processAfterSignal(workerB, "int-002-worker-b", ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		futures.get(0).get(10, TimeUnit.SECONDS);
		futures.get(1).get(10, TimeUnit.SECONDS);

		ManualReviewCase opened = manualReviewCaseRepository.findByTargetAndFilterReleaseId(sharedTarget, releaseId)
			.orElseThrow();
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM manual_review_case WHERE target_type = 'ANSWER' AND target_id = 2",
			Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM notification_event WHERE case_id = ?", Integer.class, opened.id()))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("동시 claimDue는 정확히 하나의 worker만 event를 획득한다(INV-SLK-005 전송 시점 dedup)")
	void concurrentClaimDueGrantsExactlyOneWorker() throws Exception {
		ManualReviewCase reviewCase = openCase(filterJob(target(3L), "int-003"));
		notificationEventRepository.save(NotificationEvent.pending(reviewCase.id(), adminLinkPath(reviewCase.id()), NOW));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		Callable<List<NotificationEvent>> claimA = claimAfterSignal("int-003-worker-a", ready, start);
		Callable<List<NotificationEvent>> claimB = claimAfterSignal("int-003-worker-b", ready, start);
		Future<List<NotificationEvent>> futureA = executor.submit(claimA);
		Future<List<NotificationEvent>> futureB = executor.submit(claimB);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		List<NotificationEvent> resultA = futureA.get(10, TimeUnit.SECONDS);
		List<NotificationEvent> resultB = futureB.get(10, TimeUnit.SECONDS);

		int totalClaimed = resultA.size() + resultB.size();
		assertThat(totalClaimed).isEqualTo(1);
	}

	@Test
	@DisplayName("Slack 전송이 성공하면 notification_event만 PROCESSED로 바뀌고 case·job은 실행 전후 완전히 동일하다")
	void dispatchSuccessLeavesCaseAndJobUntouched() {
		FilterJob job = filterJob(target(4L), "int-004");
		ManualReviewCase reviewCase = openCase(job);
		notificationEventRepository.save(NotificationEvent.pending(reviewCase.id(), adminLinkPath(reviewCase.id()), NOW));
		Map<String, Object> caseBefore = jdbc.queryForMap("SELECT * FROM manual_review_case WHERE id = ?", reviewCase.id());
		Map<String, Object> jobBefore = jdbc.queryForMap("SELECT * FROM filter_job WHERE id = ?", job.id());
		SlackManualReviewNotificationDispatchWorker worker =
			new SlackManualReviewNotificationDispatchWorker(notificationEventRepository, SUCCEEDING_NOTIFIER,
				Clock.fixed(NOW, ZoneOffset.UTC));

		SlackManualReviewNotificationDispatchWorker.BatchResult result =
			worker.processBatch(new SlackManualReviewNotificationDispatchWorker.BatchCommand(
				10, "int-004-worker", NOW, NOW.plusSeconds(30), retryPolicy(3)));

		assertThat(result.outcomes())
			.containsExactly(SlackManualReviewNotificationDispatchWorker.Outcome.PROCESSED);
		NotificationEvent processed = notificationEventRepository.findByCaseId(reviewCase.id()).orElseThrow();
		assertThat(processed.status()).isEqualTo(NotificationEventStatus.PROCESSED);
		assertThat(jdbc.queryForMap("SELECT * FROM manual_review_case WHERE id = ?", reviewCase.id()))
			.isEqualTo(caseBefore);
		assertThat(jdbc.queryForMap("SELECT * FROM filter_job WHERE id = ?", job.id())).isEqualTo(jobBefore);
	}

	@Test
	@DisplayName("Slack 전송이 실패해 notification_event가 DEAD가 되어도 case·job은 실행 전후 완전히 동일하다(INV-SLK-002)")
	void dispatchFailureNeverTouchesCaseOrJob() {
		FilterJob job = filterJob(target(5L), "int-005");
		ManualReviewCase reviewCase = openCase(job);
		notificationEventRepository.save(NotificationEvent.pending(reviewCase.id(), adminLinkPath(reviewCase.id()), NOW));
		Map<String, Object> caseBefore = jdbc.queryForMap("SELECT * FROM manual_review_case WHERE id = ?", reviewCase.id());
		Map<String, Object> jobBefore = jdbc.queryForMap("SELECT * FROM filter_job WHERE id = ?", job.id());
		SlackNotifier failingNotifier = notification -> {
			throw new SlackDeliveryException(true, "stub network failure", null);
		};
		SlackManualReviewNotificationDispatchWorker worker =
			new SlackManualReviewNotificationDispatchWorker(notificationEventRepository, failingNotifier,
				Clock.fixed(NOW, ZoneOffset.UTC));

		SlackManualReviewNotificationDispatchWorker.BatchResult result =
			worker.processBatch(new SlackManualReviewNotificationDispatchWorker.BatchCommand(
				10, "int-005-worker", NOW, NOW.plusSeconds(30), retryPolicy(1)));

		assertThat(result.outcomes()).containsExactly(SlackManualReviewNotificationDispatchWorker.Outcome.DEAD);
		NotificationEvent dead = notificationEventRepository.findByCaseId(reviewCase.id()).orElseThrow();
		assertThat(dead.status()).isEqualTo(NotificationEventStatus.DEAD);
		assertThat(jdbc.queryForMap("SELECT * FROM manual_review_case WHERE id = ?", reviewCase.id()))
			.isEqualTo(caseBefore);
		assertThat(jdbc.queryForMap("SELECT * FROM filter_job WHERE id = ?", job.id())).isEqualTo(jobBefore);
	}

	@Test
	@DisplayName("만료된 lease는 claimDue가 재claim해 attempt_count와 lease_generation을 갱신한다")
	void claimDueReclaimsExpiredLease() {
		ManualReviewCase reviewCase = openCase(filterJob(target(6L), "int-006"));
		notificationEventRepository.save(NotificationEvent.pending(reviewCase.id(), adminLinkPath(reviewCase.id()), NOW));
		List<NotificationEvent> firstClaim =
			notificationEventRepository.claimDue(1, "int-006-worker-a", NOW, NOW.plusSeconds(1));
		assertThat(firstClaim).hasSize(1);

		Instant afterExpiry = NOW.plusSeconds(10);
		List<NotificationEvent> reclaimed =
			notificationEventRepository.claimDue(1, "int-006-worker-b", afterExpiry, afterExpiry.plusSeconds(30));

		assertThat(reclaimed).hasSize(1);
		assertThat(reclaimed.get(0).leaseOwner()).isEqualTo("int-006-worker-b");
		assertThat(reclaimed.get(0).attemptCount()).isEqualTo(2);
		assertThat(reclaimed.get(0).leaseGeneration()).isEqualTo(2);
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

	private Callable<List<NotificationEvent>> claimAfterSignal(String owner, CountDownLatch ready, CountDownLatch start) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("claim start barrier timed out");
			}
			return notificationEventRepository.claimDue(1, owner, NOW, NOW.plusSeconds(30));
		};
	}

	private ManualReviewCase openCase(FilterJob job) {
		ManualReviewPriorityDecision decision =
			new ManualReviewPriorityDecision(ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.DEFAULT);
		return manualReviewCaseRepository.save(
			ManualReviewCase.open(job.target(), releaseId, job.id(), decision, 0, "v1", NOW));
	}

	private FilterJob filterJob(FilterTarget target, String idempotencyKey) {
		return filterJobRepository.save(
			FilterJob.create(target, releaseId, idempotencyKey, NOW.plusSeconds(600), NOW));
	}

	private static String adminLinkPath(long caseId) {
		return "/admin/filtering/manual-review-cases/" + caseId;
	}

	private static NotificationRetryPolicy retryPolicy(int maxAttempts) {
		return new NotificationRetryPolicy(maxAttempts, attempt -> Duration.ofSeconds(30));
	}

	private AnswerModerationJobIntakeService intakeService() {
		return new AnswerModerationJobIntakeService(filterJobRepository, filterReleaseRepository, historyRepository,
			outboxEventRepository, objectMapper, Duration.ofMinutes(10), transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private AnswerModerationExecutionWorker executionWorker(ModerationPipelineService pipeline, int maxAttempts) {
		AnswerModerationRetryPolicy retryPolicy =
			new AnswerModerationRetryPolicy(FAST_BACKOFF, SLOW_BACKOFF, maxAttempts, Duration.ofHours(1));
		return new AnswerModerationExecutionWorker(pipeline, filterJobRepository, filterReleaseRepository,
			historyRepository, outboxEventRepository, retryPolicy, filterReleaseRetryGateRepository, GATE_CONFIG,
			manualReviewCaseRepository, manualReviewPriorityEvaluationRepository, MANUAL_REVIEW_PRIORITY_POLICY,
			notificationEventRepository, Duration.ofSeconds(5), objectMapper, pipelineExecutor, Duration.ofSeconds(5),
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
		releaseRegistryService.markOfflineEvaluated(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		releaseRegistryService.designateShadow(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		releaseRegistryService.designateCanary(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		return releaseRegistryService.promote(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거")).id();
	}
}
