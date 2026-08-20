/**
 * Created at: 2026-08-20T19:25:00+09:00
 * Source scenario: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-001,
 * INT-002, INT-005, INT-006, INT-009, INT-011, INT-012, INT-014
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.answer.service.AnswerReactionService;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.fanout.NotificationFanOutWorker;
import com.dnd.qello.notification.repository.NotificationInboxQueryRepository;
import com.dnd.qello.question.service.QuestionAssignmentService;
import com.dnd.qello.question.service.QuestionReviewService;

@SpringBootTest
@ActiveProfiles("test")
class NotificationFanOutExpansionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH177-FANOUT";
	private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
	private static final String OWNER = "gh177-fanout-worker";

	@Autowired private JdbcTemplate jdbc;
	@Autowired private NotificationFanOutWorker worker;
	@Autowired private AnswerNotificationService answerNotificationService;
	@Autowired private AnswerReactionService answerReactionService;
	@Autowired private QuestionReviewService questionReviewService;
	@Autowired private QuestionAssignmentService questionAssignmentService;
	@Autowired private NotificationInboxQueryRepository inboxQuery;

	private long questionAuthorId;
	private long answerAuthorId;
	private long proposerId;
	private long recommendeeId;
	private long operatorId;
	private long postRecipientId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM notification_preference");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM answer_reaction");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM question_assignment");
		jdbc.update("DELETE FROM question_assignment_cycle");
		jdbc.update("DELETE FROM question_proposal_review");
		jdbc.update("DELETE FROM question_proposal");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH177 Fanout', 'REGION')
			""", REGION);

		questionAuthorId = account("question-author", "USER");
		answerAuthorId = account("answer-author", "USER");
		proposerId = account("proposal-author", "USER");
		recommendeeId = account("recommended-user", "USER");
		operatorId = account("review-operator", "OPERATOR");
		postRecipientId = createOpenPostRecipient(questionAuthorId, answerAuthorId);
	}

	@Test
	@DisplayName("네 notification 종류를 PostgreSQL outbox에서 fan-out하고 질문자 1명 규칙과 targetKind를 지킨다")
	void fansOutAllNotificationTypesWithCorrectRecipientsAndTargets() {
		Answer answer = publishAnswer();
		answerReactionService.react(answer.getId(), questionAuthorId, NOW.plusSeconds(40));
		questionReviewService.reject(
			questionReviewService.startReview(
				questionReviewService.propose(proposerId, "검토할 질문", NOW).getId()).getId(),
			operatorId, "정책 사유", NOW.plusSeconds(50));
		long approvedQuestionId = approvedQuestion("추천 질문");
		questionAssignmentService.assign(new QuestionAssignmentService.CycleCommand(
			recommendeeId, "cycle-177", "pool-v1", NOW, NOW.plus(1, ChronoUnit.HOURS),
			List.of(new QuestionAssignmentService.AssignmentCommand(approvedQuestionId, 1, NOW))));

		NotificationFanOutWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.claimed()).isEqualTo(4);
		assertThat(result.outcomes()).containsOnly(NotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(count(questionAuthorId, NotificationType.ANSWER_RECEIVED)).isEqualTo(1L);
		assertThat(count(answerAuthorId, NotificationType.ANSWER_REACTED)).isEqualTo(1L);
		assertThat(count(proposerId, NotificationType.QUESTION_PROPOSAL_REVIEWED)).isEqualTo(1L);
		assertThat(count(recommendeeId, NotificationType.QUESTION_RECOMMENDED)).isEqualTo(1L);
		assertThat(count(answerAuthorId, NotificationType.ANSWER_RECEIVED)).isZero();
		assertThat(count(recommendeeId, NotificationType.ANSWER_REACTED)).isZero();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE status = 'PROCESSED'", Long.class))
			.isEqualTo(4L);
		assertThat(inboxQuery.list(questionAuthorId, null, 20, NOW.plusSeconds(60)).items())
			.filteredOn(card -> card.type() == NotificationType.ANSWER_RECEIVED)
			.extracting(card -> card.targetKind().name())
			.containsExactly("ANSWER");
		assertThat(inboxQuery.list(proposerId, null, 20, NOW.plusSeconds(60)).items())
			.extracting(card -> card.targetKind().name())
			.containsExactly("NONE");
		assertThat(inboxQuery.list(recommendeeId, null, 20, NOW.plusSeconds(60)).items())
			.extracting(card -> card.targetKind().name())
			.containsExactly("NONE");
	}

	@Test
	@DisplayName("같은 ANSWER_PUBLISHED event를 lease 재처리해도 notification은 한 건으로 수렴한다")
	void deduplicatesRepeatedAnswerPublishedEvent() {
		Answer answer = publishAnswer();
		NotificationFanOutWorker.BatchResult first = worker.processBatch(command());

		assertThat(first.outcomes()).containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(count(questionAuthorId, NotificationType.ANSWER_RECEIVED)).isEqualTo(1L);
		jdbc.update("""
			UPDATE outbox_event
			SET status = 'PENDING', processed_at = NULL, lease_owner = NULL,
				lease_expires_at = NULL, next_attempt_at = ?
			WHERE event_type = 'ANSWER_PUBLISHED'
			""", Timestamp.from(NOW.plusSeconds(61)));
		NotificationFanOutWorker.BatchResult replay = worker.processBatch(new NotificationFanOutWorker.BatchCommand(
			10, OWNER, NOW.plusSeconds(61), NOW.plusSeconds(121),
			new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1))));
		assertThat(replay.outcomes()).containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE answer_id = ?", Long.class, answer.getId()))
			.isEqualTo(1L);
	}

	@Test
	@DisplayName("같은 답변 공감의 동시 요청은 reaction과 ANSWER_REACTED outbox를 각각 한 건으로 수렴한다")
	void concurrentReactionCreatesOneReactionAndOneOutbox() throws Exception {
		Answer answer = publishAnswer();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Long>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < 2; i++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return answerReactionService.react(answer.getId(), questionAuthorId, NOW.plusSeconds(40));
				}));
			}
			assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(futures.get(0).get()).isPositive();
			assertThat(futures.get(1).get()).isPositive();
		} finally {
			executor.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_reaction WHERE answer_id = ? AND reactor_id = ?",
			Long.class, answer.getId(), questionAuthorId)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_REACTED'",
			Long.class)).isEqualTo(1L);
	}

	private Answer publishAnswer() {
		Answer draft = Answer.submit(postRecipientId, answerAuthorId, "answer-177", "답변 본문", REGION,
			BigDecimal.TEN, "NEAR", NOW, 1000L);
		Answer saved = answerNotificationService.submit(draft);
		return answerNotificationService.publish(saved.getId(), NOW.plusSeconds(30));
	}

	private long createOpenPostRecipient(long senderId, long recipientId) {
		long questionId = approvedQuestion("답변 받을 질문");
		long postId = jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text, coarse_region_code,
				 moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', 'post-177', '질문 본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'OPENED', 'NEAR', 10, ?, ?, ?, ?, 190, 1000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION, Timestamp.from(NOW),
			Timestamp.from(NOW.plusSeconds(1)), Timestamp.from(NOW.plusSeconds(2)));
	}

	private long approvedQuestion(String text) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', ?, 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, text, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), operatorId);
	}

	private long account(String nickname, String role) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES (?, ?, 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, role, role.equals("USER") ? "KR" : null, REGION, nickname);
	}

	private long count(long recipientId, NotificationType type) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM notification WHERE recipient_id = ? AND notification_type = ?
			""", Long.class, recipientId, type.name());
	}

	private NotificationFanOutWorker.BatchCommand command() {
		return new NotificationFanOutWorker.BatchCommand(10, OWNER, NOW.plusSeconds(60),
			NOW.plusSeconds(120), new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1)));
	}
}
