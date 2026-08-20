/**
 * Created at: 2026-08-20T16:20:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-INT-013 through
 * INT-017
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.repository.NotificationInboxQueryRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.NotificationSeenStateRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@SpringBootTest
@ActiveProfiles("test")
class NotificationInboxCommandIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private NotificationInboxQueryRepository queryRepository;
	@Autowired
	private NotificationSeenStateRepository seenStateRepository;

	private Notification176IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		fixtures = new Notification176IntegrationFixtures(jdbc, notifications, outboxEvents, NOW);
		fixtures.reset();
		senderId = fixtures.account("gh176-cmd-sender");
		recipientId = fixtures.account("gh176-cmd-recipient");
	}

	@Test
	@DisplayName("INT-013 seen_at 이후 생성된 UNREAD만 hasUnseen에 반영되고 unreadCount는 전체 UNREAD를 센다")
	void hasUnseenAndUnreadCountUseDifferentBaselines() {
		long postId = fixtures.activePost(senderId, "int013", NOW.plusSeconds(3600));
		Instant seenAt = NOW.plusSeconds(10);
		fixtures.directionPostNotification(recipientId, postId, NOW); // seenAt 이전
		fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(20)); // seenAt 이후
		fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(30)); // seenAt 이후
		seenStateRepository.advance(recipientId, seenAt);

		boolean hasUnseen = queryRepository.existsUnseen(recipientId, seenAt);
		long unreadCount = queryRepository.countUnread(recipientId);

		assertThat(hasUnseen).isTrue();
		assertThat(unreadCount).isEqualTo(3);
	}

	@Test
	@DisplayName("INT-014 seen_at이 없으면 UNREAD 존재 자체로 hasUnseen을 판정한다")
	void hasUnseenWithoutSeenAtBaseline() {
		long postId = fixtures.activePost(senderId, "int014", NOW.plusSeconds(3600));
		fixtures.directionPostNotification(recipientId, postId, NOW);

		assertThat(seenStateRepository.findSeenAt(recipientId)).isEmpty();
		assertThat(queryRepository.existsUnseen(recipientId, null)).isTrue();
	}

	@Test
	@DisplayName("INT-015 DISMISSED·REVOKED는 unreadCount에 실리지 않는다")
	void dismissedAndRevokedDoNotCountAsUnread() {
		long postId = fixtures.activePost(senderId, "int015", NOW.plusSeconds(3600));
		fixtures.directionPostNotification(recipientId, postId, NOW);
		fixtures.withStatus(
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(1)),
			NotificationStatus.DISMISSED, NOW.plusSeconds(2));
		fixtures.withStatus(
			fixtures.directionPostNotification(recipientId, postId, NOW.plusSeconds(3)),
			NotificationStatus.REVOKED, null);

		assertThat(queryRepository.countUnread(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-016 같은 at으로 반복 advance해도 seen_at이 첫 값과 같다")
	void repeatedAdvanceWithSameInstantIsIdempotent() {
		Instant at = NOW.plusSeconds(100);

		Instant first = seenStateRepository.advance(recipientId, at);
		Instant second = seenStateRepository.advance(recipientId, at);
		Instant third = seenStateRepository.advance(recipientId, at);

		assertThat(first).isEqualTo(at);
		assertThat(second).isEqualTo(at);
		assertThat(third).isEqualTo(at);
	}

	@Test
	@DisplayName("INT-017 과거 시각으로 advance해도 seen_at이 되돌아가지 않는다")
	void advanceNeverMovesSeenAtBackward() {
		Instant later = NOW.plusSeconds(3600);
		seenStateRepository.advance(recipientId, later);

		Instant result = seenStateRepository.advance(recipientId, later.minusSeconds(3600));

		assertThat(result).isEqualTo(later);
		assertThat(seenStateRepository.findSeenAt(recipientId)).contains(later);
	}
}
