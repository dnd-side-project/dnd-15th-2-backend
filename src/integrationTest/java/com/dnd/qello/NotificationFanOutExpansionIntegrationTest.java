/**
 * Created at: 2026-08-20T19:25:00+09:00
 * Source scenario: TEST-PLAN-GH-177-NOTIFICATION-FANOUT-EXPANSION-INT-001,
 * INT-002, INT-003, INT-005, INT-006, INT-009, INT-011, INT-012, INT-014,
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-008 through INT-009
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.answer.service.AnswerReactionService;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.fanout.NotificationFanOutWorker;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.repository.NotificationInboxQueryRepository;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
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
	@Autowired private RecipientNotificationFanOutWorker recipientWorker;
	@Autowired private AnswerNotificationService answerNotificationService;
	@Autowired private AnswerReactionService answerReactionService;
	@Autowired private QuestionReviewService questionReviewService;
	@Autowired private QuestionAssignmentService questionAssignmentService;
	@Autowired private NotificationInboxQueryRepository inboxQuery;
	@Autowired private NotificationRepository notifications;
	@Autowired private NotificationPreferenceRepository preferences;
	@Autowired private OutboxEventRepository outboxEvents;

	private long questionAuthorId;
	private long answerAuthorId;
	private long proposerId;
	private long recommendeeId;
	private long operatorId;
	private long postRecipientId;

	@BeforeEach
	void setUpFixtures() {
		cleanupFixtures();
		ensureRegion();
		questionAuthorId = account("question-author", "USER");
		answerAuthorId = account("answer-author", "USER");
		proposerId = account("proposal-author", "USER");
		recommendeeId = account("recommended-user", "USER");
		operatorId = account("review-operator", "OPERATOR");
		postRecipientId = createOpenPostRecipient(questionAuthorId, answerAuthorId);
	}

	@AfterEach
	void tearDownFixtures() {
		cleanupFixtures();
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
		assertTargetKind(questionAuthorId, NotificationType.ANSWER_RECEIVED, "ANSWER");
		assertTargetKind(answerAuthorId, NotificationType.ANSWER_REACTED, "ANSWER");
		assertTargetKind(proposerId, NotificationType.QUESTION_PROPOSAL_REVIEWED, "NONE");
		assertTargetKind(recommendeeId, NotificationType.QUESTION_RECOMMENDED, "NONE");
	}

	@Test
	@DisplayName("답변 N개와 수신자 M명에서도 ANSWER_RECEIVED는 질문자에게 답변 수만큼만 fan-out한다")
	void answerPublishedFansOutPerAnswerOnlyToQuestionAuthor() {
		long postId = createOpenPost(questionAuthorId, "post-177-nm");
		long secondAnswerAuthorId = account("second-answer-author", "USER");
		long bystanderId = account("bystander", "USER");
		long firstRecipientId = createOpenPostRecipient(postId, answerAuthorId, 1);
		long secondRecipientId = createOpenPostRecipient(postId, secondAnswerAuthorId, 2);

		publishAnswer(firstRecipientId, answerAuthorId, "answer-177-n-1", 30);
		publishAnswer(secondRecipientId, secondAnswerAuthorId, "answer-177-n-2", 40);

		NotificationFanOutWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.claimed()).isEqualTo(2);
		assertThat(result.outcomes()).containsOnly(NotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(count(questionAuthorId, NotificationType.ANSWER_RECEIVED)).isEqualTo(2L);
		assertThat(count(answerAuthorId, NotificationType.ANSWER_RECEIVED)).isZero();
		assertThat(count(secondAnswerAuthorId, NotificationType.ANSWER_RECEIVED)).isZero();
		assertThat(count(bystanderId, NotificationType.ANSWER_RECEIVED)).isZero();
	}

	@Test
	@DisplayName("assignment 중간 실패는 cycle, assignment, outbox를 같은 transaction에서 롤백한다")
	void assignmentFailureRollsBackCycleAssignmentsAndOutbox() {
		long firstQuestionId = approvedQuestion("롤백 질문 1");
		long secondQuestionId = approvedQuestion("롤백 질문 2");

		assertThatThrownBy(() -> questionAssignmentService.assign(new QuestionAssignmentService.CycleCommand(
			recommendeeId, "cycle-177-rollback", "pool-v1", NOW, NOW.plus(1, ChronoUnit.HOURS),
			List.of(
				new QuestionAssignmentService.AssignmentCommand(firstQuestionId, 1, NOW),
				new QuestionAssignmentService.AssignmentCommand(secondQuestionId, 1, NOW.plusSeconds(1))
			))))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM question_assignment_cycle
			WHERE user_id = ? AND cycle_key = 'cycle-177-rollback'
			""", Long.class, recommendeeId)).isZero();
		assertThat(jdbc.queryForObject("""
			SELECT count(*)
			FROM question_assignment qa
			JOIN question_assignment_cycle qac ON qac.id = qa.cycle_id
			WHERE qac.user_id = ? AND qac.cycle_key = 'cycle-177-rollback'
			""", Long.class, recommendeeId)).isZero();
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM outbox_event
			WHERE event_type = 'QUESTION_RECOMMENDED'
			""", Long.class)).isZero();
	}

	@Test
	@DisplayName("기존 direction 알림과 새 네 종류 알림을 실제 consumer로 만들면 다섯 targetKind가 계약대로 노출된다")
	void listsFiveFanOutTypesWithExpectedTargetKinds() {
		outboxEvents.save(OutboxEvent.pending(
			OutboxAggregateType.POST_RECIPIENT, postRecipientId, OutboxEventType.RECIPIENTS_CONFIRMED,
			"gh177-direction-received:" + postRecipientId, "{}", NOW.plusSeconds(20)));
		assertThat(recipientWorker.processBatch(recipientCommand()).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);

		Answer answer = publishAnswer();
		answerReactionService.react(answer.getId(), questionAuthorId, NOW.plusSeconds(40));
		questionReviewService.reject(
			questionReviewService.startReview(
				questionReviewService.propose(proposerId, "targetKind 질문", NOW).getId()).getId(),
			operatorId, "정책 사유", NOW.plusSeconds(50));
		long approvedQuestionId = approvedQuestion("targetKind 추천 질문");
		questionAssignmentService.assign(new QuestionAssignmentService.CycleCommand(
			recommendeeId, "cycle-177-targets", "pool-v1", NOW, NOW.plus(1, ChronoUnit.HOURS),
			List.of(new QuestionAssignmentService.AssignmentCommand(approvedQuestionId, 1, NOW))));

		assertThat(worker.processBatch(command()).outcomes())
			.containsOnly(NotificationFanOutWorker.Outcome.PROCESSED);

		assertTargetKind(answerAuthorId, NotificationType.DIRECTION_POST_RECEIVED, "DIRECTION_POST");
		assertTargetKind(questionAuthorId, NotificationType.ANSWER_RECEIVED, "ANSWER");
		assertTargetKind(answerAuthorId, NotificationType.ANSWER_REACTED, "ANSWER");
		assertTargetKind(proposerId, NotificationType.QUESTION_PROPOSAL_REVIEWED, "NONE");
		assertTargetKind(recommendeeId, NotificationType.QUESTION_RECOMMENDED, "NONE");
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

	@ParameterizedTest(name = "global={0}, type={1}, delivery={2}")
	@MethodSource("answerPushGateCases")
	@DisplayName("ANSWER_RECEIVED fan-out은 global/type gate에 따라 notification은 남기고 delivery만 제어한다")
	void appliesGlobalAndTypePushGateForAnswerReceived(Boolean globalEnabled, Boolean typeEnabled,
		long expectedDeliveries) {
		activeDevice(questionAuthorId, "gate-answer-received");
		if (globalEnabled != null || typeEnabled != null) {
			savePushGate(questionAuthorId, NotificationType.ANSWER_RECEIVED, globalEnabled, typeEnabled);
		}
		publishAnswer();

		NotificationFanOutWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(NotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(count(questionAuthorId, NotificationType.ANSWER_RECEIVED)).isEqualTo(1L);
		assertThat(deliveryCount(questionAuthorId, NotificationType.ANSWER_RECEIVED)).isEqualTo(expectedDeliveries);
	}

	private Answer publishAnswer() {
		return publishAnswer(postRecipientId, answerAuthorId, "answer-177", 30);
	}

	private Answer publishAnswer(long targetPostRecipientId, long authorId, String key, long publishOffsetSeconds) {
		Answer draft = Answer.submit(targetPostRecipientId, authorId, key, "답변 본문", REGION,
			BigDecimal.TEN, "NEAR", NOW, 1000L);
		Answer saved = answerNotificationService.submit(draft);
		return answerNotificationService.publish(saved.getId(), NOW.plusSeconds(publishOffsetSeconds));
	}

	private long createOpenPostRecipient(long senderId, long recipientId) {
		long postId = createOpenPost(senderId, "post-177");
		return createOpenPostRecipient(postId, recipientId, 1);
	}

	private long createOpenPost(long senderId, String key) {
		long questionId = approvedQuestion("답변 받을 질문");
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text, coarse_region_code,
				 moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '질문 본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, key, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}

	private long createOpenPostRecipient(long postId, long recipientId, int index) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'OPENED', 'NEAR', ?, ?, ?, ?, ?, 190, 1000)
			RETURNING id
			""", Long.class, postId, recipientId, BigDecimal.valueOf(10L + index), REGION, Timestamp.from(NOW),
			Timestamp.from(NOW.plusSeconds(1)), Timestamp.from(NOW.plusSeconds(2)));
	}

	private long approvedQuestion(String text) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', ?, 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, "GH177 " + text, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), operatorId);
	}

	private long account(String nickname, String role) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES (?, ?, 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, role, role.equals("USER") ? "KR" : null, REGION, "gh177-" + nickname);
	}

	private PushDevice activeDevice(long userId, String fingerprint) {
		return notifications.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID, new byte[] {1, 2, 3},
			"gh177-" + fingerprint, PushDeviceStatus.ACTIVE, NOW, null));
	}

	private long count(long recipientId, NotificationType type) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM notification WHERE recipient_id = ? AND notification_type = ?
			""", Long.class, recipientId, type.name());
	}

	private long deliveryCount(long recipientId, NotificationType type) {
		return jdbc.queryForObject("""
			SELECT count(*)
			FROM notification_delivery nd
			JOIN notification n ON n.id = nd.notification_id
			WHERE n.recipient_id = ? AND n.notification_type = ?
			""", Long.class, recipientId, type.name());
	}

	private void assertTargetKind(long recipientId, NotificationType type, String targetKind) {
		assertThat(inboxQuery.list(recipientId, null, 20, NOW.plusSeconds(90)).items())
			.filteredOn(card -> card.type() == type)
			.extracting(card -> card.targetKind().name())
			.containsExactly(targetKind);
	}

	private NotificationFanOutWorker.BatchCommand command() {
		return new NotificationFanOutWorker.BatchCommand(10, OWNER, NOW.plusSeconds(60),
			NOW.plusSeconds(120), new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1)));
	}

	private RecipientNotificationFanOutWorker.BatchCommand recipientCommand() {
		return new RecipientNotificationFanOutWorker.BatchCommand(10, OWNER + "-recipient", NOW.plusSeconds(50),
			NOW.plusSeconds(110), new OutboxRetryPolicy(3, attempt -> java.time.Duration.ofSeconds(1)));
	}

	private static Stream<Arguments> answerPushGateCases() {
		return Stream.of(
			Arguments.of(null, null, 1L),
			Arguments.of(true, true, 1L),
			Arguments.of(false, true, 0L),
			Arguments.of(true, false, 0L));
	}

	private void savePushGate(long userId, NotificationType type, Boolean globalEnabled, Boolean typeEnabled) {
		if (globalEnabled != null) {
			preferences.saveUserSetting(userId, globalEnabled, null);
		}
		if (typeEnabled != null) {
			EnumMap<NotificationType, Boolean> preferencesByType = new EnumMap<>(NotificationType.class);
			for (NotificationType notificationType : NotificationType.values()) {
				preferencesByType.put(notificationType, true);
			}
			preferencesByType.put(type, typeEnabled);
			preferences.replaceTypePreferences(userId, preferencesByType);
		}
	}

	private void ensureRegion() {
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH177 Fanout', 'REGION')
			ON CONFLICT (code) DO NOTHING
			""", REGION);
	}

	private void cleanupFixtures() {
		jdbc.update("""
			DELETE FROM notification_delivery nd
			USING notification n
			WHERE nd.notification_id = n.id
			  AND n.recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM notification_seen_state
			WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM notification
			WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR direction_post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
			   OR answer_id IN (SELECT id FROM answer WHERE coarse_region_code = ?)
			""", REGION, REGION, REGION);
			jdbc.update("""
				DELETE FROM notification_preference
				WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
				""", REGION);
			jdbc.update("""
				DELETE FROM notification_user_setting
				WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
				""", REGION);
			jdbc.update("""
				DELETE FROM push_device
				WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM outbox_event
			WHERE (aggregate_type = 'POST_RECIPIENT'
			        AND aggregate_id IN (
			            SELECT pr.id
			            FROM post_recipient pr
			            JOIN direction_post dp ON dp.id = pr.post_id
			            WHERE dp.coarse_region_code = ?
			        ))
			   OR (aggregate_type = 'ANSWER'
			        AND aggregate_id IN (SELECT id FROM answer WHERE coarse_region_code = ?))
			   OR (aggregate_type = 'QUESTION_PROPOSAL'
			        AND aggregate_id IN (
			            SELECT qp.id
			            FROM question_proposal qp
			            JOIN user_account ua ON ua.id = qp.proposer_id
			            WHERE ua.coarse_region_code = ?
			        ))
			   OR (aggregate_type = 'QUESTION_ASSIGNMENT'
			        AND aggregate_id IN (
			            SELECT qa.id
			            FROM question_assignment qa
			            JOIN question_assignment_cycle qac ON qac.id = qa.cycle_id
			            JOIN user_account ua ON ua.id = qac.user_id
			            WHERE ua.coarse_region_code = ?
			        ))
			   OR dedup_key LIKE 'gh177-%'
			""", REGION, REGION, REGION, REGION);
		jdbc.update("""
			DELETE FROM user_block
			WHERE blocker_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR blocked_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("""
			DELETE FROM answer_reaction
			WHERE answer_id IN (SELECT id FROM answer WHERE coarse_region_code = ?)
			   OR reactor_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("DELETE FROM answer WHERE coarse_region_code = ?", REGION);
		jdbc.update("""
			DELETE FROM question_assignment
			WHERE cycle_id IN (
				SELECT qac.id
				FROM question_assignment_cycle qac
				JOIN user_account ua ON ua.id = qac.user_id
				WHERE ua.coarse_region_code = ?
			)
			   OR approved_question_id IN (
				SELECT aq.id
				FROM approved_question aq
				JOIN user_account ua ON ua.id = aq.approved_by
				WHERE ua.coarse_region_code = ?
			)
			""", REGION, REGION);
		jdbc.update("""
			DELETE FROM question_assignment_cycle
			WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM question_proposal_review
			WHERE proposal_id IN (
				SELECT qp.id
				FROM question_proposal qp
				JOIN user_account ua ON ua.id = qp.proposer_id
				WHERE ua.coarse_region_code = ?
			)
			   OR reviewer_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("""
			DELETE FROM question_proposal
			WHERE proposer_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM post_recipient
			WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
			   OR recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("""
			DELETE FROM post_audience
			WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("""
			DELETE FROM approved_question
			WHERE question_text LIKE 'GH177 %'
			   OR approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}
}
