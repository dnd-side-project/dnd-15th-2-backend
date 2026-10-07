/**
 * Created at: 2026-10-07T22:17:35+09:00
 * Source scenario: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-001 through INT-011, INT-013
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.AnswerModerationDeadlineWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationEventPayloadsTestSupport;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationRetryPolicy;
import com.dnd.qello.filtering.moderation.AnswerModerationVerdictWorker;
import com.dnd.qello.filtering.moderation.LocalRuleVerdict;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.moderation.ModerationProviderResult;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.filtering.repository.ManualReviewPriorityEvaluationRepository;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.NotificationEventRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #137 질문글 moderation 연결을 실제 PostgreSQL에서 관통한다. 제출(intake) → 실행 worker(파이프라인
 * double) → 판정 worker(Spring bean, 대상별 적용기 분배) → 매칭 worker 순서로, 각 단계가 실제로 만든 행만
 * 다음 단계 입력으로 쓴다. 중복·늦은 판정은 같은 job의 VERDICT_READY·DEADLINE_ELAPSED를 직접 넣어 재전달을
 * 흉내 낸다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(DirectionPostModeration137TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionPostModerationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-MODERATION-137";
	private static final Instant NOW = Instant.parse("2026-10-07T03:00:00Z");
	private static final Instant EXPIRES_AT = NOW.plusSeconds(3600);
	private static final OutboxRetryPolicy MATCHING_RETRY = new OutboxRetryPolicy(5, attempt -> Duration.ofSeconds(1));
	private static final RetryGateConfig GATE_CONFIG = new RetryGateConfig(3, 2, 2, 2, 6);
	private static final OutboxBackoffStrategy BACKOFF = attempt -> Duration.ofSeconds(60);
	private static final ManualReviewPriorityPolicy MANUAL_REVIEW_PRIORITY_POLICY = new ManualReviewPriorityPolicy(3,
			Duration.ofHours(24), "test-v1");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private DirectionPostService postService;
	@Autowired
	private DirectionMatchingWorker matchingWorker;
	@Autowired
	private AnswerModerationVerdictWorker verdictWorker;
	@Autowired
	private AnswerModerationDeadlineWorker deadlineWorker;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private DirectionSchemeRepository schemeRepository;
	@Autowired
	private OutboxEventRepository outboxEventRepository;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private FilterJobRepository filterJobRepository;
	@Autowired
	private FilterReleaseRepository filterReleaseRepository;
	@Autowired
	private FilterJobStatusHistoryRepository historyRepository;
	@Autowired
	private FilterDecisionRepository filterDecisionRepository;
	@Autowired
	private FilterReleaseRetryGateRepository retryGateRepository;
	@Autowired
	private ManualReviewCaseRepository manualReviewCaseRepository;
	@Autowired
	private ManualReviewPriorityEvaluationRepository priorityEvaluationRepository;
	@Autowired
	private NotificationEventRepository notificationEventRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private DirectionPostModeration137MutableClock clock;

	private ExecutorService pipelineExecutor;
	private ExecutorService executor;

	@BeforeEach
	void reset() {
		clock.setInstant(NOW);
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		// 본문 없는 질문글의 유일한 미디어를 먼저 떼면 deferred trigger가 정리를 막는다. 질문글을 먼저 지워
		// media_attachment가 ON DELETE CASCADE로 함께 사라지게
		// 한다(MediaAttachmentIntegrationTest와 같은 순서).
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		deleteFilteringState();
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Moderation 137', 'REGION')",
				REGION);
		AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, 1L);
		pipelineExecutor = Executors.newFixedThreadPool(2);
		executor = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	void tearDown() {
		pipelineExecutor.shutdownNow();
		executor.shutdownNow();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-001: 본문 있는 질문글 제출은 공급자 호출 없이 PENDING 질문글·질문글 job·실행 요청·매칭 요청을 한 번에 커밋한다")
	void bodyPostCommitsJobWithPost() {
		Fixture fixture = fixture(1);

		long postId = send(fixture, "int001", "판정 대기 본문", List.of());

		assertThat(moderationStatus(postId)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM filter_job WHERE target_type = 'DIRECTION_POST' AND target_id = ? AND status = 'AUTOMATED'",
				Long.class, postId)).isEqualTo(1L);
		assertThat(
				jdbc.queryForObject("SELECT idempotency_key FROM filter_job WHERE target_id = ?", String.class, postId))
				.isEqualTo("direction-post-moderation:" + postId);
		assertThat(outboxCount(OutboxEventType.MODERATION_EXECUTION_REQUESTED)).isEqualTo(1L);
		assertThat(outboxCount(OutboxEventType.RECIPIENT_MATCH_REQUESTED)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM filter_decision", Long.class)).isZero();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-002: 미디어 단독 질문글은 job 없이 PASSED로 커밋되고 매칭 대상이 된다")
	void mediaOnlyPostPassesWithoutJobAndMatches() {
		Fixture fixture = fixture(1);
		long mediaId = readyMedia(fixture.senderId());

		long postId = send(fixture, "int002", null, List.of(mediaId));

		assertThat(moderationStatus(postId)).isEqualTo("PASSED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM filter_job", Long.class)).isZero();
		assertThat(outboxCount(OutboxEventType.MODERATION_EXECUTION_REQUESTED)).isZero();
		assertThat(match(NOW.plusSeconds(1))).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(postStatus(postId)).isEqualTo("ACTIVE");
		assertThat(recipientCount(postId)).isEqualTo(1L);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-003: 승격된 release가 없으면 제출이 NO_ACTIVE_RELEASE로 실패하고 질문글·audience·Outbox·첨부가 모두 rollback된다")
	void missingReleaseRollsBackWholeSubmission() {
		deleteFilteringState();
		Fixture fixture = fixture(1);

		assertThatThrownBy(() -> send(fixture, "int003", "release 없는 본문", List.of()))
				.isInstanceOf(FilteringException.class)
				.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.NO_ACTIVE_RELEASE);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_audience", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM filter_job", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM media_attachment", Long.class)).isZero();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-004: V32 이후 filter_job·manual_review_case는 DIRECTION_POST를 받고 appeal_case는 계속 거절한다")
	void targetTypeConstraintsFollowV32() {
		assertThat(constraintDefinition("ck_filter_job_target_type")).contains("'ANSWER'", "'NICKNAME'",
				"'DIRECTION_POST'");
		assertThat(constraintDefinition("ck_manual_review_case_target_type"))
				.contains("'ANSWER'", "'NICKNAME'", "'DIRECTION_POST'");
		assertThat(constraintDefinition("ck_appeal_case_target_type")).contains("'ANSWER'", "'NICKNAME'")
				.doesNotContain("DIRECTION_POST");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-005: ALLOW 판정은 실행·판정 worker를 거쳐 질문글을 PASSED로 만들고 다음 매칭 시도에서 수신자가 확정된다")
	void allowFlowsThroughWorkersIntoMatching() {
		Fixture fixture = fixture(3);
		long postId = send(fixture, "int005", "허용될 본문", List.of());
		AtomicInteger providerCalls = new AtomicInteger();

		assertThat(match(NOW.plusSeconds(1))).containsExactly(DirectionMatchingWorker.Outcome.RETRYABLE);
		assertThat(recipientCount(postId)).isZero();

		assertThat(execute(pipeline(FilterVerdict.ALLOW, providerCalls), 3))
				.containsExactly(AnswerModerationExecutionWorker.Outcome.RESOLVED);
		assertThat(providerCalls).hasValue(1);
		assertThat(applyVerdicts()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(moderationStatus(postId)).isEqualTo("PASSED");
		assertThat(postStatus(postId)).isEqualTo("MATCHING");

		assertThat(match(NOW.plusSeconds(10))).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(postStatus(postId)).isEqualTo("ACTIVE");
		assertThat(recipientCount(postId)).isEqualTo(3L);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-006: BLOCK 판정은 질문글을 REJECTED로 만들고 status는 MATCHING인 채 수신자·슬롯 없이 매칭 이벤트를 끝낸다")
	void blockLeavesPostUnmatched() {
		Fixture fixture = fixture(2);
		long postId = send(fixture, "int006", "차단될 본문", List.of());

		assertThat(execute(pipeline(FilterVerdict.BLOCK, new AtomicInteger()), 3))
				.containsExactly(AnswerModerationExecutionWorker.Outcome.RESOLVED);
		assertThat(applyVerdicts()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(moderationStatus(postId)).isEqualTo("REJECTED");

		assertThat(match(NOW.plusSeconds(1))).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(postStatus(postId)).isEqualTo("MATCHING");
		assertThat(recipientCount(postId)).isZero();
		assertThat(jdbc.queryForObject("SELECT coalesce(sum(active_unhandled_count), 0) FROM recipient_receive_state",
				Long.class)).isZero();
		assertThat(matchingEventStatus(postId)).isEqualTo("PROCESSED");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-007: PASSED 뒤 다시 전달된 ALLOW와 늦게 온 BLOCK은 상태·수신자·Outbox를 바꾸지 않는다")
	void duplicateAndOppositeVerdictsDoNotRevert() {
		Fixture fixture = fixture(2);
		long postId = send(fixture, "int007", "중복 판정 본문", List.of());
		long jobId = jobId(postId);
		seedVerdict(jobId, postId, FilterVerdict.ALLOW, "first");
		assertThat(applyVerdicts()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);

		seedVerdict(jobId, postId, FilterVerdict.ALLOW, "redelivered");
		seedVerdict(jobId, postId, FilterVerdict.BLOCK, "late-opposite");
		assertThat(applyVerdicts()).containsOnly(AnswerModerationVerdictWorker.Outcome.RESOLVED).hasSize(2);
		assertThat(moderationStatus(postId)).isEqualTo("PASSED");

		assertThat(match(NOW.plusSeconds(1))).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		long confirmedEvents = outboxCount(OutboxEventType.RECIPIENTS_CONFIRMED);
		seedVerdict(jobId, postId, FilterVerdict.BLOCK, "after-active");
		assertThat(applyVerdicts()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);

		assertThat(moderationStatus(postId)).isEqualTo("PASSED");
		assertThat(postStatus(postId)).isEqualTo("ACTIVE");
		assertThat(recipientCount(postId)).isEqualTo(2L);
		assertThat(outboxCount(OutboxEventType.RECIPIENTS_CONFIRMED)).isEqualTo(confirmedEvents);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-008: ALLOW 반영 뒤 처리된 같은 job의 DEADLINE_ELAPSED는 PASSED를 REVIEW_HELD로 되돌리지 않는다")
	void lateDeadlineDoesNotRevertPassed() {
		Fixture fixture = fixture(1);
		long postId = send(fixture, "int008", "deadline 역전 본문", List.of());
		long jobId = jobId(postId);
		seedVerdict(jobId, postId, FilterVerdict.ALLOW, "allow");
		applyVerdicts();

		seedDeadline(jobId, postId, "late-deadline");
		assertThat(applyVerdicts()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);

		assertThat(moderationStatus(postId)).isEqualTo("PASSED");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-009: deadline이 지나면 REVIEW_HELD로 매칭이 보류되고, 이후 수동 검토 ALLOW가 오면 PASSED가 되어 수신자가 확정된다")
	void deadlineHoldsThenManualAllowResumesMatching() {
		Fixture fixture = fixture(2);
		long postId = send(fixture, "int009", "검토 보류 본문", List.of());

		Instant afterDeadline = NOW.plus(Duration.ofMinutes(6));
		clock.setInstant(afterDeadline);
		deadlineWorker.processBatch(10, afterDeadline);
		assertThat(applyVerdicts(afterDeadline)).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(moderationStatus(postId)).isEqualTo("REVIEW_HELD");

		assertThat(match(afterDeadline.plusSeconds(1))).containsExactly(DirectionMatchingWorker.Outcome.RETRYABLE);
		assertThat(recipientCount(postId)).isZero();

		seedVerdict(jobId(postId), postId, FilterVerdict.ALLOW, "manual-allow");
		assertThat(applyVerdicts(afterDeadline)).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(moderationStatus(postId)).isEqualTo("PASSED");
		assertThat(match(afterDeadline.plusSeconds(10))).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(recipientCount(postId)).isEqualTo(2L);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-010: 만료로 닫힌 질문글에 늦게 온 ALLOW는 moderation_status를 바꾸지 않고 매칭 가능하게 만들지 않는다")
	void lateAllowDoesNotReopenExpiredPost() {
		Fixture fixture = fixture(1);
		long postId = send(fixture, "int010", "만료될 본문", List.of());
		Instant afterExpiry = EXPIRES_AT.plusSeconds(60);

		assertThat(match(afterExpiry)).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(postStatus(postId)).isEqualTo("EXPIRED");

		clock.setInstant(afterExpiry);
		seedVerdict(jobId(postId), postId, FilterVerdict.ALLOW, "late-allow");
		assertThat(applyVerdicts(afterExpiry)).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);

		assertThat(moderationStatus(postId)).isEqualTo("PENDING");
		assertThat(postStatus(postId)).isEqualTo("EXPIRED");
		assertThat(recipientCount(postId)).isZero();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-011: 판정 적용과 매칭 worker가 같은 질문글을 동시에 처리해도 PASSED 전 수신자는 없고 최종 수신자는 한 번만 확정된다")
	void concurrentVerdictAndMatchingStayConsistent() throws Exception {
		Fixture fixture = fixture(3);
		long postId = send(fixture, "int011", "동시 처리 본문", List.of());
		seedVerdict(jobId(postId), postId, FilterVerdict.ALLOW, "concurrent");
		CountDownLatch start = new CountDownLatch(1);

		Future<List<DirectionMatchingWorker.Outcome>> matching = executor.submit(awaiting(start,
				() -> match(NOW.plusSeconds(1))));
		Future<List<AnswerModerationVerdictWorker.Outcome>> verdict = executor.submit(awaiting(start,
				this::applyVerdicts));
		start.countDown();

		List<DirectionMatchingWorker.Outcome> matchingOutcome = matching.get(30, TimeUnit.SECONDS);
		assertThat(verdict.get(30, TimeUnit.SECONDS)).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(moderationStatus(postId)).isEqualTo("PASSED");
		if (matchingOutcome.contains(DirectionMatchingWorker.Outcome.RETRYABLE)) {
			assertThat(recipientCount(postId)).isZero();
			assertThat(match(NOW.plusSeconds(10))).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		} else {
			assertThat(matchingOutcome).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		}
		assertThat(postStatus(postId)).isEqualTo("ACTIVE");
		assertThat(recipientCount(postId)).isEqualTo(3L);
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT recipient_id) FROM post_recipient WHERE post_id = ?",
				Long.class, postId)).isEqualTo(3L);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-013: 실행 재시도를 소진한 질문글 job은 DIRECTION_POST 수동 검토 case가 되고 질문글은 PENDING으로 남는다")
	void exhaustedJobHandsOffToManualReview() {
		Fixture fixture = fixture(1);
		long postId = send(fixture, "int013", "재시도 소진 본문", List.of());

		assertThat(execute(failingPipeline(), 1))
				.containsExactly(AnswerModerationExecutionWorker.Outcome.RETRY_EXHAUSTED);

		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM manual_review_case WHERE target_type = 'DIRECTION_POST' AND target_id = ?",
				Long.class, postId)).isEqualTo(1L);
		assertThat(moderationStatus(postId)).isEqualTo("PENDING");
		assertThat(outboxCount(OutboxEventType.MODERATION_VERDICT_READY)).isZero();
	}

	// -- workers -------------------------------------------------------------

	private List<DirectionMatchingWorker.Outcome> match(Instant at) {
		return matchingWorker.processBatch(new DirectionMatchingWorker.BatchCommand(10, "int137-matching", at,
				at.plusSeconds(60), MATCHING_RETRY)).outcomes();
	}

	private List<AnswerModerationVerdictWorker.Outcome> applyVerdicts() {
		return applyVerdicts(NOW);
	}

	private List<AnswerModerationVerdictWorker.Outcome> applyVerdicts(Instant at) {
		return verdictWorker.processBatch(new AnswerModerationVerdictWorker.BatchCommand(10, "int137-verdict", at,
				at.plusSeconds(60))).outcomes();
	}

	private List<AnswerModerationExecutionWorker.Outcome> execute(ModerationPipelineService pipeline, int maxAttempts) {
		AnswerModerationExecutionWorker worker = new AnswerModerationExecutionWorker(pipeline, filterJobRepository,
				filterReleaseRepository, historyRepository, outboxEventRepository,
				new AnswerModerationRetryPolicy(BACKOFF, BACKOFF, maxAttempts, Duration.ofHours(1)),
				retryGateRepository,
				GATE_CONFIG, manualReviewCaseRepository, priorityEvaluationRepository, MANUAL_REVIEW_PRIORITY_POLICY,
				notificationEventRepository, Duration.ofSeconds(5), objectMapper, pipelineExecutor,
				Duration.ofSeconds(5),
				transactionManager, clock);
		return worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(10, "int137-execution", NOW,
				NOW.plusSeconds(60))).outcomes();
	}

	private ModerationPipelineService pipeline(FilterVerdict verdict, AtomicInteger providerCalls) {
		return new ModerationPipelineService(
				(rawContent, normalizationRef) -> rawContent,
				(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
				(normalizedContent, modelSnapshot) -> {
					providerCalls.incrementAndGet();
					return new ModerationProviderResult(verdict == FilterVerdict.BLOCK, Map.of(), Map.of(),
							modelSnapshot);
				},
				(providerResult, contentType, language, categoryMappingRef) -> verdict,
				filterDecisionRepository, clock);
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
				filterDecisionRepository, clock);
	}

	private static <T> Callable<T> awaiting(CountDownLatch start, Callable<T> task) {
		return () -> {
			start.await(10, TimeUnit.SECONDS);
			return task.call();
		};
	}

	// -- moderation events ---------------------------------------------------

	private void seedVerdict(long jobId, long postId, FilterVerdict verdict, String suffix) {
		outboxEventRepository.save(OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, jobId,
				OutboxEventType.MODERATION_VERDICT_READY, "int137-filter-job:" + jobId + ":" + suffix,
				AnswerModerationEventPayloadsTestSupport.verdictReadyJson(objectMapper, jobId,
						FilterTargetType.DIRECTION_POST, postId, verdict),
				clock.instant()));
	}

	private void seedDeadline(long jobId, long postId, String suffix) {
		outboxEventRepository.save(OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, jobId,
				OutboxEventType.MODERATION_DEADLINE_ELAPSED, "int137-filter-job:" + jobId + ":" + suffix,
				AnswerModerationEventPayloadsTestSupport.deadlineElapsedJson(objectMapper, jobId,
						FilterTargetType.DIRECTION_POST, postId),
				clock.instant()));
	}

	// -- queries ---------------------------------------------------------------

	private long jobId(long postId) {
		return jdbc.queryForObject("SELECT id FROM filter_job WHERE target_type = 'DIRECTION_POST' AND target_id = ?",
				Long.class, postId);
	}

	private String moderationStatus(long postId) {
		return jdbc.queryForObject("SELECT moderation_status FROM direction_post WHERE id = ?", String.class, postId);
	}

	private String postStatus(long postId) {
		return jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId);
	}

	private long recipientCount(long postId) {
		return jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId);
	}

	private String matchingEventStatus(long postId) {
		return jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId);
	}

	private long outboxCount(OutboxEventType eventType) {
		return jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type = ?", Long.class,
				eventType.name());
	}

	private String constraintDefinition(String name) {
		return jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
				String.class, name);
	}

	// -- fixtures --------------------------------------------------------------

	private void deleteFilteringState() {
		jdbc.update("DELETE FROM filter_decision");
		jdbc.update("DELETE FROM filter_job_status_history");
		jdbc.update("DELETE FROM manual_review_priority_evaluation");
		jdbc.update("DELETE FROM notification_event");
		jdbc.update("DELETE FROM manual_review_case");
		jdbc.update("DELETE FROM filter_release_retry_gate");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM release_promotion_history");
		jdbc.update("DELETE FROM filter_release");
	}

	private long send(Fixture fixture, String key, String body, List<Long> mediaIds) {
		return postService.send(new DirectionPostService.SendCommand(fixture.senderId(), fixture.questionId(),
				fixture.schemeId(), "S0", 0, 5_000, REGION, key, body, mediaIds, NOW.minusSeconds(60), EXPIRES_AT))
				.post().getId();
	}

	private Fixture fixture(int candidateCount) {
		long senderId = account("int137-sender");
		long questionId = activeQuestion(senderId);
		long schemeId = eightSegmentScheme();
		presence(senderId, 37.5000);
		List<Long> candidates = IntStream.range(0, candidateCount)
				.mapToObj(index -> account("int137-candidate-" + index))
				.toList();
		IntStream.range(0, candidateCount).forEach(index -> presence(candidates.get(index), 37.5010 + index * 0.0001));
		return new Fixture(senderId, questionId, schemeId);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
				""", Long.class, REGION, nickname + "-" + System.nanoTime());
	}

	private long activeQuestion(long approverId) {
		return jdbc.queryForObject(
				"""
						INSERT INTO approved_question (source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
						VALUES ('OPERATOR', 'ACTIVE', 'moderation 137 question', 'TEXT', ?, ?, ?, ?) RETURNING id
						""",
				Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
				Timestamp.from(NOW.minusSeconds(120)), approverId);
	}

	private long eightSegmentScheme() {
		DirectionScheme scheme = schemeRepository.save(
				DirectionScheme.createEqual("TEST-MODERATION-" + System.nanoTime(), 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(),
				"S" + index, "moderation-segment-" + index, BigDecimal.valueOf(index * 45L + 22.5),
				BigDecimal.valueOf(45),
				index)));
		return scheme.getId();
	}

	private void presence(long userId, double latitude) {
		presenceRepository.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(latitude),
				BigDecimal.valueOf(127.0000), null, REGION, BigDecimal.ONE, true, NOW.minusSeconds(120),
				NOW.plus(Duration.ofHours(24))));
	}

	private long readyMedia(long ownerId) {
		return jdbc.queryForObject("""
				INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum)
				VALUES (?, 'READY', ?, 'image/png', 128, 'int137-checksum')
				RETURNING id
				""", Long.class, ownerId, "media/" + ownerId + "/int137-" + System.nanoTime());
	}

	private record Fixture(long senderId, long questionId, long schemeId) {
	}
}

@TestConfiguration
class DirectionPostModeration137TestClockConfiguration {

	@Bean
	@Primary
	DirectionPostModeration137MutableClock directionPostModeration137MutableClock() {
		return new DirectionPostModeration137MutableClock(Instant.parse("2026-10-07T03:00:00Z"), ZoneOffset.UTC);
	}
}

final class DirectionPostModeration137MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	DirectionPostModeration137MutableClock(Instant initial, ZoneId zone) {
		this.current = new AtomicReference<>(initial);
		this.zone = zone;
	}

	void setInstant(Instant instant) {
		current.set(instant);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId newZone) {
		return new DirectionPostModeration137MutableClock(current.get(), newZone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}
}
