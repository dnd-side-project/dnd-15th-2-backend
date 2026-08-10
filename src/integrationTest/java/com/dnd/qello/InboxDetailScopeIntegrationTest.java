/**
 * Created at: 2026-08-10T21:51:18+09:00
 * Source scenario: TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-001 through INT-008
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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

import com.dnd.qello.feed.service.InboxQueryService;
import com.dnd.qello.feed.service.PostAnswerQueryService;
import com.dnd.qello.feed.view.InboxCategory;

@SpringBootTest
@ActiveProfiles("test")
class InboxDetailScopeIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-INBOX96";
	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxQueryService inboxQueryService;
	@Autowired
	private PostAnswerQueryService postAnswerQueryService;

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
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Inbox Detail Scope', 'REGION')", REGION);

		senderId = account("id-sender");
		recipientId = account("id-recipient");
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

	private long post(String key, String status, Instant expiresAt) {
		return post(key, status, expiresAt, null);
	}

	private long post(String key, String status, Instant expiresAt, Instant deletedAt) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at, deleted_at)
			VALUES (?, ?, ?, ?, '질문글 본문', ?, 'PASSED', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, status, key, REGION,
			Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(expiresAt),
			deletedAt == null ? null : Timestamp.from(deletedAt));
	}

	private long recipient(long postId, String status) {
		return recipient(postId, recipientId, status);
	}

	private long recipient(long postId, long userId, String status) {
		String[] columns = switch (status) {
			case "DISCOVERED" -> new String[] {"discovered_at"};
			case "OPENED" -> new String[] {"discovered_at", "opened_at"};
			case "ANSWERED" -> new String[] {"discovered_at", "opened_at", "capacity_released_at"};
			case "SKIP_PENDING" -> new String[] {"discovered_at", "skip_requested_at"};
			case "SKIPPED" -> new String[] {"discovered_at", "skip_requested_at", "skipped_at", "capacity_released_at"};
			case "EXPIRED" -> new String[] {"expired_at", "capacity_released_at"};
			case "BLOCKED" -> new String[] {"blocked_at", "capacity_released_at"};
			default -> new String[0];
		};
		String columnList = columns.length == 0 ? "" : ", " + String.join(", ", columns);
		String placeholderList = columns.length == 0 ? "" : ", "
			+ Arrays.stream(columns).map(column -> "?").collect(Collectors.joining(", "));
		Object[] baseParams = {postId, userId, status, REGION, Timestamp.from(NOW), 5000L};
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

	private long answer(long postRecipientId, String key) {
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at, published_at)
			VALUES (?, ?, 'PUBLISHED', ?, '답변 본문', ?, 45, 'NEAR', 5000, 'PASSED', ?, ?)
			RETURNING id
			""", Long.class, postRecipientId, recipientId, key, REGION,
			Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(10)));
	}

	@Test
	@DisplayName("SKIPPED 수신 항목의 상세는 빈 결과다")
	void skippedDetailIsEmpty() {
		long postId = post("p-skipped", "ACTIVE", NOW.plusSeconds(3600));
		long postRecipientId = recipient(postId, "SKIPPED");

		assertThat(inboxQueryService.detail(recipientId, postRecipientId, NOW.plusSeconds(1))).isEmpty();
	}

	@Test
	@DisplayName("답변 없이 만료된 EXPIRED 수신 항목은 만료 전후 시각 계약에 따라 상세가 차단된다")
	void expiredWithoutAnswerIsEmptyAndAtIsExplicit() {
		long expiredPostId = post("p-expired", "ACTIVE", NOW.plusSeconds(30));
		long expiredRecipientId = recipient(expiredPostId, "EXPIRED");
		assertThat(inboxQueryService.detail(recipientId, expiredRecipientId, NOW.plusSeconds(31))).isEmpty();

		long availablePostId = post("p-boundary", "ACTIVE", NOW.plusSeconds(30));
		long availableRecipientId = recipient(availablePostId, "AVAILABLE");
		assertThat(inboxQueryService.detail(recipientId, availableRecipientId, NOW.plusSeconds(29))).isPresent();
		assertThat(inboxQueryService.detail(recipientId, availableRecipientId, NOW.plusSeconds(30))).isEmpty();
	}

	@Test
	@DisplayName("ANSWERED 수신 항목은 만료 후에도 상세와 답변 열람 자격을 유지한다")
	void answeredDetailAndAnswersRemainVisibleAfterExpiry() {
		long postId = post("p-answered-expired", "ACTIVE", NOW.plusSeconds(30));
		long postRecipientId = recipient(postId, "ANSWERED");
		answer(postRecipientId, "a-answered-expired");
		Instant afterExpiry = NOW.plusSeconds(3600);

		assertThat(inboxQueryService.detail(recipientId, postRecipientId, afterExpiry)).isPresent();
		assertThat(postAnswerQueryService.canView(recipientId, postId, afterExpiry)).isTrue();
		assertThat(postAnswerQueryService.answers(recipientId, postId, null, 10, afterExpiry)).hasSize(1);
		assertThat(inboxQueryService.list(recipientId, InboxCategory.ANSWERED, null, afterExpiry).cards()).isEmpty();
	}

	@Test
	@DisplayName("SKIP_PENDING 수신 항목은 만료 전 유예 중 상세·목록·답변 열람 자격을 유지한다")
	void skipPendingRemainsVisibleBeforeExpiry() {
		long postId = post("p-skip-pending", "ACTIVE", NOW.plusSeconds(3600));
		long postRecipientId = recipient(postId, "SKIP_PENDING");
		Instant at = NOW.plusSeconds(1);

		assertThat(inboxQueryService.detail(recipientId, postRecipientId, at)).isPresent();
		assertThat(inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, at).cards())
			.extracting(card -> card.postRecipientId()).containsExactly(postRecipientId);
		assertThat(postAnswerQueryService.canView(recipientId, postId, at)).isTrue();
	}

	@Test
	@DisplayName("활성 발신자 차단은 상세와 목록을 숨기고 차단 해제는 다시 노출한다")
	void activeSenderBlockHidesDetailAndReleaseRestoresIt() {
		long postId = post("p-blocked-sender", "ACTIVE", NOW.plusSeconds(3600));
		long postRecipientId = recipient(postId, "AVAILABLE");
		Instant createdAt = NOW.plusSeconds(1);
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
			recipientId, senderId, Timestamp.from(createdAt));

		assertThat(inboxQueryService.detail(recipientId, postRecipientId, createdAt)).isEmpty();
		assertThat(inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, createdAt).cards()).isEmpty();

		jdbc.update("UPDATE user_block SET released_at = ? WHERE blocker_id = ? AND blocked_id = ?",
			Timestamp.from(createdAt.plusSeconds(1)), recipientId, senderId);
		assertThat(inboxQueryService.detail(recipientId, postRecipientId, createdAt)).isPresent();
	}

	@Test
	@DisplayName("존재하지 않는 수신 항목과 다른 사용자의 수신 항목은 모두 Optional.empty다")
	void nonexistentAndUnauthorizedDetailsAreIndistinguishable() {
		long postId = post("p-owner-scope", "ACTIVE", NOW.plusSeconds(3600));
		long postRecipientId = recipient(postId, "OPENED");
		long outsiderId = account("id-outsider");
		Instant at = NOW.plusSeconds(1);

		assertThat(inboxQueryService.detail(outsiderId, postRecipientId, at)).isEmpty();
		assertThat(inboxQueryService.detail(recipientId, postRecipientId + 999_999L, at)).isEmpty();
	}

	@Test
	@DisplayName("비활성 또는 삭제된 질문글은 수신자 상태와 무관하게 상세와 목록에서 숨겨진다")
	void inactiveAndDeletedPostsAreHidden() {
		long hiddenPostId = post("p-hidden", "HIDDEN", NOW.plusSeconds(3600));
		long hiddenRecipientId = recipient(hiddenPostId, "OPENED");
		long deletedPostId = post("p-deleted", "DELETED", NOW.plusSeconds(3600), NOW.plusSeconds(1));
		long deletedRecipientId = recipient(deletedPostId, "OPENED");
		Instant at = NOW.plusSeconds(2);

		assertThat(inboxQueryService.detail(recipientId, hiddenRecipientId, at)).isEmpty();
		assertThat(inboxQueryService.detail(recipientId, deletedRecipientId, at)).isEmpty();
		assertThat(inboxQueryService.list(recipientId, InboxCategory.UNANSWERED, null, at).cards()).isEmpty();
	}

	@Test
	@DisplayName("상태와 만료 시각 조합은 답변 완료 항목만 만료 후에도 상세·답변 열람을 허용한다")
	void statusAndTimeMatrixMatchesRecipientEligibilityPolicy() {
		List<String> statuses = List.of("AVAILABLE", "DISCOVERED", "OPENED", "SKIP_PENDING", "ANSWERED", "SKIPPED", "EXPIRED", "BLOCKED");
		Instant atBeforeExpiry = NOW.plusSeconds(29);
		Instant atAfterExpiry = NOW.plusSeconds(31);

		for (String status : statuses) {
			long postId = post("p-matrix-" + status.toLowerCase(), "ACTIVE", NOW.plusSeconds(30));
			long postRecipientId = recipient(postId, status);
			if (status.equals("ANSWERED")) {
				answer(postRecipientId, "a-matrix-answered");
			}

			boolean visibleBefore = !status.equals("SKIPPED") && !status.equals("EXPIRED") && !status.equals("BLOCKED");
			boolean visibleAfter = status.equals("ANSWERED");
			assertThat(inboxQueryService.detail(recipientId, postRecipientId, atBeforeExpiry).isPresent())
				.as("detail before expiry for %s", status).isEqualTo(visibleBefore);
			assertThat(inboxQueryService.detail(recipientId, postRecipientId, atAfterExpiry).isPresent())
				.as("detail after expiry for %s", status).isEqualTo(visibleAfter);
			assertThat(postAnswerQueryService.canView(recipientId, postId, atBeforeExpiry))
				.as("answer eligibility before expiry for %s", status).isEqualTo(visibleBefore);
			assertThat(postAnswerQueryService.canView(recipientId, postId, atAfterExpiry))
				.as("answer eligibility after expiry for %s", status).isEqualTo(visibleAfter);
		}
	}
}
