/**
 * Created at: 2026-08-18T22:30:00+09:00
 * Source scenario: TEST-PLAN-GH-154-REPORT-INTAKE-API-INT-001 through INT-015
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.domain.ReportReason;
import com.dnd.qello.safety.domain.ReportSubmission;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportContentSnapshotRepository;
import com.dnd.qello.safety.repository.SafetyRepository;
import com.dnd.qello.safety.service.ReportOutcome;
import com.dnd.qello.safety.service.SafetyReportService;
import com.dnd.qello.safety.service.SafetyService;

@SpringBootTest
@ActiveProfiles("test")
class ReportIntakeApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH154";
	private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private SafetyRepository safetyRepository;
	@Autowired
	private ReportContentSnapshotRepository reportContentSnapshotRepository;
	@Autowired
	private SafetyReportService safetyReportService;
	@Autowired
	private SafetyService safetyService;

	private long reporterId;
	private long authorId;
	private long senderId;
	private long unrelatedUserId;
	private long postId;
	private long answerId;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("TRUNCATE report_case_event");
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM moderation_review");
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
			+ "VALUES (?, 'KR', 'GH154 Test', 'REGION')", REGION);

		reporterId = account("reporter");
		authorId = account("author");
		senderId = account("sender");
		unrelatedUserId = account("unrelated");
		long questionId = question();
		postId = post(questionId);
		// ANSWERED로의 실제 전이는 별도 트랜잭션에서 capacity_released_at과 함께
		// 기록돼야 하는 지연 트리거 제약이 있다 — 이 테스트는 열람 자격만
		// 필요하므로 AVAILABLE(+ 만료 전) 경로로 자격을 준다.
		long authorRecipientId = insertRecipient(postId, authorId, "AVAILABLE");
		// reporter는 같은 질문글의 또 다른 recipient다 — sender가 아니어도 답변을
		// 열람·신고할 수 있는 현실적인 경로(#79 이후 recipient 간 상호 열람)다.
		insertRecipient(postId, reporterId, "AVAILABLE");
		answerId = answerRepository.save(Answer.restore(null, authorRecipientId, authorId,
			AnswerStatus.PUBLISHED, "gh154-answer", "신고 대상 답변", REGION,
			BigDecimal.valueOf(90), "NEAR", AnswerModerationStatus.PASSED,
			NOW, NOW, null, 5000L, null, 0)).getId();
	}

	@Test
	@DisplayName("신고 접수는 report·스냅샷·사건·CASE_OPENED 이벤트를 같은 트랜잭션에서 만든다 (INV-RPT-003)")
	void submitCreatesReportSnapshotCaseAndEventAtomically() {
		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);

		assertThat(outcome.alreadyReceived()).isFalse();
		Report saved = outcome.report();
		assertThat(reportContentSnapshotRepository.findByReportId(saved.id())).isPresent();
		assertThat(saved.caseId()).isNotNull();
		Integer caseCount = jdbc.queryForObject(
			"SELECT count(*) FROM report_case WHERE answer_id = ? AND status = 'OPEN'", Integer.class, answerId);
		assertThat(caseCount).isEqualTo(1);
		Integer eventCount = jdbc.queryForObject(
			"SELECT count(*) FROM report_case_event WHERE case_id = ? AND event_type = 'CASE_OPENED'",
			Integer.class, saved.caseId());
		assertThat(eventCount).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 신고자의 열린 신고 재접수는 새 행을 만들지 않고 기존 접수증을 반환한다")
	void resubmitReturnsExistingOpenReport() {
		ReportOutcome first = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);

		ReportOutcome second = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW.plusSeconds(10));

		assertThat(second.alreadyReceived()).isTrue();
		assertThat(second.report().id()).isEqualTo(first.report().id());
		Integer reportCount = jdbc.queryForObject(
			"SELECT count(*) FROM report WHERE reporter_id = ? AND answer_id = ?",
			Integer.class, reporterId, answerId);
		assertThat(reportCount).isEqualTo(1);
	}

	@Test
	@DisplayName("MORE_INFO_REQUIRED 상태에서도 재접수는 멱등하다 (Foundation INV-RPT-002 회귀 확인)")
	void resubmitIsIdempotentWhenExistingReportAwaitsMoreInfo() {
		ReportOutcome first = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);
		jdbc.update("UPDATE report SET status = 'MORE_INFO_REQUIRED' WHERE id = ?", first.report().id());

		ReportOutcome second = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW.plusSeconds(10));

		assertThat(second.alreadyReceived()).isTrue();
		assertThat(second.report().id()).isEqualTo(first.report().id());
	}

	@Test
	@DisplayName("서로 다른 신고자 2명의 동시 신고는 사건 1개로 수렴한다 (INV-RPT-001)")
	void concurrentReportsFromDifferentReportersMergeIntoOneCase() throws Exception {
		long secondReporterId = account("second-reporter");
		insertRecipient(postId, secondReporterId, "AVAILABLE");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<ReportOutcome>> results = List.of(
				executor.submit(() -> submitInTransaction(reporterId, ready, start)),
				executor.submit(() -> submitInTransaction(secondReporterId, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			ReportOutcome first = results.get(0).get(10, TimeUnit.SECONDS);
			ReportOutcome second = results.get(1).get(10, TimeUnit.SECONDS);

			assertThat(first.report().caseId()).isEqualTo(second.report().caseId());
			Integer caseCount = jdbc.queryForObject(
				"SELECT count(*) FROM report_case WHERE answer_id = ?", Integer.class, answerId);
			assertThat(caseCount).isEqualTo(1);
			Integer openedEvents = jdbc.queryForObject(
				"SELECT count(*) FROM report_case_event WHERE case_id = ? AND event_type = 'CASE_OPENED'",
				Integer.class, first.report().caseId());
			Integer attachedEvents = jdbc.queryForObject(
				"SELECT count(*) FROM report_case_event WHERE case_id = ? AND event_type = 'REPORT_ATTACHED'",
				Integer.class, first.report().caseId());
			assertThat(openedEvents).isEqualTo(1);
			assertThat(attachedEvents).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("종결된 사건과 내용이 같은 재신고는 새 사건 없이 DUPLICATE_SUPPRESSED 이벤트만 남긴다")
	void resubmitAfterResolutionIsSuppressedWhenContentUnchanged() {
		ReportOutcome first = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);
		jdbc.update("UPDATE report SET status = 'NO_VIOLATION', resolved_at = ? WHERE id = ?",
			Timestamp.from(NOW.plusSeconds(60)), first.report().id());
		jdbc.update("UPDATE report_case SET status = 'RESOLVED', decision = 'NO_VIOLATION', resolved_at = ? WHERE id = ?",
			Timestamp.from(NOW.plusSeconds(60)), first.report().caseId());

		ReportOutcome second = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW.plusSeconds(120));

		assertThat(second.alreadyReceived()).isTrue();
		assertThat(second.report().id()).isEqualTo(first.report().id());
		Integer reportCount = jdbc.queryForObject(
			"SELECT count(*) FROM report WHERE reporter_id = ? AND answer_id = ?",
			Integer.class, reporterId, answerId);
		assertThat(reportCount).isEqualTo(1);
		Integer suppressedEvents = jdbc.queryForObject(
			"SELECT count(*) FROM report_case_event WHERE case_id = ? AND event_type = 'DUPLICATE_SUPPRESSED'",
			Integer.class, first.report().caseId());
		assertThat(suppressedEvents).isEqualTo(1);
	}

	@Test
	@DisplayName("종결된 사건이라도 내용이 바뀌었으면 새 사건을 만든다")
	void resubmitAfterResolutionCreatesNewCaseWhenContentChanged() {
		ReportOutcome first = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);
		jdbc.update("UPDATE report SET status = 'NO_VIOLATION', resolved_at = ? WHERE id = ?",
			Timestamp.from(NOW.plusSeconds(60)), first.report().id());
		jdbc.update("UPDATE report_case SET status = 'RESOLVED', decision = 'NO_VIOLATION', resolved_at = ? WHERE id = ?",
			Timestamp.from(NOW.plusSeconds(60)), first.report().caseId());
		jdbc.update("UPDATE answer SET body_text = '수정된 본문' WHERE id = ?", answerId);

		ReportOutcome second = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW.plusSeconds(120));

		assertThat(second.alreadyReceived()).isFalse();
		assertThat(second.report().id()).isNotEqualTo(first.report().id());
		assertThat(second.report().caseId()).isNotEqualTo(first.report().caseId());
	}

	@Test
	@DisplayName("열람 자격이 없는 답변 신고는 404다")
	void reportingUnviewableAnswerIsNotFound() {
		long strangerId = account("stranger");

		assertThatThrownBy(() -> safetyReportService.submitAnswerReport(
			strangerId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_TARGET_NOT_FOUND);
	}

	@Test
	@DisplayName("자기 답변을 신고하면 400이다")
	void reportingOwnAnswerIsRejected() {
		assertThatThrownBy(() -> safetyReportService.submitAnswerReport(
			authorId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.SELF_REPORT_NOT_ALLOWED);
	}

	@Test
	@DisplayName("주입된 rate limit 한도를 초과하면 429다")
	void exceedingRateLimitIsRejected() {
		for (int i = 0; i < 10; i++) {
			jdbc.update("""
				INSERT INTO report (reporter_id, target_user_id, reason_code, status, created_at)
				VALUES (?, ?, 'SPAM_OR_ADVERTISING', 'NO_VIOLATION', ?)
				""", reporterId, unrelatedUserId, Timestamp.from(NOW.minusSeconds(30)));
		}

		assertThatThrownBy(() -> safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_RATE_LIMIT_EXCEEDED);
	}

	@Test
	@DisplayName("blockAuthor=true는 신고 저장과 같은 트랜잭션에서 차단까지 만든다")
	void blockAuthorOptionCreatesBlockInSameTransaction() {
		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), true, NOW);

		assertThat(outcome.alreadyReceived()).isFalse();
		Integer blockCount = jdbc.queryForObject(
			"SELECT count(*) FROM user_block WHERE blocker_id = ? AND blocked_id = ? AND released_at IS NULL",
			Integer.class, reporterId, authorId);
		assertThat(blockCount).isEqualTo(1);
	}

	@Test
	@DisplayName("매칭 이력이 없는 사용자 신고는 404다")
	void reportingUnrelatedUserIsNotFound() {
		assertThatThrownBy(() -> safetyReportService.submitUserReport(
			reporterId, unrelatedUserId, submission(ReportReason.IMPERSONATION), false, NOW))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_TARGET_NOT_FOUND);
	}

	@Test
	@DisplayName("방향글로 마주친 사용자 신고는 성공하고 닉네임을 스냅샷 본문으로 캡처한다")
	void reportingMatchedUserSucceedsAndCapturesNickname() {
		ReportOutcome outcome = safetyReportService.submitUserReport(
			reporterId, authorId, submission(ReportReason.IMPERSONATION), false, NOW);

		assertThat(outcome.alreadyReceived()).isFalse();
		ReportContentSnapshot snapshot =
			reportContentSnapshotRepository.findByReportId(outcome.report().id()).orElseThrow();
		assertThat(snapshot.bodyText()).isEqualTo("author");
	}

	@Test
	@DisplayName("질문글 신고는 성공하고 발신자를 authorId로, 본문을 스냅샷으로 캡처한다")
	void reportingPostSucceeds() {
		ReportOutcome outcome = safetyReportService.submitPostReport(
			reporterId, postId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);

		assertThat(outcome.alreadyReceived()).isFalse();
		assertThat(outcome.report().directionPostId()).isEqualTo(postId);
		ReportContentSnapshot snapshot =
			reportContentSnapshotRepository.findByReportId(outcome.report().id()).orElseThrow();
		assertThat(snapshot.authorId()).isEqualTo(senderId);
	}

	@Test
	@DisplayName("내 신고 내역은 최신순이며 다른 신고자의 신고를 포함하지 않는다")
	void findMyReportsReturnsOwnReportsInDescendingOrder() {
		long secondAuthorId = account("author2");
		long secondAuthorRecipientId = insertRecipient(postId, secondAuthorId, "AVAILABLE");
		long otherAnswerId = answerRepository.save(Answer.restore(null, secondAuthorRecipientId, secondAuthorId,
			AnswerStatus.PUBLISHED, "gh154-answer-2", "두 번째 답변", REGION,
			BigDecimal.valueOf(90), "NEAR", AnswerModerationStatus.PASSED,
			NOW, NOW, null, 5000L, null, 0)).getId();
		safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);
		safetyReportService.submitAnswerReport(
			reporterId, otherAnswerId, submission(ReportReason.HATE_OR_HARASSMENT), false, NOW.plusSeconds(10));
		long otherReporterId = account("other-reporter");
		insertRecipient(postId, otherReporterId, "AVAILABLE");
		safetyReportService.submitAnswerReport(
			otherReporterId, answerId, submission(ReportReason.PRIVACY_VIOLATION), false, NOW.plusSeconds(20));

		List<Report> myReports = safetyReportService.findMyReports(reporterId, null, null, 20);

		assertThat(myReports).hasSize(2);
		assertThat(myReports.get(0).createdAt()).isAfterOrEqualTo(myReports.get(1).createdAt());
		assertThat(myReports).allMatch(report -> report.reporterId() == reporterId);
	}

	@Test
	@DisplayName("본인 소유가 아닌 신고 조회는 404다 (403 아님 — 존재 비노출)")
	void findReportReturnsNotFoundForNonOwner() {
		ReportOutcome outcome = safetyReportService.submitAnswerReport(
			reporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW);
		long otherUserId = account("other-viewer");

		assertThatThrownBy(() -> safetyReportService.requireOwnReport(outcome.report().id(), otherUserId))
			.isInstanceOf(SafetyException.class)
			.hasFieldOrPropertyWithValue("errorCode", SafetyErrorCode.REPORT_NOT_FOUND);
	}

	@Test
	@DisplayName("SafetyController가 감싸는 차단·차단 해제 서비스 경로는 그대로 동작한다(#93 이후 회귀 없음)")
	void blockAndReleaseServiceCallsSucceed() {
		safetyService.block(reporterId, authorId, NOW);
		Integer blockCount = jdbc.queryForObject(
			"SELECT count(*) FROM user_block WHERE blocker_id = ? AND blocked_id = ? AND released_at IS NULL",
			Integer.class, reporterId, authorId);
		assertThat(blockCount).isEqualTo(1);

		safetyService.releaseBlock(reporterId, authorId, NOW.plusSeconds(10));
		Integer activeAfterRelease = jdbc.queryForObject(
			"SELECT count(*) FROM user_block WHERE blocker_id = ? AND blocked_id = ? AND released_at IS NULL",
			Integer.class, reporterId, authorId);
		assertThat(activeAfterRelease).isZero();
	}

	private ReportOutcome submitInTransaction(long asReporterId, CountDownLatch ready, CountDownLatch start)
		throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return transactionTemplate.execute(status ->
			safetyReportService.submitAnswerReport(
				asReporterId, answerId, submission(ReportReason.SPAM_OR_ADVERTISING), false, NOW));
	}

	private ReportSubmission submission(ReportReason reason) {
		return new ReportSubmission(reason, null, null);
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
			VALUES ('OPERATOR', 'ACTIVE', 'GH154 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), authorId);
	}

	private long post(long questionId) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', 'gh154-post', '질문글 본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}
}
