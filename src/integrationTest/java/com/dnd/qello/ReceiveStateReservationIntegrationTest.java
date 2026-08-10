package com.dnd.qello;

/**
 * Created at: 2026-08-10T15:15:11+09:00
 * Source scenario: TEST-PLAN-GH-94-RECEIVE-STATE-INIT-RACE-INT-001 through INT-012
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.domain.RecipientReceiveState;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.direction.service.DirectionPostService;

/**
 * `recipient_receive_state` 초기 행 생성 경쟁(#94)을 고정한다.
 *
 * 이 결함은 Java 로직이 아니라 두 SQL 문 사이의 원자성 부재에 있어 stub 기반
 * 단위 테스트로는 관측되지 않는다. 따라서 P0 증거는 전부 실제 PostgreSQL
 * 컨테이너를 쓰는 이 클래스의 시나리오다.
 *
 * 동시 스레드는 2개까지만 쓴다 — `application-test.properties`의
 * `hikari.maximum-pool-size=4`이고 각 스레드가 트랜잭션마다 커넥션을 잡으므로
 * 3개 이상은 pool 고갈로 거짓 실패를 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReceiveStateReservationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-94";
	private static final Instant AT = Instant.parse("2026-08-10T12:00:00Z");
	private static final int CAPACITY = 5;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ActiveUserPresenceRepository presenceRepository;

	@Autowired
	private DirectionSchemeRepository schemeRepository;

	@Autowired
	private RecipientReceiveStateRepository receiveStateRepository;

	@Autowired
	private DirectionPostService postService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM direction_segment");
		jdbc.update("DELETE FROM direction_scheme");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Receive State Test Region', 'REGION')", REGION);
	}

	// ---------------------------------------------------------------------
	// INT-001, INT-002 — 초기화 경쟁 재현
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("INT-001: 수신 상태가 없는 사용자에게 두 발송이 동시에 도착해도 카운터가 실제 배달 건수와 일치한다")
	void concurrentSendsToNewRecipientKeepCounterEqualToDeliveredRows() throws Exception {
		Fixture fixture = twoSendersTargetingOneRecipient();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_receive_state WHERE user_id = ?",
			Integer.class, fixture.recipientId())).isZero();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<DirectionPostService.SendResult>> results = List.of(
				executor.submit(() -> sendAfterSignal(fixture.sendCommand(fixture.senderOneId(), "S0", "gh94-concurrent-1"), ready, start)),
				executor.submit(() -> sendAfterSignal(fixture.sendCommand(fixture.senderTwoId(), "S7", "gh94-concurrent-2"), ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			results.get(0).get(20, TimeUnit.SECONDS);
			results.get(1).get(20, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		int deliveredRows = jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE recipient_id = ?",
			Integer.class, fixture.recipientId());
		int counter = jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?",
			Integer.class, fixture.recipientId());

		// 카운터만 보는 단언은 "둘 다 틀렸지만 서로 일치하는" 상태를 놓친다.
		// 두 발송 모두 상한 안에 있으므로 실제 배달은 2건이어야 하고, 카운터가 그와 같아야 한다.
		assertThat(deliveredRows).isEqualTo(2);
		assertThat(counter).isEqualTo(deliveredRows);
	}

	@Test
	@DisplayName("INT-002: 수신 상태가 없는 사용자에게 두 트랜잭션이 동시에 예약하면 각각 슬롯을 하나씩 점유한다")
	void concurrentReserveOnMissingRowReservesTwoSlots() throws Exception {
		long recipientId = createUser("gh94-int002-recipient");
		TransactionTemplate transaction = requiresNewTransaction();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = List.of(
				executor.submit(() -> reserveAfterSignal(transaction, recipientId, AT, ready, start)),
				executor.submit(() -> reserveAfterSignal(transaction, recipientId, AT, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(results.get(0).get(20, TimeUnit.SECONDS)).isTrue();
			assertThat(results.get(1).get(20, TimeUnit.SECONDS)).isTrue();
		} finally {
			executor.shutdownNow();
		}

		RecipientReceiveState state = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(state.getActiveUnhandledCount()).isEqualTo(2);
		assertThat(state.getRecentReceivedCount()).isEqualTo(2);
	}

	// ---------------------------------------------------------------------
	// INT-003 ~ INT-007 — 예약의 단일 호출 계약
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("INT-003: 기존 수신 상태가 있는 사용자를 예약해도 누적 카운터와 수신 윈도우가 초기화되지 않는다")
	void reserveOnExistingRowPreservesAccumulatedValues() {
		long recipientId = createUser("gh94-int003-recipient");
		Instant windowStartedAt = AT.minusSeconds(3600);
		Instant lastReceivedAt = AT.minusSeconds(600);
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 2, 7, windowStartedAt, lastReceivedAt, lastReceivedAt));

		assertThat(receiveStateRepository.reserve(recipientId, AT, CAPACITY)).isTrue();

		RecipientReceiveState state = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(state.getActiveUnhandledCount()).isEqualTo(3);
		assertThat(state.getRecentReceivedCount()).isEqualTo(8);
		assertThat(state.getLastReceivedAt()).isEqualTo(AT);
		// 수신 윈도우 시작점은 예약이 건드리는 값이 아니다. 매 예약마다 리셋되면
		// recent_received_count의 기준 구간이 사라진다.
		assertThat(state.getRecentWindowStartedAt()).isEqualTo(windowStartedAt);
	}

	@Test
	@DisplayName("INT-004: 수신 상태가 없는 사용자를 예약하면 행이 생성되고 슬롯 하나를 점유한다")
	void reserveOnMissingRowCreatesRowWithOneSlot() {
		long recipientId = createUser("gh94-int004-recipient");

		assertThat(receiveStateRepository.reserve(recipientId, AT, CAPACITY)).isTrue();

		RecipientReceiveState state = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(state.getActiveUnhandledCount()).isEqualTo(1);
		assertThat(state.getRecentReceivedCount()).isEqualTo(1);
		assertThat(state.getLastReceivedAt()).isEqualTo(AT);
		// ck_recipient_receive_state_last_received: last_received_at >= recent_window_started_at.
		// 신규 행이 이 제약을 어기면 첫 예약부터 커밋이 거부된다.
		assertThat(state.getRecentWindowStartedAt()).isBeforeOrEqualTo(AT);
	}

	@Test
	@DisplayName("INT-005: 상한에 도달한 사용자를 예약하면 실패하고 수신 이력이 변하지 않는다")
	void reserveAtCapacityFailsAndLeavesStateUntouched() {
		long recipientId = createUser("gh94-int005-recipient");
		Instant windowStartedAt = AT.minusSeconds(3600);
		Instant lastReceivedAt = AT.minusSeconds(600);
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, CAPACITY, CAPACITY, windowStartedAt, lastReceivedAt, lastReceivedAt));

		assertThat(receiveStateRepository.reserve(recipientId, AT, CAPACITY)).isFalse();

		RecipientReceiveState state = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(state.getActiveUnhandledCount()).isEqualTo(CAPACITY);
		assertThat(state.getRecentReceivedCount()).isEqualTo(CAPACITY);
		// 예약하지 않은 시각이 수신 시각으로 기록되면 안 된다.
		assertThat(state.getLastReceivedAt()).isEqualTo(lastReceivedAt);
		assertThat(state.getRecentWindowStartedAt()).isEqualTo(windowStartedAt);
	}

	@Test
	@DisplayName("INT-006: 예약을 반복하면 상한까지만 성공하고 반환값과 카운터가 매번 일치한다")
	void repeatedReserveSucceedsOnlyUpToLimit() {
		long recipientId = createUser("gh94-int006-recipient");
		int limit = 3;

		assertThat(receiveStateRepository.reserve(recipientId, AT, limit)).isTrue();
		assertThat(receiveStateRepository.reserve(recipientId, AT.plusSeconds(1), limit)).isTrue();
		assertThat(receiveStateRepository.reserve(recipientId, AT.plusSeconds(2), limit)).isTrue();
		assertThat(receiveStateRepository.reserve(recipientId, AT.plusSeconds(3), limit)).isFalse();

		RecipientReceiveState state = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(state.getActiveUnhandledCount()).isEqualTo(limit);
		assertThat(state.getRecentReceivedCount()).isEqualTo(limit);
		assertThat(state.getLastReceivedAt()).isEqualTo(AT.plusSeconds(2));
	}

	@Test
	@DisplayName("INT-007: 동시 예약 중 한쪽이 롤백되면 커밋된 예약만 남는다")
	void rolledBackReserveLeavesNoTrace() throws Exception {
		long recipientId = createUser("gh94-int007-recipient");
		TransactionTemplate transaction = requiresNewTransaction();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = List.of(
				executor.submit(() -> reserveAfterSignal(transaction, recipientId, AT, ready, start)),
				executor.submit(() -> reserveAfterSignal(requiresNewTransaction(), recipientId, AT, ready, start, true)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			results.get(0).get(20, TimeUnit.SECONDS);
			results.get(1).get(20, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		// 어느 쪽이 먼저 실행되든 커밋된 예약은 하나뿐이다.
		RecipientReceiveState state = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(state.getActiveUnhandledCount()).isEqualTo(1);
		assertThat(state.getRecentReceivedCount()).isEqualTo(1);
	}

	// ---------------------------------------------------------------------
	// INT-008, INT-009 — 발송 end-to-end
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("INT-008: 상한까지 받은 사용자는 더 이상 수신자로 선정되지 않고 카운터가 배달 건수와 같다")
	void sendStopsSelectingRecipientAtCapacity() {
		Fixture fixture = twoSendersTargetingOneRecipient();

		List<Integer> deliveredPerSend = IntStream.range(0, CAPACITY + 1)
			.mapToObj(index -> postService.send(fixture.sendCommand(fixture.senderOneId(), "S0", "gh94-capacity-" + index)))
			.map(result -> result.recipients().size())
			.toList();

		assertThat(deliveredPerSend).containsExactly(1, 1, 1, 1, 1, 0);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE recipient_id = ?",
			Integer.class, fixture.recipientId())).isEqualTo(CAPACITY);
		assertThat(jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?",
			Integer.class, fixture.recipientId())).isEqualTo(CAPACITY);
	}

	@Test
	@DisplayName("INT-009: 남은 슬롯이 하나인 사용자에게 두 발송이 동시에 도착하면 한쪽만 배달된다")
	void concurrentSendsWithSingleRemainingSlotDeliverOnce() throws Exception {
		Fixture fixture = twoSendersTargetingOneRecipient();
		receiveStateRepository.save(RecipientReceiveState.restore(fixture.recipientId(), CAPACITY - 1, CAPACITY - 1,
			AT.minusSeconds(3600), AT.minusSeconds(600), AT.minusSeconds(600)));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		int totalDelivered;
		try {
			List<Future<DirectionPostService.SendResult>> results = List.of(
				executor.submit(() -> sendAfterSignal(fixture.sendCommand(fixture.senderOneId(), "S0", "gh94-lastslot-1"), ready, start)),
				executor.submit(() -> sendAfterSignal(fixture.sendCommand(fixture.senderTwoId(), "S7", "gh94-lastslot-2"), ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			totalDelivered = results.get(0).get(20, TimeUnit.SECONDS).recipients().size()
				+ results.get(1).get(20, TimeUnit.SECONDS).recipients().size();
		} finally {
			executor.shutdownNow();
		}

		assertThat(totalDelivered).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE recipient_id = ?",
			Integer.class, fixture.recipientId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?",
			Integer.class, fixture.recipientId())).isEqualTo(CAPACITY);
	}

	// ---------------------------------------------------------------------
	// INT-010 ~ INT-012 — 인접 계약 회귀 가드
	// ---------------------------------------------------------------------

	@Test
	@DisplayName("INT-010: save는 기존 수신 상태를 인자 값으로 덮어쓴다")
	void saveOverwritesExistingState() {
		long recipientId = createUser("gh94-int010-recipient");
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 3, 9, AT.minusSeconds(7200), AT.minusSeconds(60), AT.minusSeconds(60)));

		Instant windowStartedAt = AT.minusSeconds(3600);
		Instant lastReceivedAt = AT.minusSeconds(600);
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 1, 1, windowStartedAt, lastReceivedAt, lastReceivedAt));

		// save()는 통합 테스트 7개 지점에서 카운터를 정확한 값으로 세팅하는 시더다.
		// 이 덮어쓰기 계약이 깨지면 그 시나리오들이 조용히 무력화된다.
		RecipientReceiveState state = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(state.getActiveUnhandledCount()).isEqualTo(1);
		assertThat(state.getRecentReceivedCount()).isEqualTo(1);
		assertThat(state.getLastReceivedAt()).isEqualTo(lastReceivedAt);
		assertThat(state.getRecentWindowStartedAt()).isEqualTo(windowStartedAt);
	}

	@Test
	@DisplayName("INT-011: release는 슬롯을 하나 반환하고 0에서는 음수로 내려가지 않는다")
	void releaseDecrementsOnceAndNeverGoesNegative() {
		long recipientId = createUser("gh94-int011-recipient");
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 2, 2, AT.minusSeconds(3600), AT.minusSeconds(600), AT.minusSeconds(600)));

		assertThat(receiveStateRepository.release(recipientId, AT)).isTrue();
		RecipientReceiveState afterRelease = receiveStateRepository.findByUserId(recipientId).orElseThrow();
		assertThat(afterRelease.getActiveUnhandledCount()).isEqualTo(1);
		assertThat(afterRelease.getUpdatedAt()).isEqualTo(AT);

		long emptyRecipientId = createUser("gh94-int011-empty");
		receiveStateRepository.save(RecipientReceiveState.restore(emptyRecipientId, 0, 0, AT.minusSeconds(3600), null, AT.minusSeconds(3600)));

		assertThat(receiveStateRepository.release(emptyRecipientId, AT)).isFalse();
		assertThat(receiveStateRepository.findByUserId(emptyRecipientId).orElseThrow().getActiveUnhandledCount()).isZero();
	}

	@Test
	@DisplayName("INT-012: 존재하지 않는 사용자를 예약하면 조용히 실패하지 않고 제약 위반으로 드러난다")
	void reserveForUnknownUserFailsLoudly() {
		long unknownUserId = jdbc.queryForObject("SELECT COALESCE(max(id), 0) + 1000 FROM user_account", Long.class);

		assertThatThrownBy(() -> receiveStateRepository.reserve(unknownUserId, AT, CAPACITY))
			.isInstanceOf(DataAccessException.class);
	}

	// ---------------------------------------------------------------------
	// Fixtures
	// ---------------------------------------------------------------------

	/**
	 * sender1은 수신자의 정남쪽, sender2는 수신자의 정남동쪽에 둔다. 수신자는
	 * sender1 기준 방위 0도(segment S0 = [0, 45)), sender2 기준 방위 약 322도
	 * (segment S7 = [315, 360))에 놓여 두 발송의 유일한 공통 후보가 된다.
	 * 두 sender는 서로의 segment 밖에 있어 후보로 잡히지 않는다.
	 */
	private Fixture twoSendersTargetingOneRecipient() {
		long senderOneId = createUser("gh94-sender-1");
		long senderTwoId = createUser("gh94-sender-2");
		long recipientId = createUser("gh94-recipient");
		long questionId = createActiveQuestion(senderOneId);
		long schemeId = createEightSegmentScheme();

		savePresence(senderOneId, "37.5000", "127.0000");
		savePresence(senderTwoId, "37.5000", "127.0010");
		savePresence(recipientId, "37.5010", "127.0000");

		return new Fixture(senderOneId, senderTwoId, recipientId, questionId, schemeId);
	}

	private record Fixture(long senderOneId, long senderTwoId, long recipientId, long questionId, long schemeId) {
		DirectionPostService.SendCommand sendCommand(long senderId, String segmentKey, String idempotencyKey) {
			return new DirectionPostService.SendCommand(senderId, questionId, schemeId, segmentKey,
				0, 500, REGION, idempotencyKey, "테스트 방향 글", AT, AT.plusSeconds(3600));
		}
	}

	private void savePresence(long userId, String latitude, String longitude) {
		presenceRepository.save(ActiveUserPresence.create(userId, new BigDecimal(latitude), new BigDecimal(longitude),
			null, REGION, BigDecimal.ONE, true, AT.minusSeconds(10), AT.plusSeconds(3600)));
	}

	private TransactionTemplate requiresNewTransaction() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template;
	}

	private DirectionPostService.SendResult sendAfterSignal(DirectionPostService.SendCommand command,
		CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return postService.send(command);
	}

	private Boolean reserveAfterSignal(TransactionTemplate transaction, long userId, Instant at,
		CountDownLatch ready, CountDownLatch start) throws Exception {
		return reserveAfterSignal(transaction, userId, at, ready, start, false);
	}

	private Boolean reserveAfterSignal(TransactionTemplate transaction, long userId, Instant at,
		CountDownLatch ready, CountDownLatch start, boolean rollback) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return transaction.execute(status -> {
			boolean reserved = receiveStateRepository.reserve(userId, at, CAPACITY);
			if (rollback) {
				status.setRollbackOnly();
			}
			return reserved;
		});
	}

	private long createUser(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('KR', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long createActiveQuestion(long approverId) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
			(source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '방향 질문', 'TEXT', ?, ?, ?, ?) RETURNING id
			""", Long.class, Timestamp.from(AT.minusSeconds(1)), Timestamp.from(AT.plusSeconds(7200)),
			Timestamp.from(AT.minusSeconds(1)), approverId);
	}

	private long createEightSegmentScheme() {
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual("TEST-94", 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(), "S" + index,
			"segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index)));
		return scheme.getId();
	}
}
