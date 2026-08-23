/**
 * Created at: 2026-08-21T19:30:00+09:00
 * Source scenario: TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE-INT-001, INT-002, INT-003
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubReason;
import com.dnd.qello.safety.domain.ReportSubmission;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.service.ReportOutcome;
import com.dnd.qello.safety.service.SafetyReportService;

/**
 * {@code critical-enabled} 플래그가 켜졌을 때만 나타나는 "즉시 전역 숨김"(설계
 * §4.1 A안, #157) 동작을 검증한다. 기본값(꺼짐)에서의 동작은
 * {@link ReportCaseSeverityIntegrationTest}가 이미 검증한다 — 이 클래스가
 * 존재한다는 사실 자체가 플래그 없이는 자동 숨김이 트리거되지 않는다는
 * 증거이기도 하다(별도 property override 없이는 이 동작을 재현할 수 없다).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "qello.safety.report-case.auto-suppress.critical-enabled=true")
class ReportCaseCriticalAutoSuppressionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH157-CRIT";
	private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private SafetyReportService safetyReportService;
	@Autowired
	private ReportCaseRepository reportCaseRepository;

	private long authorId;
	private long senderId;
	private long postId;

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
			+ "VALUES (?, 'KR', 'GH157 Test', 'REGION')", REGION);

		authorId = account("author");
		senderId = account("sender");
	}

	@Test
	@DisplayName("INT-001: critical-enabled가 켜져 있으면 CRITICAL 신고 1건으로 새로 열린 사건이 즉시 전역 숨김된다")
	void newCriticalCaseIsAutoSuppressedWhenFlagIsEnabled() {
		long answerId = publishedAnswer("gh157-int001");
		long reporter = reporter("reporter1");

		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporter, answerId, criticalSubmission(), false, NOW);

		ReportCase resolved = reportCaseRepository.findById(outcome.report().caseId()).orElseThrow();
		assertThat(resolved.status()).isEqualTo(ReportCaseStatus.RESOLVED);
		Answer answer = answerRepository.findById(answerId).orElseThrow();
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.HIDDEN);
	}

	@Test
	@DisplayName("INT-002: SELF_HARM_RISK 신고도 critical-enabled가 켜져 있으면 즉시 전역 숨김된다")
	void newSelfHarmRiskCaseIsAutoSuppressedWhenFlagIsEnabled() {
		long answerId = publishedAnswer("gh157-int002");
		long reporter = reporter("reporter1");
		ReportSubmission selfHarmSubmission =
			new ReportSubmission(ReportReason.ILLEGAL_OR_DANGEROUS, ReportSubReason.SELF_HARM_RISK, null);

		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporter, answerId, selfHarmSubmission, false, NOW);

		ReportCase resolved = reportCaseRepository.findById(outcome.report().caseId()).orElseThrow();
		assertThat(resolved.status()).isEqualTo(ReportCaseStatus.RESOLVED);
		Answer answer = answerRepository.findById(answerId).orElseThrow();
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.HIDDEN);
	}

	@Test
	@DisplayName("INT-003: NORMAL로 열린 사건이 CRITICAL 신고로 승격되면 critical-enabled 하에서 즉시 전역 숨김된다")
	void escalatedCaseIsAutoSuppressedWhenFlagIsEnabled() {
		long answerId = publishedAnswer("gh157-int003");
		long reporterA = reporter("reporter1");
		long reporterB = reporter("reporter2");
		ReportOutcome first = safetyReportService.submitAnswerReport(
			reporterA, answerId, normalSubmission(), false, NOW);
		ReportCase openedCase = reportCaseRepository.findById(first.report().caseId()).orElseThrow();
		assertThat(openedCase.status()).isEqualTo(ReportCaseStatus.OPEN);

		safetyReportService.submitAnswerReport(
			reporterB, answerId, criticalSubmission(), false, NOW.plusSeconds(10));

		ReportCase resolved = reportCaseRepository.findById(openedCase.id()).orElseThrow();
		assertThat(resolved.status()).isEqualTo(ReportCaseStatus.RESOLVED);
		Answer answer = answerRepository.findById(answerId).orElseThrow();
		assertThat(answer.getStatus()).isEqualTo(AnswerStatus.HIDDEN);
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

	private long reporter(String nickname) {
		long id = account(nickname);
		insertRecipient(postId, id, "AVAILABLE");
		return id;
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
			VALUES ('OPERATOR', 'ACTIVE', 'GH157 질문', 'TEXT', ?, ?, ?)
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
			""", Long.class, senderId, questionId, "gh157-post-" + questionId, REGION, Timestamp.from(NOW),
			Timestamp.from(NOW), Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}
}
