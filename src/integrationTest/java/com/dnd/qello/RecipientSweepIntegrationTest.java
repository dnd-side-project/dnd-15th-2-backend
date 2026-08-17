/**
 * Created at: 2026-08-17T20:49:31+09:00
 * Source scenario: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP-INT-001 through INT-012
 * Source scenario: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP-INT-016 through INT-017
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.direction.config.SkipConfirmationProperties;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;
import com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker;
import com.dnd.qello.direction.sweep.SkipConfirmationSweepWorker;
import com.dnd.qello.direction.sweep.SweepBatchResult;

/**
 * 만료·넘김 확정 sweep 실행기(#126)가 {@link ReceiveSlotReleaseService}의 기존 슬롯
 * 해제 보장(#93) 위에서 처리량 제한·재실행 안전성·행 단위 실패 격리를 지키는지
 * 검증한다. 슬롯 해제 자체의 기본 동작은 ReceiveSlotReleaseIntegrationTest(#93)가
 * 이미 소유하므로 이 클래스는 그 위에 얹히는 batch 실행기만 다룬다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecipientSweepIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-EXPSWEEP";
	private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
	private static final Instant BASELINE = NOW.minusSeconds(3600);
	private static final long DEFAULT_DISTANCE_M = 5_000L;

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private RecipientExpirationSweepWorker expirationSweepWorker;
	@Autowired
	private SkipConfirmationSweepWorker skipConfirmationSweepWorker;
	@Autowired
	private ReceiveSlotReleaseService receiveSlotReleaseService;
	@Autowired
	private AnswerNotificationService answerNotificationService;
	@Autowired
	private SkipConfirmationProperties skipConfirmationProperties;

	private long senderId;
	private long recipientId;
	private int graceSeconds;

	@BeforeEach
	void resetFixtures() {
		graceSeconds = skipConfirmationProperties.skipConfirmationGraceSeconds();
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Recipient Sweep Test', 'REGION')", REGION);

		senderId = account("expsweep-sender");
		recipientId = account("expsweep-recipient");
	}

	@Test
	@DisplayName("만료 sweep은 만료된 AVAILABLE 항목을 EXPIRED로 전이하고 슬롯을 한 번 해제한다")
	void expirationSweepReleasesSlotForExpiredAvailableRecipient() {
		long questionId = question();
		long postId = post(senderId, questionId, "int001-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		SweepBatchResult result = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.scanned()).isEqualTo(1);
		assertThat(result.released()).isEqualTo(1);
		assertThat(status(prId)).isEqualTo("EXPIRED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("같은 만료 sweep을 재실행해도 카운터가 중복 감소하지 않는다")
	void reRunningExpirationSweepDoesNotDoubleReleaseTheSlot() {
		long questionId = question();
		long postId = post(senderId, questionId, "int002-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);
		expirationSweepWorker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		SweepBatchResult second = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW.plusSeconds(10)));

		assertThat(second.scanned()).isZero();
		assertThat(second.released()).isZero();
		assertThat(status(prId)).isEqualTo("EXPIRED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("만료되지 않은 질문글의 수신 항목은 그대로 둔다")
	void expirationSweepLeavesNonExpiredRecipientsUntouched() {
		long questionId = question();
		long postId = post(senderId, questionId, "int003-post", NOW.plusSeconds(3600));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		SweepBatchResult result = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.scanned()).isZero();
		assertThat(status(prId)).isEqualTo("AVAILABLE");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("만료 sweep은 SKIP_PENDING 항목을 후보에서 제외한다")
	void expirationSweepExcludesSkipPendingRecipients() {
		long questionId = question();
		long postId = post(senderId, questionId, "int004-post", NOW.minusSeconds(60));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(30));
		receiveState(recipientId, 1);

		SweepBatchResult result = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.scanned()).isZero();
		assertThat(status(prId)).isEqualTo("SKIP_PENDING");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("만료 sweep은 검사 중인 답변이 있는 수신 항목을 후보에서 제외한다")
	void expirationSweepExcludesRecipientsWithPendingAnswers() {
		long questionId = question();
		long postId = post(senderId, questionId, "int005-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);
		answerNotificationService.submit(Answer.submit(prId, recipientId, "int005-answer",
			"답변 본문", REGION, BigDecimal.valueOf(90), "NEAR", NOW.minusSeconds(120), DEFAULT_DISTANCE_M));

		SweepBatchResult result = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.scanned()).isZero();
		assertThat(status(prId)).isEqualTo("AVAILABLE");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("후보 조회 이후 답변이 제출되면 잠금 재검사로 만료를 거부한다")
	void expirationSweepRejectsStaleCandidateAfterAnswerIsSubmitted() {
		long questionId = question();
		long postId = post(senderId, questionId, "int006-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		List<PostRecipient> stale =
			receiveSlotReleaseService.findExpirable(NOW, 10);
		assertThat(stale).hasSize(1);
		answerNotificationService.submit(Answer.submit(prId, recipientId, "int006-answer",
			"답변 본문", REGION, BigDecimal.valueOf(90), "NEAR", NOW.minusSeconds(30), DEFAULT_DISTANCE_M));

		var result = receiveSlotReleaseService.expire(stale.get(0).getId(), NOW);

		assertThat(result).isEmpty();
		assertThat(status(prId)).isEqualTo("AVAILABLE");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("유예가 지나지 않은 SKIP_PENDING은 넘김확정 sweep에서 확정되지 않는다")
	void skipConfirmationSweepDoesNotConfirmBeforeGraceElapses() {
		long questionId = question();
		long postId = post(senderId, questionId, "int007-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(graceSeconds - 1));
		receiveState(recipientId, 1);

		SweepBatchResult result = skipConfirmationSweepWorker.processBatch(
			new SkipConfirmationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.scanned()).isZero();
		assertThat(status(prId)).isEqualTo("SKIP_PENDING");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("유예 시간이 정확히 지난 SKIP_PENDING은 넘김확정 sweep에서 확정된다")
	void skipConfirmationSweepConfirmsExactlyAtGraceBoundary() {
		long questionId = question();
		long postId = post(senderId, questionId, "int008-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(graceSeconds));
		receiveState(recipientId, 1);

		SweepBatchResult result = skipConfirmationSweepWorker.processBatch(
			new SkipConfirmationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.released()).isEqualTo(1);
		assertThat(status(prId)).isEqualTo("SKIPPED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("유예가 지난 SKIP_PENDING은 확정되어 capacity_released_at을 함께 남긴다")
	void skipConfirmationSweepSetsCapacityReleasedAtOnConfirmation() {
		long questionId = question();
		long postId = post(senderId, questionId, "int009-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(graceSeconds + 1));
		receiveState(recipientId, 1);

		SweepBatchResult result = skipConfirmationSweepWorker.processBatch(
			new SkipConfirmationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.released()).isEqualTo(1);
		assertThat(status(prId)).isEqualTo("SKIPPED");
		assertThat(capacityReleasedAt(prId)).isNotNull();
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("같은 넘김확정 sweep을 재실행해도 카운터가 중복 감소하지 않는다")
	void reRunningSkipConfirmationSweepDoesNotDoubleReleaseTheSlot() {
		long questionId = question();
		long postId = post(senderId, questionId, "int010-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(graceSeconds + 1));
		receiveState(recipientId, 1);
		skipConfirmationSweepWorker.processBatch(new SkipConfirmationSweepWorker.BatchCommand(10, NOW));

		SweepBatchResult second = skipConfirmationSweepWorker.processBatch(
			new SkipConfirmationSweepWorker.BatchCommand(10, NOW.plusSeconds(10)));

		assertThat(second.scanned()).isZero();
		assertThat(second.released()).isZero();
		assertThat(status(prId)).isEqualTo("SKIPPED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("limit보다 후보가 많으면 반복 실행이 남은 대상을 결정적 순서로 모두 소진한다")
	void expirationSweepPagesThroughCandidatesInDeterministicOrder() {
		long questionId = question();
		List<Long> postRecipientIds = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			long postId = post(senderId, questionId, "int011-post-" + index, NOW.minusSeconds(60 - index));
			postRecipientIds.add(available(postId, recipientId));
		}
		receiveState(recipientId, 5);

		SweepBatchResult first = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(2, NOW));
		SweepBatchResult secondRound = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(2, NOW));
		SweepBatchResult third = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(2, NOW));

		assertThat(first.scanned()).isEqualTo(2);
		assertThat(secondRound.scanned()).isEqualTo(2);
		assertThat(third.scanned()).isEqualTo(1);
		for (long prId : postRecipientIds) {
			assertThat(status(prId)).isEqualTo("EXPIRED");
		}
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("한 행의 처리 실패가 같은 batch의 나머지 행 커밋을 되돌리지 않는다")
	void expirationSweepIsolatesOneRowFailureFromTheRestOfTheBatch() {
		long questionId = question();
		long postId1 = post(senderId, questionId, "int012-post1", NOW.minusSeconds(60));
		long postId2 = post(senderId, questionId, "int012-post2", NOW.minusSeconds(50));
		long postId3 = post(senderId, questionId, "int012-post3", NOW.minusSeconds(40));
		long recipient2 = account("expsweep-recipient-2");
		long recipient3 = account("expsweep-recipient-3");
		long prId1 = available(postId1, recipientId);
		long prId2 = available(postId2, recipient2);
		long prId3 = available(postId3, recipient3);
		receiveState(recipientId, 1);
		receiveState(recipient2, 1);
		receiveState(recipient3, 1);

		// receiveSlotReleaseService는 @Transactional 프록시라 그대로 spy()하면 Mockito가
		// 프록시 언랩에 실패한다(UnfinishedStubbingException). AopTestUtils로 실제 대상을
		// 꺼내 spy한다 — 이 경로는 트랜잭션 경계 없이 개별 문장이 바로 커밋되지만, 이
		// 시나리오는 실패 행 격리만 검증하면 되므로 영향이 없다.
		ReceiveSlotReleaseService target = AopTestUtils.getUltimateTargetObject(receiveSlotReleaseService);
		ReceiveSlotReleaseService spyService = spy(target);
		doThrow(new RuntimeException("주입된 실패")).when(spyService).expire(eq(prId2), any());
		RecipientExpirationSweepWorker workerWithInjectedFailure =
			new RecipientExpirationSweepWorker(spyService, Clock.fixed(NOW, ZoneOffset.UTC));

		SweepBatchResult result = workerWithInjectedFailure.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.released()).isEqualTo(2);
		assertThat(status(prId1)).isEqualTo("EXPIRED");
		assertThat(status(prId2)).isEqualTo("AVAILABLE");
		assertThat(status(prId3)).isEqualTo("EXPIRED");
		assertThat(activeCount(recipientId)).isZero();
		assertThat(activeCount(recipient2)).isEqualTo(1);
		assertThat(activeCount(recipient3)).isZero();
	}

	@Test
	@DisplayName("한 사용자의 서로 다른 질문글 만료 항목 여러 건을 한 sweep으로 모두 해제한다")
	void expirationSweepReleasesMultipleExpiredItemsForTheSameRecipient() {
		long questionId = question();
		long postId1 = post(senderId, questionId, "int016-post1", NOW.minusSeconds(60));
		long postId2 = post(senderId, questionId, "int016-post2", NOW.minusSeconds(50));
		long postId3 = post(senderId, questionId, "int016-post3", NOW.minusSeconds(40));
		long prId1 = available(postId1, recipientId);
		long prId2 = available(postId2, recipientId);
		long prId3 = available(postId3, recipientId);
		receiveState(recipientId, 3);

		SweepBatchResult result = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.released()).isEqualTo(3);
		assertThat(status(prId1)).isEqualTo("EXPIRED");
		assertThat(status(prId2)).isEqualTo("EXPIRED");
		assertThat(status(prId3)).isEqualTo("EXPIRED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("카운터가 이미 0인 상태에서 만료 전이가 발생해도 예외 없이 커밋된다")
	void expirationSweepCommitsEvenWhenCounterIsAlreadyZero() {
		long questionId = question();
		long postId = post(senderId, questionId, "int017-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 0);

		SweepBatchResult result = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.released()).isEqualTo(1);
		assertThat(status(prId)).isEqualTo("EXPIRED");
		assertThat(activeCount(recipientId)).isZero();
	}

	private String status(long postRecipientId) {
		return jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId);
	}

	private Instant capacityReleasedAt(long postRecipientId) {
		Timestamp value = jdbc.queryForObject("SELECT capacity_released_at FROM post_recipient WHERE id = ?",
			(rs, rowNum) -> rs.getTimestamp("capacity_released_at"), postRecipientId);
		return value == null ? null : value.toInstant();
	}

	private int activeCount(long userId) {
		return jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, userId);
	}

	private void receiveState(long userId, int count) {
		jdbc.update("""
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?)
			""", userId, count, count, Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW), Timestamp.from(NOW));
	}

	private long available(long postId, long targetRecipientId) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.AVAILABLE, null, null, null, null, null);
	}

	private long skipPending(long postId, long targetRecipientId, Instant skipRequestedAt) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.SKIP_PENDING, null, null, skipRequestedAt, null, null);
	}

	private long insertRecipient(long postId, long targetRecipientId, PostRecipientStatus status,
		Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt, Instant capacityReleasedAt) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, skip_requested_at, skipped_at, capacity_released_at,
				 inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, ?, ?, ?, ?, ?, 190, ?)
			RETURNING id
			""", Long.class, postId, targetRecipientId, status.name(), REGION, Timestamp.from(BASELINE),
			ts(discoveredAt), ts(openedAt), ts(skipRequestedAt), ts(skippedAt), ts(capacityReleasedAt),
			DEFAULT_DISTANCE_M);
	}

	private static Timestamp ts(Instant value) {
		return value == null ? null : Timestamp.from(value);
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
			VALUES ('OPERATOR', 'ACTIVE', 'EXPSWEEP 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(BASELINE), Timestamp.from(BASELINE), senderId);
	}

	private long post(long postSenderId, long questionId, String idempotencyKey, Instant expiresAt) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '글', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, postSenderId, questionId, idempotencyKey, REGION,
			Timestamp.from(BASELINE), Timestamp.from(BASELINE), Timestamp.from(expiresAt));
	}
}
