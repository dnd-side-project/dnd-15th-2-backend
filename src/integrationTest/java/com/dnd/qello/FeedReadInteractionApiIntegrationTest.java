/**
 * Created at: 2026-08-19T15:29:03+09:00
 * Source scenario: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-INT-001 through
 * INT-019, INT-022 through INT-025, INT-027
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.service.FeedInteractionApplicationService;
import com.dnd.qello.feed.view.AnswerCard;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostDetail;
import com.dnd.qello.feed.view.SentPostFilter;

@SpringBootTest
@ActiveProfiles("test")
@Import(Feed170TestClockConfiguration.class)
class FeedReadInteractionApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private FeedInteractionApplicationService feed;
	@Autowired
	private Feed170MutableClock clock;

	private Feed170IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;
	private long outsiderId;

	@BeforeEach
	void resetFixtures() {
		clock.setInstant(NOW);
		fixtures = new Feed170IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("feed170-sender");
		recipientId = fixtures.account("feed170-recipient");
		outsiderId = fixtures.account("feed170-outsider");
	}

	@Test
	@DisplayName("INT-001 filter는 만료 여부로 목록을 ALL·IN_PROGRESS·EXPIRED로 가른다")
	void filterSplitsListByExpiry() {
		fixtures.post(senderId, "int001-in-progress", NOW.plusSeconds(3600));
		fixtures.postExpired(senderId, "int001-expired", NOW.minusSeconds(60));

		assertThat(feed.listSentPosts(senderId, SentPostFilter.ALL, null, null, 20)).hasSize(2);
		assertThat(feed.listSentPosts(senderId, SentPostFilter.IN_PROGRESS, null, null, 20)).hasSize(1);
		assertThat(feed.listSentPosts(senderId, SentPostFilter.EXPIRED, null, null, 20)).hasSize(1);
	}

	@Test
	@DisplayName("INT-002 커서 페이징은 3페이지를 중복·누락 없이 이어간다")
	void cursorPaginatesWithoutDuplicateOrGap() {
		for (int i = 0; i < 5; i++) {
			fixtures.post(senderId, "int002-" + i, NOW.plusSeconds(3600 + i));
		}

		List<SentPostCard> page1 = feed.listSentPosts(senderId, SentPostFilter.ALL, null, null, 2);
		SentPostCard last1 = page1.get(page1.size() - 1);
		List<SentPostCard> page2 = feed.listSentPosts(
			senderId, SentPostFilter.ALL, last1.submittedAt(), last1.postId(), 2);
		SentPostCard last2 = page2.get(page2.size() - 1);
		List<SentPostCard> page3 = feed.listSentPosts(
			senderId, SentPostFilter.ALL, last2.submittedAt(), last2.postId(), 2);

		assertThat(page1).hasSize(2);
		assertThat(page2).hasSize(2);
		assertThat(page3).hasSize(1);
		List<Long> allIds = List.of(page1.get(0).postId(), page1.get(1).postId(),
			page2.get(0).postId(), page2.get(1).postId(), page3.get(0).postId());
		assertThat(allIds).doesNotHaveDuplicates().hasSize(5);
	}

	@Test
	@DisplayName("INT-003 남의 질문글과 삭제된 본인 질문글의 상세는 둘 다 SENT_POST_NOT_FOUND다")
	void detailHidesOthersAndDeletedPosts() {
		long othersPostId = fixtures.post(senderId, "int003-others", NOW.plusSeconds(3600));
		long deletedPostId = fixtures.postDeleted(senderId, "int003-deleted", NOW.plusSeconds(3600), NOW.minusSeconds(1));

		assertThatThrownBy(() -> feed.sentPostDetail(outsiderId, othersPostId))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.SENT_POST_NOT_FOUND);
		assertThatThrownBy(() -> feed.sentPostDetail(senderId, deletedPostId))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.SENT_POST_NOT_FOUND);
	}

	@Test
	@DisplayName("INT-004 limit이 51이면 LIMIT_OUT_OF_RANGE다")
	void rejectsLimitAboveMax() {
		assertThatThrownBy(() -> feed.listSentPosts(senderId, SentPostFilter.ALL, null, null, 51))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.LIMIT_OUT_OF_RANGE);
	}

	@Test
	@DisplayName("INT-005 목록은 자기 질문글만 반환한다")
	void listReturnsOnlyOwnPosts() {
		fixtures.post(senderId, "int005-mine", NOW.plusSeconds(3600));
		fixtures.post(outsiderId, "int005-others", NOW.plusSeconds(3600));

		List<SentPostCard> cards = feed.listSentPosts(senderId, SentPostFilter.ALL, null, null, 20);

		assertThat(cards).hasSize(1);
	}

	@Test
	@DisplayName("INT-006 질문자와 수신 자격자는 답변 목록에서 뷰어 기준 reactedByMe와 reactionCount를 함께 받는다")
	void answersExposeViewerScopedReactionState() {
		long postId = fixtures.post(senderId, "int006-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		long answerId = fixtures.answer(recipientItemId, recipientId, "int006-a1", NOW.minusSeconds(10));
		fixtures.answerReact(answerId, senderId, NOW.minusSeconds(5));

		List<AnswerCard> senderView = feed.answers(senderId, postId, null, null, 20);
		List<AnswerCard> recipientView = feed.answers(recipientId, postId, null, null, 20);

		assertThat(senderView).hasSize(1);
		assertThat(senderView.get(0).reactedByMe()).isTrue();
		assertThat(senderView.get(0).reactionCount()).isEqualTo(1);
		assertThat(recipientView.get(0).reactedByMe()).isFalse();
	}

	@Test
	@DisplayName("INT-007 그 질문글과 무관한 뷰어의 답변 목록은 403이 아니라 빈 목록이다")
	void answersReturnEmptyListForIneligibleViewer() {
		long postId = fixtures.post(senderId, "int007-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		fixtures.answer(recipientItemId, recipientId, "int007-a1", NOW.minusSeconds(10));

		assertThat(feed.answers(outsiderId, postId, null, null, 20)).isEmpty();
	}

	@Test
	@DisplayName("INT-008 넘긴 수신자와 만료된 미답변 수신자는 답변 내용도 개수도 받지 못한다")
	void skippedAndExpiredUnansweredRecipientsGetEmptyList() {
		long skippedPostId = fixtures.post(senderId, "int008-skipped", NOW.plusSeconds(3600));
		fixtures.recipient(skippedPostId, recipientId, "SKIPPED", NOW.minusSeconds(30));
		long skippedRecipientItemId = fixtures.recipient(skippedPostId, outsiderId, "OPENED", NOW.minusSeconds(30));
		fixtures.answer(skippedRecipientItemId, outsiderId, "int008-a1", NOW.minusSeconds(10));

		long expiredPostId = fixtures.postExpired(senderId, "int008-expired", NOW.minusSeconds(60));
		fixtures.recipient(expiredPostId, recipientId, "AVAILABLE", NOW.minusSeconds(3600));

		assertThat(feed.answers(recipientId, skippedPostId, null, null, 20)).isEmpty();
		assertThat(feed.answers(recipientId, expiredPostId, null, null, 20)).isEmpty();
	}

	@Test
	@DisplayName("INT-009 답변 목록 커서 페이징은 3페이지를 중복·누락 없이 이어간다")
	void answerCursorPaginatesWithoutDuplicateOrGap() {
		// 답변은 수신 항목 1개당 1건이라(uq_answer_one_per_recipient), 다섯 건을 채우려면
		// 다섯 명의 수신자가 필요하다.
		long postId = fixtures.post(senderId, "int009-post", NOW.plusSeconds(3600));
		for (int i = 0; i < 5; i++) {
			long answererId = fixtures.account("feed170-int009-answerer-" + i);
			long recipientItemId = fixtures.recipient(postId, answererId, "OPENED", NOW.minusSeconds(30 - i));
			fixtures.answer(recipientItemId, answererId, "int009-a" + i, NOW.minusSeconds(20 - i));
		}

		List<AnswerCard> page1 = feed.answers(senderId, postId, null, null, 2);
		AnswerCard last1 = page1.get(page1.size() - 1);
		List<AnswerCard> page2 = feed.answers(senderId, postId, last1.publishedAt(), last1.answerId(), 2);
		AnswerCard last2 = page2.get(page2.size() - 1);
		List<AnswerCard> page3 = feed.answers(senderId, postId, last2.publishedAt(), last2.answerId(), 2);

		assertThat(page1).hasSize(2);
		assertThat(page2).hasSize(2);
		assertThat(page3).hasSize(1);
		List<Long> allIds = List.of(page1.get(0).answerId(), page1.get(1).answerId(),
			page2.get(0).answerId(), page2.get(1).answerId(), page3.get(0).answerId());
		assertThat(allIds).doesNotHaveDuplicates().hasSize(5);
	}

	@Test
	@DisplayName("INT-010 질문자 읽음 처리는 answers_read_at을 기록하고 unreadAnswerCount를 0으로 만든다")
	void senderMarksAnswersReadAdvancesTimestamp() {
		long postId = fixtures.post(senderId, "int010-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		fixtures.answer(recipientItemId, recipientId, "int010-a1", NOW.minusSeconds(10));

		Instant result = feed.markSenderAnswersRead(senderId, postId);

		assertThat(result).isEqualTo(NOW);
		assertThat(fixtures.directionPostAnswersReadAt(postId)).isEqualTo(NOW);
		SentPostDetail detail = feed.sentPostDetail(senderId, postId);
		assertThat(detail.card().unreadAnswerCount()).isZero();
	}

	@Test
	@DisplayName("INT-011 질문자 읽음 처리는 이미 기록된 시각보다 이전으로 되돌아가지 않는다")
	void senderMarkAnswersReadNeverRegresses() {
		long postId = fixtures.post(senderId, "int011-post", NOW.plusSeconds(3600));
		clock.setInstant(NOW.plusSeconds(60));
		feed.markSenderAnswersRead(senderId, postId);
		clock.setInstant(NOW.plusSeconds(10));

		feed.markSenderAnswersRead(senderId, postId);

		assertThat(fixtures.directionPostAnswersReadAt(postId)).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	@DisplayName("INT-012 수신자 읽음 처리는 그 수신 항목의 unreadAnswerCount를 0으로 만든다")
	void recipientMarksAnswersReadAdvancesTimestamp() {
		long postId = fixtures.post(senderId, "int012-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		fixtures.answer(fixtures.recipient(postId, outsiderId, "OPENED", NOW.minusSeconds(29)), outsiderId,
			"int012-a1", NOW.minusSeconds(10));

		Instant result = feed.markRecipientAnswersRead(recipientId, recipientItemId);

		assertThat(result).isEqualTo(NOW);
		assertThat(fixtures.postRecipientAnswersReadAt(recipientItemId)).isEqualTo(NOW);
	}

	@Test
	@DisplayName("INT-013 남의 수신 항목의 읽음 처리는 RECIPIENT_NOT_FOUND다")
	void recipientMarksAnswersReadRejectsOthersItem() {
		long postId = fixtures.post(senderId, "int013-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));

		assertThatThrownBy(() -> feed.markRecipientAnswersRead(outsiderId, recipientItemId))
			.hasFieldOrPropertyWithValue("errorCode",
				DirectionErrorCode.RECIPIENT_NOT_FOUND);
	}

	@Test
	@DisplayName("INT-014 질문글 공감 PUT을 반복해도 행은 1개, 공감 수는 1로 고정된다")
	void postReactionPutIsIdempotent() {
		long postId = fixtures.post(senderId, "int014-post", NOW.plusSeconds(3600));
		fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));

		long first = feed.reactToPost(postId, recipientId);
		long second = feed.reactToPost(postId, recipientId);
		long third = feed.reactToPost(postId, recipientId);

		assertThat(List.of(first, second, third)).containsOnly(1L);
		assertThat(fixtures.postReactionCount(postId)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-015 질문글 공감 DELETE를 반복해도 행은 0개, 공감 수는 0으로 고정된다")
	void postReactionDeleteIsIdempotent() {
		long postId = fixtures.post(senderId, "int015-post", NOW.plusSeconds(3600));
		fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		feed.reactToPost(postId, recipientId);

		long first = feed.cancelPostReaction(postId, recipientId);
		long second = feed.cancelPostReaction(postId, recipientId);

		assertThat(List.of(first, second)).containsOnly(0L);
		assertThat(fixtures.postReactionCount(postId)).isZero();
	}

	@Test
	@DisplayName("INT-016 수신 자격이 없는 사용자의 질문글 공감은 403이고 행을 만들지 않는다")
	void postReactionRejectsIneligibleReactor() {
		long postId = fixtures.post(senderId, "int016-post", NOW.plusSeconds(3600));

		assertThatThrownBy(() -> feed.reactToPost(postId, outsiderId))
			.hasFieldOrPropertyWithValue("errorCode",
				DirectionErrorCode.INELIGIBLE_REACTOR);
		assertThat(fixtures.postReactionCount(postId)).isZero();
	}

	@Test
	@DisplayName("INT-017 질문글이 만료된 뒤에도 자기 공감은 취소할 수 있다")
	void postReactionCancelWorksAfterExpiry() {
		long postId = fixtures.post(senderId, "int017-post", NOW.plusSeconds(60));
		fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		feed.reactToPost(postId, recipientId);
		clock.setInstant(NOW.plusSeconds(120));

		long remaining = feed.cancelPostReaction(postId, recipientId);

		assertThat(remaining).isZero();
		assertThat(fixtures.postReactionCount(postId)).isZero();
	}

	@Test
	@DisplayName("INT-018 답변 공감 PUT 반복 후 DELETE 반복은 countByAnswerId를 1에서 0으로 되돌린다")
	void answerReactionPutThenDeleteIsIdempotent() {
		long postId = fixtures.post(senderId, "int018-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		long answerId = fixtures.answer(recipientItemId, recipientId, "int018-a1", NOW.minusSeconds(10));

		long first = feed.reactToAnswer(answerId, senderId);
		long second = feed.reactToAnswer(answerId, senderId);
		assertThat(List.of(first, second)).containsOnly(1L);
		assertThat(fixtures.answerReactionCount(answerId)).isEqualTo(1);

		long third = feed.cancelAnswerReaction(answerId, senderId);
		long fourth = feed.cancelAnswerReaction(answerId, senderId);
		assertThat(List.of(third, fourth)).containsOnly(0L);
		assertThat(fixtures.answerReactionCount(answerId)).isZero();
	}

	@Test
	@DisplayName("INT-019 답변 작성자 본인과 그 질문글과 무관한 사용자의 답변 공감은 서로 다른 사유로 403이다")
	void answerReactionRejectsAuthorAndOutsider() {
		long postId = fixtures.post(senderId, "int019-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		long answerId = fixtures.answer(recipientItemId, recipientId, "int019-a1", NOW.minusSeconds(10));

		assertThatThrownBy(() -> feed.reactToAnswer(answerId, recipientId))
			.hasFieldOrPropertyWithValue("field", "reactorId")
			.extracting(failure -> ((com.dnd.qello.common.error.DomainException) failure).getReason())
			.asString().contains("자기 답변");
		assertThatThrownBy(() -> feed.reactToAnswer(answerId, outsiderId))
			.hasFieldOrPropertyWithValue("field", "reactorId")
			.extracting(failure -> ((com.dnd.qello.common.error.DomainException) failure).getReason())
			.asString().contains("수신 자격자");
		assertThat(fixtures.answerReactionCount(answerId)).isZero();
	}

	@Test
	@DisplayName("INT-022 존재하지 않는 계정과 BLOCKED 계정은 7개 진입점 모두 계정 게이트에서 거부된다")
	void accountGateRejectsMissingAndBlockedAccounts() {
		long postId = fixtures.post(senderId, "int022-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		long blockedAccountId = fixtures.blockedAccount("feed170-int022-blocked");
		long missingAccountId = 987654321L;

		for (long accountId : List.of(blockedAccountId, missingAccountId)) {
			FeedErrorCode expected = accountId == missingAccountId
				? FeedErrorCode.INBOX_ACCOUNT_NOT_FOUND
				: FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE;
			assertThatThrownBy(() -> feed.listSentPosts(accountId, SentPostFilter.ALL, null, null, 20))
				.hasFieldOrPropertyWithValue("errorCode", expected);
			assertThatThrownBy(() -> feed.sentPostDetail(accountId, postId))
				.hasFieldOrPropertyWithValue("errorCode", expected);
			assertThatThrownBy(() -> feed.answers(accountId, postId, null, null, 20))
				.hasFieldOrPropertyWithValue("errorCode", expected);
			assertThatThrownBy(() -> feed.markSenderAnswersRead(accountId, postId))
				.hasFieldOrPropertyWithValue("errorCode", expected);
			assertThatThrownBy(() -> feed.markRecipientAnswersRead(accountId, recipientItemId))
				.hasFieldOrPropertyWithValue("errorCode", expected);
			assertThatThrownBy(() -> feed.reactToPost(postId, accountId))
				.hasFieldOrPropertyWithValue("errorCode", expected);
			assertThatThrownBy(() -> feed.cancelPostReaction(postId, accountId))
				.hasFieldOrPropertyWithValue("errorCode", expected);
		}
	}

	@Test
	@DisplayName("INT-024 같은 사용자·같은 질문글의 동시 공감 PUT은 예외 없이 최종 1행으로 수렴한다")
	void concurrentPostReactionPutConvergesToOneRow() throws Exception {
		long postId = fixtures.post(senderId, "int024-post", NOW.plusSeconds(3600));
		fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));

		RacePair<Long, Long> results = race(
			() -> feed.reactToPost(postId, recipientId),
			() -> feed.reactToPost(postId, recipientId));

		assertThat(results.first().failure()).isNull();
		assertThat(results.second().failure()).isNull();
		assertThat(fixtures.postReactionCount(postId)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-025 같은 답변의 동시 PUT·DELETE는 예외 없이 0 또는 1행으로 수렴하고 공감 수가 음수가 되지 않는다")
	void concurrentAnswerReactionPutAndDeleteConverge() throws Exception {
		long postId = fixtures.post(senderId, "int025-post", NOW.plusSeconds(3600));
		long recipientItemId = fixtures.recipient(postId, recipientId, "OPENED", NOW.minusSeconds(30));
		long answerId = fixtures.answer(recipientItemId, recipientId, "int025-a1", NOW.minusSeconds(10));
		feed.reactToAnswer(answerId, senderId);

		RacePair<Long, Long> results = race(
			() -> feed.reactToAnswer(answerId, senderId),
			() -> feed.cancelAnswerReaction(answerId, senderId));

		assertThat(results.first().failure()).isNull();
		assertThat(results.second().failure()).isNull();
		assertThat(fixtures.answerReactionCount(answerId)).isIn(0L, 1L);
		assertThat(fixtures.answerReactionCount(answerId)).isNotNegative();
	}

	@Test
	@DisplayName("INT-027 검토 대기 답변과 삭제된 답변은 답변 목록과 answerCount에서 빠진다")
	void pendingAndDeletedAnswersAreHiddenFromAnswersList() {
		// 답변은 수신 항목 1개당 1건이라(uq_answer_one_per_recipient), 상태별로 다른
		// 수신자를 쓴다.
		long postId = fixtures.post(senderId, "int027-post", NOW.plusSeconds(3600));
		long publishedAnswererId = fixtures.account("feed170-int027-published");
		long pendingAnswererId = fixtures.account("feed170-int027-pending");
		long deletedAnswererId = fixtures.account("feed170-int027-deleted");
		fixtures.answer(fixtures.recipient(postId, publishedAnswererId, "OPENED", NOW.minusSeconds(30)),
			publishedAnswererId, "int027-published", NOW.minusSeconds(10));
		fixtures.answerPending(fixtures.recipient(postId, pendingAnswererId, "OPENED", NOW.minusSeconds(29)),
			pendingAnswererId, "int027-pending", NOW.minusSeconds(9));
		fixtures.answerDeleted(fixtures.recipient(postId, deletedAnswererId, "OPENED", NOW.minusSeconds(28)),
			deletedAnswererId, "int027-deleted", NOW.minusSeconds(8));

		List<AnswerCard> answers = feed.answers(senderId, postId, null, null, 20);
		SentPostDetail detail = feed.sentPostDetail(senderId, postId);

		assertThat(answers).hasSize(1);
		assertThat(detail.card().answerCount()).isEqualTo(1);
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

@TestConfiguration(proxyBeanMethods = false)
class Feed170TestClockConfiguration {

	@Bean
	@Primary
	Feed170MutableClock feed170MutableClock() {
		return new Feed170MutableClock(Instant.parse("2026-08-19T06:00:00Z"), ZoneOffset.UTC);
	}
}

final class Feed170MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	Feed170MutableClock(Instant initial, ZoneId zone) {
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
	public Clock withZone(ZoneId zone) {
		return new Feed170MutableClock(current.get(), zone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}
}

/** feed170 지역 코드로 격리된 테스트 데이터를 만들고 정리한다. */
final class Feed170IntegrationFixtures {

