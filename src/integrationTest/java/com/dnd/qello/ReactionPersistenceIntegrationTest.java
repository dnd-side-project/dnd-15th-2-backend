/**
 * Created at: 2026-08-05T11:48:27+09:00
 * Source scenario: TEST-PLAN-GH-55-REACTION-PERSISTENCE-INT-001 through INT-006,
 * TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-003 through INT-006
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.answer.domain.AnswerReaction;
import com.dnd.qello.answer.repository.AnswerReactionRepository;
import com.dnd.qello.direction.domain.PostReaction;
import com.dnd.qello.direction.repository.PostReactionRepository;

@SpringBootTest
@ActiveProfiles("test")
class ReactionPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-REACT";
	private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PostReactionRepository postReactionRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private AnswerReactionRepository answerReactionRepository;

	private long senderId;
	private long recipientId;
	private long secondRecipientId;
	private long outsiderId;
	private long postId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM answer_reaction");
		jdbc.update("DELETE FROM post_reaction");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, display_name, level) VALUES (?, 'Reaction Test', 'COUNTRY')", REGION);

		senderId = account("react-sender");
		recipientId = account("react-recipient");
		secondRecipientId = account("react-second-recipient");
		outsiderId = account("react-outsider");
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '공감 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), senderId);
		postId = jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', 'react-post', '글', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
		// recipientId는 답변을 쓰는 수신자다(insertAnswer가 author로 쓴다). secondRecipientId는
		// 같은 질문글의 또 다른 수신 자격자이지만 이 답변의 작성자는 아니다 — "볼 수 있는
		// 사람 전원이 공감할 수 있다"를 검증하려면 답변 작성자가 아닌 열람 자격자가 최소
		// 하나 더 필요하다.
		jdbc.update("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, opened_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'OPENED', 'NEAR', 10, ?, ?, ?, 190, 5000)
			""", postId, recipientId, REGION, Timestamp.from(NOW), Timestamp.from(NOW));
		jdbc.update("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, opened_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'OPENED', 'NEAR', 20, ?, ?, ?, 200, 6000)
			""", postId, secondRecipientId, REGION, Timestamp.from(NOW), Timestamp.from(NOW));
	}

	@Test
	@DisplayName("수신 자격이 있는 사용자만 질문글에 공감할 수 있다")
	void onlyRecipientsCanReactToAPost() {
		PostReaction saved = postReactionRepository.react(PostReaction.create(postId, recipientId, NOW));

		assertThat(saved.getPostId()).isEqualTo(postId);
		assertThat(saved.getReactorId()).isEqualTo(recipientId);
		assertThat(postReactionRepository.countByPostId(postId)).isEqualTo(1);
		assertThat(postReactionRepository.exists(postId, recipientId)).isTrue();

		assertThatThrownBy(() -> postReactionRepository.react(PostReaction.create(postId, outsiderId, NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("질문글 작성자는 자기 글의 수신자가 아니므로 자기 글에 공감할 수 없다")
	void theAuthorCannotReactToTheirOwnPost() {
		assertThatThrownBy(() -> postReactionRepository.react(PostReaction.create(postId, senderId, NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("공감 취소는 행 삭제이며 다시 누르면 되살아난다")
	void cancellingAReactionDeletesTheRow() {
		postReactionRepository.react(PostReaction.create(postId, recipientId, NOW));
		postReactionRepository.cancel(postId, recipientId);

		assertThat(postReactionRepository.countByPostId(postId)).isZero();
		assertThat(postReactionRepository.exists(postId, recipientId)).isFalse();

		postReactionRepository.react(PostReaction.create(postId, recipientId, NOW.plusSeconds(30)));

		assertThat(postReactionRepository.countByPostId(postId)).isEqualTo(1);
	}

	@Test
	@DisplayName("질문자와 수신 자격자가 같은 답변에 각각 공감하면 둘 다 성공하고 서로 다른 행으로 저장된다")
	void senderAndEligibleRecipientCanBothReactToTheSameAnswer() {
		long answerId = insertAnswer();

		AnswerReaction bySender = transactionTemplate.execute(status ->
			answerReactionRepository.react(
				AnswerReaction.create(answerId, senderId, NOW.plusSeconds(60))));
		AnswerReaction bySecondRecipient = transactionTemplate.execute(status ->
			answerReactionRepository.react(
				AnswerReaction.create(answerId, secondRecipientId, NOW.plusSeconds(70))));

		assertThat(bySender.getReactorId()).isEqualTo(senderId);
		assertThat(bySecondRecipient.getReactorId()).isEqualTo(secondRecipientId);
		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, senderId)).isPresent();
		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, secondRecipientId)).isPresent();
		Integer reactionCount = jdbc.queryForObject(
			"SELECT count(*) FROM answer_reaction WHERE answer_id = ?", Integer.class, answerId);
		assertThat(reactionCount).isEqualTo(2);
	}

	@Test
	@DisplayName("그 질문글과 무관한 사용자는 답변에 공감할 수 없다")
	void outsiderCannotReactToAnAnswer() {
		long answerId = insertAnswer();

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
			answerReactionRepository.react(
				AnswerReaction.create(answerId, outsiderId, NOW.plusSeconds(60)))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("답변자 본인은 열람 자격이 있어도 자기 답변에 공감할 수 없다")
	void theAnswerAuthorCannotReactToTheirOwnAnswer() {
		long answerId = insertAnswer();

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
			answerReactionRepository.react(
				AnswerReaction.create(answerId, recipientId, NOW.plusSeconds(60)))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("답변 공감 취소는 행 삭제이며 다시 누르면 되살아난다")
	void cancellingAnAnswerReactionDeletesTheRow() {
		long answerId = insertAnswer();
		transactionTemplate.executeWithoutResult(status ->
			answerReactionRepository.react(
				AnswerReaction.create(answerId, senderId, NOW.plusSeconds(60))));

		answerReactionRepository.cancel(answerId, senderId);

		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, senderId)).isEmpty();

		transactionTemplate.executeWithoutResult(status ->
			answerReactionRepository.react(
				AnswerReaction.create(answerId, senderId, NOW.plusSeconds(90))));

		assertThat(answerReactionRepository.findByAnswerIdAndReactorId(answerId, senderId)).isPresent();
	}

	private long insertAnswer() {
		Long postRecipientId = jdbc.queryForObject(
			"SELECT id FROM post_recipient WHERE post_id = ? AND recipient_id = ?",
			Long.class, postId, recipientId);
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at, published_at)
			VALUES (?, ?, 'PUBLISHED', 'react-answer', '답변', ?, 90, 'NEAR', 5000, 'PASSED', ?, ?)
			RETURNING id
			""", Long.class, postRecipientId, recipientId, REGION,
			Timestamp.from(NOW), Timestamp.from(NOW));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}
}
