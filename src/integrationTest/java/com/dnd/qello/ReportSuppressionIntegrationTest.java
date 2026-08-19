/**
 * Created at: 2026-08-19T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS-INT-001 through INT-005, INT-018
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.feed.repository.InboxQueryRepository;
import com.dnd.qello.feed.repository.PostAnswerQueryRepository;
import com.dnd.qello.feed.repository.SentPostQueryRepository;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostFilter;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.SafetyRepository;
import com.dnd.qello.safety.service.SafetyCaseResolutionService;

@SpringBootTest
@ActiveProfiles("test")
class ReportSuppressionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH155-SUP";
	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private SafetyRepository safetyRepository;
	@Autowired
	private ReportCaseRepository reportCaseRepository;
	@Autowired
	private SafetyCaseResolutionService resolutionService;
	@Autowired
	private PostAnswerQueryRepository postAnswerQueryRepository;
	@Autowired
	private InboxQueryRepository inboxQueryRepository;
	@Autowired
	private SentPostQueryRepository sentPostQueryRepository;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("TRUNCATE report_case_event");
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM report_case");
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
			+ "VALUES (?, 'KR', 'GH155 Suppression Test', 'REGION')", REGION);
	}

	@Test
	@DisplayName("INT-001: 신고자는 자신이 신고한 답변을 목록에서 보지 못하지만 다른 열람자는 본다")
	void reporterDoesNotSeeOwnReportedAnswerInList() {
		long reporterA = account("sender");
		long author = account("author");
		long questionId = question(reporterA);
		long postId = post(reporterA, questionId);
		long postRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(postRecipientId, author, "답변 본문");
		safetyRepository.saveReport(Report.forAnswer(reporterA, answerId, "SPAM_OR_ADVERTISING", null, NOW));

		List<Long> reporterView = postAnswerQueryRepository
			.findAnswers(reporterA, postId, null, 20, NOW).stream().map(card -> card.answerId()).toList();
		List<Long> authorView = postAnswerQueryRepository
			.findAnswers(author, postId, null, 20, NOW).stream().map(card -> card.answerId()).toList();

		assertThat(reporterView).doesNotContain(answerId);
		assertThat(authorView).contains(answerId);
	}

	@Test
	@DisplayName("INT-002: 신고자의 수신함 answer_count는 자신이 신고한 답변을 빼고 센다")
	void reporterInboxAnswerCountExcludesOwnReportedAnswer() {
		long sender = account("sender");
		long reporterA = account("reporterA");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		recipient(postId, reporterA);
		long authorPostRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(authorPostRecipientId, author, "답변 본문");

		InboxCard before = findInboxCard(reporterA, postId);
		assertThat(before.answerCount()).isEqualTo(1);

		safetyRepository.saveReport(Report.forAnswer(reporterA, answerId, "SPAM_OR_ADVERTISING", null, NOW));

		InboxCard after = findInboxCard(reporterA, postId);
		assertThat(after.answerCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("INT-003: 신고자의 수신함 unread_answer_count도 자신이 신고한 답변을 빼고 센다")
	void reporterInboxUnreadAnswerCountExcludesOwnReportedAnswer() {
		long sender = account("sender");
		long reporterA = account("reporterA");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		recipient(postId, reporterA);
		long authorPostRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(authorPostRecipientId, author, "답변 본문");

		assertThat(findInboxCard(reporterA, postId).unreadAnswerCount()).isEqualTo(1);

		safetyRepository.saveReport(Report.forAnswer(reporterA, answerId, "SPAM_OR_ADVERTISING", null, NOW));

		assertThat(findInboxCard(reporterA, postId).unreadAnswerCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("INT-004: 발신자 본인이 신고하면 보낸 질문 카드의 answer_count/unread_answer_count가 함께 줄어든다")
	void senderSentPostCountsExcludeOwnReportedAnswer() {
		long reporterA = account("sender");
		long author = account("author");
		long questionId = question(reporterA);
		long postId = post(reporterA, questionId);
		long postRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(postRecipientId, author, "답변 본문");

		SentPostCard before = findSentPostCard(reporterA, postId);
		assertThat(before.answerCount()).isEqualTo(1);
		assertThat(before.unreadAnswerCount()).isEqualTo(1);

		safetyRepository.saveReport(Report.forAnswer(reporterA, answerId, "SPAM_OR_ADVERTISING", null, NOW));

		SentPostCard after = findSentPostCard(reporterA, postId);
		assertThat(after.answerCount()).isEqualTo(0);
		assertThat(after.unreadAnswerCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("INT-005: 사건이 종결돼도 신고자 한정 숨김은 유지된다")
	void suppressionSurvivesCaseResolution() {
		long reporterA = account("sender");
		long author = account("author");
		long questionId = question(reporterA);
		long postId = post(reporterA, questionId);
		long postRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(postRecipientId, author, "답변 본문");
		Report saved = safetyRepository.saveReport(
			Report.forAnswer(reporterA, answerId, "SPAM_OR_ADVERTISING", null, NOW));
		ReportCase opened = reportCaseRepository.save(ReportCase.open(null, null, answerId, NOW));
		safetyRepository.updateReport(saved.attachToCase(opened.id()));

		resolutionService.resolveCase(opened.id(), ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		List<Long> reporterView = postAnswerQueryRepository
			.findAnswers(reporterA, postId, null, 20, NOW.plusSeconds(20)).stream()
			.map(card -> card.answerId()).toList();
		assertThat(reporterView).doesNotContain(answerId);
	}

	@Test
	@DisplayName("INT-018: report(reporter_id, answer_id) 부분 인덱스가 존재한다")
	void suppressionIndexExists() {
		Integer count = jdbc.queryForObject("""
			SELECT count(*) FROM pg_indexes
			WHERE tablename = 'report' AND indexname = 'idx_report_reporter_answer_suppression'
			""", Integer.class);
		assertThat(count).isEqualTo(1);

		String indexDef = jdbc.queryForObject("""
			SELECT indexdef FROM pg_indexes
			WHERE tablename = 'report' AND indexname = 'idx_report_reporter_answer_suppression'
			""", String.class);
		assertThat(indexDef).contains("answer_id IS NOT NULL");
	}

	private InboxCard findInboxCard(long recipientId, long postId) {
		return inboxQueryRepository.findInbox(recipientId, InboxCategory.UNANSWERED, null, NOW).stream()
			.filter(card -> card.postId() == postId)
			.findFirst().orElseThrow();
	}

	private SentPostCard findSentPostCard(long senderId, long postId) {
		return sentPostQueryRepository.findSentPosts(senderId, SentPostFilter.ALL, null, 20, NOW).stream()
			.filter(card -> card.postId() == postId)
			.findFirst().orElseThrow();
	}

	private long publishedAnswer(long postRecipientId, long authorId, String bodyText) {
		Answer submitted = Answer.submit(postRecipientId, authorId, "gh155-sup-" + postRecipientId, bodyText,
			REGION, BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		Answer published = submitted.startSafetyCheck().markSafetyPassed().publish(NOW);
		return answerRepository.save(published).getId();
	}

	private long recipient(long postId, long recipientId) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION, Timestamp.from(NOW));
	}

	private long account(String nicknamePrefix) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nicknamePrefix + "-" + System.nanoTime());
	}

	private long question(long approverId) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'GH155 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), approverId);
	}

	private long post(long senderId, long questionId) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '글', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, "gh155-post-" + System.nanoTime(), REGION, Timestamp.from(NOW),
			Timestamp.from(NOW), Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}
}