	private static final String REGION = "TEST-FEED170";
	private static final Instant BASELINE = Instant.parse("2026-08-19T05:00:00Z");

	private final JdbcTemplate jdbc;
	private final Instant now;

	Feed170IntegrationFixtures(JdbcTemplate jdbc, Instant now) {
		this.jdbc = jdbc;
		this.now = now;
	}

	void reset() {
		jdbc.update("""
			DELETE FROM answer_reaction WHERE answer_id IN (
				SELECT a.id FROM answer a JOIN post_recipient pr ON pr.id = a.post_recipient_id
				JOIN direction_post dp ON dp.id = pr.post_id WHERE dp.coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM post_reaction WHERE post_id IN (
				SELECT id FROM direction_post WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM answer WHERE post_recipient_id IN (
				SELECT pr.id FROM post_recipient pr JOIN direction_post dp ON dp.id = pr.post_id
				WHERE dp.coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM post_recipient pr
			WHERE pr.recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR pr.post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("DELETE FROM post_audience WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("""
			DELETE FROM approved_question WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Feed 170 Test', 'REGION')
			""", REGION);
	}

	long account(String nickname) {
		return account(nickname, "USER", "ACTIVE");
	}

	long blockedAccount(String nickname) {
		return account(nickname, "USER", "BLOCKED");
	}

	private long account(String nickname, String role, String status) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES (?, 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, role, status, REGION, nickname);
	}

	long post(long senderId, String key, Instant expiresAt) {
		return post(senderId, key, expiresAt, null);
	}

	long postExpired(long senderId, String key, Instant expiresAt) {
		return post(senderId, key, expiresAt, null);
	}

	long postDeleted(long senderId, String key, Instant expiresAt, Instant deletedAt) {
		return post(senderId, key, expiresAt, deletedAt);
	}

	private long post(long senderId, String key, Instant expiresAt, Instant deletedAt) {
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'FEED170 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(BASELINE), Timestamp.from(BASELINE), senderId);
		String status = deletedAt != null ? "DELETED" : "ACTIVE";
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at, deleted_at)
			VALUES (?, ?, ?, ?, 'FEED170 본문', ?, 'PASSED', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, status, "feed170-" + key, REGION,
			Timestamp.from(BASELINE), Timestamp.from(BASELINE), Timestamp.from(expiresAt), ts(deletedAt));
	}

	/**
	 * ck_post_recipient_status_timestamps가 상태별 필수 시각을 강제한다 — SKIPPED는
	 * skipped_at, EXPIRED는 expired_at, BLOCKED는 blocked_at이 함께 있어야 한다.
	 */
	/**
	 * ct_post_recipient_capacity_release 지연 트리거가 ANSWERED·SKIPPED·EXPIRED·BLOCKED
	 * 상태와 capacity_released_at의 존재 여부가 일치할 것을 요구한다.
	 */
	long recipient(long postId, long recipientId, String status, Instant matchedAt) {
		Instant discoveredAt = matchedAt.plusSeconds(1);
		Instant openedAt = matchedAt.plusSeconds(2);
		Instant terminalAt = matchedAt.plusSeconds(3);
		boolean available = status.equals("AVAILABLE");
		Instant skippedAt = status.equals("SKIPPED") ? terminalAt : null;
		Instant expiredAt = status.equals("EXPIRED") ? terminalAt : null;
		Instant blockedAt = status.equals("BLOCKED") ? terminalAt : null;
		boolean releasesCapacity = skippedAt != null || expiredAt != null || blockedAt != null;
		Instant capacityReleasedAt = releasesCapacity ? terminalAt : null;
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, skip_requested_at, skipped_at, capacity_released_at,
				 expired_at, blocked_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, ?, ?, ?, ?, ?, ?, ?, 90, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, status, REGION, Timestamp.from(matchedAt),
			available ? null : ts(discoveredAt), available ? null : ts(openedAt),
			ts(skippedAt), ts(skippedAt), ts(capacityReleasedAt), ts(expiredAt), ts(blockedAt));
	}

	long answer(long postRecipientId, long authorId, String key, Instant publishedAt) {
		return answer(postRecipientId, authorId, key, publishedAt, "PUBLISHED", "PASSED", null);
	}

	long answerPending(long postRecipientId, long authorId, String key, Instant submittedAt) {
		return answer(postRecipientId, authorId, key, submittedAt, "SAFETY_CHECKING", "PENDING", null);
	}

	/** ck_answer_deleted_at이 (status = 'DELETED') = (deleted_at IS NOT NULL)를 강제한다. */
	long answerDeleted(long postRecipientId, long authorId, String key, Instant publishedAt) {
		return answer(postRecipientId, authorId, key, publishedAt, "DELETED", "PASSED", publishedAt.plusSeconds(1));
	}

	private long answer(long postRecipientId, long authorId, String key, Instant at, String status,
		String moderationStatus, Instant deletedAt) {
		Instant publishedAt = status.equals("PUBLISHED") || status.equals("DELETED") ? at : null;
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at,
				 published_at, deleted_at)
			VALUES (?, ?, ?, ?, 'FEED170 답변', ?, 45, 'NEAR', 4000, ?, ?, ?, ?)
			RETURNING id
			""", Long.class, postRecipientId, authorId, status, "feed170-" + key, REGION, moderationStatus,
			Timestamp.from(at), ts(publishedAt), ts(deletedAt));
	}

