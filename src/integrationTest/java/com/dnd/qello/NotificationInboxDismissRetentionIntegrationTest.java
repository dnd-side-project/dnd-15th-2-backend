/**
 * Created at: 2026-10-08T02:53:18+09:00
 * Source scenario: TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-001 through INT-007
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

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

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.NotificationSeenStateRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.service.NotificationInboxService;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationDismissal;
import com.dnd.qello.notification.view.NotificationListing;
import com.dnd.qello.notification.view.NotificationTargetDecision;
import com.dnd.qello.notification.view.NotificationTargetState;
import com.dnd.qello.notification.view.UnreadSignal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationDismissRetention332TestClockConfiguration.class)
class NotificationInboxDismissRetentionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");
	/** application.yml의 qello.notification.inbox.retention 기본값 P30D를 그대로 쓴다. */
	private static final Instant RETENTION_FLOOR = NOW.minus(Duration.ofDays(30));
	private static final Duration ONE_MICROSECOND = Duration.ofNanos(1_000);

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private NotificationInboxService inboxService;
	@Autowired
	private NotificationSeenStateRepository seenStateRepository;

	private Notification176IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;
	private long outsiderId;
	private long postId;

	@BeforeEach
	void resetFixtures() {
		fixtures = new Notification176IntegrationFixtures(jdbc, notifications, outboxEvents, NOW);
		fixtures.reset();
		senderId = fixtures.account("gh332-sender");
		recipientId = fixtures.account("gh332-recipient");
		outsiderId = fixtures.account("gh332-outsider");
		postId = fixtures.activePost(senderId, "gh332-post", NOW.plusSeconds(3600));
	}

	@Test
	@DisplayName("INT-001 전체 지우기는 본인의 요청 시각 이전 UNREAD·READ만 DISMISSED로 바꾸고 read_at과 나머지 행은 그대로 둔다")
	void dismissAllTransitionsOnlyOwnUnreadAndReadCreatedUpToRequestInstant() {
		Notification unread = unread(NOW.minusSeconds(60));
		Notification unreadAtInstant = unread(NOW);
		Notification read = fixtures.withStatus(unread(NOW.minusSeconds(50)), NotificationStatus.READ,
				NOW.minusSeconds(40));
		Notification revoked = fixtures.withStatus(unread(NOW.minusSeconds(30)), NotificationStatus.REVOKED, null);
		Notification alreadyDismissed = fixtures.withStatus(unread(NOW.minusSeconds(20)),
				NotificationStatus.DISMISSED, NOW.minusSeconds(10));
		Notification arrivedLater = unread(NOW.plusSeconds(10));
		Notification outsiders = fixtures.directionPostNotification(outsiderId, postId, NOW.minusSeconds(60));

		NotificationDismissal dismissal = inboxService.dismissAll(recipientId);

		assertThat(dismissal.dismissedCount()).isEqualTo(3);
		assertThat(dismissal.dismissedAt()).isEqualTo(NOW);
		assertRow(unread, "DISMISSED", null);
		assertRow(unreadAtInstant, "DISMISSED", null);
		assertRow(read, "DISMISSED", NOW.minusSeconds(40));
		assertRow(revoked, "REVOKED", null);
		assertRow(alreadyDismissed, "DISMISSED", NOW.minusSeconds(10));
		assertRow(arrivedLater, "UNREAD", null);
		assertRow(outsiders, "UNREAD", null);
	}

	@Test
	@DisplayName("INT-002 같은 시각에 전체 지우기를 다시 호출하면 0건이고 어떤 행도 바뀌지 않는다")
	void repeatedDismissAllIsIdempotent() {
		Notification unread = unread(NOW.minusSeconds(60));
		Notification read = fixtures.withStatus(unread(NOW.minusSeconds(50)), NotificationStatus.READ,
				NOW.minusSeconds(40));
		Notification revoked = fixtures.withStatus(unread(NOW.minusSeconds(30)), NotificationStatus.REVOKED, null);
		Notification outsiders = fixtures.directionPostNotification(outsiderId, postId, NOW.minusSeconds(60));
		assertThat(inboxService.dismissAll(recipientId).dismissedCount()).isEqualTo(2);

		NotificationDismissal second = inboxService.dismissAll(recipientId);

		assertThat(second.dismissedCount()).isZero();
		assertThat(second.dismissedAt()).isEqualTo(NOW);
		assertRow(unread, "DISMISSED", null);
		assertRow(read, "DISMISSED", NOW.minusSeconds(40));
		assertRow(revoked, "REVOKED", null);
		assertRow(outsiders, "UNREAD", null);
	}

	@Test
	@DisplayName("INT-003 전체 지우기 직후 목록은 비고 unreadCount는 0, hasUnseen은 false다")
	void inboxIsEmptyRightAfterDismissAll() {
		unread(NOW.minusSeconds(60));
		unread(NOW.minusSeconds(50));
		fixtures.withStatus(unread(NOW.minusSeconds(40)), NotificationStatus.READ, NOW.minusSeconds(30));
		UnreadSignal before = inboxService.unreadSignal(recipientId);
		assertThat(before.unreadCount()).isEqualTo(2);
		assertThat(before.hasUnseen()).isTrue();
		assertThat(before.seenAt()).isNull();

		inboxService.dismissAll(recipientId);

		NotificationListing listing = inboxService.list(recipientId, null, null, 20);
		UnreadSignal after = inboxService.unreadSignal(recipientId);
		assertThat(listing.items()).isEmpty();
		assertThat(listing.nextCursor()).isNull();
		assertThat(after.unreadCount()).isZero();
		assertThat(after.hasUnseen()).isFalse();
	}

	@Test
	@DisplayName("INT-004 DISMISSED 알림을 읽음 처리해도 예외 없이 DISMISSED와 read_at이 그대로다")
	void markReadKeepsDismissedNotificationUnchanged() {
		Notification dismissed = fixtures.withStatus(unread(NOW.minusSeconds(60)), NotificationStatus.DISMISSED, null);

		NotificationCard card = inboxService.markRead(recipientId, dismissed.id());

		assertThat(card.notificationId()).isEqualTo(dismissed.id());
		assertThat(card.unread()).isFalse();
		assertThat(card.readAt()).isNull();
		assertRow(dismissed, "DISMISSED", null);
	}

	@Test
	@DisplayName("INT-005 보존 하한 정각과 그 이전 알림은 빠지고 하한 1마이크로초 뒤 알림만 목록·배지에 남는다")
	void retentionFloorIsExclusiveAtMicrosecondPrecision() {
		unread(RETENTION_FLOOR.minus(ONE_MICROSECOND));
		unread(RETENTION_FLOOR);
		Notification justInside = unread(RETENTION_FLOOR.plus(ONE_MICROSECOND));

		NotificationListing listing = inboxService.list(recipientId, null, null, 20);
		UnreadSignal signal = inboxService.unreadSignal(recipientId);

		assertThat(listing.items()).extracting(NotificationCard::notificationId).containsExactly(justInside.id());
		assertThat(listing.items().getFirst().createdAt()).isEqualTo(RETENTION_FLOOR.plus(ONE_MICROSECOND));
		assertThat(signal.unreadCount()).isEqualTo(1);
		assertThat(signal.hasUnseen()).isTrue();
	}

	@Test
	@DisplayName("INT-005 하한 정각과 그 이전 UNREAD만 있으면 seen 기준선이 없든 그 줄보다 이르든 배지는 0이고 알림 점은 꺼진다")
	void badgeIgnoresRowsOutsideRetentionWithAndWithoutSeenAt() {
		unread(RETENTION_FLOOR.minus(ONE_MICROSECOND));
		unread(RETENTION_FLOOR);

		UnreadSignal withoutSeenAt = inboxService.unreadSignal(recipientId);
		assertThat(withoutSeenAt.seenAt()).isNull();
		assertThat(withoutSeenAt.hasUnseen()).isFalse();
		assertThat(withoutSeenAt.unreadCount()).isZero();
		assertThat(inboxService.list(recipientId, null, null, 20).items()).isEmpty();

		// 두 줄보다 이른 seen 기준선이라 seenAt 조건만으로는 두 줄 모두 "새 알림"이 된다 — 보존 하한만이 걸러낸다.
		Instant earlierSeenAt = RETENTION_FLOOR.minus(Duration.ofDays(1));
		seenStateRepository.advance(recipientId, earlierSeenAt);

		UnreadSignal withEarlierSeenAt = inboxService.unreadSignal(recipientId);
		assertThat(withEarlierSeenAt.seenAt()).isEqualTo(earlierSeenAt);
		assertThat(withEarlierSeenAt.hasUnseen()).isFalse();
		assertThat(withEarlierSeenAt.unreadCount()).isZero();
	}

	@Test
	@DisplayName("INT-006 보존 하한을 걸친 알림을 cursor로 순회하면 하한 이후 3건만 중복·누락 없이 나온다")
	void cursorPagingStopsAtRetentionFloor() {
		Notification newest = unread(NOW.minusSeconds(10));
		Notification middle = unread(NOW.minusSeconds(20));
		Notification oldestInside = unread(RETENTION_FLOOR.plusSeconds(1));
		unread(RETENTION_FLOOR.minusSeconds(1));
		unread(RETENTION_FLOOR.minus(Duration.ofDays(1)));

		NotificationListing page1 = inboxService.list(recipientId, null, null, 2);
		NotificationListing.Cursor cursor = page1.nextCursor();
		NotificationListing page2 = inboxService.list(recipientId, cursor.createdAt(), cursor.notificationId(), 2);

		assertThat(page1.items()).extracting(NotificationCard::notificationId)
				.containsExactly(newest.id(), middle.id());
		assertThat(page2.items()).extracting(NotificationCard::notificationId)
				.containsExactly(oldestInside.id());
		assertThat(page2.nextCursor()).isNull();
	}

	@Test
	@DisplayName("INT-007 진입 판정은 DISMISSED 알림과 보존 기간이 지난 알림도 기존처럼 판정한다")
	void targetDecisionIgnoresDismissAndRetention() {
		Notification dismissed = fixtures.withStatus(unread(NOW.minusSeconds(60)), NotificationStatus.DISMISSED, null);
		Notification expiredByRetention = unread(RETENTION_FLOOR.minus(Duration.ofDays(1)));

		NotificationTargetDecision dismissedDecision = inboxService.target(recipientId, dismissed.id());
		NotificationTargetDecision retainedDecision = inboxService.target(recipientId, expiredByRetention.id());

		assertThat(dismissedDecision.targetState()).isEqualTo(NotificationTargetState.AVAILABLE);
		assertThat(dismissedDecision.targetId()).isEqualTo(postId);
		assertThat(retainedDecision.targetState()).isEqualTo(NotificationTargetState.AVAILABLE);
		assertThat(retainedDecision.targetId()).isEqualTo(postId);
	}

	private Notification unread(Instant createdAt) {
		return fixtures.directionPostNotification(recipientId, postId, createdAt);
	}

	private void assertRow(Notification notification, String expectedStatus, Instant expectedReadAt) {
		String status = jdbc.queryForObject(
				"SELECT status FROM notification WHERE id = ?", String.class, notification.id());
		Timestamp readAt = jdbc.queryForObject(
				"SELECT read_at FROM notification WHERE id = ?", Timestamp.class, notification.id());
		assertThat(status).as("status of notification %d", notification.id()).isEqualTo(expectedStatus);
		assertThat(readAt == null ? null : readAt.toInstant())
				.as("read_at of notification %d", notification.id())
				.isEqualTo(expectedReadAt);
	}
}

/** 전체 지우기 기준 시각과 보존 하한을 고정하려고 애플리케이션 Clock을 대체한다. */
@TestConfiguration
class NotificationDismissRetention332TestClockConfiguration {

	@Bean
	@Primary
	Clock notificationDismissRetention332Clock() {
		return Clock.fixed(NotificationInboxDismissRetentionIntegrationTest.NOW, ZoneOffset.UTC);
	}
}
