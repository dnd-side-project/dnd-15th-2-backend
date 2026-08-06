/**
 * Created at: 2026-08-06T15:00:00+09:00
 * Source scenario: TEST-PLAN-GH-67-INBOX-QUERY-INT-001 through INT-005
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.feed.service.InboxQueryService;
import com.dnd.qello.feed.view.InboxCard;

@SpringBootTest
@ActiveProfiles("test")
class InboxQueryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-INBOXQ";
	private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxQueryService inboxQueryService;

	private long senderId;
	private long recipientId;
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
		jdbc.update("INSERT INTO region_code (code, display_name, level) VALUES (?, 'Inbox Query', 'COUNTRY')", REGION);

		senderId = account("iq-sender");
		recipientId = account("iq-recipient");
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

	private long post(long author, String key, Instant expiresAt, String status) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, ?, ?, '본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, author, questionId, status, key, REGION,
			Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(expiresAt));
	}

	private long recipient(long targetPostId, String status, Instant matchedAt) {
		String columns = switch (status) {
			case "SKIP_PENDING" -> "skip_requested_at";
			case "OPENED" -> "opened_at";
			default -> null;
		};
		if (columns == null) {
			return jdbc.queryForObject("""
				INSERT INTO post_recipient
					(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at)
				VALUES (?, ?, ?, 'NEAR', 45, ?, ?)
				RETURNING id
				""", Long.class, targetPostId, recipientId, status, REGION, Timestamp.from(matchedAt));
		}
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, %s)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, ?, ?)
			RETURNING id
			""".formatted(columns), Long.class, targetPostId, recipientId, status, REGION,
			Timestamp.from(matchedAt), Timestamp.from(matchedAt), Timestamp.from(matchedAt));
	}

	@Test
	@DisplayName("수신함은 아직 처리하지 않은 질문글만 보여준다")
	void listsOnlyUnhandledPosts() {
		long active = post(senderId, "p-active", NOW.plus(1, ChronoUnit.HOURS), "ACTIVE");
		recipient(active, "AVAILABLE", NOW);

		List<InboxCard> cards = inboxQueryService.list(recipientId, NOW.plusSeconds(1));

		assertThat(cards).hasSize(1);
		assertThat(cards.getFirst().postId()).isEqualTo(active);
		assertThat(cards.getFirst().questionText()).isEqualTo("오늘 뭐 하고 있나요?");
		assertThat(cards.getFirst().mediaIds()).isEmpty();
	}

	@Test
	@DisplayName("넘김 되돌리기 대기(SKIP_PENDING) 항목은 수신함에 남는다")
	void keepsSkipPendingInInbox() {
		long post = post(senderId, "p-pending", NOW.plus(1, ChronoUnit.HOURS), "ACTIVE");
		recipient(post, "SKIP_PENDING", NOW);

		assertThat(inboxQueryService.list(recipientId, NOW.plusSeconds(1))).hasSize(1);
	}

	@Test
	@DisplayName("만료된 질문글은 수신함에서 빠진다")
	void hidesExpiredPosts() {
		long post = post(senderId, "p-expired", NOW.plusSeconds(30), "ACTIVE");
		recipient(post, "AVAILABLE", NOW);

		assertThat(inboxQueryService.list(recipientId, NOW.plusSeconds(31))).isEmpty();
		assertThat(inboxQueryService.list(recipientId, NOW.plusSeconds(29))).hasSize(1);
	}

	@Test
	@DisplayName("차단한 사용자가 보낸 질문글은 수신함에서 빠진다")
	void hidesBlockedSenderPosts() {
		long post = post(senderId, "p-blocked", NOW.plus(1, ChronoUnit.HOURS), "ACTIVE");
		recipient(post, "AVAILABLE", NOW);
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id) VALUES (?, ?)", recipientId, senderId);

		assertThat(inboxQueryService.list(recipientId, NOW.plusSeconds(1))).isEmpty();
	}

	@Test
	@DisplayName("남의 수신 항목 상세는 조회되지 않는다")
	void detailIsScopedToOwner() {
		long post = post(senderId, "p-detail", NOW.plus(1, ChronoUnit.HOURS), "ACTIVE");
		long postRecipientId = recipient(post, "OPENED", NOW);
		long outsiderId = account("iq-outsider");

		assertThat(inboxQueryService.detail(recipientId, postRecipientId)).isPresent();
		assertThat(inboxQueryService.detail(outsiderId, postRecipientId)).isEmpty();
	}
}
