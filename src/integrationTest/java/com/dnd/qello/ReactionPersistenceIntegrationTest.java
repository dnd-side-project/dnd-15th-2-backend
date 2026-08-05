/**
 * Created at: 2026-08-05T11:48:27+09:00
 * Source scenario: TEST-PLAN-GH-55-REACTION-PERSISTENCE-INT-001 through INT-003
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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

	private long senderId;
	private long recipientId;
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
		jdbc.update("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at, opened_at)
			VALUES (?, ?, 'OPENED', 'NEAR', 10, ?, ?, ?)
			""", postId, recipientId, REGION, Timestamp.from(NOW), Timestamp.from(NOW));
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

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}
}
