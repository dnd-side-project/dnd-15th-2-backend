/**
 * Created at: 2026-08-06T14:00:00+09:00
 * Source scenario: TEST-PLAN-GH-67-INBOX-SENT-POST-INT-001 through INT-019
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.domain.RecipientReceiveState;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.direction.service.PostRecipientService;

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
	private PostRecipientService postRecipientService;
	@Autowired
	private DirectionPostService directionPostService;

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
		jdbc.update("INSERT INTO region_code (code, display_name, level) VALUES (?, 'Inbox Test', 'COUNTRY')", REGION);

		senderId = account("inbox-sender");
		recipientId = account("inbox-recipient");
		outsiderId = account("inbox-outsider");
		postId = post("inbox-post", NOW.plus(1, ChronoUnit.HOURS));
		postRecipientId = recipient(postId, recipientId, "AVAILABLE");
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
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
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?)
			RETURNING id
			""", Long.class, targetPostId, targetRecipientId, status, REGION, Timestamp.from(NOW));
	}

	@Test
	@DisplayName("findByIdAndRecipientId는 본인 수신 항목만 반환한다")
	void findsRecipientOnlyForOwner() {
		assertThat(postRecipientRepository.findByIdAndRecipientId(postRecipientId, recipientId)).isPresent();
		assertThat(postRecipientRepository.findByIdAndRecipientId(postRecipientId, outsiderId)).isEmpty();
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

		PostRecipient reverted = postRecipientService.revertSkip(recipientId, postRecipientId);

		assertThat(reverted.getStatus()).isEqualTo(PostRecipientStatus.OPENED);
		assertThat(reverted.getSkipRequestedAt()).isNull();
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
}
