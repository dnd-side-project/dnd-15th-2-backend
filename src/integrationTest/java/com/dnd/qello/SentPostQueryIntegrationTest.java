/**
 * Created at: 2026-08-06T15:30:00+09:00
 * Source scenario: TEST-PLAN-GH-67-SENT-POST-QUERY-INT-006 through INT-012
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.feed.repository.SentPostQueryRepository;
import com.dnd.qello.feed.service.SentPostQueryService;
import com.dnd.qello.feed.view.AnswerCard;
import com.dnd.qello.feed.view.SentPostCard;
import com.dnd.qello.feed.view.SentPostFilter;

@SpringBootTest
@ActiveProfiles("test")
class SentPostQueryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-SENTQ";
	private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private SentPostQueryService sentPostQueryService;
	@Autowired
	private DirectionPostService directionPostService;

	private long senderId;
	private long recipientId;
	private long outsiderId;
	private long questionId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM answer_reaction");
		jdbc.update("DELETE FROM post_reaction");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, display_name, level) VALUES (?, 'Sent Query', 'COUNTRY')", REGION);

		senderId = account("sq-sender");
		recipientId = account("sq-recipient");
		outsiderId = account("sq-outsider");
		questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '오늘 뭐 하고 있나요?', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), senderId);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long post(String key, Instant submittedAt, Instant expiresAt) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, key, REGION,
			Timestamp.from(submittedAt), Timestamp.from(submittedAt), Timestamp.from(expiresAt));
	}

	private long recipient(long postId, long userId) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at)
			VALUES (?, ?, 'OPENED', 'NEAR', 45, ?, ?, ?, ?)
			RETURNING id
			""", Long.class, postId, userId, REGION,
			Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
	}

	private long answer(long postRecipientId, long authorId, String key, String status, Instant publishedAt) {
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, moderation_status, submitted_at, published_at)
			VALUES (?, ?, ?, ?, '답변 본문', ?, 45, 'NEAR', 'PASSED', ?, ?)
			RETURNING id
			""", Long.class, postRecipientId, authorId, status, key, REGION,
			Timestamp.from(NOW), publishedAt == null ? null : Timestamp.from(publishedAt));
	}

	@Test
	@DisplayName("필터는 만료 여부로 목록을 가른다")
	void filtersByExpiry() {
		post("p-live", NOW, NOW.plus(2, ChronoUnit.HOURS));
		post("p-done", NOW.minusSeconds(10), NOW.plusSeconds(30));
		Instant at = NOW.plus(1, ChronoUnit.HOURS);

		assertThat(sentPostQueryService.list(senderId, SentPostFilter.ALL, null, 10, at)).hasSize(2);
		assertThat(sentPostQueryService.list(senderId, SentPostFilter.IN_PROGRESS, null, 10, at))
			.singleElement().extracting(SentPostCard::questionText).isEqualTo("오늘 뭐 하고 있나요?");
		assertThat(sentPostQueryService.list(senderId, SentPostFilter.EXPIRED, null, 10, at)).hasSize(1);
	}

	@Test
	@DisplayName("커서 페이징은 중복이나 누락 없이 이어진다")
	void paginatesWithoutGaps() {
		List<Long> postIds = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			postIds.add(post("p-" + index, NOW.plusSeconds(index), NOW.plus(2, ChronoUnit.HOURS)));
		}
		Instant at = NOW.plus(1, ChronoUnit.HOURS);

		List<Long> collected = new ArrayList<>();
		SentPostQueryRepository.SentPostCursor cursor = null;
		List<SentPostCard> page;
		do {
			page = sentPostQueryService.list(senderId, SentPostFilter.ALL, cursor, 2, at);
			page.forEach(card -> collected.add(card.postId()));
			if (page.isEmpty()) break;
			SentPostCard last = page.getLast();
			cursor = new SentPostQueryRepository.SentPostCursor(last.submittedAt(), last.postId());
		} while (page.size() == 2);

		assertThat(collected).containsExactlyInAnyOrderElementsOf(postIds);
	}

	@Test
	@DisplayName("답변 수는 공개된 답변만 세고 공감 수는 질문글 공감만 센다")
	void countsPublishedAnswersAndReactions() {
		long postId = post("p-count", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long recipientRowId = recipient(postId, recipientId);
		answer(recipientRowId, recipientId, "a-published", "PUBLISHED", NOW.plusSeconds(60));
		long otherRecipient = recipient(postId, outsiderId);
		answer(otherRecipient, outsiderId, "a-rejected", "REJECTED", null);
		jdbc.update("INSERT INTO post_reaction (post_id, reactor_id) VALUES (?, ?)", postId, recipientId);

		SentPostCard card = sentPostQueryService
			.list(senderId, SentPostFilter.ALL, null, 10, NOW.plus(1, ChronoUnit.HOURS)).getFirst();

		assertThat(card.answerCount()).isEqualTo(1L);
		assertThat(card.reactionCount()).isEqualTo(1L);
	}

	@Test
	@DisplayName("새 답변 수는 answers_read_at 이후 공개된 답변만 센다")
	void countsUnreadAnswersAfterReadMark() {
		long postId = post("p-unread", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long recipientRowId = recipient(postId, recipientId);
		answer(recipientRowId, recipientId, "a-old", "PUBLISHED", NOW.plusSeconds(60));
		Instant at = NOW.plus(1, ChronoUnit.HOURS);

		assertThat(sentPostQueryService.list(senderId, SentPostFilter.ALL, null, 10, at)
			.getFirst().unreadAnswerCount()).isEqualTo(1L);

		directionPostService.markAnswersRead(senderId, postId, NOW.plusSeconds(120));

		assertThat(sentPostQueryService.list(senderId, SentPostFilter.ALL, null, 10, at)
			.getFirst().unreadAnswerCount()).isZero();
	}

	@Test
	@DisplayName("남이 보낸 질문글의 상세는 조회되지 않는다")
	void detailIsScopedToSender() {
		long postId = post("p-detail", NOW, NOW.plus(2, ChronoUnit.HOURS));

		assertThat(sentPostQueryService.detail(senderId, postId)).isPresent();
		assertThat(sentPostQueryService.detail(outsiderId, postId)).isEmpty();
	}

	@Test
	@DisplayName("질문자만 답변 목록을 볼 수 있다")
	void onlySenderCanReadAnswers() {
		long postId = post("p-answers", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long recipientRowId = recipient(postId, recipientId);
		answer(recipientRowId, recipientId, "a-1", "PUBLISHED", NOW.plusSeconds(60));

		assertThat(sentPostQueryService.answers(senderId, postId, null, 10)).hasSize(1);
		assertThat(sentPostQueryService.answers(outsiderId, postId, null, 10)).isEmpty();
		assertThat(sentPostQueryService.answers(recipientId, postId, null, 10)).isEmpty();
	}

	@Test
	@DisplayName("답변 목록은 공개된 답변만 최신순으로 보여주고 공감 여부를 함께 준다")
	void listsPublishedAnswersNewestFirst() {
		long postId = post("p-answers", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long first = recipient(postId, recipientId);
		long second = recipient(postId, outsiderId);
		long oldAnswer = answer(first, recipientId, "a-old", "PUBLISHED", NOW.plusSeconds(60));
		long newAnswer = answer(second, outsiderId, "a-new", "PUBLISHED", NOW.plusSeconds(120));
		jdbc.update("INSERT INTO answer_reaction (answer_id, reactor_id) VALUES (?, ?)", newAnswer, senderId);

		List<AnswerCard> answers = sentPostQueryService.answers(senderId, postId, null, 10);

		assertThat(answers).extracting(AnswerCard::answerId).containsExactly(newAnswer, oldAnswer);
		assertThat(answers.getFirst().reactedByMe()).isTrue();
		assertThat(answers.getFirst().authorNickname()).isEqualTo("sq-outsider");
		assertThat(answers.getLast().reactedByMe()).isFalse();
	}

	@Test
	@DisplayName("차단한 답변 작성자의 답변은 목록에서 빠진다")
	void hidesBlockedAuthorAnswers() {
		long postId = post("p-answers", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long recipientRowId = recipient(postId, recipientId);
		answer(recipientRowId, recipientId, "a-1", "PUBLISHED", NOW.plusSeconds(60));
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id) VALUES (?, ?)", senderId, recipientId);

		assertThat(sentPostQueryService.answers(senderId, postId, null, 10)).isEmpty();
		assertThat(sentPostQueryService
			.list(senderId, SentPostFilter.ALL, null, 10, NOW.plus(1, ChronoUnit.HOURS)).getFirst()
			.answerCount()).isZero();
	}
}
