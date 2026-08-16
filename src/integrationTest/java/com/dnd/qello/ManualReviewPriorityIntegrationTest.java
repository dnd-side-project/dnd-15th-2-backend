/**
 * Created at: 2026-08-17T00:30:00+09:00
 * Source scenario: TEST-PLAN-GH-110-MANUAL-REVIEW-PRIORITY-INT-001 through INT-008
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewPriorityDecision;
import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationJobIntakeService;
import com.dnd.qello.filtering.moderation.AnswerModerationRetryPolicy;
import com.dnd.qello.filtering.moderation.LocalRuleVerdict;
import com.dnd.qello.filtering.moderation.ManualReviewDecisionService;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

// #110: 검토자 결정 트랜잭션 원자성, 수동·자동 authority 경합, 늦은 자동 결과의
// 감사 기록, 큐 정렬, priority 재평가 이력, REST endpoint 인가, 기존 유일성
// 회귀를 실제 PostgreSQL 위에서 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManualReviewPriorityIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final String MODEL_SNAPSHOT = "omni-moderation-2026-08-01";
	private static final ManualReviewPriorityPolicy POLICY =
		new ManualReviewPriorityPolicy(3, Duration.ofHours(24), "test-v1");
	private static final RetryGateConfig GATE_CONFIG = new RetryGateConfig(3, 2, 2, 2, 6);
	private static final String LOGIN_ID = "qello-admin";
	private static final String PASSWORD = "example-operator-password";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private OperatorSeedService operatorSeedService;
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
	private ManualReviewDecisionService manualReviewDecisionService;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private long releaseId;
	private ExecutorService executor;
	private ExecutorService pipelineExecutor;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM manual_review_priority_evaluation");
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'FILTER_JOB'");
		jdbc.update("DELETE FROM filter_decision");
		jdbc.update("DELETE FROM filter_job_status_history");
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
	@DisplayName("검토자 결정은 job 전이·outbox 발행·history 기록·case 종료를 한 트랜잭션으로 반영한다")
	void decisionAppliesAtomically() {
		FilterJob job = filterJobRepository.save(
			FilterJob.create(target(1L), releaseId, "decide-atomic-1", NOW.plusSeconds(600), NOW))
			.exhaustRetries(NOW);
		filterJobRepository.save(job.openManualReview(NOW));
		ManualReviewCase reviewCase = openCase(job);

		ManualReviewCase resolved = manualReviewDecisionService.decide(reviewCase.id(), FilterVerdict.ALLOW, 1L);

		assertThat(resolved.status().name()).isEqualTo("RESOLVED");
		FilterJob resolvedJob = filterJobRepository.findById(job.id()).orElseThrow();
		assertThat(resolvedJob.status()).isEqualTo(FilterJobStatus.RESOLVED);
		assertThat(resolvedJob.resolvedVerdict()).isEqualTo(FilterVerdict.ALLOW);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'MODERATION_VERDICT_READY' AND aggregate_id = ?",
			Integer.class, job.id())).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM filter_job_status_history WHERE filter_job_id = ? AND to_status = 'RESOLVED'",
			Integer.class, job.id())).isEqualTo(1);
	}

	@Test
	@DisplayName("검토자 수동 결정과 자동 결과 적용이 동시에 경합해도 job은 정확히 하나의 판정으로 수렴한다")
	void concurrentManualAndAutomatedResolutionConverge() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob submitted = intake.submit(target(2L), "동시 경합 답변", ModerationLanguage.KO, "race-1");
		FilterJob manualReviewRequired = filterJobRepository.save(
			filterJobRepository.findById(submitted.id()).orElseThrow().exhaustRetries(NOW).openManualReview(NOW));
		ManualReviewCase reviewCase = openCase(manualReviewRequired);
		AnswerModerationExecutionWorker worker = executionWorker(allowPipeline());

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<ManualReviewCase> manualDecision = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return manualReviewDecisionService.decide(reviewCase.id(), FilterVerdict.BLOCK, 1L);
		};
		Callable<AnswerModerationExecutionWorker.BatchResult> automatedDecision = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(
				10, "race-worker", NOW, NOW.plusSeconds(30)));
		};

		Future<ManualReviewCase> manualFuture = executor.submit(manualDecision);
		Future<AnswerModerationExecutionWorker.BatchResult> automatedFuture = executor.submit(automatedDecision);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		manualFuture.get(10, TimeUnit.SECONDS);
		automatedFuture.get(10, TimeUnit.SECONDS);

		FilterJob finalJob = filterJobRepository.findById(manualReviewRequired.id()).orElseThrow();
		assertThat(finalJob.status()).isEqualTo(FilterJobStatus.RESOLVED);
		ManualReviewCase finalCase = manualReviewCaseRepository.findById(reviewCase.id()).orElseThrow();
		assertThat(finalCase.status().name()).isEqualTo("RESOLVED");
		// 어느 경로가 이겼든 case의 최종 판정은 job의 최종 판정과 항상 일치해야 한다.
		assertThat(finalCase.resolvedVerdict()).isEqualTo(finalJob.resolvedVerdict());
	}

	@Test
	@DisplayName("이미 수동으로 종결된 job에 늦게 도착한 자동 처리 시도는 감사 기록만 남기고 상태를 바꾸지 않는다")
	void lateAutomatedAttemptAfterManualResolutionIsAudited() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob submitted = intake.submit(target(3L), "늦은 자동 처리 답변", ModerationLanguage.KO, "late-automated-1");
		FilterJob manualReviewRequired = filterJobRepository.save(
			filterJobRepository.findById(submitted.id()).orElseThrow().exhaustRetries(NOW).openManualReview(NOW));
		ManualReviewCase reviewCase = openCase(manualReviewRequired);
		manualReviewDecisionService.decide(reviewCase.id(), FilterVerdict.BLOCK, 1L);

		AnswerModerationExecutionWorker worker = executionWorker(allowPipeline());
		worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(
			10, "late-worker", NOW.plusSeconds(5), NOW.plusSeconds(35)));

		FilterJob finalJob = filterJobRepository.findById(manualReviewRequired.id()).orElseThrow();
		assertThat(finalJob.resolvedVerdict()).isEqualTo(FilterVerdict.BLOCK);
		assertThat(finalJob.manuallyResolved()).isTrue();
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM filter_job_status_history
			WHERE filter_job_id = ? AND reason LIKE 'late automated result ignored%'
			""", Integer.class, manualReviewRequired.id())).isEqualTo(1);
	}

	@Test
	@DisplayName("검토자 큐는 effectiveBand 내림차순과 band 내 FIFO로 정렬돼 반환된다")
	void queueOrdersByEffectiveBandAndFifo() {
		FilterJob jobA = filterJob(target(4L), "queue-a");
		FilterJob jobB = filterJob(target(5L), "queue-b");
		FilterJob jobC = filterJob(target(6L), "queue-c");
		ManualReviewCase standardOld = saveCase(jobA, ManualReviewBand.STANDARD, NOW.minusSeconds(30));
		ManualReviewCase standardNew = saveCase(jobB, ManualReviewBand.STANDARD, NOW.minusSeconds(10));
		ManualReviewCase highCase = saveCase(jobC, ManualReviewBand.HIGH, NOW.minusSeconds(5));

		List<ManualReviewCase> queue =
			manualReviewDecisionService.findQueue(POLICY.agingThreshold(), 50);

		assertThat(queue).extracting(ManualReviewCase::id)
			.containsExactly(highCase.id(), standardOld.id(), standardNew.id());
	}

	@Test
	@DisplayName("case open 시 priority 평가가 append-only 이력으로 기록된다")
	void priorityEvaluationIsRecordedOnCaseOpen() throws Exception {
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob submitted = intake.submit(target(7L), "평가 이력 답변", ModerationLanguage.KO, "eval-history-1");
		AnswerModerationExecutionWorker worker = executionWorker(failingPipeline());

		worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(
			10, "eval-worker", NOW, NOW.plusSeconds(30)));

		ManualReviewCase opened = manualReviewCaseRepository.findByTargetAndFilterReleaseId(target(7L), releaseId)
			.orElseThrow();
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM manual_review_priority_evaluation WHERE manual_review_case_id = ?",
			Integer.class, opened.id())).isEqualTo(1);
	}

	@Test
	@DisplayName("인증된 운영자는 검토자 endpoint로 case를 종료할 수 있다")
	void operatorDecidesThroughEndpoint() throws Exception {
		FilterJob job = filterJobRepository.save(
			FilterJob.create(target(8L), releaseId, "endpoint-decide-1", NOW.plusSeconds(600), NOW)
				.exhaustRetries(NOW));
		filterJobRepository.save(job.openManualReview(NOW));
		ManualReviewCase reviewCase = openCase(job);
		OperatorSession session = login();

		mockMvc.perform(withCsrf(withSession(post(
				"/admin/filtering/manual-review-cases/%d/decide".formatted(reviewCase.id())), session), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"verdict\":\"ALLOW\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RESOLVED"))
			.andExpect(jsonPath("$.data.resolvedVerdict").value("ALLOW"));
	}

	@Test
	@DisplayName("세션이 없으면 검토자 결정 endpoint는 401로 거절된다")
	void rejectsUnauthenticatedDecision() throws Exception {
		FilterJob job = filterJobRepository.save(
			FilterJob.create(target(9L), releaseId, "endpoint-unauth-1", NOW.plusSeconds(600), NOW)
				.exhaustRetries(NOW));
		filterJobRepository.save(job.openManualReview(NOW));
		ManualReviewCase reviewCase = openCase(job);
		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode csrfData = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");

		mockMvc.perform(post("/admin/filtering/manual-review-cases/%d/decide".formatted(reviewCase.id()))
				.header(csrfData.get("headerName").asText(), csrfData.get("token").asText())
				.cookie(issued.getResponse().getCookies())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"verdict\":\"ALLOW\"}"))
			.andExpect(status().isUnauthorized());

		assertThat(manualReviewCaseRepository.findById(reviewCase.id()).orElseThrow().status().name())
			.isEqualTo("OPEN");
	}

	@Test
	@DisplayName("동일 대상·release의 ManualReviewCase 동시 생성은 하나만 성공한다(기존 유일성 회귀)")
	void concurrentCaseCreationKeepsUniqueness() throws Exception {
		FilterJob jobA = filterJob(target(10L), "unique-race-a");
		FilterJob jobB = filterJob(target(10L), "unique-race-b");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ManualReviewPriorityDecision decision =
			new ManualReviewPriorityDecision(ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.DEFAULT);

		Callable<Boolean> attemptA = () -> attemptOpen(jobA, decision, ready, start);
		Callable<Boolean> attemptB = () -> attemptOpen(jobB, decision, ready, start);
		Future<Boolean> futureA = executor.submit(attemptA);
		Future<Boolean> futureB = executor.submit(attemptB);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		boolean successA = futureA.get(10, TimeUnit.SECONDS);
		boolean successB = futureB.get(10, TimeUnit.SECONDS);

		assertThat(successA ^ successB).isTrue();
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM manual_review_case WHERE target_type = 'ANSWER' AND target_id = 10",
			Integer.class)).isEqualTo(1);
	}

	private boolean attemptOpen(
		FilterJob job, ManualReviewPriorityDecision decision, CountDownLatch ready, CountDownLatch start
	) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		try {
			manualReviewCaseRepository.save(
				ManualReviewCase.open(job.target(), releaseId, job.id(), decision, 0, "v1", NOW));
			return true;
		} catch (org.springframework.dao.DataIntegrityViolationException raceAlreadyOpened) {
			return false;
		}
	}

	private ManualReviewCase openCase(FilterJob job) {
		ManualReviewPriorityDecision decision =
			new ManualReviewPriorityDecision(ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.DEFAULT);
		return manualReviewCaseRepository.save(
			ManualReviewCase.open(job.target(), releaseId, job.id(), decision, 0, "v1", NOW));
	}

	private ManualReviewCase saveCase(FilterJob job, ManualReviewBand band, Instant createdAt) {
		ManualReviewPriorityReasonCode reasonCode =
			band == ManualReviewBand.HIGH ? ManualReviewPriorityReasonCode.REPORT_SIGNAL
				: ManualReviewPriorityReasonCode.DEFAULT;
		ManualReviewPriorityDecision decision = new ManualReviewPriorityDecision(band, reasonCode);
		return manualReviewCaseRepository.save(
			ManualReviewCase.open(job.target(), releaseId, job.id(), decision, 0, "v1", createdAt));
	}

	private FilterJob filterJob(FilterTarget target, String idempotencyKey) {
		return filterJobRepository.save(
			FilterJob.create(target, releaseId, idempotencyKey, NOW.plusSeconds(600), NOW));
	}

	private AnswerModerationJobIntakeService intakeService() {
		return new AnswerModerationJobIntakeService(filterJobRepository, filterReleaseRepository, historyRepository,
			outboxEventRepository, objectMapper, Duration.ofMinutes(10), transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private AnswerModerationExecutionWorker executionWorker(ModerationPipelineService pipeline) {
		AnswerModerationRetryPolicy retryPolicy = new AnswerModerationRetryPolicy(
			attempt -> Duration.ofSeconds(60), attempt -> Duration.ofSeconds(120), 1, Duration.ofHours(1));
		return new AnswerModerationExecutionWorker(pipeline, filterJobRepository, filterReleaseRepository,
			historyRepository, outboxEventRepository, retryPolicy, filterReleaseRetryGateRepository, GATE_CONFIG,
			manualReviewCaseRepository, manualReviewPriorityEvaluationRepository, POLICY, Duration.ofSeconds(5),
			objectMapper, pipelineExecutor, Duration.ofSeconds(5), transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private ModerationPipelineService allowPipeline() {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			(normalizedContent, modelSnapshot) -> new com.dnd.qello.filtering.moderation.ModerationProviderResult(
				false, java.util.Map.of(), java.util.Map.of(), modelSnapshot),
			(providerResult, contentType, language, categoryMappingRef) -> FilterVerdict.ALLOW,
			filterDecisionRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private ModerationPipelineService failingPipeline() {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			(normalizedContent, modelSnapshot) -> {
				throw new com.dnd.qello.filtering.error.FilteringException(
					com.dnd.qello.filtering.error.FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "boom");
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
		return releaseRegistryService.promote(candidate.id(), 1L).id();
	}

	private OperatorSession login() throws Exception {
		jdbc.update("DELETE FROM operator_credential");
		jdbc.update("DELETE FROM user_account WHERE nickname = 'qello-admin'");
		jdbc.update("DELETE FROM region_code WHERE code = 'MANUAL-REVIEW-TEST'");
		jdbc.update("""
			INSERT INTO region_code (code, display_name, level)
			VALUES ('MANUAL-REVIEW-TEST', 'Test Country', 'COUNTRY')
			""");
		operatorSeedService.seedIfAbsent(
			LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), "qello-admin", "MANUAL-REVIEW-TEST", "ko-KR",
			"Asia/Seoul");

		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		String csrfHeaderName = data.get("headerName").asText();
		String csrfToken = data.get("token").asText();
		Cookie[] csrfCookies = issued.getResponse().getCookies();

		MvcResult loggedIn = mockMvc.perform(withCsrf(post("/admin/login"), csrfHeaderName, csrfToken, csrfCookies)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		Cookie sessionCookie = loggedIn.getResponse().getCookie("SESSION");
		return new OperatorSession(csrfHeaderName, csrfToken, csrfCookies, sessionCookie);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withSession(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, OperatorSession session
	) {
		return request.cookie(session.sessionCookie());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, OperatorSession session
	) {
		return withCsrf(request, session.csrfHeaderName(), session.csrfToken(), session.csrfCookies());
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String csrfHeaderName,
		String csrfToken, Cookie[] csrfCookies
	) {
		return request.header(csrfHeaderName, csrfToken).cookie(csrfCookies);
	}

	private record OperatorSession(String csrfHeaderName, String csrfToken, Cookie[] csrfCookies, Cookie sessionCookie) {
	}
}
