/**
 * Created at: 2026-08-08T14:19:39+09:00
 * Source scenario: TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS-INT-001 through INT-003, INT-006
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.feed.service.PostAnswerQueryService;
import com.dnd.qello.feed.view.AnswerCard;

@SpringBootTest
@ActiveProfiles("test")
class PostAnswerQueryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-ANSQ";
	private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PostAnswerQueryService postAnswerQueryService;

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
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Answer Query', 'REGION')", REGION);

		senderId = account("aq-sender");
		recipientId = account("aq-recipient");
		outsiderId = account("aq-outsider");
		questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '오늘 뭐 하고 있나요?', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), senderId);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
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

	/**
	 * V1 ck_post_recipient_status_timestamps가 상태별로 채워야 하는 타임스탬프를
	 * 강제한다(AVAILABLE은 discovered_at·opened_at 둘 다 NULL, OPENED는 둘 다 필요,
	 * ANSWERED는 discovered_at·opened_at·capacity_released_at 모두 필요) — 기존
	 * InboxQueryIntegrationTest.recipient()와 동일한 패턴이다.
	 */
	private long recipient(long postId, long userId, String status) {
		return recipient(postId, userId, status, 5000L);
	}

	private long recipient(long postId, long userId, String status, long distanceM) {
		String[] columns = switch (status) {
			case "OPENED" -> new String[] {"discovered_at", "opened_at"};
			case "ANSWERED" -> new String[] {"discovered_at", "opened_at", "capacity_released_at"};
			case "SKIPPED" -> new String[] {"discovered_at", "skip_requested_at", "skipped_at", "capacity_released_at"};
			default -> new String[0];
		};
		String columnList = columns.length == 0 ? "" : ", " + String.join(", ", columns);
		String placeholderList = columns.length == 0 ? "" : ", "
			+ Arrays.stream(columns).map(column -> "?").collect(Collectors.joining(", "));
		Object[] baseParams = {postId, userId, status, REGION, Timestamp.from(NOW), distanceM};
		Object[] params = new Object[baseParams.length + columns.length];
		System.arraycopy(baseParams, 0, params, 0, baseParams.length);
		Arrays.fill(params, baseParams.length, params.length, Timestamp.from(NOW));
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, inbound_bearing_deg, distance_m%s)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, 225, ?%s)
			RETURNING id
			""".formatted(columnList, placeholderList), Long.class, params);
	}

	private long answer(long postRecipientId, long authorId, String key, Instant publishedAt) {
		return answer(postRecipientId, authorId, key, publishedAt, 5000L);
	}

	private long answer(long postRecipientId, long authorId, String key, Instant publishedAt, long distanceM) {
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at, published_at)
			VALUES (?, ?, 'PUBLISHED', ?, '답변 본문', ?, 45, 'NEAR', ?, 'PASSED', ?, ?)
			RETURNING id
			""", Long.class, postRecipientId, authorId, key, REGION, distanceM,
			Timestamp.from(NOW), Timestamp.from(publishedAt));
	}

	@Test
	@DisplayName("질문자와 수신 자격자는 답변을 볼 수 있고 무관한 outsider는 볼 수 없다")
	void senderAndEligibleRecipientCanViewAnswersButOutsiderCannot() {
		long postId = post("p-answers", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long recipientRowId = recipient(postId, recipientId, "OPENED");
		answer(recipientRowId, recipientId, "a-1", NOW.plusSeconds(60));
		Instant at = NOW.plusSeconds(120);

		assertThat(postAnswerQueryService.canView(senderId, postId, at)).isTrue();
		assertThat(postAnswerQueryService.canView(recipientId, postId, at)).isTrue();
		assertThat(postAnswerQueryService.canView(outsiderId, postId, at)).isFalse();

		assertThat(postAnswerQueryService.answers(senderId, postId, null, 10, at)).hasSize(1);
		assertThat(postAnswerQueryService.answers(recipientId, postId, null, 10, at)).hasSize(1);
		assertThat(postAnswerQueryService.answers(outsiderId, postId, null, 10, at)).isEmpty();
	}

	@Test
	@DisplayName("아직 EXPIRED로 전이되지 않았어도 만료 시각이 지나면 답변 안 한 수신자는 자격을 잃는다")
	void timeBoundRecipientLosesEligibilityAfterExpiryRegardlessOfStatus() {
		long postId = post("p-expiring", NOW, NOW.plusSeconds(30));
		recipient(postId, recipientId, "AVAILABLE");
		long answeredRowId = recipient(postId, outsiderId, "ANSWERED");
		answer(answeredRowId, outsiderId, "a-1", NOW.plusSeconds(10));

		assertThat(postAnswerQueryService.canView(recipientId, postId, NOW.plusSeconds(29))).isTrue();
		assertThat(postAnswerQueryService.canView(recipientId, postId, NOW.plusSeconds(31))).isFalse();
		assertThat(postAnswerQueryService.answers(recipientId, postId, null, 10, NOW.plusSeconds(31))).isEmpty();
	}

	@Test
	@DisplayName("넘긴(SKIPPED) 수신자는 답변 내용도 개수도 받지 못한다")
	void skippedRecipientCannotViewAnswersAtAll() {
		long postId = post("p-skipped", NOW, NOW.plus(2, ChronoUnit.HOURS));
		recipient(postId, recipientId, "SKIPPED");
		long answeredRowId = recipient(postId, outsiderId, "ANSWERED");
		answer(answeredRowId, outsiderId, "a-1", NOW.plusSeconds(10));
		Instant at = NOW.plusSeconds(20);

		assertThat(postAnswerQueryService.canView(recipientId, postId, at)).isFalse();
		assertThat(postAnswerQueryService.answers(recipientId, postId, null, 10, at)).isEmpty();
	}

	@Test
	@DisplayName("답변한 수신자는 만료 후에도 자격을 유지한다")
	void answeredRecipientKeepsEligibilityAfterExpiry() {
		long postId = post("p-answered-expired", NOW, NOW.plusSeconds(30));
		long recipientRowId = recipient(postId, recipientId, "ANSWERED");
		answer(recipientRowId, recipientId, "a-1", NOW.plusSeconds(10));

		assertThat(postAnswerQueryService.canView(recipientId, postId, NOW.plusSeconds(3600))).isTrue();
		assertThat(postAnswerQueryService.answers(recipientId, postId, null, 10, NOW.plusSeconds(3600))).hasSize(1);
	}

	@Test
	@DisplayName("답변 목록은 공개된 답변만 최신순으로 보여주고 뷰어 기준 공감 여부와 공감 수를 함께 준다")
	void listsPublishedAnswersNewestFirstWithViewerScopedReaction() {
		long postId = post("p-order", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long first = recipient(postId, recipientId, "OPENED");
		long second = recipient(postId, outsiderId, "OPENED");
		long oldAnswer = answer(first, recipientId, "a-old", NOW.plusSeconds(60));
		long newAnswer = answer(second, outsiderId, "a-new", NOW.plusSeconds(120));
		jdbc.update("INSERT INTO answer_reaction (answer_id, reactor_id) VALUES (?, ?)", newAnswer, senderId);
		Instant at = NOW.plusSeconds(200);

		List<AnswerCard> answers = postAnswerQueryService.answers(senderId, postId, null, 10, at);

		assertThat(answers).extracting(AnswerCard::answerId).containsExactly(newAnswer, oldAnswer);
		assertThat(answers.getFirst().reactedByMe()).isTrue();
		assertThat(answers.getFirst().reactionCount()).isEqualTo(1L);
		assertThat(answers.getLast().reactedByMe()).isFalse();
		assertThat(answers.getLast().reactionCount()).isZero();
	}

	@Test
	@DisplayName("답변의 거리는 답변 작성자가 아니라 현재 뷰어의 질문 원점까지 거리로 표시한다")
	void usesViewerDistanceToQuestionOriginForAnswerDisplay() {
		long postId = post("p-viewer-distance", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long authorRecipientId = recipient(postId, recipientId, "OPENED", 5_000L);
		recipient(postId, outsiderId, "OPENED", 15_000L);
		answer(authorRecipientId, recipientId, "a-viewer-distance", NOW.plusSeconds(60), 1_000_000L);
		Instant at = NOW.plusSeconds(120);

		AnswerCard farViewerCard = postAnswerQueryService.answers(outsiderId, postId, null, 10, at).getFirst();
		assertThat(farViewerCard.distanceM()).isEqualTo(15_000L);
		assertThat(farViewerCard.distanceBand()).isNull();

		AnswerCard nearViewerCard = postAnswerQueryService.answers(recipientId, postId, null, 10, at).getFirst();
		assertThat(nearViewerCard.distanceM()).isNull();
		assertThat(nearViewerCard.distanceBand()).isEqualTo("10km 이내");
	}

	@Test
	@DisplayName("뷰어가 차단한 답변 작성자의 답변은 그 뷰어의 목록에서만 빠진다")
	void hidesBlockedAuthorAnswersFromTheBlockingViewerOnly() {
		long postId = post("p-blocked-author", NOW, NOW.plus(2, ChronoUnit.HOURS));
		long recipientRowId = recipient(postId, recipientId, "OPENED");
		recipient(postId, outsiderId, "OPENED");
		answer(recipientRowId, recipientId, "a-1", NOW.plusSeconds(60));
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id) VALUES (?, ?)", senderId, recipientId);
		Instant at = NOW.plusSeconds(120);

		assertThat(postAnswerQueryService.answers(senderId, postId, null, 10, at)).isEmpty();
		assertThat(postAnswerQueryService.answers(outsiderId, postId, null, 10, at)).hasSize(1);
	}
}
