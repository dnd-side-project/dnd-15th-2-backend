/**
 * Created at: 2026-08-16T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION-INT-001 through INT-008
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.ModerationFailureClassification;
import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthPolicy;
import com.dnd.qello.filtering.domain.SnapshotHealthStatus;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationJobIntakeService;
import com.dnd.qello.filtering.moderation.AnswerModerationRetryPolicy;
import com.dnd.qello.filtering.moderation.LocalRuleVerdict;
import com.dnd.qello.filtering.moderation.ModerationLanguage;
import com.dnd.qello.filtering.moderation.ModerationPipelineService;
import com.dnd.qello.filtering.moderation.SnapshotHealthProbeRecorder;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.filtering.repository.ManualReviewPriorityEvaluationRepository;
import com.dnd.qello.filtering.repository.SnapshotEmergencyMigrationHistoryRepository;
import com.dnd.qello.filtering.repository.SnapshotHealthProbeResultRepository;
import com.dnd.qello.filtering.repository.SnapshotHealthRepository;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.filtering.service.SnapshotEmergencyMigrationService;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;
import com.dnd.qello.notification.repository.NotificationEventRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

// #109: snapshot health의 동시 probe 갱신, emergency migration의 원자성·재실행 안전성,
// migration 이후 worker가 새 release로 자연스럽게 이어지는지, stale generation 결과
// 거절, 운영자 승인 endpoint 인가를 실제 PostgreSQL 위에서 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SnapshotHealthMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
	private static final String SOURCE_MODEL_SNAPSHOT = "omni-moderation-2026-07-01";
	private static final String TARGET_MODEL_SNAPSHOT = "omni-moderation-2026-08-01";
	private static final SnapshotHealthPolicy POLICY = new SnapshotHealthPolicy(1, Duration.ZERO);
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
	private SnapshotHealthRepository snapshotHealthRepository;
	@Autowired
	private SnapshotHealthProbeResultRepository probeResultRepository;
	@Autowired
	private SnapshotEmergencyMigrationHistoryRepository migrationHistoryRepository;
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
	private SnapshotEmergencyMigrationService emergencyMigrationService;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private ExecutorService executor;
	private ExecutorService pipelineExecutor;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM snapshot_emergency_migration_history");
		jdbc.update("DELETE FROM snapshot_health_probe_result");
		jdbc.update("DELETE FROM snapshot_health");
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
		executor = Executors.newFixedThreadPool(4);
		pipelineExecutor = Executors.newFixedThreadPool(4);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		pipelineExecutor.shutdownNow();
	}

	@Test
	@DisplayName("동시에 기록되는 target probe 2건이 FOR UPDATE 직렬화로 유실 없이 모두 반영된다")
	void concurrentProbeRecordingsDoNotLoseUpdates() throws Exception {
		SnapshotHealthProbeRecorder recorder = probeRecorder();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<SnapshotHealth>> futures = List.of(
			executor.submit(recordAfterSignal(recorder, ready, start)),
			executor.submit(recordAfterSignal(recorder, ready, start)));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		futures.get(0).get(10, TimeUnit.SECONDS);
		futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(jdbc.queryForObject(
			"SELECT target_only_failure_count FROM snapshot_health WHERE model_snapshot = ?",
			Integer.class, SOURCE_MODEL_SNAPSHOT)).isEqualTo(2);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM snapshot_health_probe_result WHERE model_snapshot = ?",
			Integer.class, SOURCE_MODEL_SNAPSHOT)).isEqualTo(4);
	}

	@Test
	@DisplayName("emergency migration은 AUTOMATED job만 원자적으로 이관하고 RESOLVED job은 건드리지 않는다")
	void emergencyMigrationMovesOnlyAutomatedJobs() {
		long sourceReleaseId = promotedRelease(SOURCE_MODEL_SNAPSHOT);
		long targetReleaseId = canaryRelease(TARGET_MODEL_SNAPSHOT);
		FilterJob automated = createJob(sourceReleaseId, target(1L), "migrate-automated-1");
		FilterJob resolved = createJob(sourceReleaseId, target(2L), "migrate-resolved-1")
			.applyAutomatedDecision(1, com.dnd.qello.filtering.domain.FilterVerdict.ALLOW, NOW);
		filterJobRepository.save(resolved);
		confirmPermanent(sourceReleaseId);

		var history = emergencyMigrationService.emergencyMigrate(sourceReleaseId, targetReleaseId, 1L);

		assertThat(history.migratedJobCount()).isEqualTo(1);
		FilterJob migratedAutomated = filterJobRepository.findById(automated.id()).orElseThrow();
		assertThat(migratedAutomated.filterReleaseId()).isEqualTo(targetReleaseId);
		assertThat(migratedAutomated.attemptGeneration()).isEqualTo(2);
		FilterJob untouchedResolved = filterJobRepository.findById(resolved.id()).orElseThrow();
		assertThat(untouchedResolved.filterReleaseId()).isEqualTo(sourceReleaseId);
		assertThat(filterReleaseRepository.findById(targetReleaseId).orElseThrow().status())
			.isEqualTo(com.dnd.qello.filtering.domain.FilterReleaseStatus.PROMOTED);
	}

	@Test
	@DisplayName("대상 release를 찾을 수 없으면 어떤 job도 이관되지 않고 이력도 남지 않는다")
	void emergencyMigrationLeavesNoSideEffectWhenTargetMissing() {
		long sourceReleaseId = promotedRelease(SOURCE_MODEL_SNAPSHOT);
		FilterJob automated = createJob(sourceReleaseId, target(3L), "migrate-missing-target-1");
		confirmPermanent(sourceReleaseId);

		assertThatThrownBy(() -> emergencyMigrationService.emergencyMigrate(sourceReleaseId, 999_999L, 1L))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.RELEASE_NOT_FOUND);

		FilterJob unchanged = filterJobRepository.findById(automated.id()).orElseThrow();
		assertThat(unchanged.filterReleaseId()).isEqualTo(sourceReleaseId);
		assertThat(unchanged.attemptGeneration()).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM snapshot_emergency_migration_history", Integer.class)).isEqualTo(0);
	}

	@Test
	@DisplayName("동일 snapshot에 emergency migration을 두 번 호출해도 job이 이중 이관되지 않는다")
	void duplicateEmergencyMigrationDoesNotDoubleMigrateJobs() {
		long sourceReleaseId = promotedRelease(SOURCE_MODEL_SNAPSHOT);
		long targetReleaseId = canaryRelease(TARGET_MODEL_SNAPSHOT);
		FilterJob automated = createJob(sourceReleaseId, target(4L), "migrate-duplicate-1");
		confirmPermanent(sourceReleaseId);

		emergencyMigrationService.emergencyMigrate(sourceReleaseId, targetReleaseId, 1L);
		// 두 번째 호출 시점에는 source release에 남은 AUTOMATED job이 없어 대상
		// release도 이미 PROMOTED라 재승격 시도가 거절된다 — 이중 이관이 되지 않는다.
		assertThatThrownBy(() -> emergencyMigrationService.emergencyMigrate(sourceReleaseId, targetReleaseId, 1L))
			.isInstanceOf(FilteringException.class);

		FilterJob afterSecondAttempt = filterJobRepository.findById(automated.id()).orElseThrow();
		assertThat(afterSecondAttempt.attemptGeneration()).isEqualTo(2);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM snapshot_emergency_migration_history", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("migration 이후 worker는 코드 변경 없이 대상 release의 modelSnapshot으로 pipeline을 호출한다")
	void workerUsesNewReleaseModelSnapshotAfterMigration() {
		long sourceReleaseId = promotedRelease(SOURCE_MODEL_SNAPSHOT);
		long targetReleaseId = canaryRelease(TARGET_MODEL_SNAPSHOT);
		AnswerModerationJobIntakeService intake = intakeService();
		FilterJob job = intake.submit(target(5L), "migration worker 연동 답변", ModerationLanguage.KO, "migrate-worker-1");
		confirmPermanent(sourceReleaseId);
		emergencyMigrationService.emergencyMigrate(sourceReleaseId, targetReleaseId, 1L);

		AtomicReference<String> calledWithModelSnapshot = new AtomicReference<>();
		AnswerModerationExecutionWorker worker = executionWorker(recordingPipeline(calledWithModelSnapshot));
		worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(
			10, "migration-worker-owner", NOW, NOW.plusSeconds(30)));

		assertThat(calledWithModelSnapshot.get()).isEqualTo(TARGET_MODEL_SNAPSHOT);
		FilterJob current = filterJobRepository.findById(job.id()).orElseThrow();
		assertThat(current.filterReleaseId()).isEqualTo(targetReleaseId);
	}

	@Test
	@DisplayName("emergency migration 이전 generation으로 도착한 결과는 거절되고 job 상태를 바꾸지 않는다")
	void staleGenerationResultAfterMigrationIsRejected() {
		long sourceReleaseId = promotedRelease(SOURCE_MODEL_SNAPSHOT);
		long targetReleaseId = canaryRelease(TARGET_MODEL_SNAPSHOT);
		FilterJob job = createJob(sourceReleaseId, target(6L), "migrate-stale-1");
		confirmPermanent(sourceReleaseId);
		emergencyMigrationService.emergencyMigrate(sourceReleaseId, targetReleaseId, 1L);

		FilterJob migrated = filterJobRepository.findById(job.id()).orElseThrow();
		assertThat(migrated.attemptGeneration()).isEqualTo(2);
		assertThatThrownBy(() -> migrated.applyAutomatedDecision(
			1, com.dnd.qello.filtering.domain.FilterVerdict.ALLOW, NOW.plusSeconds(1)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.STALE_ATTEMPT_GENERATION);
	}

	@Test
	@DisplayName("인증된 운영자는 PERMANENT_SUSPECTED snapshot을 confirm-permanent로 승인할 수 있다")
	void operatorConfirmsPermanentThroughEndpoint() throws Exception {
		seedSuspectedHealth(SOURCE_MODEL_SNAPSHOT);
		OperatorSession session = login();

		mockMvc.perform(withSession(withCsrf(post(
				"/admin/filtering/snapshot-health/%s/confirm-permanent".formatted(SOURCE_MODEL_SNAPSHOT)), session), session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PERMANENT_CONFIRMED"))
			.andExpect(jsonPath("$.data.confirmedByOperatorUserId").exists());

		assertThat(jdbc.queryForObject(
			"SELECT status FROM snapshot_health WHERE model_snapshot = ?", String.class, SOURCE_MODEL_SNAPSHOT))
			.isEqualTo("PERMANENT_CONFIRMED");
	}

	@Test
	@DisplayName("세션이 없으면 confirm-permanent endpoint는 401로 거절된다")
	void rejectsUnauthenticatedConfirmPermanent() throws Exception {
		seedSuspectedHealth(SOURCE_MODEL_SNAPSHOT);
		// CSRF 검사가 인증 검사보다 먼저 실행되므로, 세션만 없고 CSRF 토큰은 유효한
		// 상태로 요청해야 "인증되지 않음"만 분리해서 검증할 수 있다 — 토큰까지
		// 없으면 403(CSRF 거절)이 먼저 나와 인증 여부를 가리지 못한다.
		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode csrfData = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");

		mockMvc.perform(post("/admin/filtering/snapshot-health/%s/confirm-permanent".formatted(SOURCE_MODEL_SNAPSHOT))
				.header(csrfData.get("headerName").asText(), csrfData.get("token").asText())
				.cookie(issued.getResponse().getCookies()))
			.andExpect(status().isUnauthorized());

		assertThat(jdbc.queryForObject(
			"SELECT status FROM snapshot_health WHERE model_snapshot = ?", String.class, SOURCE_MODEL_SNAPSHOT))
			.isEqualTo("PERMANENT_SUSPECTED");
	}

	private Callable<SnapshotHealth> recordAfterSignal(
		SnapshotHealthProbeRecorder recorder, CountDownLatch ready, CountDownLatch start
	) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("probe recording start barrier timed out");
			}
			return recorder.recordProbe(SOURCE_MODEL_SNAPSHOT, ModerationFailureClassification.SERVER_ERROR, null);
		};
	}

	private SnapshotHealthProbeRecorder probeRecorder() {
		return new SnapshotHealthProbeRecorder(snapshotHealthRepository, probeResultRepository, POLICY,
			Clock.fixed(NOW, ZoneOffset.UTC), new TransactionTemplate(transactionManager));
	}

	private void confirmPermanent(long sourceReleaseId) {
		FilterRelease source = filterReleaseRepository.findById(sourceReleaseId).orElseThrow();
		SnapshotHealth health = snapshotHealthRepository.findOrCreateForUpdate(source.modelSnapshot(), NOW)
			.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW, POLICY)
			.confirmPermanent(1L, NOW);
		snapshotHealthRepository.save(health);
	}

	private void seedSuspectedHealth(String modelSnapshot) {
		SnapshotHealth suspected = snapshotHealthRepository.findOrCreateForUpdate(modelSnapshot, NOW)
			.recordProbe(ModerationFailureClassification.SERVER_ERROR, null, NOW, POLICY);
		snapshotHealthRepository.save(suspected);
	}

	private AnswerModerationJobIntakeService intakeService() {
		return new AnswerModerationJobIntakeService(filterJobRepository, filterReleaseRepository, historyRepository,
			outboxEventRepository, objectMapper, Duration.ofMinutes(10), transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private AnswerModerationExecutionWorker executionWorker(ModerationPipelineService pipeline) {
		AnswerModerationRetryPolicy retryPolicy = new AnswerModerationRetryPolicy(
			attempt -> Duration.ofSeconds(60), attempt -> Duration.ofSeconds(120), 100, Duration.ofHours(1));
		return new AnswerModerationExecutionWorker(pipeline, filterJobRepository, filterReleaseRepository,
			historyRepository, outboxEventRepository, retryPolicy, filterReleaseRetryGateRepository,
			new com.dnd.qello.filtering.domain.RetryGateConfig(3, 2, 2, 2, 6), manualReviewCaseRepository,
			manualReviewPriorityEvaluationRepository,
			new com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy(3, Duration.ofHours(24), "test-v1"),
			notificationEventRepository,
			Duration.ofSeconds(5), objectMapper, pipelineExecutor, Duration.ofSeconds(5), transactionManager,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private ModerationPipelineService recordingPipeline(AtomicReference<String> calledWithModelSnapshot) {
		return new ModerationPipelineService(
			(rawContent, normalizationRef) -> rawContent,
			(normalizedContent, localRulesetRef) -> LocalRuleVerdict.noMatch(),
			(normalizedContent, modelSnapshot) -> {
				calledWithModelSnapshot.set(modelSnapshot);
				throw new FilteringException(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "boom");
			},
			(providerResult, contentType, language, categoryMappingRef) -> {
				throw new AssertionError("판정까지 도달하면 안 됩니다");
			},
			filterDecisionRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private FilterJob createJob(long filterReleaseId, FilterTarget target, String idempotencyKey) {
		FilterJob job = FilterJob.create(target, filterReleaseId, idempotencyKey, NOW.plusSeconds(600), NOW);
		return filterJobRepository.save(job);
	}

	private static FilterTarget target(long targetId) {
		return FilterTarget.of(FilterTargetType.ANSWER, targetId);
	}

	private long promotedRelease(String modelSnapshot) {
		FilterRelease candidate = releaseRegistryService.createCandidate(
			"norm-" + modelSnapshot, "ruleset-" + modelSnapshot, "category-" + modelSnapshot, modelSnapshot);
		releaseRegistryService.markOfflineEvaluated(candidate.id());
		releaseRegistryService.designateShadow(candidate.id());
		releaseRegistryService.designateCanary(candidate.id());
		return releaseRegistryService.promote(candidate.id(), 1L).id();
	}

	private long canaryRelease(String modelSnapshot) {
		FilterRelease candidate = releaseRegistryService.createCandidate(
			"norm-" + modelSnapshot, "ruleset-" + modelSnapshot, "category-" + modelSnapshot, modelSnapshot);
		releaseRegistryService.markOfflineEvaluated(candidate.id());
		releaseRegistryService.designateShadow(candidate.id());
		return releaseRegistryService.designateCanary(candidate.id()).id();
	}

	private OperatorSession login() throws Exception {
		jdbc.update("DELETE FROM operator_credential");
		jdbc.update("DELETE FROM user_account WHERE nickname = 'qello-admin'");
		jdbc.update("DELETE FROM region_code WHERE code = 'SNAPSHOT-HEALTH-TEST'");
		jdbc.update("""
			INSERT INTO region_code (code, display_name, level)
			VALUES ('SNAPSHOT-HEALTH-TEST', 'Test Country', 'COUNTRY')
			""");
		operatorSeedService.seedIfAbsent(
			LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), "qello-admin", "SNAPSHOT-HEALTH-TEST", "ko-KR",
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

	private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request, OperatorSession session) {
		return request.cookie(session.sessionCookie());
	}

	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, OperatorSession session) {
		return withCsrf(request, session.csrfHeaderName(), session.csrfToken(), session.csrfCookies());
	}

	private static MockHttpServletRequestBuilder withCsrf(
		MockHttpServletRequestBuilder request, String csrfHeaderName, String csrfToken, Cookie[] csrfCookies
	) {
		return request.header(csrfHeaderName, csrfToken).cookie(csrfCookies);
	}

	private record OperatorSession(String csrfHeaderName, String csrfToken, Cookie[] csrfCookies, Cookie sessionCookie) {
	}
}