	/** fk_post_reaction_recipient가 (post_id, reactor_id)를 post_recipient에서 강제한다. */
	void postReact(long postId, long reactorId, Instant at) {
		jdbc.update("INSERT INTO post_reaction (post_id, reactor_id, created_at) VALUES (?, ?, ?)",
			postId, reactorId, Timestamp.from(at));
	}

	void answerReact(long answerId, long reactorId, Instant at) {
		jdbc.update("INSERT INTO answer_reaction (answer_id, reactor_id, created_at) VALUES (?, ?, ?)",
			answerId, reactorId, Timestamp.from(at));
	}

	long postReactionCount(long postId) {
		return jdbc.queryForObject("SELECT count(*) FROM post_reaction WHERE post_id = ?", Long.class, postId);
	}

	long answerReactionCount(long answerId) {
		return jdbc.queryForObject("SELECT count(*) FROM answer_reaction WHERE answer_id = ?", Long.class, answerId);
	}

	Instant directionPostAnswersReadAt(long postId) {
		Timestamp value = jdbc.queryForObject(
			"SELECT answers_read_at FROM direction_post WHERE id = ?", Timestamp.class, postId);
		return value == null ? null : value.toInstant();
	}

	Instant postRecipientAnswersReadAt(long postRecipientId) {
		Timestamp value = jdbc.queryForObject(
			"SELECT answers_read_at FROM post_recipient WHERE id = ?", Timestamp.class, postRecipientId);
		return value == null ? null : value.toInstant();
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
