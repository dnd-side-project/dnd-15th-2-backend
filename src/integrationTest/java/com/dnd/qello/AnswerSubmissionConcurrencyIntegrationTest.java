/**
 * Created at: 2026-08-17T18:00:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-005, INT-006,
 * INT-015, INT-016
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.answer.service.AnswerSubmissionApplicationService;
import com.dnd.qello.common.web.GlobalExceptionHandler;
import com.dnd.qello.common.web.response.ApiErrorResponse;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;

@SpringBootTest
@ActiveProfiles("test")
@Import(AnswerConcurrency125TestClockConfiguration.class)
class AnswerSubmissionConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-17T06:00:00.123456Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AnswerSubmissionApplicationService submissionApplicationService;
	@Autowired
	private AnswerNotificationService answerNotificationService;
	@Autowired
	private ReceiveSlotReleaseService receiveSlotReleaseService;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private GlobalExceptionHandler exceptionHandler;
	@Autowired
	private AnswerConcurrency125MutableClock clock;

	private Answer125IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		clock.setInstant(NOW);
		fixtures = new Answer125IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("concurrency125-sender");
		recipientId = fixtures.account("concurrency125-recipient");
		promotedRelease();
	}

	@Test
	@DisplayName("INT-005: 같은 key·동일 payload를 동시에 제출해도 answer/filter_job/EXECUTION_REQUESTED는 각 정확히 1건이다")
	void concurrentIdenticalSubmissionsConvergeOnSingleAnswer() throws Exception {
		long postId = fixtures.post(senderId, "int005", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);

		RacePair<Answer, Answer> race = race(
			() -> submissionApplicationService.submit(recipientId, "int005-key", postRecipientId, "본문", List.of()),
			() -> submissionApplicationService.submit(recipientId, "int005-key", postRecipientId, "본문", List.of()));

		assertThat(race.first().failure()).isNull();
		assertThat(race.second().failure()).isNull();
		assertThat(race.first().value().getId()).isEqualTo(race.second().value().getId());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE post_recipient_id = ?",
			Integer.class, postRecipientId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM filter_job WHERE target_type = 'ANSWER' AND target_id = ?",
			Integer.class, race.first().value().getId())).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'MODERATION_EXECUTION_REQUESTED'"
				+ " AND aggregate_id = (SELECT id FROM filter_job WHERE target_id = ?)",
			Integer.class, race.first().value().getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-006: 같은 recipient에 서로 다른 key·본문을 동시에 제출하면 한 요청만 성공하고 나머지는 기능 409(원문 미노출)로 거절된다")
	void concurrentDifferentSubmissionsToSameRecipientLeaveOnlyOneAnswer() throws Exception {
		long postId = fixtures.post(senderId, "int006", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);

		RacePair<Answer, Answer> race = race(
			() -> submissionApplicationService.submit(recipientId, "int006-a", postRecipientId, "본문 A", List.of()),
			() -> submissionApplicationService.submit(recipientId, "int006-b", postRecipientId, "본문 B", List.of()));

		boolean firstWon = race.first().failure() == null;
		boolean secondWon = race.second().failure() == null;
		assertThat(firstWon ^ secondWon).as("정확히 한 쪽만 성공해야 한다").isTrue();
		Attempt<Answer> loser = firstWon ? race.second() : race.first();
		assertThat(loser.failure()).isInstanceOf(DataIntegrityViolationException.class);

		ResponseEntity<ApiErrorResponse> mapped = exceptionHandler.handleDataIntegrityViolation(
			(DataIntegrityViolationException) loser.failure());
		assertThat(mapped.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(mapped.getBody().errorDetail().code()).isEqualTo(AnswerErrorCode.DUPLICATE_ACTIVE_ANSWER.code());
		// partial unique index 이름(uq_answer_one_per_recipient)이나 원문 SQL 메시지가 아니라
		// 매핑된 기능 오류 코드만 노출됨을 위 code 비교로 확인한다.

		assertThat(jdbc.queryForObject("SELECT count(*) FROM answer WHERE post_recipient_id = ?",
			Integer.class, postRecipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-015: ALLOW 공개와 차단 전이가 동시에 실행되면 하나만 성립하고 슬롯은 정확히 1회만 감소한다")
	void concurrentPublishAndBlockLeaveExactlyOneWinner() throws Exception {
		long postId = fixtures.post(senderId, "int015", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = submissionApplicationService.submit(recipientId, "int015-key", postRecipientId, "본문", List.of());
		jdbc.update("UPDATE answer SET status = 'SAFETY_CHECKING' WHERE id = ?", submitted.getId());

		RacePair<Answer, java.util.Optional<com.dnd.qello.direction.domain.PostRecipient>> race = race(
			() -> answerNotificationService.publish(submitted.getId(), NOW.plusSeconds(1)),
			() -> receiveSlotReleaseService.block(postRecipientId, NOW.plusSeconds(1)));

		String finalStatus = fixtures.status(postRecipientId);
		assertThat(finalStatus).isIn("ANSWERED", "BLOCKED");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(0);
		if (finalStatus.equals("ANSWERED")) {
			assertThat(race.first().failure()).isNull();
			assertThat(race.second().failure()).isNull();
			assertThat(race.second().value()).isEmpty();
		} else {
			assertThat(race.second().value()).isPresent();
			assertThat(race.first().failure()).isInstanceOf(com.dnd.qello.answer.error.AnswerException.class)
				.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_ANSWER_STATUS);
		}
	}

	@Test
	@DisplayName("INT-016: 신규 제출은 post_recipient.status를 직접 쓰지 않으므로 동시 차단 전이와 경쟁해도 자격 판정이 잠금 시점 상태와 항상 일치한다")
	void concurrentSubmitAndBlockKeepEligibilityConsistentWithLockedState() throws Exception {
		// submit()은 findInboxCommandItemForUpdate로 행을 잠그고 읽기만 할 뿐, 그 트랜잭션 안에서
		// post_recipient.status를 직접 갱신하지 않는다(ANSWERED 전이는 이후 publish()의 몫이다).
		// 반면 block()은 낙관적 조건부 UPDATE라서 submit의 FOR UPDATE 잠금 뒤에서 대기하다가도
		// 잠금 해제 후 조건이 여전히 맞으면 성공한다. 따라서 이 경합의 실제 불변식은 "정확히
		// 하나만 이긴다"가 아니라 "block()은 항상 결국 성공하고, submit()의 성패는 그 시점에
		// 잠근 행 상태(AVAILABLE/DISCOVERED/OPENED 여부)와 항상 일치한다"이다 — 제출 이후의
		// 차단은 이후 publish() 단계의 release Slot 조건부 전이(#93/releaseSlot)가 fail-closed로
		// 막는다는 기존 설계와 일관된다.
		long postId = fixtures.post(senderId, "int016", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);

		RacePair<Answer, java.util.Optional<com.dnd.qello.direction.domain.PostRecipient>> race = race(
			() -> submissionApplicationService.submit(recipientId, "int016-key", postRecipientId, "본문", List.of()),
			() -> receiveSlotReleaseService.block(postRecipientId, NOW.plusSeconds(1)));

		assertThat(fixtures.status(postRecipientId)).isEqualTo("BLOCKED");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(0);
		boolean submitSucceeded = race.first().failure() == null;
		int answerCount = jdbc.queryForObject(
			"SELECT count(*) FROM answer WHERE post_recipient_id = ?", Integer.class, postRecipientId);
		assertThat(answerCount).isEqualTo(submitSucceeded ? 1 : 0);
		if (!submitSucceeded) {
			assertThat(race.first().failure()).isInstanceOf(com.dnd.qello.answer.error.AnswerException.class)
				.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.RECIPIENT_NOT_FOUND);
		}
	}

	private void promotedRelease() {
		AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, senderId);
	}

	private static <A, B> RacePair<A, B> race(Callable<A> first, Callable<B> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Attempt<A>> firstFuture = executor.submit(() -> attempt(first, ready, start));
			Future<Attempt<B>> secondFuture = executor.submit(() -> attempt(second, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both threads reached the start latch").isTrue();
			start.countDown();
			Attempt<A> firstResult = firstFuture.get(15, TimeUnit.SECONDS);
			Attempt<B> secondResult = secondFuture.get(15, TimeUnit.SECONDS);
			return new RacePair<>(firstResult, secondResult);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("executor terminated").isTrue();
		}
	}

	private static <T> Attempt<T> attempt(Callable<T> action, CountDownLatch ready, CountDownLatch start) {
		ready.countDown();
		try {
			if (!start.await(5, TimeUnit.SECONDS)) {
				return new Attempt<>(null, new IllegalStateException("race start latch timed out"));
			}
			return new Attempt<>(action.call(), null);
		} catch (Throwable failure) {
			return new Attempt<>(null, failure);
		}
	}

	private record Attempt<T>(T value, Throwable failure) { }

	private record RacePair<A, B>(Attempt<A> first, Attempt<B> second) { }
}

@TestConfiguration
class AnswerConcurrency125TestClockConfiguration {

	@Bean
	@Primary
	AnswerConcurrency125MutableClock answerConcurrency125MutableClock() {
		return new AnswerConcurrency125MutableClock(Instant.parse("2026-08-17T06:00:00.123456Z"), ZoneOffset.UTC);
	}
}

final class AnswerConcurrency125MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	AnswerConcurrency125MutableClock(Instant initial, ZoneId zone) {
		this.current = new AtomicReference<>(initial);
		this.zone = zone;
	}

	void setInstant(Instant instant) {
		current.set(instant);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId newZone) {
		return new AnswerConcurrency125MutableClock(current.get(), newZone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}
}
