/**
 * Created at: 2026-08-16T14:59:30+09:00
 * Source scenario: TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-013 through INT-016
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.safety.domain.UserBlock;
import com.dnd.qello.safety.service.SafetyService;

@SpringBootTest
@ActiveProfiles("test")
@Import(Inbox124TestClockConfiguration.class)
class InboxCommandConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-16T06:00:00.123456Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxApplicationService inbox;
	@Autowired
	private ReceiveSlotReleaseService receiveSlotReleaseService;
	@Autowired
	private SafetyService safetyService;
	@Autowired
	private AnswerNotificationService answerNotificationService;
	@Autowired
	private Inbox124MutableClock clock;

	private Inbox124IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		clock.setInstant(NOW);
		fixtures = new Inbox124IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("inbox124-concurrent-sender");
		recipientId = fixtures.account("inbox124-concurrent-recipient");
	}

	@Test
	@DisplayName("INT-013 두 동시 skip은 같은 최초 시각의 성공 snapshot을 반환하고 슬롯을 유지한다")
	void duplicateConcurrentSkipsShareOneFirstRequestTimestamp() throws Exception {
		long postId = fixtures.post(senderId, "int013-duplicate", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);

		RacePair<PostRecipient, PostRecipient> results = race(
			() -> inbox.skip(recipientId, postRecipientId),
			() -> inbox.skip(recipientId, postRecipientId));

		assertThat(results.first().failure()).isNull();
		assertThat(results.second().failure()).isNull();
		assertThat(results.first().value().getSkipRequestedAt()).isEqualTo(NOW);
		assertThat(results.second().value().getSkipRequestedAt()).isEqualTo(NOW);
		assertThat(fixtures.status(postRecipientId)).isEqualTo("SKIP_PENDING");
		assertThat(fixtures.skipRequestedAt(postRecipientId)).isEqualTo(NOW);
		assertThat(fixtures.capacityReleasedAt(postRecipientId)).isNull();
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-014 deadline의 revert-confirm 경쟁은 최종 SKIPPED와 정확히 한 번의 슬롯 해제로 수렴한다")
	void revertAndConfirmRaceConvergesToConfirmedSkipAtDeadline() throws Exception {
		long postId = fixtures.post(senderId, "int014-revert-confirm", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		inbox.skip(recipientId, postRecipientId);
		clock.setInstant(NOW.plusSeconds(5));

		RacePair<PostRecipient, Optional<PostRecipient>> results = race(
			() -> inbox.revertSkip(recipientId, postRecipientId),
			() -> receiveSlotReleaseService.confirmSkip(postRecipientId, NOW.plusSeconds(5)));

		assertThat(results.first().failure()).isNotNull();
		assertThat(results.second().failure()).isNull();
		assertThat(results.second().value()).isPresent();
		assertThat(fixtures.status(postRecipientId)).isEqualTo("SKIPPED");
		assertThat(fixtures.capacityReleasedAt(postRecipientId)).isEqualTo(NOW.plusSeconds(5));
		assertThat(fixtures.activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("INT-015 open/skip과 Safety block 경쟁은 BLOCKED terminal 상태와 슬롯 해제를 되돌리지 않는다")
	void openAndSkipRacesCannotOverwriteSafetyBlock() throws Exception {
		long openSender = fixtures.account("inbox124-int015-open-sender");
		long openPost = fixtures.post(openSender, "int015-open", NOW.plusSeconds(3600), "ACTIVE", null);
		long openRecipient = fixtures.available(openPost, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);

		RacePair<Object, UserBlock> openRace = race(
			() -> inbox.detail(recipientId, openRecipient),
			() -> safetyService.block(recipientId, openSender, NOW));

		assertValidCommandOutcome(openRace.first());
		assertThat(openRace.second().failure()).isNull();
		assertThat(openRace.second().value()).isNotNull();
		assertThat(fixtures.status(openRecipient)).isEqualTo("BLOCKED");
		assertThat(fixtures.blockedAt(openRecipient)).isEqualTo(NOW);
		assertThat(fixtures.capacityReleasedAt(openRecipient)).isEqualTo(NOW);
		assertThat(fixtures.activeCount(recipientId)).isZero();

		long skipSender = fixtures.account("inbox124-int015-skip-sender");
		long skipPost = fixtures.post(skipSender, "int015-skip", NOW.plusSeconds(3600), "ACTIVE", null);
		long skipRecipient = fixtures.opened(skipPost, recipientId, NOW.minusSeconds(10), 180);
		fixtures.receiveState(recipientId, 1);

		RacePair<PostRecipient, UserBlock> skipRace = race(
			() -> inbox.skip(recipientId, skipRecipient),
			() -> safetyService.block(recipientId, skipSender, NOW));

		assertValidCommandOutcome(skipRace.first());
		assertThat(skipRace.second().failure()).isNull();
		assertThat(skipRace.second().value()).isNotNull();
		assertThat(fixtures.status(skipRecipient)).isEqualTo("BLOCKED");
		assertThat(fixtures.blockedAt(skipRecipient)).isEqualTo(NOW);
		assertThat(fixtures.capacityReleasedAt(skipRecipient)).isEqualTo(NOW);
		assertThat(fixtures.skipRequestedAt(skipRecipient)).isNull();
		assertThat(fixtures.activeCount(recipientId)).isZero();
	}

	@Test
	@DisplayName("INT-016 skip과 답변 공개 경쟁은 ANSWERED 또는 SKIP_PENDING 하나만 성립하고 슬롯이 음수가 되지 않는다")
	void skipAndAnswerPublishRaceHasOneLinearizedOutcome() throws Exception {
		long postId = fixtures.post(senderId, "int016-skip-answer", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.opened(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = answerNotificationService.submit(Answer.submit(
			postRecipientId, recipientId, "gh124-int016-answer", "답변", Inbox124IntegrationFixtures.REGION,
			BigDecimal.valueOf(45), "NEAR", NOW, 5_000L));

		RacePair<PostRecipient, Answer> results = race(
			() -> inbox.skip(recipientId, postRecipientId),
			() -> answerNotificationService.publish(submitted.getId(), NOW));

		String status = fixtures.status(postRecipientId);
		int successCount = (results.first().failure() == null ? 1 : 0) + (results.second().failure() == null ? 1 : 0);
		assertThat(successCount).isEqualTo(1);
		assertThat(status).isIn("ANSWERED", "SKIP_PENDING");
		if (status.equals("ANSWERED")) {
			assertThat(fixtures.activeCount(recipientId)).isZero();
			assertThat(fixtures.capacityReleasedAt(postRecipientId)).isEqualTo(NOW);
		} else {
			assertThat(fixtures.activeCount(recipientId)).isEqualTo(1);
			assertThat(fixtures.capacityReleasedAt(postRecipientId)).isNull();
			assertThat(fixtures.skipRequestedAt(postRecipientId)).isEqualTo(NOW);
		}
		assertThat(fixtures.activeCount(recipientId)).isGreaterThanOrEqualTo(0);
	}

	private static void assertValidCommandOutcome(Attempt<?> outcome) {
		if (outcome.failure() == null) {
			assertThat(outcome.value()).isNotNull();
			return;
		}
		assertThat(outcome.value()).isNull();
		assertThat(outcome.failure()).isInstanceOf(FeedException.class);
		FeedException failure = (FeedException) outcome.failure();
		assertThat(failure.getErrorCode()).isIn(
			FeedErrorCode.INBOX_ITEM_NOT_FOUND, FeedErrorCode.INBOX_TRANSITION_CONFLICT);
	}

	private static <A, B> RacePair<A, B> race(Callable<A> first, Callable<B> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Attempt<A>> firstFuture = executor.submit(() -> attempt(first, ready, start));
			Future<Attempt<B>> secondFuture = executor.submit(() -> attempt(second, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both transactions became ready").isTrue();
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
