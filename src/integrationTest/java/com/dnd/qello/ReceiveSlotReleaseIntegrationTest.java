/**
 * Created at: 2026-08-10T15:30:00+09:00
 * Source scenario: TEST-PLAN-GH-93-RELEASE-RECEIVE-SLOTS-INT-001 through INT-015
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.PostRecipientService;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;
import com.dnd.qello.safety.service.SafetyService;

/**
 * 만료·넘김확정·차단 세 경로의 수신 슬롯 해제(#93)를 검증한다. 세 전이의 방향성과
 * 경쟁 시나리오가 핵심이며, 방향 판정 자체(45° 구간 로직)는 다루지 않는다 — INT-012만
 * 예외로 DirectionPostService.send()의 end-to-end 재현을 위해 기존 시드 OCTANT
 * 스킴을 재사용한다(새 스킴을 만들지 않는다 — 다른 테스트 클래스와 컨테이너를
 * 공유하므로 시드 데이터를 건드리지 않는다).
 */
@SpringBootTest
@ActiveProfiles("test")
class ReceiveSlotReleaseIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-RELSLOT";
	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
	/** post_recipient.matched_at과 direction_post.submitted_at의 공통 기준선. 모든 테스트 시각(NOW ± 수 분)보다 앞서야 한다. */
	private static final Instant BASELINE = NOW.minusSeconds(3600);
	private static final long DEFAULT_DISTANCE_M = 5_000L;

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ReceiveSlotReleaseService receiveSlotReleaseService;
	@Autowired
	private PostRecipientService postRecipientService;
	@Autowired
	private SafetyService safetyService;
	@Autowired
	private DirectionPostService directionPostService;
	@Autowired
	private AnswerNotificationService answerNotificationService;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private DirectionSchemeRepository schemeRepository;

	private long senderId;
	private long blockedSenderId;
	private long outsiderId;
	private long recipientId;

	@BeforeEach
	void resetFixtures() {
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
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Receive Slot Release Test', 'REGION')", REGION);

		senderId = account("relslot-sender");
		blockedSenderId = account("relslot-blocked-sender");
		outsiderId = account("relslot-outsider-sender");
		recipientId = account("relslot-recipient");
	}

	@Test
	@DisplayName("만료 전이는 AVAILABLE 수신 항목의 슬롯을 해제한다")
	void expireReleasesSlotForAvailableRecipient() {
		long questionId = question();
		long postId = post(senderId, questionId, "int001-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		Optional<PostRecipient> result = receiveSlotReleaseService.expire(prId, NOW);

		assertThat(result).isPresent();
		assertThat(result.get().getStatus()).isEqualTo(PostRecipientStatus.EXPIRED);
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("만료 sweep은 만료된 행만 전이시키고 안 만료된 행은 그대로 둔다")
	void expireSweepOnlyTouchesExpiredCandidates() {
		long questionId = question();
		long userA = account("int002-a");
		long userB = account("int002-b");
		long userC = account("int002-c");
		receiveState(userA, 2);
		receiveState(userB, 1);
		receiveState(userC, 1);

		long expiredPostA1 = post(senderId, questionId, "int002-a1", NOW.minusSeconds(60));
		long expiredPostA2 = post(senderId, questionId, "int002-a2", NOW.minusSeconds(30));
		long activePostB = post(senderId, questionId, "int002-b1", NOW.plusSeconds(3600));
		long expiredPostC = post(senderId, questionId, "int002-c1", NOW.minusSeconds(10));
		long prA1 = available(expiredPostA1, userA);
		long prA2 = available(expiredPostA2, userA);
		long prB1 = available(activePostB, userB);
		long prC1 = available(expiredPostC, userC);

		receiveSlotReleaseService.findExpirable(NOW).forEach(candidate -> receiveSlotReleaseService.expire(candidate.getId(), NOW));

		assertThat(activeCount(userA)).isZero();
		assertThat(activeCount(userB)).isEqualTo(1);
		assertThat(activeCount(userC)).isZero();
		assertThat(status(prA1)).isEqualTo("EXPIRED");
		assertThat(status(prA2)).isEqualTo("EXPIRED");
		assertThat(status(prB1)).isEqualTo("AVAILABLE");
		assertThat(status(prC1)).isEqualTo("EXPIRED");
	}

	@Test
	@DisplayName("유예가 지난 SKIP_PENDING은 확정되어 슬롯을 해제한다")
	void confirmSkipReleasesSlotAfterGracePeriodElapses() {
		long questionId = question();
		long postId = post(senderId, questionId, "int003-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(10));
		receiveState(recipientId, 1);

		Optional<PostRecipient> result = receiveSlotReleaseService.confirmSkip(prId, NOW);

		assertThat(result).isPresent();
		assertThat(result.get().getStatus()).isEqualTo(PostRecipientStatus.SKIPPED);
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("유예 시간이 지나지 않은 SKIP_PENDING은 확정되지 않는다")
	void skipPendingIsNotConfirmedBeforeGracePeriodElapses() {
		long questionId = question();
		long postId = post(senderId, questionId, "int004-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW);
		receiveState(recipientId, 1);

		Optional<PostRecipient> result = receiveSlotReleaseService.confirmSkip(prId, NOW.plusSeconds(1));

		assertThat(result).isEmpty();
		assertThat(status(prId)).isEqualTo("SKIP_PENDING");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("유예 중 되돌린 SKIP_PENDING은 이후 확정 시도에서 대상이 아니다")
	void revertedSkipIsNoLongerConfirmable() {
		long questionId = question();
		long postId = post(senderId, questionId, "int005-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW);
		receiveState(recipientId, 1);

		postRecipientService.revertSkip(recipientId, prId);
		Optional<PostRecipient> result = receiveSlotReleaseService.confirmSkip(prId, NOW.plusSeconds(60));

		assertThat(result).isEmpty();
		assertThat(status(prId)).isEqualTo("AVAILABLE");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("만료 전이를 같은 행에 동시에 두 번 실행해도 슬롯은 한 번만 해제된다")
	void expiringTheSameRecipientConcurrentlyReleasesTheSlotOnlyOnce() throws Exception {
		long questionId = question();
		long postId = post(senderId, questionId, "int006-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = List.of(
				executor.submit(() -> expireConcurrently(prId, ready, start)),
				executor.submit(() -> expireConcurrently(prId, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			boolean first = results.get(0).get(10, TimeUnit.SECONDS);
			boolean second = results.get(1).get(10, TimeUnit.SECONDS);
			assertThat(first ^ second).isTrue();
		} finally {
			executor.shutdownNow();
		}

		assertThat(status(prId)).isEqualTo("EXPIRED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("차단은 차단자 자신이 보유한 차단대상 발신자의 미종결 수신 항목을 모두 해제한다")
	void blockingReleasesAllPendingRecipientItemsFromTheBlockedSender() {
		long questionId = question();
		long postId1 = post(blockedSenderId, questionId, "int007-post1", NOW.plusSeconds(3600));
		long postId2 = post(blockedSenderId, questionId, "int007-post2", NOW.plusSeconds(3600));
		long prId1 = opened(postId1, recipientId);
		long prId2 = available(postId2, recipientId);
		receiveState(recipientId, 2);

		safetyService.block(recipientId, blockedSenderId, NOW);

		assertThat(status(prId1)).isEqualTo("BLOCKED");
		assertThat(status(prId2)).isEqualTo("BLOCKED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("차단은 SKIP_PENDING 수신 항목을 BLOCKED로 전이하며 skip_requested_at을 비운다")
	void blockingClearsSkipRequestedAtForSkipPendingItems() {
		long questionId = question();
		long postId = post(blockedSenderId, questionId, "int016-post", NOW.plusSeconds(3600));
		long prId = skipPending(postId, recipientId, NOW.minusSeconds(2));
		receiveState(recipientId, 1);

		safetyService.block(recipientId, blockedSenderId, NOW);

		assertThat(status(prId)).isEqualTo("BLOCKED");
		assertThat(skipRequestedAt(prId)).isNull();
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("차단은 이미 ANSWERED인 수신 항목을 재전이하지 않는다")
	void blockingDoesNotRetransitionAnAlreadyAnsweredItem() {
		long questionId = question();
		long postId = post(blockedSenderId, questionId, "int008-post", NOW.plusSeconds(3600));
		long prId = answered(postId, recipientId);
		receiveState(recipientId, 0);

		safetyService.block(recipientId, blockedSenderId, NOW);

		assertThat(status(prId)).isEqualTo("ANSWERED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("차단은 차단당한 사람 자신의 수신 항목은 건드리지 않는다")
	void blockingDoesNotTouchTheBlockedPersonsOwnRecipientItems() {
		long questionId = question();
		long postId = post(recipientId, questionId, "int009-post", NOW.plusSeconds(3600));
		long prId = available(postId, blockedSenderId);
		receiveState(blockedSenderId, 1);

		safetyService.block(recipientId, blockedSenderId, NOW);

		assertThat(status(prId)).isEqualTo("AVAILABLE");
		assertThat(activeCount(blockedSenderId)).isEqualTo(1);
	}

	@Test
	@DisplayName("차단은 관련 없는 제3자가 보낸 질문글의 수신 항목은 건드리지 않는다")
	void blockingDoesNotTouchUnrelatedSendersRecipientItems() {
		long questionId = question();
		long postId = post(outsiderId, questionId, "int010-post", NOW.plusSeconds(3600));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		safetyService.block(recipientId, blockedSenderId, NOW);

		assertThat(status(prId)).isEqualTo("AVAILABLE");
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 EXPIRED인 행에 만료 전이를 재실행해도 슬롯이 다시 줄지 않는다")
	void reRunningExpireOnAnAlreadyExpiredRowDoesNotReleaseTwice() {
		long questionId = question();
		long postId = post(senderId, questionId, "int011-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);

		Optional<PostRecipient> first = receiveSlotReleaseService.expire(prId, NOW);
		Optional<PostRecipient> second = receiveSlotReleaseService.expire(prId, NOW.plusSeconds(10));

		assertThat(first).isPresent();
		assertThat(second).isEmpty();
		assertThat(status(prId)).isEqualTo("EXPIRED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("상한까지 받은 뒤 전부 만료시킨 사용자는 다시 신규 발송의 수신자로 선정된다")
	void userBecomesEligibleAgainAfterExpiringAllUnansweredSlots() {
		DirectionScheme octant = schemeRepository.findByCodeAndVersion("OCTANT", 1).orElseThrow();
		presenceRepository.save(ActiveUserPresence.create(senderId, BigDecimal.valueOf(37.5000), BigDecimal.valueOf(127.0000),
			null, REGION, BigDecimal.ONE, true, NOW.minusSeconds(10), NOW.plusSeconds(3600)));
		presenceRepository.save(ActiveUserPresence.create(recipientId, BigDecimal.valueOf(37.5010), BigDecimal.valueOf(127.0000),
			null, REGION, BigDecimal.ONE, true, NOW.minusSeconds(10), NOW.plusSeconds(3600)));

		long questionId = question();
		for (int index = 0; index < 5; index++) {
			long oldPostId = post(senderId, questionId, "int012-old-" + index, NOW.minusSeconds(60));
			available(oldPostId, recipientId);
		}
		receiveState(recipientId, 5);
		assertThat(activeCount(recipientId)).isEqualTo(5);

		receiveSlotReleaseService.findExpirable(NOW).forEach(candidate -> receiveSlotReleaseService.expire(candidate.getId(), NOW));
		assertThat(activeCount(recipientId)).isZero();

		var result = directionPostService.send(new DirectionPostService.SendCommand(senderId, questionId, octant.getId(), "N",
			0, 500, REGION, "int012-new-post", "새 질문글", NOW, NOW.plusSeconds(3600)));

		assertThat(result.recipients()).extracting(PostRecipient::getRecipientId).containsExactly(recipientId);
		assertThat(activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("만료 sweep은 ANSWERED 항목을 전이 대상에서 제외한다")
	void expireSweepExcludesAnsweredItems() {
		long questionId = question();
		long postId = post(senderId, questionId, "int013-post", NOW.minusSeconds(60));
		long prId = answered(postId, recipientId);
		receiveState(recipientId, 0);

		List<PostRecipient> candidates = receiveSlotReleaseService.findExpirable(NOW);

		assertThat(candidates).extracting(PostRecipient::getId).doesNotContain(prId);
		assertThat(status(prId)).isEqualTo("ANSWERED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("세 전이 모두 이미 terminal인 행을 다시 건드리지 않는다")
	void allThreeTransitionsLeaveAlreadyTerminalRowsUntouched() {
		long questionId = question();
		long skippedPostId = post(senderId, questionId, "int014-skipped", NOW.plusSeconds(3600));
		long expiredPostId = post(senderId, questionId, "int014-expired", NOW.minusSeconds(60));
		long blockedPostId = post(senderId, questionId, "int014-blocked", NOW.plusSeconds(3600));
		long skippedPrId = skipped(skippedPostId, recipientId, NOW.minusSeconds(30), NOW.minusSeconds(20));
		long expiredPrId = expired(expiredPostId, recipientId, NOW.minusSeconds(10));
		long blockedPrId = blocked(blockedPostId, recipientId, NOW.minusSeconds(10));
		receiveState(recipientId, 0);

		for (long prId : List.of(skippedPrId, expiredPrId, blockedPrId)) {
			assertThat(receiveSlotReleaseService.expire(prId, NOW)).isEmpty();
			assertThat(receiveSlotReleaseService.confirmSkip(prId, NOW)).isEmpty();
			assertThat(receiveSlotReleaseService.block(prId, NOW)).isEmpty();
		}

		assertThat(status(skippedPrId)).isEqualTo("SKIPPED");
		assertThat(status(expiredPrId)).isEqualTo("EXPIRED");
		assertThat(status(blockedPrId)).isEqualTo("BLOCKED");
		assertThat(activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("만료 전이와 답변 게시가 같은 행에 동시에 경쟁하면 정확히 하나만 성공한다")
	void expireAndPublishRaceExclusivelyOnTheSameRecipient() throws Exception {
		long questionId = question();
		long postId = post(senderId, questionId, "int015-post", NOW.minusSeconds(60));
		long prId = available(postId, recipientId);
		receiveState(recipientId, 1);
		Answer submitted = answerNotificationService.submit(Answer.submit(prId, recipientId, "int015-answer",
			"답변", REGION, BigDecimal.valueOf(90), "NEAR", NOW, DEFAULT_DISTANCE_M));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		boolean expireSucceeded;
		boolean publishSucceeded;
		try {
			Future<Boolean> expireFuture = executor.submit(() -> expireConcurrently(prId, ready, start));
			Future<Boolean> publishFuture = executor.submit(() -> publishConcurrently(submitted.getId(), ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			expireSucceeded = expireFuture.get(10, TimeUnit.SECONDS);
			publishSucceeded = publishFuture.get(10, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(expireSucceeded ^ publishSucceeded).isTrue();
		assertThat(activeCount(recipientId)).isZero();
		assertThat(status(prId)).isIn("EXPIRED", "ANSWERED");
	}

	private boolean expireConcurrently(long postRecipientId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return receiveSlotReleaseService.expire(postRecipientId, NOW).isPresent();
	}

	private boolean publishConcurrently(long answerId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		try {
			answerNotificationService.publish(answerId, NOW);
			return true;
		} catch (AnswerException exception) {
			return false;
		}
	}

	private String status(long postRecipientId) {
		return jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId);
	}

	private Instant skipRequestedAt(long postRecipientId) {
		Timestamp value = jdbc.queryForObject("SELECT skip_requested_at FROM post_recipient WHERE id = ?",
			(rs, rowNum) -> rs.getTimestamp("skip_requested_at"), postRecipientId);
		return value == null ? null : value.toInstant();
	}

	private int activeCount(long userId) {
		return jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, userId);
	}

	private void receiveState(long userId, int count) {
		jdbc.update("""
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?)
			""", userId, count, count, Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW), Timestamp.from(NOW));
	}

	private long available(long postId, long targetRecipientId) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.AVAILABLE, null, null, null, null, null, null, null);
	}

	private long opened(long postId, long targetRecipientId) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.OPENED, NOW, NOW, null, null, null, null, null);
	}

	private long skipPending(long postId, long targetRecipientId, Instant skipRequestedAt) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.SKIP_PENDING, null, null, skipRequestedAt, null, null, null, null);
	}

	private long skipped(long postId, long targetRecipientId, Instant skipRequestedAt, Instant skippedAt) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.SKIPPED, null, null, skipRequestedAt, skippedAt, skippedAt, null, null);
	}

	private long answered(long postId, long targetRecipientId) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.ANSWERED, NOW, NOW, null, null, NOW, null, null);
	}

	private long expired(long postId, long targetRecipientId, Instant expiredAt) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.EXPIRED, null, null, null, null, expiredAt, expiredAt, null);
	}

	private long blocked(long postId, long targetRecipientId, Instant blockedAt) {
		return insertRecipient(postId, targetRecipientId, PostRecipientStatus.BLOCKED, null, null, null, null, blockedAt, null, blockedAt);
	}

	private long insertRecipient(long postId, long targetRecipientId, PostRecipientStatus status,
		Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt,
		Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, skip_requested_at, skipped_at, capacity_released_at,
				 expired_at, blocked_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, ?, ?, ?, ?, ?, ?, ?, 190, ?)
			RETURNING id
			""", Long.class, postId, targetRecipientId, status.name(), REGION, Timestamp.from(BASELINE),
			ts(discoveredAt), ts(openedAt), ts(skipRequestedAt), ts(skippedAt), ts(capacityReleasedAt),
			ts(expiredAt), ts(blockedAt), DEFAULT_DISTANCE_M);
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
			VALUES ('OPERATOR', 'ACTIVE', 'RELSLOT 질문', 'TEXT', ?, ?, ?)
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
