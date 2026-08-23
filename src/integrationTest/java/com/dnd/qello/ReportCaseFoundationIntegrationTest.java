/**
 * Created at: 2026-08-17T20:15:00+09:00
 * Source scenario: TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION-INT-001 through INT-013
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseQueue;
import com.dnd.qello.safety.domain.ReportCaseSeverity;
import com.dnd.qello.safety.domain.ReportContentSnapshot;
import com.dnd.qello.safety.domain.ReportTargetType;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.ReportContentSnapshotRepository;
import com.dnd.qello.safety.repository.SafetyRepository;

@SpringBootTest
@ActiveProfiles("test")
class ReportCaseFoundationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH153";
	private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private SafetyRepository safetyRepository;
	@Autowired
	private ReportCaseRepository reportCaseRepository;
	@Autowired
	private ReportContentSnapshotRepository reportContentSnapshotRepository;

	private long reporterId;
	private long authorId;
	private long senderId;
	private long postId;
	private long answerId;

	@BeforeEach
	void resetSchemaFixtures() {
		// report_case_event·report_content_snapshot는 append-only 트리거가 DELETE도
		// 막는다(INV-RPT-004). TRUNCATE는 row-level BEFORE DELETE 트리거를 발동시키지
		// 않으므로 fixture 초기화에는 이것만 쓴다.
		jdbc.update("TRUNCATE report_case_event");
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM moderation_review");
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
			+ "VALUES (?, 'KR', 'GH153 Test', 'REGION')", REGION);

		reporterId = account("reporter");
		authorId = account("author");
		senderId = account("sender");
		long questionId = question();
		postId = post(questionId);
		long postRecipientId = jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, authorId, REGION, Timestamp.from(NOW));
		answerId = answerRepository.save(Answer.submit(postRecipientId, authorId, "gh153-answer",
			"답변", REGION, BigDecimal.valueOf(90), "NEAR", NOW, 5000L)).getId();
	}

	@Test
	@DisplayName("동일 대상에 대한 두 사건 생성 경쟁은 하나만 성공한다 (INV-RPT-001)")
	void concurrentCaseCreationForSameAnswerAllowsOnlyOneWinner() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = List.of(
				executor.submit(() -> openCaseInTransaction(ready, start)),
				executor.submit(() -> openCaseInTransaction(ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			boolean firstSucceeded = results.get(0).get(10, TimeUnit.SECONDS);
			boolean secondSucceeded = results.get(1).get(10, TimeUnit.SECONDS);
			assertThat(firstSucceeded ^ secondSucceeded).isTrue();
			assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM report_case WHERE answer_id = ?", Integer.class, answerId)).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("이미 열린 사건이 있는 대상에 새 OPEN 사건을 삽입하면 unique violation이 발생한다")
	void rejectsSecondOpenCaseForSameTarget() {
		reportCaseRepository.save(open(null, postId, null, NOW));

		assertThatThrownBy(() -> reportCaseRepository.save(open(null, postId, null, NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("종결된 사건이 있는 대상에는 새 OPEN 사건을 만들 수 있다 — 재발은 새 사건이다")
	void allowsNewCaseForTargetWithResolvedCase() {
		ReportCase opened = reportCaseRepository.save(open(null, null, answerId, NOW));
		reportCaseRepository.update(opened.resolve(ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10)));

		ReportCase reopened = reportCaseRepository.save(open(null, null, answerId, NOW.plusSeconds(20)));

		assertThat(reopened.id()).isNotEqualTo(opened.id());
	}

	@Test
	@DisplayName("신고 하나에는 증거 스냅샷이 정확히 하나만 존재할 수 있다 (INV-RPT-003)")
	void allowsExactlyOneSnapshotPerReport() {
		Report report = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW));

		reportContentSnapshotRepository.save(snapshot(report.id()));

		assertThatThrownBy(() -> reportContentSnapshotRepository.save(snapshot(report.id())))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("증거 스냅샷은 저장된 뒤 UPDATE할 수 없다 (INV-RPT-004)")
	void snapshotCannotBeUpdatedAfterCreation() {
		Report report = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW));
		reportContentSnapshotRepository.save(snapshot(report.id()));

		assertThatThrownBy(() -> jdbc.update(
			"UPDATE report_content_snapshot SET body_text = 'tampered' WHERE report_id = ?", report.id()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("증거 스냅샷은 저장된 뒤 DELETE할 수 없다 (INV-RPT-004) — 기존 선례는 DELETE를 막지 않으므로 별도 확인한다")
	void snapshotCannotBeDeletedAfterCreation() {
		Report report = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW));
		reportContentSnapshotRepository.save(snapshot(report.id()));

		assertThatThrownBy(() -> jdbc.update("DELETE FROM report_content_snapshot WHERE report_id = ?", report.id()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("사건 이력은 저장된 뒤 UPDATE·DELETE할 수 없다 (INV-RPT-004)")
	void caseEventCannotBeUpdatedOrDeletedAfterCreation() {
		ReportCase reportCase = reportCaseRepository.save(open(null, null, answerId, NOW));
		long eventId = jdbc.queryForObject("""
			INSERT INTO report_case_event (case_id, event_type, occurred_at)
			VALUES (?, 'CASE_OPENED', ?) RETURNING id
			""", Long.class, reportCase.id(), Timestamp.from(NOW));

		assertThatThrownBy(() -> jdbc.update(
			"UPDATE report_case_event SET detail = 'tampered' WHERE id = ?", eventId))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM report_case_event WHERE id = ?", eventId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("답변이 삭제된 뒤에도 증거 스냅샷은 그대로 조회되고, 스냅샷은 답변·계정을 FK로 참조하지 않는다")
	void snapshotSurvivesAnswerDeletionAndHasNoForeignKeyToTarget() {
		Report report = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW));
		reportContentSnapshotRepository.save(snapshot(report.id()));

		jdbc.update("UPDATE answer SET status = 'DELETED', deleted_at = ? WHERE id = ?",
			Timestamp.from(NOW.plusSeconds(10)), answerId);

		ReportContentSnapshot survived = reportContentSnapshotRepository.findByReportId(report.id()).orElseThrow();
		assertThat(survived.bodyText()).isEqualTo("신고 시점 본문");
		assertThat(survived.mediaObjectKeys()).containsExactlyInAnyOrder("media-a", "media-b");

		Integer fkToTargetTables = jdbc.queryForObject("""
			SELECT count(*) FROM information_schema.table_constraints tc
			JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
			WHERE tc.table_name = 'report_content_snapshot' AND tc.constraint_type = 'FOREIGN KEY'
			  AND ccu.table_name IN ('answer', 'user_account', 'direction_post')
			""", Integer.class);
		assertThat(fkToTargetTables).isZero();
	}

	@Test
	@DisplayName("사유-하위사유 조합은 CHECK 제약이 강제한다")
	void reasonSubReasonPairingIsEnforcedByCheckConstraint() {
		// 서로 다른 reporterId를 써서 uq_open_report_answer가 아니라 ck_report_sub_reason만 검증한다.
		insertReportRow(reporterId, "SEXUAL_CONTENT", "CSAM");

		assertThatThrownBy(() -> insertReportRow(senderId, "SPAM_OR_ADVERTISING", "CSAM"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("카탈로그 밖의 reason_code는 CHECK 제약이 거부한다")
	void unknownReasonCodeIsRejectedByCheckConstraint() {
		assertThatThrownBy(() -> insertReportRow(reporterId, "NOT_A_REAL_REASON", null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("MORE_INFO_REQUIRED 상태에서도 같은 신고자·같은 대상의 중복 신고는 여전히 차단된다 (INV-RPT-002)")
	void moreInfoRequestedReportStillBlocksDuplicateReport() {
		Report moreInfo = Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW)
			.requestMoreInfo(NOW.plusSeconds(10));
		safetyRepository.saveReport(moreInfo);

		assertThatThrownBy(() -> safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW.plusSeconds(20))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("새 컬럼(case_id, sub_reason_code) 추가 후에도 기존 신고 조회는 정상 동작한다")
	void existingReportQueriesAreUnaffectedByNewColumns() {
		Report saved = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", "detail", NOW));

		assertThat(safetyRepository.findReportById(saved.id())).contains(saved);
		assertThat(safetyRepository.findOpenReport(reporterId, null, null, answerId)).contains(saved);
		assertThat(saved.caseId()).isNull();
		assertThat(saved.subReasonCode()).isNull();
	}

	@Test
	@DisplayName("notification.report_id만 채운 알림은 저장되고, report_id와 answer_id를 함께 채우면 거절된다")
	void notificationTargetCheckAllowsAtMostOneTarget() {
		Report report = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW));
		long outboxEventId = jdbc.queryForObject("""
			INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, dedup_key, payload)
			VALUES ('REPORT', ?, 'REPORT_RESOLVED', ?, '{}') RETURNING id
			""", Long.class, report.id(), "gh153-notification-dedup-" + report.id());

		int inserted = jdbc.update("""
			INSERT INTO notification (recipient_id, outbox_event_id, notification_type, dedup_key, report_id)
			VALUES (?, ?, 'REPORT_RESOLVED', ?, ?)
			""", reporterId, outboxEventId, "gh153-notification-report-only-" + report.id(), report.id());
		assertThat(inserted).isEqualTo(1);

		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO notification (recipient_id, outbox_event_id, notification_type, dedup_key, report_id, answer_id)
			VALUES (?, ?, 'ANSWER_RECEIVED', ?, ?, ?)
			""", reporterId, outboxEventId, "gh153-notification-two-targets-" + report.id(), report.id(), answerId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private boolean openCaseInTransaction(CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		try {
			return transactionTemplate.execute(status -> {
				reportCaseRepository.save(open(null, null, answerId, NOW));
				return true;
			});
		} catch (DataIntegrityViolationException exception) {
			return false;
		}
	}

	private void insertReportRow(long asReporterId, String reasonCode, String subReasonCode) {
		jdbc.update("""
			INSERT INTO report (reporter_id, answer_id, reason_code, sub_reason_code, status, created_at)
			VALUES (?, ?, ?, ?, 'RECEIVED', ?)
			""", asReporterId, answerId, reasonCode, subReasonCode, Timestamp.from(NOW));
	}

	private ReportContentSnapshot snapshot(long reportId) {
		return ReportContentSnapshot.capture(reportId, NOW, ReportTargetType.ANSWER, answerId, authorId,
			"신고 시점 본문", List.of("media-b", "media-a"), 0, NOW, null);
	}

	private static ReportCase open(Long targetUserId, Long directionPostId, Long answerId, Instant now) {
		return ReportCase.open(targetUserId, directionPostId, answerId,
			ReportCaseSeverity.NORMAL, ReportCaseQueue.STANDARD, now, now.plus(Duration.ofDays(3)));
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
			VALUES ('OPERATOR', 'ACTIVE', 'GH153 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), authorId);
	}

	private long post(long questionId) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', 'gh153-post', '글', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}
}
