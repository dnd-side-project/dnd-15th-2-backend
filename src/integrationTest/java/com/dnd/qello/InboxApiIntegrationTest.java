/**
 * Created at: 2026-08-16T14:59:30+09:00
 * Source scenario: TEST-PLAN-GH-124-INBOX-READ-SKIP-API-INT-001 through INT-012,
 * TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-INT-020, INT-021
 * (added 2026-08-19T15:16:05+09:00)
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;
import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;
import com.dnd.qello.feed.view.InboxListing;

@SpringBootTest
@ActiveProfiles("test")
@Import(Inbox124TestClockConfiguration.class)
class InboxApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-16T06:00:00.123456Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxApplicationService inbox;
	@Autowired
	private ReceiveSlotReleaseService receiveSlotReleaseService;
	@Autowired
	private Inbox124MutableClock clock;

	private Inbox124IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;
	private long outsiderId;

	@BeforeEach
	void resetFixtures() {
		clock.setInstant(NOW);
		fixtures = new Inbox124IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("inbox124-sender");
		recipientId = fixtures.account("inbox124-recipient");
		outsiderId = fixtures.account("inbox124-outsider");
	}

	@Test
	@DisplayName("INT-001 카테고리 목록은 상태 집합을 나누고 matched_at 최신순을 유지한다")
	void listsCardsByCategoryInNewestFirstOrder() {
		long olderPostId = fixtures.post(senderId, "int001-older", NOW.plusSeconds(3600), "ACTIVE", null);
		long olderRecipientId = fixtures.available(olderPostId, recipientId, NOW.minusSeconds(20), 0);
		long newerPostId = fixtures.post(senderId, "int001-newer", NOW.plusSeconds(3600), "ACTIVE", null);
		long newerRecipientId = fixtures.opened(newerPostId, recipientId, NOW.minusSeconds(10), 180);
		long answeredPostId = fixtures.post(senderId, "int001-answered", NOW.plusSeconds(3600), "ACTIVE", null);
		long answeredRecipientId = fixtures.answered(answeredPostId, recipientId, NOW.minusSeconds(5), 90, NOW.minusSeconds(1));

		InboxListing unanswered = inbox.list(recipientId, InboxCategory.UNANSWERED, null);
		InboxListing answered = inbox.list(recipientId, InboxCategory.ANSWERED, null);

		assertThat(unanswered.cards()).extracting(card -> card.postRecipientId())
			.containsExactly(newerRecipientId, olderRecipientId);
		assertThat(answered.cards()).extracting(card -> card.postRecipientId())
			.containsExactly(answeredRecipientId);
	}

	@Test
	@DisplayName("INT-002 방향 필터는 카드만 N으로 줄이고 칩은 카테고리 전체 N/S를 유지한다")
	void directionFilterDoesNotNarrowCategoryChips() {
		long northPost = fixtures.post(senderId, "int002-north", NOW.plusSeconds(3600), "ACTIVE", null);
		long northRecipient = fixtures.available(northPost, recipientId, NOW.minusSeconds(2), 0);
		long southPost = fixtures.post(senderId, "int002-south", NOW.plusSeconds(3600), "ACTIVE", null);
		long southRecipient = fixtures.available(southPost, recipientId, NOW.minusSeconds(1), 180);

		InboxListing all = inbox.list(recipientId, InboxCategory.UNANSWERED, null);
		InboxListing northOnly = inbox.list(recipientId, InboxCategory.UNANSWERED, "N");

		assertThat(all.cards()).extracting(card -> card.postRecipientId())
			.containsExactly(southRecipient, northRecipient);
		assertThat(northOnly.cards()).extracting(card -> card.postRecipientId()).containsExactly(northRecipient);
		assertThat(all.chips()).extracting(chip -> chip.segmentKey()).containsExactly("N", "S");
		assertThat(northOnly.chips()).isEqualTo(all.chips());
	}

	@Test
	@DisplayName("INT-003 상세 열람은 AVAILABLE/DISCOVERED만 최초 OPENED로 전이하고 열린 시각을 보존한다")
	void detailOpensOnlyEligibleUnopenedRowsOnce() {
		long availablePost = fixtures.post(senderId, "int003-available", NOW.plusSeconds(3600), "ACTIVE", null);
		long availableId = fixtures.available(availablePost, recipientId, NOW.minusSeconds(30), 0);
		long discoveredPost = fixtures.post(senderId, "int003-discovered", NOW.plusSeconds(3600), "ACTIVE", null);
		long discoveredId = fixtures.discovered(discoveredPost, recipientId, NOW.minusSeconds(30), 90, NOW.minusSeconds(20));
		long openedPost = fixtures.post(senderId, "int003-opened", NOW.plusSeconds(3600), "ACTIVE", null);
		Instant originalOpenedAt = NOW.minusSeconds(10);
		long openedId = fixtures.opened(openedPost, recipientId, NOW.minusSeconds(30), 180, NOW.minusSeconds(20), originalOpenedAt);

		assertThat(inbox.detail(recipientId, availableId).openedAt()).isEqualTo(NOW);
		assertThat(inbox.detail(recipientId, discoveredId).openedAt()).isEqualTo(NOW);
		assertThat(inbox.detail(recipientId, openedId).openedAt()).isEqualTo(originalOpenedAt);
		clock.setInstant(NOW.plusSeconds(30));

		assertThat(inbox.detail(recipientId, availableId).openedAt()).isEqualTo(NOW);
		assertThat(inbox.detail(recipientId, discoveredId).openedAt()).isEqualTo(NOW);
		assertThat(inbox.detail(recipientId, openedId).openedAt()).isEqualTo(originalOpenedAt);
		assertThat(fixtures.status(availableId)).isEqualTo("OPENED");
		assertThat(fixtures.status(discoveredId)).isEqualTo("OPENED");
	}

	@Test
	@DisplayName("INT-004 상세 상태와 만료 경계는 미종결 만료 전 및 ANSWERED 예외만 허용한다")
	void detailEligibilityMatchesStatusAndExpiryMatrix() {
		Instant expiry = NOW.plusSeconds(30);
		List<String> statuses = List.of(
			"AVAILABLE", "DISCOVERED", "OPENED", "SKIP_PENDING", "ANSWERED", "SKIPPED", "EXPIRED", "BLOCKED");

		for (String status : statuses) {
			long postId = fixtures.post(senderId, "int004-" + status.toLowerCase(), expiry, "ACTIVE", null);
			long postRecipientId = fixtures.recipientForStatus(postId, recipientId, status, NOW.minusSeconds(20), 0);
			Inbox124IntegrationFixtures.RecipientSnapshot initialSnapshot = fixtures.snapshot(postRecipientId);
			clock.setInstant(expiry.minusNanos(1_000));

			boolean visibleBefore = !List.of("SKIPPED", "EXPIRED", "BLOCKED").contains(status);
			if (visibleBefore) {
				assertThat(inbox.detail(recipientId, postRecipientId).card().postRecipientId())
					.as("before expiry for %s", status).isEqualTo(postRecipientId);
			} else {
				assertNotFound(() -> inbox.detail(recipientId, postRecipientId));
				assertThat(fixtures.snapshot(postRecipientId))
					.as("rejected before-expiry row remains unchanged for %s", status)
					.isEqualTo(initialSnapshot);
			}
			Inbox124IntegrationFixtures.RecipientSnapshot beforeExpiredRejections = fixtures.snapshot(postRecipientId);
			clock.setInstant(expiry);
			if (status.equals("ANSWERED")) {
				assertThat(inbox.detail(recipientId, postRecipientId).card().postRecipientId()).isEqualTo(postRecipientId);
				clock.setInstant(expiry.plusNanos(1_000));
				assertThat(inbox.detail(recipientId, postRecipientId).card().postRecipientId()).isEqualTo(postRecipientId);
			} else {
				assertNotFound(() -> inbox.detail(recipientId, postRecipientId));
				clock.setInstant(expiry.plusNanos(1_000));
				assertNotFound(() -> inbox.detail(recipientId, postRecipientId));
			}
			assertThat(fixtures.snapshot(postRecipientId))
				.as("exact/after-expiry access leaves row unchanged for %s", status)
				.isEqualTo(beforeExpiredRejections);
		}
	}

	@Test
	@DisplayName("INT-005 만료된 ANSWERED는 목록에서 빠지지만 상세 상태와 열람 시각은 유지한다")
	void expiredAnsweredItemRemainsDetailVisibleOnly() {
		Instant openedAt = NOW.minusSeconds(30);
		long postId = fixtures.post(senderId, "int005-answered", NOW.minusSeconds(1), "ACTIVE", null);
		long postRecipientId = fixtures.answered(
			postId, recipientId, NOW.minusSeconds(60), 0, NOW.minusSeconds(10), NOW.minusSeconds(40), openedAt);

		assertThat(inbox.list(recipientId, InboxCategory.ANSWERED, null).cards()).isEmpty();
		assertThat(inbox.detail(recipientId, postRecipientId).card().status()).isEqualTo(PostRecipientStatus.ANSWERED);
		assertThat(inbox.detail(recipientId, postRecipientId).openedAt()).isEqualTo(openedAt);
		assertThat(fixtures.status(postRecipientId)).isEqualTo("ANSWERED");
	}

	@Test
	@DisplayName("INT-006 존재하지 않는 항목과 타인 소유 항목은 상세·skip·revert에서 같은 404이며 계정도 검증한다")
	void ownershipAndAccountEligibilityDoNotRevealItemExistence() {
		long postId = fixtures.post(senderId, "int006-owner", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(1), 0);
		long missingId = postRecipientId + 999_999L;
		long blockedAccountId = fixtures.account("inbox124-blocked-account", "USER", "BLOCKED");

		for (ThrowingAction action : List.<ThrowingAction>of(
			() -> inbox.detail(outsiderId, postRecipientId),
			() -> inbox.skip(outsiderId, postRecipientId),
			() -> inbox.revertSkip(outsiderId, postRecipientId),
			() -> inbox.detail(recipientId, missingId),
			() -> inbox.skip(recipientId, missingId),
			() -> inbox.revertSkip(recipientId, missingId))) {
			assertNotFound(action);
		}
		assertThatThrownBy(() -> inbox.list(blockedAccountId, InboxCategory.UNANSWERED, null))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ACCOUNT_NOT_ELIGIBLE);
		assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
	}

	@Test
	@DisplayName("INT-007 비ACTIVE·삭제·만료 글과 SKIPPED/EXPIRED/BLOCKED 항목은 조회·명령으로 다시 열리지 않는다")
	void hiddenAndTerminalItemsCannotBeReadOrReopened() {
		long hiddenPost = fixtures.post(senderId, "int007-hidden", NOW.plusSeconds(3600), "HIDDEN", null);
		long hiddenId = fixtures.available(hiddenPost, recipientId, NOW.minusSeconds(10), 0);
		long deletedPost = fixtures.post(senderId, "int007-deleted", NOW.plusSeconds(3600), "DELETED", NOW.minusSeconds(1));
		long deletedId = fixtures.opened(deletedPost, recipientId, NOW.minusSeconds(10), 90);
		long expiredPost = fixtures.post(senderId, "int007-expired-post", NOW, "ACTIVE", null);
		long expiredPostId = fixtures.available(expiredPost, recipientId, NOW.minusSeconds(10), 180);
		long skippedPost = fixtures.post(senderId, "int007-skipped", NOW.plusSeconds(3600), "ACTIVE", null);
		long skippedId = fixtures.skipped(skippedPost, recipientId, NOW.minusSeconds(10), 270, NOW.minusSeconds(2), NOW.minusSeconds(1));
		long expiredRecipientPost = fixtures.post(senderId, "int007-expired-recipient", NOW.plusSeconds(3600), "ACTIVE", null);
		long expiredId = fixtures.expired(expiredRecipientPost, recipientId, NOW.minusSeconds(10), 45, NOW.minusSeconds(1));
		long blockedPost = fixtures.post(senderId, "int007-blocked", NOW.plusSeconds(3600), "ACTIVE", null);
		long blockedId = fixtures.blocked(blockedPost, recipientId, NOW.minusSeconds(10), 135, NOW.minusSeconds(1));

		for (long id : List.of(hiddenId, deletedId, expiredPostId, skippedId, expiredId, blockedId)) {
			Inbox124IntegrationFixtures.RecipientSnapshot beforeRejections = fixtures.snapshot(id);
			assertNotFound(() -> inbox.detail(recipientId, id));
			assertNotFound(() -> inbox.skip(recipientId, id));
			assertNotFound(() -> inbox.revertSkip(recipientId, id));
			assertThat(fixtures.snapshot(id))
				.as("detail/skip/revert rejection leaves row unchanged for postRecipientId=%s", id)
				.isEqualTo(beforeRejections);
		}
		assertThat(inbox.list(recipientId, InboxCategory.UNANSWERED, null).cards()).isEmpty();
	}

	@Test
	@DisplayName("INT-008 양방향 active block은 목록·상세·skip을 숨기고 released block은 다시 허용한다")
	void bidirectionalActiveAndReleasedBlocksShareTheSameScope() {
		long recipientBlocksSender = fixtures.account("inbox124-recipient-blocks-sender");
		long senderBlocksRecipient = fixtures.account("inbox124-sender-blocks-recipient");
		long releasedByRecipient = fixtures.account("inbox124-released-by-recipient");
		long releasedBySender = fixtures.account("inbox124-released-by-sender");
		long activeA = fixtures.available(fixtures.post(recipientBlocksSender, "int008-active-a", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(4), 0);
		long activeB = fixtures.available(fixtures.post(senderBlocksRecipient, "int008-active-b", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(3), 90);
		long releasedA = fixtures.available(fixtures.post(releasedByRecipient, "int008-released-a", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(2), 180);
		long releasedB = fixtures.available(fixtures.post(releasedBySender, "int008-released-b", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(1), 270);
		fixtures.block(recipientId, recipientBlocksSender, null);
		fixtures.block(senderBlocksRecipient, recipientId, null);
		fixtures.block(recipientId, releasedByRecipient, NOW.minusSeconds(1));
		fixtures.block(releasedBySender, recipientId, NOW.minusSeconds(1));

		assertThat(inbox.list(recipientId, InboxCategory.UNANSWERED, null).cards())
			.extracting(card -> card.postRecipientId()).containsExactly(releasedB, releasedA);
		assertNotFound(() -> inbox.detail(recipientId, activeA));
		assertNotFound(() -> inbox.skip(recipientId, activeB));
		assertThat(inbox.detail(recipientId, releasedA).card().postRecipientId()).isEqualTo(releasedA);
		assertThat(inbox.skip(recipientId, releasedB).getStatus()).isEqualTo(PostRecipientStatus.SKIP_PENDING);
	}

	@Test
	@DisplayName("INT-009 skip은 SKIP_PENDING과 서버 deadline만 기록하고 슬롯·outbox를 변경하지 않는다")
	void skipKeepsReceiveCapacityAndDoesNotCreateOutbox() {
		long postId = fixtures.post(senderId, "int009-skip", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		int outboxBefore = fixtures.outboxCount();

		PostRecipient result = inbox.skip(recipientId, postRecipientId);

		assertThat(result.getStatus()).isEqualTo(PostRecipientStatus.SKIP_PENDING);
		assertThat(result.getSkipRequestedAt()).isEqualTo(NOW);
		assertThat(inbox.revertibleUntil(result)).isEqualTo(NOW.plusSeconds(5));
		assertThat(result.getCapacityReleasedAt()).isNull();
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(1);
		assertThat(fixtures.outboxCount()).isEqualTo(outboxBefore);
	}

	@Test
	@DisplayName("INT-010 중복 skip은 최초 요청 시각과 deadline을 연장하지 않는다")
	void duplicateSkipReturnsTheFirstSnapshot() {
		long postId = fixtures.post(senderId, "int010-duplicate", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.opened(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		int outboxBefore = fixtures.outboxCount();
		PostRecipient first = inbox.skip(recipientId, postRecipientId);
		clock.setInstant(NOW.plusSeconds(4));

		PostRecipient duplicate = inbox.skip(recipientId, postRecipientId);

		assertThat(duplicate.getSkipRequestedAt()).isEqualTo(first.getSkipRequestedAt()).isEqualTo(NOW);
		assertThat(inbox.revertibleUntil(duplicate)).isEqualTo(NOW.plusSeconds(5));
		assertThat(fixtures.skipRequestedAt(postRecipientId)).isEqualTo(NOW);
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(1);
		assertThat(fixtures.outboxCount()).isEqualTo(outboxBefore);
	}

	@Test
	@DisplayName("INT-011 deadline 직전 revert는 AVAILABLE/DISCOVERED/OPENED 원상태와 슬롯을 보존한다")
	void revertBeforeDeadlineRestoresEachPreviousState() {
		long availableId = fixtures.available(
			fixtures.post(senderId, "int011-available", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(10), 0);
		long discoveredId = fixtures.discovered(
			fixtures.post(senderId, "int011-discovered", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(10), 90, NOW.minusSeconds(5));
		long openedId = fixtures.opened(
			fixtures.post(senderId, "int011-opened", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(10), 180);
		fixtures.receiveState(recipientId, 3);
		for (long id : List.of(availableId, discoveredId, openedId)) {
			inbox.skip(recipientId, id);
		}
		clock.setInstant(NOW.plusSeconds(5).minusNanos(1_000));

		assertThat(inbox.revertSkip(recipientId, availableId).getStatus()).isEqualTo(PostRecipientStatus.AVAILABLE);
		assertThat(inbox.revertSkip(recipientId, discoveredId).getStatus()).isEqualTo(PostRecipientStatus.DISCOVERED);
		assertThat(inbox.revertSkip(recipientId, openedId).getStatus()).isEqualTo(PostRecipientStatus.OPENED);
		assertThat(List.of(availableId, discoveredId, openedId)).allSatisfy(id -> {
			assertThat(fixtures.skipRequestedAt(id)).isNull();
			assertThat(fixtures.capacityReleasedAt(id)).isNull();
		});
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(3);
	}

	@Test
	@DisplayName("INT-012 deadline 정확히 및 이후 revert는 409이고 confirm 후보·슬롯·최초 시각을 유지한다")
	void revertAtAndAfterDeadlineFailsWithoutPartialMutation() {
		long exactId = fixtures.available(
			fixtures.post(senderId, "int012-exact", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(10), 0);
		long afterId = fixtures.available(
			fixtures.post(senderId, "int012-after", NOW.plusSeconds(3600), "ACTIVE", null), recipientId, NOW.minusSeconds(9), 180);
		fixtures.receiveState(recipientId, 2);
		inbox.skip(recipientId, exactId);
		inbox.skip(recipientId, afterId);

		clock.setInstant(NOW.plusSeconds(5));
		assertConflict(() -> inbox.revertSkip(recipientId, exactId));
		clock.setInstant(NOW.plusSeconds(5).plusNanos(1_000));
		assertConflict(() -> inbox.revertSkip(recipientId, afterId));

		assertThat(fixtures.status(exactId)).isEqualTo("SKIP_PENDING");
		assertThat(fixtures.status(afterId)).isEqualTo("SKIP_PENDING");
		assertThat(fixtures.skipRequestedAt(exactId)).isEqualTo(NOW);
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(2);
		assertThat(receiveSlotReleaseService.findConfirmableSkips(NOW.plusSeconds(5)))
			.extracting(PostRecipient::getId).contains(exactId, afterId);
	}

	@Test
	@DisplayName("INT-020 뷰어 본인이 공감한 질문글은 목록과 상세 모두에서 reactedByMe가 true다")
	void reactedByMeIsTrueForViewersOwnReaction() {
		long postId = fixtures.post(senderId, "int020-post", NOW.plusSeconds(3600), "ACTIVE", null);
		long recipientItemId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.react(postId, recipientId, NOW.minusSeconds(5));

		InboxListing listing = inbox.list(recipientId, InboxCategory.UNANSWERED, null);
		InboxDetail detail = inbox.detail(recipientId, recipientItemId);

		assertThat(listing.cards()).hasSize(1);
		assertThat(listing.cards().get(0).postRecipientId()).isEqualTo(recipientItemId);
		assertThat(listing.cards().get(0).reactedByMe()).isTrue();
		assertThat(detail.card().reactedByMe()).isTrue();
		assertThat(detail.card().reactionCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-021 다른 수신자만 공감한 질문글은 뷰어 기준 reactedByMe가 false이고 reactionCount는 그대로다")
	void reactedByMeIsFalseWhenOnlyAnotherRecipientReacted() {
		long postId = fixtures.post(senderId, "int021-post", NOW.plusSeconds(3600), "ACTIVE", null);
		long recipientItemId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.available(postId, outsiderId, NOW.minusSeconds(9), 90);
		fixtures.react(postId, outsiderId, NOW.minusSeconds(5));

		InboxDetail detail = inbox.detail(recipientId, recipientItemId);

		assertThat(detail.card().reactedByMe()).isFalse();
		assertThat(detail.card().reactionCount()).isEqualTo(1);
	}

	private static void assertNotFound(ThrowingAction action) {
		assertThatThrownBy(action::run)
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ITEM_NOT_FOUND);
	}

	private static void assertConflict(ThrowingAction action) {
		assertThatThrownBy(action::run)
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_TRANSITION_CONFLICT);
	}

	@FunctionalInterface
	private interface ThrowingAction {
		void run();
	}
}

@TestConfiguration(proxyBeanMethods = false)
class Inbox124TestClockConfiguration {

	@Bean
	@Primary
	Inbox124MutableClock inbox124MutableClock() {
		return new Inbox124MutableClock(Instant.parse("2026-08-16T06:00:00.123456Z"), ZoneOffset.UTC);
	}
}

final class Inbox124MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	Inbox124MutableClock(Instant initial, ZoneId zone) {
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
	public Clock withZone(ZoneId newZone) {
		return new Inbox124MutableClock(current.get(), newZone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}
}

final class Inbox124IntegrationFixtures {

	static final String REGION = "TEST-INBOX124";
	private static final Instant BASELINE = Instant.parse("2026-08-16T05:00:00.000000Z");
	private final JdbcTemplate jdbc;
	private final Instant now;

	Inbox124IntegrationFixtures(JdbcTemplate jdbc, Instant now) {
		this.jdbc = jdbc;
		this.now = now;
	}

	void reset() {
		jdbc.update("""
			DELETE FROM outbox_event
			WHERE aggregate_type = 'ANSWER'
			  AND aggregate_id IN (SELECT id FROM answer WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("DELETE FROM answer WHERE coarse_region_code = ?", REGION);
		jdbc.update("""
			DELETE FROM post_recipient pr
			WHERE pr.recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR pr.post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("DELETE FROM post_audience WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM recipient_receive_state WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("""
			DELETE FROM user_block
			WHERE blocker_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR blocked_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("DELETE FROM approved_question WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Inbox 124 Test', 'REGION')
			""", REGION);
	}

	long account(String nickname) {
		return account(nickname, "USER", "ACTIVE");
	}

	long account(String nickname, String role, String status) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES (?, 'KR', ?, ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, role, status, REGION, nickname);
	}

	long post(long senderId, String key, Instant expiresAt, String status, Instant deletedAt) {
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'INBOX124 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(BASELINE), Timestamp.from(BASELINE), senderId);
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at, deleted_at)
			VALUES (?, ?, ?, ?, 'INBOX124 본문', ?, 'PASSED', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, status, "gh124-" + key, REGION,
			Timestamp.from(BASELINE), Timestamp.from(BASELINE), Timestamp.from(expiresAt), ts(deletedAt));
	}

	long available(long postId, long recipientId, Instant matchedAt, int inboundBearing) {
		return recipient(postId, recipientId, "AVAILABLE", matchedAt, inboundBearing,
			null, null, null, null, null, null, null);
	}

	long discovered(long postId, long recipientId, Instant matchedAt, int inboundBearing, Instant discoveredAt) {
		return recipient(postId, recipientId, "DISCOVERED", matchedAt, inboundBearing,
			discoveredAt, null, null, null, null, null, null);
	}

	long opened(long postId, long recipientId, Instant matchedAt, int inboundBearing) {
		return opened(postId, recipientId, matchedAt, inboundBearing, matchedAt.plusSeconds(1), matchedAt.plusSeconds(2));
	}

	long opened(long postId, long recipientId, Instant matchedAt, int inboundBearing,
		Instant discoveredAt, Instant openedAt) {
		return recipient(postId, recipientId, "OPENED", matchedAt, inboundBearing,
			discoveredAt, openedAt, null, null, null, null, null);
	}

	long answered(long postId, long recipientId, Instant matchedAt, int inboundBearing, Instant answeredAt) {
		return answered(postId, recipientId, matchedAt, inboundBearing, answeredAt,
			matchedAt.plusSeconds(1), matchedAt.plusSeconds(2));
	}

	long answered(long postId, long recipientId, Instant matchedAt, int inboundBearing, Instant answeredAt,
		Instant discoveredAt, Instant openedAt) {
		return recipient(postId, recipientId, "ANSWERED", matchedAt, inboundBearing,
			discoveredAt, openedAt, null, null, answeredAt, null, null);
	}

	long skipped(long postId, long recipientId, Instant matchedAt, int inboundBearing,
		Instant requestedAt, Instant skippedAt) {
		return recipient(postId, recipientId, "SKIPPED", matchedAt, inboundBearing,
			null, null, requestedAt, skippedAt, skippedAt, null, null);
	}

	long expired(long postId, long recipientId, Instant matchedAt, int inboundBearing, Instant expiredAt) {
		return recipient(postId, recipientId, "EXPIRED", matchedAt, inboundBearing,
			null, null, null, null, expiredAt, expiredAt, null);
	}

	long blocked(long postId, long recipientId, Instant matchedAt, int inboundBearing, Instant blockedAt) {
		return recipient(postId, recipientId, "BLOCKED", matchedAt, inboundBearing,
			null, null, null, null, blockedAt, null, blockedAt);
	}

	long recipientForStatus(long postId, long recipientId, String status, Instant matchedAt, int inboundBearing) {
		Instant discoveredAt = matchedAt.plusSeconds(1);
		Instant openedAt = matchedAt.plusSeconds(2);
		Instant terminalAt = matchedAt.plusSeconds(3);
		return switch (status) {
			case "AVAILABLE" -> available(postId, recipientId, matchedAt, inboundBearing);
			case "DISCOVERED" -> discovered(postId, recipientId, matchedAt, inboundBearing, discoveredAt);
			case "OPENED" -> opened(postId, recipientId, matchedAt, inboundBearing, discoveredAt, openedAt);
			case "SKIP_PENDING" -> recipient(postId, recipientId, status, matchedAt, inboundBearing,
				discoveredAt, openedAt, terminalAt, null, null, null, null);
			case "ANSWERED" -> answered(postId, recipientId, matchedAt, inboundBearing, terminalAt, discoveredAt, openedAt);
			case "SKIPPED" -> skipped(postId, recipientId, matchedAt, inboundBearing, terminalAt, terminalAt.plusSeconds(1));
			case "EXPIRED" -> expired(postId, recipientId, matchedAt, inboundBearing, terminalAt);
			case "BLOCKED" -> blocked(postId, recipientId, matchedAt, inboundBearing, terminalAt);
			default -> throw new IllegalArgumentException("unsupported status: " + status);
		};
	}

	long recipient(long postId, long recipientId, String status, Instant matchedAt, int inboundBearing,
		Instant discoveredAt, Instant openedAt, Instant skipRequestedAt, Instant skippedAt,
		Instant capacityReleasedAt, Instant expiredAt, Instant blockedAt) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, skip_requested_at, skipped_at, capacity_released_at,
				 expired_at, blocked_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 45, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, status, REGION, Timestamp.from(matchedAt),
			ts(discoveredAt), ts(openedAt), ts(skipRequestedAt), ts(skippedAt), ts(capacityReleasedAt),
			ts(expiredAt), ts(blockedAt), inboundBearing);
	}

	/** fk_post_reaction_recipient가 (post_id, reactor_id)를 post_recipient에서 강제하므로
	 * reactorId는 그 postId의 수신자여야 한다. */
	void react(long postId, long reactorId, Instant at) {
		jdbc.update("""
			INSERT INTO post_reaction (post_id, reactor_id, created_at)
			VALUES (?, ?, ?)
			""", postId, reactorId, Timestamp.from(at));
	}

	void receiveState(long userId, int count) {
		jdbc.update("""
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at,
				 last_received_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT (user_id) DO UPDATE
			SET active_unhandled_count = EXCLUDED.active_unhandled_count,
			    recent_received_count = EXCLUDED.recent_received_count,
			    recent_window_started_at = EXCLUDED.recent_window_started_at,
			    last_received_at = EXCLUDED.last_received_at,
			    updated_at = EXCLUDED.updated_at
			""", userId, count, count, Timestamp.from(BASELINE), Timestamp.from(now), Timestamp.from(now));
	}

	void block(long blockerId, long blockedId, Instant releasedAt) {
		jdbc.update("""
			INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at)
			VALUES (?, ?, ?, ?)
			""", blockerId, blockedId, Timestamp.from(now.minusSeconds(2)), ts(releasedAt));
	}

	String status(long postRecipientId) {
		return jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId);
	}

	Instant skipRequestedAt(long postRecipientId) {
		return instantColumn(postRecipientId, "skip_requested_at");
	}

	Instant capacityReleasedAt(long postRecipientId) {
		return instantColumn(postRecipientId, "capacity_released_at");
	}

	Instant blockedAt(long postRecipientId) {
		return instantColumn(postRecipientId, "blocked_at");
	}

	int activeCount(long userId) {
		return jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, userId);
	}

	int outboxCount() {
		return jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class);
	}

	RecipientSnapshot snapshot(long postRecipientId) {
		return jdbc.queryForObject("""
			SELECT status, matched_at, discovered_at, opened_at, skip_requested_at, skipped_at,
			       capacity_released_at, expired_at, blocked_at, answers_read_at
			FROM post_recipient
			WHERE id = ?
			""", (rs, rowNum) -> new RecipientSnapshot(
			rs.getString("status"), instant(rs, "matched_at"), instant(rs, "discovered_at"),
			instant(rs, "opened_at"), instant(rs, "skip_requested_at"), instant(rs, "skipped_at"),
			instant(rs, "capacity_released_at"), instant(rs, "expired_at"), instant(rs, "blocked_at"),
			instant(rs, "answers_read_at")), postRecipientId);
	}

	private Instant instantColumn(long postRecipientId, String column) {
		Timestamp value = jdbc.queryForObject("SELECT " + column + " FROM post_recipient WHERE id = ?",
			(rs, rowNum) -> rs.getTimestamp(column), postRecipientId);
		return value == null ? null : value.toInstant();
	}

	private static Timestamp ts(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	record RecipientSnapshot(
		String status,
		Instant matchedAt,
		Instant discoveredAt,
		Instant openedAt,
		Instant skipRequestedAt,
		Instant skippedAt,
		Instant capacityReleasedAt,
		Instant expiredAt,
		Instant blockedAt,
		Instant answersReadAt
	) { }
}
