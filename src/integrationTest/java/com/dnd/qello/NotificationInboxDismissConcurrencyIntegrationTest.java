/**
 * Created at: 2026-10-08T02:53:18+09:00
 * Source scenario: TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-008
 */
package com.dnd.qello;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.service.NotificationInboxService;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationDismissal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationDismissConcurrency332TestClockConfiguration.class)
class NotificationInboxDismissConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");
	/** 커밋 순서가 반복마다 달라지도록 여러 번 경쟁시킨다. 한 번의 성공만으로는 어느 한 순서만 본 것일 수 있다. */
	private static final int ITERATIONS = 20;

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private NotificationInboxService inboxService;

	@Test
	@DisplayName("INT-008 전체 지우기와 읽음 처리를 동시에 실행해도 매 반복 최종 상태는 DISMISSED이고 두 호출 모두 예외가 없다")
	void concurrentDismissAllAndMarkReadAlwaysEndDismissed() throws Exception {
		for (int iteration = 0; iteration < ITERATIONS; iteration++) {
			Notification176IntegrationFixtures fixtures = new Notification176IntegrationFixtures(jdbc, notifications,
					outboxEvents, NOW);
			fixtures.reset();
			long senderId = fixtures.account("gh332-race-sender");
			long recipientId = fixtures.account("gh332-race-recipient");
			long postId = fixtures.activePost(senderId, "gh332-race-" + iteration, NOW.plusSeconds(3600));
			Notification unread = fixtures.directionPostNotification(recipientId, postId, NOW.minusSeconds(60));

			race(recipientId, unread.id());

			String status = jdbc.queryForObject(
					"SELECT status FROM notification WHERE id = ?", String.class, unread.id());
			assertThat(status).as("final status at iteration %d", iteration).isEqualTo("DISMISSED");
		}
	}

	/** 두 호출의 예외는 Future.get이 ExecutionException으로 다시 던지므로 그대로 테스트 실패가 된다. */
	private void race(long recipientId, long notificationId) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<NotificationDismissal> dismissFuture = executor.submit(
					task(ready, start, () -> inboxService.dismissAll(recipientId)));
			Future<NotificationCard> readFuture = executor.submit(
					task(ready, start, () -> inboxService.markRead(recipientId, notificationId)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both threads ready").isTrue();
			start.countDown();
			// 읽음이 먼저 커밋되면 READ 행을, 전체 지우기가 먼저면 UNREAD 행을 전이하므로 어느 순서든 1건이다.
			assertThat(dismissFuture.get(15, TimeUnit.SECONDS).dismissedCount()).isEqualTo(1);
			assertThat(readFuture.get(15, TimeUnit.SECONDS).notificationId()).isEqualTo(notificationId);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("executor terminated").isTrue();
		}
	}

	private static <T> Callable<T> task(CountDownLatch ready, CountDownLatch start, Callable<T> body) {
		return () -> {
			ready.countDown();
			// 시작 신호를 못 받은 채 실행하면 두 호출이 경쟁하지 않으므로 그 반복은 실패로 본다.
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("start latch timed out");
			}
			return body.call();
		};
	}
}

/** 전체 지우기 기준 시각이 fixture 생성 시각보다 항상 뒤에 오도록 애플리케이션 Clock을 고정한다. */
@TestConfiguration
class NotificationDismissConcurrency332TestClockConfiguration {

	@Bean
	@Primary
	Clock notificationDismissConcurrency332Clock() {
		return Clock.fixed(NotificationInboxDismissConcurrencyIntegrationTest.NOW, ZoneOffset.UTC);
	}
}
