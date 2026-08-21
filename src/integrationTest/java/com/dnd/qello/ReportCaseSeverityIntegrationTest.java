/**
 * Created at: 2026-08-20T21:07:44+09:00
 * Source scenario: TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW-INT-001 through INT-009
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewPriorityDecision;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubReason;
import com.dnd.qello.safety.domain.ReportSubmission;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.service.ReportCaseAutoSuppressionEvaluator;
import com.dnd.qello.safety.service.ReportOutcome;
import com.dnd.qello.safety.service.SafetyReportService;

@SpringBootTest
@ActiveProfiles("test")
class ReportCaseSeverityIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH156";
	private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private SafetyReportService safetyReportService;
	@Autowired
	private ReportCaseRepository reportCaseRepository;
	@Autowired
	private FilterJobRepository filterJobRepository;
	@Autowired
	private ManualReviewCaseRepository manualReviewCaseRepository;
	@Autowired
	private ReportCaseAutoSuppressionEvaluator autoSuppressionEvaluator;

	private long authorId;
	private long senderId;
	private long postId;
	private long filterReleaseId;
	private ExecutorService executor;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("TRUNCATE report_case_event");
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM moderation_review");
		jdbc.update("DELETE FROM manual_review_case");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM filter_release");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM report_case");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES (?, 'KR', 'GH156 Test', 'REGION')", REGION);

		authorId = account("author");
		senderId = account("sender");
		filterReleaseId = insertFilterRelease();
		executor = Executors.newFixedThreadPool(4);
	}

	@AfterEach
	void shutdownExecutor() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("INT-001: CSAM 신고로 새로 열리는 사건은 처음부터 CRITICAL/URGENT다")
	void newCaseOpensAsCriticalWhenSubReasonIsCsam() {
		long answerId = publishedAnswer("gh156-int001");
		long reporter = reporter("reporter1");

		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporter, answerId, criticalSubmission(), false, NOW);

		ReportCase opened = reportCaseRepository.findById(outcome.report().caseId()).orElseThrow();
		assertThat(opened.severity()).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(opened.queue()).isEqualTo(ReportCaseQueue.URGENT);
		assertThat(opened.slaDueAt()).isAfter(NOW);
	}

	@Test
	@DisplayName("INT-002: NORMAL로 열린 사건에 CRITICAL 신고가 붙으면 승격되고 ESCALATED 이벤트가 남는다")
	void escalatesExistingNormalCaseWhenCriticalReportAttaches() {
		long answerId = publishedAnswer("gh156-int002");
		long reporterA = reporter("reporter1");
		long reporterB = reporter("reporter2");
		ReportOutcome first = safetyReportService.submitAnswerReport(
			reporterA, answerId, normalSubmission(), false, NOW);
		ReportCase openedCase = reportCaseRepository.findById(first.report().caseId()).orElseThrow();
		assertThat(openedCase.severity()).isEqualTo(ReportCaseSeverity.NORMAL);

		safetyReportService.submitAnswerReport(
			reporterB, answerId, criticalSubmission(), false, NOW.plusSeconds(10));

		ReportCase escalated = reportCaseRepository.findById(openedCase.id()).orElseThrow();
		assertThat(escalated.severity()).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(escalated.queue()).isEqualTo(ReportCaseQueue.URGENT);
		assertThat(escalated.slaDueAt()).isNotEqualTo(openedCase.slaDueAt());
		assertThat(eventCount(escalated.id(), "ESCALATED")).isEqualTo(1);
		assertThat(eventCount(escalated.id(), "REPORT_ATTACHED")).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-003: 두 CRITICAL 신고가 동시에 같은 NORMAL 사건에 붙어도 ESCALATED는 정확히 1건이다")
	void concurrentEscalationProducesExactlyOneEscalatedEvent() throws Exception {
		long answerId = publishedAnswer("gh156-int003");
		long reporterA = reporter("reporter1");
		long reporterB = reporter("reporter2");
		ReportOutcome opened = safetyReportService.submitAnswerReport(
			reporterA, answerId, normalSubmission(), false, NOW);
		long caseId = opened.report().caseId();
		long thirdReporter = reporter("reporter3");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		Future<ReportOutcome> first = executor.submit(
			() -> submitCritical(reporterB, answerId, ready, start));
		Future<ReportOutcome> second = executor.submit(
			() -> submitCritical(thirdReporter, answerId, ready, start));
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		first.get(10, TimeUnit.SECONDS);
		second.get(10, TimeUnit.SECONDS);

		ReportCase escalated = reportCaseRepository.findById(caseId).orElseThrow();
		assertThat(escalated.severity()).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(eventCount(caseId, "ESCALATED")).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-004: CRITICAL 사건에 NORMAL 신고가 붙어도 강등되지 않는다")
	void doesNotDeescalateWhenNormalReportAttachesToCriticalCase() {
		long answerId = publishedAnswer("gh156-int004");
		long reporterA = reporter("reporter1");
		long reporterB = reporter("reporter2");
		ReportOutcome first = safetyReportService.submitAnswerReport(
			reporterA, answerId, criticalSubmission(), false, NOW);
		long caseId = first.report().caseId();

		safetyReportService.submitAnswerReport(reporterB, answerId, normalSubmission(), false, NOW.plusSeconds(10));

		ReportCase stillCritical = reportCaseRepository.findById(caseId).orElseThrow();
		assertThat(stillCritical.severity()).isEqualTo(ReportCaseSeverity.CRITICAL);
		assertThat(eventCount(caseId, "ESCALATED")).isZero();
	}

	@Test
	@DisplayName("INT-005: 서로 다른 신고자 수가 임계값에 도달하면 자동으로 전역 숨김된다")
	void autoSuppressesWhenDistinctReporterThresholdIsReached() {
		long answerId = publishedAnswer("gh156-int005");
		long caseId = -1;
		for (int i = 0; i < 5; i++) {
			long reporter = reporter("autosup-reporter-" + i);
			ReportOutcome outcome = safetyReportService.submitAnswerReport(
				reporter, answerId, normalSubmission(), false, NOW.plusSeconds(i));
			caseId = outcome.report().caseId();
		}

		ReportCase resolved = reportCaseRepository.findById(caseId).orElseThrow();
		assertThat(resolved.status()).isEqualTo(ReportCaseStatus.RESOLVED);
		Answer answer = answerRepository.findById(answerId).orElseThrow();
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.HIDDEN);
	}

	@Test
	@DisplayName("이미 종결된 사건에 자동 숨김 평가가 재호출돼도(동시 경합 재현) 예외 없이 멱등하게 넘어간다")
	void autoSuppressionEvaluationIsIdempotentOnAlreadyResolvedCase() {
		long answerId = publishedAnswer("gh156-int005-idempotent");
		long caseId = -1;
		for (int i = 0; i < 5; i++) {
			long reporter = reporter("idempotent-reporter-" + i);
			ReportOutcome outcome = safetyReportService.submitAnswerReport(
				reporter, answerId, normalSubmission(), false, NOW.plusSeconds(i));
			caseId = outcome.report().caseId();
		}
		assertThat(reportCaseRepository.findById(caseId).orElseThrow().status())
			.isEqualTo(ReportCaseStatus.RESOLVED);

		// 두 신고가 임계값 도달을 동시에 관측했다가 한쪽만 먼저 종결에 성공하는
		// 경합을 재현한다 — 패자 쪽 evaluate() 재호출이 예외를 던지면 안 된다.
		autoSuppressionEvaluator.evaluate(caseId, answerId, NOW.plusSeconds(100));

		assertThat(reportCaseRepository.findById(caseId).orElseThrow().status())
			.isEqualTo(ReportCaseStatus.RESOLVED);
	}

	@Test
	@DisplayName("서로 다른 신고자 수가 임계값 미만이면 자동 숨김되지 않는다")
	void doesNotAutoSuppressBelowReporterThreshold() {
		long answerId = publishedAnswer("gh156-int005-below");
		long reporter = reporter("autosup-reporter-single");

		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporter, answerId, normalSubmission(), false, NOW);

		ReportCase stillOpen = reportCaseRepository.findById(outcome.report().caseId()).orElseThrow();
		assertThat(stillOpen.status()).isEqualTo(ReportCaseStatus.OPEN);
		Answer answer = answerRepository.findById(answerId).orElseThrow();
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
	}

	@Test
	@DisplayName("INT-007: 같은 답변에 OPEN 상태의 ManualReviewCase가 있으면 자동 숨김되고 상관관계가 기록된다")
	void autoSuppressesWhenManualReviewCaseIsOpen() {
		long answerId = publishedAnswer("gh156-int007");
		ManualReviewCase openReview = openManualReviewCase(answerId);
		long reporter = reporter("reporter1");

		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporter, answerId, normalSubmission(), false, NOW);

		ReportCase resolved = reportCaseRepository.findById(outcome.report().caseId()).orElseThrow();
		assertThat(resolved.status()).isEqualTo(ReportCaseStatus.RESOLVED);
		assertThat(resolved.linkedManualReviewCaseId()).isEqualTo(openReview.id());
		Answer answer = answerRepository.findById(answerId).orElseThrow();
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.HIDDEN);
	}

	@Test
	@DisplayName("INT-008: ManualReviewCase가 RESOLVED+ALLOW(무혐의)면 자동 숨김되지 않는다")
	void doesNotAutoSuppressWhenManualReviewCaseIsResolvedAllow() {
		long answerId = publishedAnswer("gh156-int008");
		ManualReviewCase allowed = openManualReviewCase(answerId).resolve(FilterVerdict.ALLOW, 1L, NOW);
		manualReviewCaseRepository.save(allowed);
		long reporter = reporter("reporter1");

		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporter, answerId, normalSubmission(), false, NOW);

		ReportCase stillOpen = reportCaseRepository.findById(outcome.report().caseId()).orElseThrow();
		assertThat(stillOpen.status()).isEqualTo(ReportCaseStatus.OPEN);
		assertThat(stillOpen.linkedManualReviewCaseId()).isNull();
	}

	// INT-009(자동 숨김 후 재신고가 재개방이 아니라 새 사건을 여는지)는 이 경로로는
	// 실제로 도달할 수 없다 — findViewableAnswer가 status='PUBLISHED'만 조회하므로
	// 답변이 이미 HIDDEN이면 재신고 자체가 REPORT_TARGET_NOT_FOUND로 막힌다. 같은
	// 불변식(INV-RPT-007)은 ReportCaseFoundationIntegrationTest#allowsNewCaseForTargetWithResolvedCase
	// (#154, NO_VIOLATION 종결 경로)가 이미 검증하므로 중복 테스트를 추가하지 않는다.

	private ReportOutcome submitCritical(long reporterId, long answerId, CountDownLatch ready, CountDownLatch start)
		throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return safetyReportService.submitAnswerReport(reporterId, answerId, criticalSubmission(), false, NOW);
	}

	private ManualReviewCase openManualReviewCase(long answerId) {
		FilterTarget target = FilterTarget.of(FilterTargetType.ANSWER, answerId);
		FilterJob job = filterJobRepository.save(
			FilterJob.create(target, filterReleaseId, "gh156-job-" + answerId, NOW.plusSeconds(600), NOW));
		ManualReviewPriorityDecision decision =
			new ManualReviewPriorityDecision(ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.DEFAULT);
		return manualReviewCaseRepository.save(
			ManualReviewCase.open(target, filterReleaseId, job.id(), decision, 0, "v1", NOW));
	}

	private int eventCount(long caseId, String eventType) {
		return jdbc.queryForObject(
			"SELECT count(*) FROM report_case_event WHERE case_id = ? AND event_type = ?",
			Integer.class, caseId, eventType);
	}

	private ReportSubmission criticalSubmission() {
		return new ReportSubmission(ReportReason.SEXUAL_CONTENT, ReportSubReason.CSAM, null);
	}

	private ReportSubmission normalSubmission() {
		return new ReportSubmission(ReportReason.SPAM_OR_ADVERTISING, null, null);
	}

	private long publishedAnswer(String idempotencyKey) {
		long questionId = question();
		postId = post(questionId);
		long authorRecipientId = insertRecipient(postId, authorId, "AVAILABLE");
		return answerRepository.save(Answer.restore(null, authorRecipientId, authorId,
			AnswerStatus.PUBLISHED, idempotencyKey, "신고 대상 답변", REGION,
			BigDecimal.valueOf(90), "NEAR", AnswerModerationStatus.PASSED,
			NOW, NOW, null, 5000L, null, 0)).getId();
	}

	// 신고자도 그 질문글의 recipient여야 findViewableAnswer의 열람 자격을 통과한다
	// (ReportIntakeApiIntegrationTest와 동일한 전제).
	private long reporter(String nickname) {
		long id = account(nickname);
		insertRecipient(postId, id, "AVAILABLE");
		return id;
	}

	private long insertFilterRelease() {
		return jdbc.queryForObject("""
			INSERT INTO filter_release (normalization_ref, local_ruleset_ref, category_mapping_ref, model_snapshot)
			VALUES ('gh156-norm-v1', 'gh156-ruleset-v1', 'gh156-category-v1', 'gh156-model-v1')
			RETURNING id
			""", Long.class);
	}

	private long insertRecipient(long postIdValue, long recipientId, String status) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postIdValue, recipientId, status, REGION, Timestamp.from(NOW));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long question() {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'GH156 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), authorId);
	}

	private long post(long questionId) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '질문글 본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, "gh156-post-" + questionId, REGION, Timestamp.from(NOW),
			Timestamp.from(NOW), Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}
}
