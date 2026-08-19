/**
 * Created at: 2026-08-19T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS-INT-006 through INT-010
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.feed.repository.InboxQueryRepository;
import com.dnd.qello.feed.repository.PostAnswerQueryRepository;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.SafetyRepository;
import com.dnd.qello.safety.service.SafetyCaseResolutionService;

@SpringBootTest
@ActiveProfiles("test")
class AnswerGlobalHideIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH155-HIDE";
	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private OutboxEventRepository outboxEventRepository;
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
			+ "VALUES (?, 'KR', 'GH155 Hide Test', 'REGION')", REGION);
	}

	@Test
	@DisplayName("INT-006: 전역 숨김하면 목록에서 빠지고 answer_count도 같이 줄어든다")
	void globalHideRemovesAnswerFromListAndCount() {
		long sender = account("sender");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		long postRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(postRecipientId, author, "답변 본문");

		List<Long> before = postAnswerQueryRepository
			.findAnswers(sender, postId, null, 20, NOW.plusSeconds(5)).stream()
			.map(card -> card.answerId()).toList();
		assertThat(before).contains(answerId);

		resolveWithActionedDecision(sender, answerId, NOW.plusSeconds(10));

		List<Long> after = postAnswerQueryRepository
			.findAnswers(sender, postId, null, 20, NOW.plusSeconds(20)).stream()
			.map(card -> card.answerId()).toList();
		assertThat(after).doesNotContain(answerId);
	}

	@Test
	@DisplayName("INT-007: 전역 숨김하면 unread_answer_count도 함께 줄어든다")
	void globalHideRemovesUnreadAnswerCount() {
		long sender = account("sender");
		long viewer = account("viewer");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		recipient(postId, viewer);
		long authorPostRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(authorPostRecipientId, author, "답변 본문");

		assertThat(findInboxCard(viewer, postId).orElseThrow().unreadAnswerCount()).isEqualTo(1);

		resolveWithActionedDecision(sender, answerId, NOW.plusSeconds(10));

		assertThat(findInboxCard(viewer, postId).orElseThrow().unreadAnswerCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("INT-008: 판정으로 전역 숨김되면 그 답변을 가리키던 기존 알림이 REVOKED로 전이된다")
	void globalHideRevokesExistingNotification() {
		long sender = account("sender");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		long postRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(postRecipientId, author, "답변 본문");
		OutboxEvent event = outboxEventRepository.save(OutboxEvent.pending(OutboxAggregateType.ANSWER, answerId,
			OutboxEventType.ANSWER_PUBLISHED, "answer-published:" + answerId,
			"{\"answerId\":" + answerId + "}", NOW));
		Notification notification = notificationRepository.save(new Notification(null, sender, event.id(),
			NotificationType.ANSWER_RECEIVED, "answer:" + answerId, null, answerId, null,
			NotificationStatus.UNREAD, NOW, null));

		resolveWithActionedDecision(sender, answerId, NOW.plusSeconds(10));

		Notification revoked = notificationRepository.findById(notification.id()).orElseThrow();
		assertThat(revoked.status()).isEqualTo(NotificationStatus.REVOKED);
		assertThat(revoked.readAt()).isNull();
	}

	// 이 이슈는 운영자 조치와 판정(resolveCase의 ACTIONED)에 의한 숨김만 배선한다 —
	// Answer.hide(at)를 직접 호출하는 경로는 아직 없다.
	private void resolveWithActionedDecision(long reporterId, long answerId, Instant at) {
		Report saved = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW));
		ReportCase opened = reportCaseRepository.save(ReportCase.open(null, null, answerId, NOW));
		safetyRepository.updateReport(saved.attachToCase(opened.id()));
		resolutionService.resolveCase(opened.id(), ModerationDecision.ACTIONED, at);
	}

	@Test
	@DisplayName("INT-009: 복원하면 목록과 카운트에 다시 나타난다")
	void restoreBringsAnswerBackToListAndCount() {
		long sender = account("sender");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		long postRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(postRecipientId, author, "답변 본문");
		Answer published = answerRepository.findById(answerId).orElseThrow();
		Answer hidden = answerRepository.save(published.hide(NOW.plusSeconds(10)));

		answerRepository.save(hidden.restore(NOW.plusSeconds(20)));

		List<Long> viewerAnswers = postAnswerQueryRepository
			.findAnswers(sender, postId, null, 20, NOW.plusSeconds(30)).stream()
			.map(card -> card.answerId()).toList();
		assertThat(viewerAnswers).contains(answerId);
	}

	@Test
	@DisplayName("INT-010: 숨김 기간에 미디어가 정리된 텍스트 없는 답변의 복원은 DB 제약 위반으로 실패한다")
	void restoringContentlessAnswerAfterMediaCleanupFails() {
		long sender = account("sender");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		long postRecipientId = recipient(postId, author);

		Answer submitted = Answer.submit(postRecipientId, author, "gh155-int010", null, REGION,
			BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		Answer saved = answerRepository.save(submitted);
		long mediaAssetId = mediaAsset(author);
		attachMedia(mediaAssetId, author, saved.getId());

		Answer published = answerRepository.save(saved.startSafetyCheck().markSafetyPassed().publish(NOW));
		Answer hidden = answerRepository.save(published.hide(NOW.plusSeconds(10)));

		// 숨김 기간에 미디어가 정리됐다고 가정한다.
		jdbc.update("UPDATE media_asset SET status = 'DELETED', deleted_at = ? WHERE id = ?",
			Timestamp.from(NOW.plusSeconds(15)), mediaAssetId);

		assertThatThrownBy(() -> answerRepository.save(hidden.restore(NOW.plusSeconds(20))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long mediaAsset(long ownerId) {
		return jdbc.queryForObject("""
			INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum, moderation_status)
			VALUES (?, 'READY', ?, 'image/jpeg', 1024, 'checksum-value', 'PASSED')
			RETURNING id
			""", Long.class, ownerId, "gh155-int010-" + System.nanoTime());
	}

	private void attachMedia(long mediaId, long ownerId, long answerId) {
		jdbc.update("""
			INSERT INTO media_attachment (media_id, owner_id, answer_id, display_order)
			VALUES (?, ?, ?, 0)
			""", mediaId, ownerId, answerId);
	}

	private java.util.Optional<InboxCard> findInboxCard(long recipientId, long postId) {
		return inboxQueryRepository.findInbox(recipientId, InboxCategory.UNANSWERED, null, NOW).stream()
			.filter(card -> card.postId() == postId)
			.findFirst();
	}

	private long publishedAnswer(long postRecipientId, long authorId, String bodyText) {
		Answer submitted = Answer.submit(postRecipientId, authorId, "gh155-hide-" + postRecipientId, bodyText,
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
