/**
 * Created at: 2026-08-06T14:00:00+09:00
 * Source scenario: TEST-PLAN-GH-67-INBOX-SENT-POST-INT-001 through INT-019,
 * TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-010, INT-011,
 * TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-004, INT-010, INT-011
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.repository.AnswerReactionRepository;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.answer.service.AnswerReactionService;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.domain.RecipientReceiveState;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostReactionRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.PostReactionService;
import com.dnd.qello.direction.service.PostRecipientService;
import com.dnd.qello.feed.service.InboxQueryService;
import com.dnd.qello.feed.view.InboxCategory;

@SpringBootTest
@ActiveProfiles("test")
class InboxSentPostWriteIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-INBOX";
	private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PostRecipientRepository postRecipientRepository;
	@Autowired
	private DirectionPostRepository directionPostRepository;
	@Autowired
	private RecipientReceiveStateRepository receiveStateRepository;
	@Autowired
	private PostReactionRepository postReactionRepository;
	@Autowired
	private AnswerReactionRepository answerReactionRepository;
	@Autowired
	private PostRecipientService postRecipientService;
	@Autowired
	private DirectionPostService directionPostService;
	@Autowired
	private PostReactionService postReactionService;
	@Autowired
	private AnswerReactionService answerReactionService;
	@Autowired
	private AnswerNotificationService answerNotificationService;
	@Autowired
	private InboxQueryService inboxQueryService;

	private long senderId;
	private long recipientId;
	private long outsiderId;
	private long postId;
	private long postRecipientId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM answer_reaction");
		jdbc.update("DELETE FROM post_reaction");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Inbox Test', 'REGION')", REGION);

		senderId = account("inbox-sender");
		recipientId = account("inbox-recipient");
		outsiderId = account("inbox-outsider");
		postId = post("inbox-post", NOW.plus(1, ChronoUnit.HOURS));
		postRecipientId = recipient(postId, recipientId, "AVAILABLE");
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long post(String idempotencyKey, Instant expiresAt) {
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '오늘 뭐 하고 있나요?', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), senderId);
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '점심 먹는 중', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, idempotencyKey, REGION,
			Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(expiresAt));
	}

	private long recipient(long targetPostId, long targetRecipientId, String status) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at,
				 inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, 225, 5000)
			RETURNING id
			""", Long.class, targetPostId, targetRecipientId, status, REGION, Timestamp.from(NOW));
	}

	private long publishedAnswer(long targetPostRecipientId, long authorId, String idempotencyKey, Instant publishedAt) {
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at, published_at)
			VALUES (?, ?, 'PUBLISHED', ?, '답변 본문', ?, 45, 'NEAR', 5000, 'PASSED', ?, ?)
			RETURNING id
			""", Long.class, targetPostRecipientId, authorId, idempotencyKey, REGION,
			Timestamp.from(publishedAt), Timestamp.from(publishedAt));
	}

	@Test
	@DisplayName("findByIdAndRecipientId는 본인 수신 항목만 반환한다")
	void findsRecipientOnlyForOwner() {
		assertThat(postRecipientRepository.findByIdAndRecipientId(postRecipientId, recipientId)).isPresent();
		assertThat(postRecipientRepository.findByIdAndRecipientId(postRecipientId, outsiderId)).isEmpty();
	}

	@Test
	@DisplayName("transitionToAnswered는 예상 상태가 아니면 전이하지 않는다")
	void transitionToAnsweredSkipsWhenStatusAlreadyChanged() {
		PostRecipient opened = postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		PostRecipient answered = opened.answered(NOW.plusSeconds(20));

		assertThat(postRecipientRepository.transitionToAnswered(answered, PostRecipientStatus.OPENED)).isPresent();
		assertThat(postRecipientRepository.findById(postRecipientId).orElseThrow().getStatus())
			.isEqualTo(PostRecipientStatus.ANSWERED);

		assertThat(postRecipientRepository.transitionToAnswered(answered, PostRecipientStatus.OPENED)).isEmpty();
	}

	@Test
	@DisplayName("findByPostIdAndRecipientId는 질문글과 수신자 조합으로 찾는다")
	void findsRecipientByPostAndUser() {
		PostRecipient found = postRecipientRepository.findByPostIdAndRecipientId(postId, recipientId).orElseThrow();

		assertThat(found.getId()).isEqualTo(postRecipientId);
		assertThat(found.getMatchedBearingDegrees()).isEqualByComparingTo(BigDecimal.valueOf(45));
		assertThat(postRecipientRepository.findByPostIdAndRecipientId(postId, outsiderId)).isEmpty();
	}

	@Test
	@DisplayName("findByIdAndSenderId는 본인이 보낸 질문글만 반환한다")
	void findsPostOnlyForSender() {
		assertThat(directionPostRepository.findByIdAndSenderId(postId, senderId)).isPresent();
		assertThat(directionPostRepository.findByIdAndSenderId(postId, outsiderId)).isEmpty();
	}

	@Test
	@DisplayName("release는 호출자가 준 시각을 updated_at에 기록한다")
	void releaseUsesInjectedTimestamp() {
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 1, 1, NOW, NOW, NOW));
		Instant releasedAt = NOW.plus(30, ChronoUnit.MINUTES);

		assertThat(receiveStateRepository.release(recipientId, releasedAt)).isTrue();

		Timestamp updatedAt = jdbc.queryForObject(
			"SELECT updated_at FROM recipient_receive_state WHERE user_id = ?", Timestamp.class, recipientId);
		assertThat(updatedAt.toInstant()).isEqualTo(releasedAt);
		assertThat(receiveStateRepository.findByUserId(recipientId).orElseThrow().getActiveUnhandledCount()).isZero();
	}

	@Test
	@DisplayName("release는 이미 0인 카운터를 음수로 만들지 않는다")
	void releaseDoesNotGoNegative() {
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 0, 0, NOW, null, NOW));

		assertThat(receiveStateRepository.release(recipientId, NOW.plusSeconds(1))).isFalse();
		assertThat(receiveStateRepository.findByUserId(recipientId).orElseThrow().getActiveUnhandledCount()).isZero();
	}

	@Test
	@DisplayName("열람은 멱등이며 최초 열람 시각을 유지한다")
	void openIsIdempotent() {
		PostRecipient opened = postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		PostRecipient reopened = postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(60));

		assertThat(opened.getStatus()).isEqualTo(PostRecipientStatus.OPENED);
		assertThat(reopened.getOpenedAt()).isEqualTo(NOW.plusSeconds(10));
	}

	@Test
	@DisplayName("남의 수신 항목은 열람할 수 없다")
	void cannotOpenOthersRecipient() {
		assertThatThrownBy(() -> postRecipientService.open(outsiderId, postRecipientId, NOW.plusSeconds(10)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.RECIPIENT_NOT_FOUND);
	}

	@Test
	@DisplayName("넘김 요청은 SKIP_PENDING으로 두고 슬롯을 해제하지 않는다")
	void requestSkipKeepsCapacity() {
		PostRecipient pending = postRecipientService.requestSkip(recipientId, postRecipientId, NOW.plusSeconds(10));

		assertThat(pending.getStatus()).isEqualTo(PostRecipientStatus.SKIP_PENDING);
		assertThat(pending.getSkipRequestedAt()).isEqualTo(NOW.plusSeconds(10));
		assertThat(pending.getCapacityReleasedAt()).isNull();
	}

	@Test
	@DisplayName("넘김을 되돌리면 열람 이력에 따라 이전 상태로 돌아간다")
	void revertSkipRestoresPreviousStatus() {
		postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		postRecipientService.requestSkip(recipientId, postRecipientId, NOW.plusSeconds(20));

		PostRecipient reverted = postRecipientService.revertSkip(recipientId, postRecipientId, NOW.plusSeconds(21));

		assertThat(reverted.getStatus()).isEqualTo(PostRecipientStatus.OPENED);
		assertThat(reverted.getSkipRequestedAt()).isNull();
	}

	@Test
	@DisplayName("markAnswersRead는 수신자의 답변 열람 시각을 기록한다")
	void recipientMarksAnswersRead() {
		PostRecipient read = postRecipientService.markAnswersRead(recipientId, postRecipientId, NOW.plusSeconds(120));

		assertThat(read.getAnswersReadAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(postRecipientRepository.findById(postRecipientId).orElseThrow().getAnswersReadAt())
			.isEqualTo(NOW.plusSeconds(120));
	}

	@Test
	@DisplayName("남의 수신 항목은 답변 열람 시각을 기록할 수 없다")
	void outsiderCannotMarkRecipientAnswersRead() {
		assertThatThrownBy(() -> postRecipientService.markAnswersRead(outsiderId, postRecipientId, NOW.plusSeconds(120)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.RECIPIENT_NOT_FOUND);
	}

	@Test
	@DisplayName("markAnswersRead는 이미 기록된 시각보다 이른 시각으로 되돌아가지 않는다 — advanceAnswersReadAt의 GREATEST 보장")
	void recipientMarksAnswersReadNeverRegresses() {
		postRecipientService.markAnswersRead(recipientId, postRecipientId, NOW.plusSeconds(120));

		PostRecipient result = postRecipientService.markAnswersRead(recipientId, postRecipientId, NOW.plusSeconds(60));

		assertThat(result.getAnswersReadAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(postRecipientRepository.findById(postRecipientId).orElseThrow().getAnswersReadAt())
			.isEqualTo(NOW.plusSeconds(120));
	}

	@Test
	@DisplayName("markAnswersRead는 질문자의 답변 열람 시각을 기록한다")
	void marksAnswersReadForSender() {
		DirectionPost read = directionPostService.markAnswersRead(senderId, postId, NOW.plusSeconds(120));

		assertThat(read.getAnswersReadAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(directionPostRepository.findById(postId).orElseThrow().getAnswersReadAt())
			.isEqualTo(NOW.plusSeconds(120));
	}

	@Test
	@DisplayName("질문자가 아니면 답변 열람 시각을 기록할 수 없다")
	void nonSenderCannotMarkAnswersRead() {
		assertThatThrownBy(() -> directionPostService.markAnswersRead(outsiderId, postId, NOW.plusSeconds(120)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.POST_NOT_FOUND);
	}

	@Test
	@DisplayName("markAnswersRead는 이미 기록된 시각보다 이른 시각으로 되돌아가지 않는다")
	void marksAnswersReadNeverRegresses() {
		directionPostService.markAnswersRead(senderId, postId, NOW.plusSeconds(120));

		DirectionPost result = directionPostService.markAnswersRead(senderId, postId, NOW.plusSeconds(60));

		assertThat(result.getAnswersReadAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(directionPostRepository.findById(postId).orElseThrow().getAnswersReadAt())
			.isEqualTo(NOW.plusSeconds(120));
	}

	@Test
	@DisplayName("수신 자격이 있는 사용자의 공감 토글은 남김과 취소를 오간다")
	void togglesPostReaction() {
		assertThat(postReactionService.toggle(postId, recipientId, NOW.plusSeconds(10))).isTrue();
		assertThat(postReactionRepository.countByPostId(postId)).isEqualTo(1L);

		assertThat(postReactionService.toggle(postId, recipientId, NOW.plusSeconds(20))).isFalse();
		assertThat(postReactionRepository.countByPostId(postId)).isZero();
	}

	@Test
	@DisplayName("수신 자격이 없는 사용자는 질문글에 공감할 수 없다")
	void outsiderCannotReactToPost() {
		assertThatThrownBy(() -> postReactionService.toggle(postId, outsiderId, NOW.plusSeconds(10)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INELIGIBLE_REACTOR);
		assertThat(postReactionRepository.countByPostId(postId)).isZero();
	}

	@Test
	@DisplayName("질문자의 답변 공감 토글은 남김과 취소를 오간다")
	void togglesAnswerReaction() {
		long answerId = publishedAnswer(postRecipientId, recipientId, "answer-1", NOW.plusSeconds(30));

		assertThat(answerReactionService.toggle(answerId, senderId, NOW.plusSeconds(40))).isTrue();
		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, senderId)).isPresent();

		assertThat(answerReactionService.toggle(answerId, senderId, NOW.plusSeconds(50))).isFalse();
		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, senderId)).isEmpty();
	}

	@Test
	@DisplayName("2026-08-07 개정: 질문자가 아닌 수신 자격자도 남의 답변에 공감할 수 있다")
	void eligibleRecipientCanReactToAnotherRecipientsAnswer() {
		long secondRecipientId = account("inbox-second-recipient");
		recipient(postId, secondRecipientId, "AVAILABLE");
		long answerId = publishedAnswer(postRecipientId, recipientId, "answer-1", NOW.plusSeconds(30));

		assertThat(answerReactionService.toggle(answerId, secondRecipientId, NOW.plusSeconds(40))).isTrue();
		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, secondRecipientId)).isPresent();
	}

	@Test
	@DisplayName("수신 자격자가 아니면 답변에 공감할 수 없고 commit 전에 차단된다")
	void outsiderCannotReactToAnswer() {
		long answerId = publishedAnswer(postRecipientId, recipientId, "answer-1", NOW.plusSeconds(30));

		assertThatThrownBy(() -> answerReactionService.toggle(answerId, outsiderId, NOW.plusSeconds(40)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INELIGIBLE_REACTOR);
		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, outsiderId)).isEmpty();
	}

	@Test
	@DisplayName("답변 작성자 본인은 자기 답변을 볼 자격이 있어도 공감할 수 없다 — 거절 사유는 자기 답변 금지")
	void answerAuthorCannotReactToOwnAnswer() {
		long answerId = publishedAnswer(postRecipientId, recipientId, "answer-1", NOW.plusSeconds(30));

		assertThatThrownBy(() -> answerReactionService.toggle(answerId, recipientId, NOW.plusSeconds(40)))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INELIGIBLE_REACTOR)
			.hasFieldOrPropertyWithValue("reason", "자기 답변에는 공감할 수 없습니다");
	}

	private long submittedAnswer(long targetPostRecipientId, long authorId, String idempotencyKey) {
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at)
			VALUES (?, ?, 'SUBMITTED', ?, '답변 본문', ?, 45, 'NEAR', 5000, 'PENDING', ?)
			RETURNING id
			""", Long.class, targetPostRecipientId, authorId, idempotencyKey, REGION, Timestamp.from(NOW));
	}

	@Test
	@DisplayName("답변이 공개되면 수신 항목이 ANSWERED가 되고 슬롯 1개가 회수된다")
	void publishingAnswerReleasesSlot() {
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 1, 1, NOW, NOW, NOW));
		postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		long answerId = submittedAnswer(postRecipientId, recipientId, "answer-publish");

		Answer published = answerNotificationService.publish(answerId, NOW.plusSeconds(30));

		assertThat(published.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
		PostRecipient recipient = postRecipientRepository.findById(postRecipientId).orElseThrow();
		assertThat(recipient.getStatus()).isEqualTo(PostRecipientStatus.ANSWERED);
		assertThat(recipient.getCapacityReleasedAt()).isEqualTo(NOW.plusSeconds(30));
		assertThat(receiveStateRepository.findByUserId(recipientId).orElseThrow().getActiveUnhandledCount()).isZero();
	}

	@Test
	@DisplayName("같은 답변을 두 번 공개해도 슬롯은 한 번만 회수된다")
	void publishingTwiceReleasesSlotOnce() {
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 2, 2, NOW, NOW, NOW));
		postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		long answerId = submittedAnswer(postRecipientId, recipientId, "answer-publish");

		answerNotificationService.publish(answerId, NOW.plusSeconds(30));
		answerNotificationService.publish(answerId, NOW.plusSeconds(40));

		assertThat(receiveStateRepository.findByUserId(recipientId).orElseThrow().getActiveUnhandledCount())
			.isEqualTo(1);
	}

	@Test
	@DisplayName("같은 답변을 동시에 공개해도 슬롯은 한 번만 회수된다")
	void publishingConcurrentlyReleasesSlotOnce() throws Exception {
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 5, 5, NOW, NOW, NOW));
		postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		long answerId = submittedAnswer(postRecipientId, recipientId, "answer-concurrent");

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Answer>> results = List.of(
				executor.submit(() -> publishAfterSignal(answerId, ready, start)),
				executor.submit(() -> publishAfterSignal(answerId, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			results.get(0).get(10, TimeUnit.SECONDS);
			results.get(1).get(10, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(receiveStateRepository.findByUserId(recipientId).orElseThrow().getActiveUnhandledCount())
			.isEqualTo(4);
	}

	private Answer publishAfterSignal(long answerId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return answerNotificationService.publish(answerId, NOW.plusSeconds(30));
	}

	@Test
	@DisplayName("수신 상한까지 찬 사용자가 답변하면 다시 받을 수 있게 된다")
	void answeringFreesCapacityForNewPosts() {
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 1, 1, NOW, NOW, NOW));
		postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		long answerId = submittedAnswer(postRecipientId, recipientId, "answer-publish");

		assertThat(receiveStateRepository.reserve(recipientId, NOW.plusSeconds(20), 1)).isFalse();
		answerNotificationService.publish(answerId, NOW.plusSeconds(30));
		assertThat(receiveStateRepository.reserve(recipientId, NOW.plusSeconds(40), 1)).isTrue();
	}

	@Test
	@DisplayName("답변을 보내면 그 질문글이 답변 안 한 목록에서 답변한 목록으로 옮겨간다")
	void answeredPostMovesFromUnansweredToAnsweredCategory() {
		receiveStateRepository.save(RecipientReceiveState.restore(recipientId, 1, 1, NOW, NOW, NOW));
		postRecipientService.open(recipientId, postRecipientId, NOW.plusSeconds(10));
		assertThat(inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(15)).cards()).hasSize(1);

		long answerId = submittedAnswer(postRecipientId, recipientId, "answer-vanish");
		answerNotificationService.publish(answerId, NOW.plusSeconds(30));

		assertThat(inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(35)).cards()).isEmpty();
		assertThat(inboxQueryService.list(recipientId, InboxCategory.ANSWERED, null, NOW.plusSeconds(35)).cards()).hasSize(1);
	}

	@Test
	@DisplayName("넘김을 요청해도 되돌릴 수 있는 동안에는 답변 안 한 목록에 남는다")
	void skipPendingStaysVisibleUntilConfirmed() {
		postRecipientService.requestSkip(recipientId, postRecipientId, NOW.plusSeconds(10));

		assertThat(inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, NOW.plusSeconds(15)).cards()).hasSize(1);
	}
}
