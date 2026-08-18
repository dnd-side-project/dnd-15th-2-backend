/**
 * Created at: 2026-08-17T20:49:31+09:00
 * Source scenario: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP-INT-013 through INT-015
 * Source scenario: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP-INT-018
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.direction.config.SkipConfirmationProperties;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.service.PostRecipientService;
import com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker;
import com.dnd.qello.direction.sweep.SkipConfirmationSweepWorker;
import com.dnd.qello.direction.sweep.SweepBatchResult;
import com.dnd.qello.safety.service.SafetyService;

/**
 * sweep 실행기와 사용자 요청·다른 내부 전이가 같은 수신 행 또는 같은 카운터 행을
 * 동시에 건드릴 때 정확히 한 번만 슬롯이 해제되고 교착이 없는지 검증한다. 어느
 * 경로가 이기는지는 이 클래스에서 단언하지 않는다 — 성립한 전이가 정확히 하나이고
 * 카운터 감소가 총 1회임을 단언한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecipientSweepConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-EXPSWEEP-CC";
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
	private AnswerNotificationService answerNotificationService;
	@Autowired
	private PostRecipientService postRecipientService;
	@Autowired
	private SafetyService safetyService;
	@Autowired
	private SkipConfirmationProperties skipConfirmationProperties;

	private long senderId;
	private long blockedSenderId;
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
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Recipient Sweep Concurrency Test', 'REGION')", REGION);

		senderId = account("expsweepcc-sender");
		blockedSenderId = account("expsweepcc-blocked-sender");
		recipientId = account("expsweepcc-recipient");
	}

	@Test
	@DisplayName("만료 sweep과 답변 공개가 같은 행에 동시에 경쟁하면 정확히 하나만 성공하고 슬롯은 한 번만 해제된다")
	void expirationSweepAndAnswerPublicationRaceExclusivelyOnTheSameRecipient() throws Exception {
		long questionId = question();
		long postId = post(senderId, questionId, "int013-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		boolean sweepReleased;
		boolean publishSucceeded;
		try {
			Future<Boolean> sweepFuture = executor.submit(() -> sweepConcurrently(ready, start));
			Future<Boolean> publishFuture = executor.submit(() -> submitAndPublishConcurrently(prId, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			sweepReleased = sweepFuture.get(10, TimeUnit.SECONDS);
			publishSucceeded = publishFuture.get(10, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(sweepReleased ^ publishSucceeded).isTrue();
		assertThat(activeCount(recipientId)).isZero();
		assertThat(status(prId)).isIn("EXPIRED", "ANSWERED");
	}

	@Test
	@DisplayName("넘김확정 sweep과 되돌리기가 같은 행에 동시에 경쟁해도 슬롯은 최대 한 번만 해제된다")
	void skipConfirmationSweepAndRevertRaceOnTheSameRecipientNeverDoubleReleases() throws Exception {
		long questionId = question();
		long postId = post(senderId, questionId, "int014-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(graceSeconds + 1));
		receiveState(recipientId, 1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<SweepBatchResult> confirmFuture = executor.submit(() -> confirmSweepConcurrently(ready, start));
			Future<Boolean> revertFuture = executor.submit(() -> revertConcurrently(prId, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			confirmFuture.get(10, TimeUnit.SECONDS);
			revertFuture.get(10, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		String finalStatus = status(prId);
		int finalCount = activeCount(recipientId);
		if (finalStatus.equals("SKIPPED")) {
			assertThat(finalCount).isZero();
		} else {
			assertThat(finalStatus).isEqualTo("AVAILABLE");
			assertThat(finalCount).isEqualTo(1);
		}
	}

	@Test
	@DisplayName("만료 sweep과 차단이 같은 행에 동시에 경쟁하면 정확히 하나만 성공하고 슬롯은 한 번만 해제된다")
	void expirationSweepAndBlockRaceExclusivelyOnTheSameRecipient() throws Exception {
		long questionId = question();
		long postId = post(blockedSenderId, questionId, "int015-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Boolean> sweepFuture = executor.submit(() -> sweepConcurrently(ready, start));
			Future<Boolean> blockFuture = executor.submit(() -> blockConcurrently(ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			sweepFuture.get(10, TimeUnit.SECONDS);
			blockFuture.get(10, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(status(prId)).isIn("EXPIRED", "BLOCKED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("만료 sweep과 넘김확정 sweep을 동시에 실행해도 같은 사용자의 카운터가 정확히 두 번만 감소한다")
	void expirationAndSkipConfirmationSweepsRunConcurrentlyWithoutLosingCounterUpdates() throws Exception {
		long questionId = question();
		long expiredPostId = post(senderId, questionId, "int018-post-expired", NOW.minusSeconds(60));
		long skipPostId = post(senderId, questionId, "int018-post-skip", NOW.plusSeconds(3600));
		long expiredPrId = available(expiredPostId, recipientId);
		long skipPrId = skipPending(skipPostId, recipientId, NOW.minusSeconds(graceSeconds + 1));
		receiveState(recipientId, 2);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<SweepBatchResult> expireFuture = executor.submit(() -> expireSweepConcurrently(ready, start));
			Future<SweepBatchResult> confirmFuture = executor.submit(() -> confirmSweepConcurrently(ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			SweepBatchResult expireResult = expireFuture.get(10, TimeUnit.SECONDS);
			SweepBatchResult confirmResult = confirmFuture.get(10, TimeUnit.SECONDS);
			assertThat(expireResult.released()).isEqualTo(1);
			assertThat(confirmResult.released()).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}

		assertThat(status(expiredPrId)).isEqualTo("EXPIRED");
		assertThat(status(skipPrId)).isEqualTo("SKIPPED");
		assertThat(activeCount(recipientId)).isZero();
	}

	private boolean sweepConcurrently(CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		SweepBatchResult result = expirationSweepWorker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));
		return result.released() == 1;
	}

	private SweepBatchResult expireSweepConcurrently(CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return expirationSweepWorker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));
	}

	private SweepBatchResult confirmSweepConcurrently(CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return skipConfirmationSweepWorker.processBatch(new SkipConfirmationSweepWorker.BatchCommand(10, NOW));
	}

	private boolean submitAndPublishConcurrently(long postRecipientId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		try {
			Answer submitted = answerNotificationService.submit(Answer.submit(postRecipientId, recipientId,
				"int013-answer", "답변 본문", REGION, BigDecimal.valueOf(90), "NEAR", NOW.minusSeconds(1), DEFAULT_DISTANCE_M));
			answerNotificationService.publish(submitted.getId(), NOW);
			return true;
		} catch (AnswerException failed) {
			return false;
		}
	}

	private boolean revertConcurrently(long postRecipientId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		try {
			postRecipientService.revertSkip(recipientId, postRecipientId, NOW);
			return true;
		} catch (DirectionException failed) {
			return false;
		}
	}

	private boolean blockConcurrently(CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		safetyService.block(recipientId, blockedSenderId, NOW);
		return true;
	}

	private String status(long postRecipientId) {
		return jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId);
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
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.AVAILABLE, null, null);
	}

	private long skipPending(long postId, long targetRecipientId, Instant skipRequestedAt) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.SKIP_PENDING, skipRequestedAt, null);
	}

	private long insertRecipient(long postId, long targetRecipientId, PostRecipientStatus status,
		Instant skipRequestedAt, Instant skippedAt) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, skip_requested_at, skipped_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, ?, ?, 190, ?)
			RETURNING id
			""", Long.class, postId, targetRecipientId, status.name(), REGION, Timestamp.from(BASELINE),
			ts(skipRequestedAt), ts(skippedAt), DEFAULT_DISTANCE_M);
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
			VALUES ('OPERATOR', 'ACTIVE', 'EXPSWEEPCC 질문', 'TEXT', ?, ?, ?)
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
