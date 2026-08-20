/**
 * Created at: 2026-08-20T16:25:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-INT-018
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.NotificationSeenStateRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@SpringBootTest
@ActiveProfiles("test")
class NotificationInboxConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private NotificationSeenStateRepository seenStateRepository;

	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		Notification176IntegrationFixtures fixtures =
			new Notification176IntegrationFixtures(jdbc, notifications, outboxEvents, NOW);
		fixtures.reset();
		recipientId = fixtures.account("gh176-concurrency-recipient");
	}

	@Test
	@DisplayName("INT-018 서로 다른 시각으로 동시에 advance해도 최종 seen_at은 더 늦은 시각이고 행은 하나다")
	void concurrentAdvanceConvergesToTheLaterInstantWithoutDuplicateRows() throws Exception {
		Instant earlier = NOW.plusSeconds(100);
		Instant later = NOW.plusSeconds(200);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Instant> earlierFuture = executor.submit(advanceTask(earlier, ready, start));
			Future<Instant> laterFuture = executor.submit(advanceTask(later, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both threads ready").isTrue();
			start.countDown();
			earlierFuture.get(15, TimeUnit.SECONDS);
			laterFuture.get(15, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("executor terminated").isTrue();
		}

		assertThat(seenStateRepository.findSeenAt(recipientId)).contains(later);
		Integer rowCount = jdbc.queryForObject(
			"SELECT count(*) FROM notification_seen_state WHERE user_id = ?", Integer.class, recipientId);
		assertThat(rowCount).isEqualTo(1);
	}

	private Callable<Instant> advanceTask(Instant at, CountDownLatch ready, CountDownLatch start) {
		return () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return seenStateRepository.advance(recipientId, at);
		};
	}
}
