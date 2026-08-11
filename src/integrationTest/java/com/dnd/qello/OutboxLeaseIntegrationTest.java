/**
 * Created at: 2026-08-11T20:15:23+09:00
 * Source scenario: TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-005, INT-006, INT-009
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@SpringBootTest
@ActiveProfiles("test")
class OutboxLeaseIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-11T11:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private OutboxEventRepository outboxRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
	}

	@Test
	@DisplayName("batch claim은 due 행만 한 worker에게 점유하고 future·유효 lease·terminal 행은 제외한다")
	void claimsOnlyDueRows() {
		OutboxEvent due = outboxRepository.save(event("lease-due", NOW));
		OutboxEvent future = outboxRepository.save(event("lease-future", NOW.plusSeconds(60)));
		OutboxEvent processing = outboxRepository.save(event("lease-processing", NOW));
		OutboxEvent claimedProcessing = outboxRepository.claim(processing.id(), "worker-existing", NOW,
			NOW.plusSeconds(60)).orElseThrow();
		OutboxEvent processed = outboxRepository.save(event("lease-processed", NOW));
		OutboxEvent claimedProcessed = outboxRepository.claim(processed.id(), "worker-terminal", NOW,
			NOW.plusSeconds(60)).orElseThrow();
		assertThat(outboxRepository.complete(claimedProcessed.id(), "worker-terminal",
			claimedProcessed.leaseGeneration(), NOW.plusSeconds(1))).isTrue();
		OutboxEvent dead = outboxRepository.save(event("lease-dead", NOW));
		OutboxEvent claimedDead = outboxRepository.claim(dead.id(), "worker-terminal", NOW,
			NOW.plusSeconds(60)).orElseThrow();
		assertThat(outboxRepository.fail(claimedDead.id(), "worker-terminal", claimedDead.leaseGeneration(),
			NOW.plusSeconds(1), NOW.plusSeconds(100), true)).isTrue();

		List<OutboxEvent> claimed = outboxRepository.claimDue(10, "worker-batch", NOW.plusSeconds(1),
			NOW.plusSeconds(61));

		assertThat(claimed).extracting(OutboxEvent::id).containsExactly(due.id());
		OutboxEvent restored = outboxRepository.findEventById(due.id()).orElseThrow();
		assertThat(restored.status()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(restored.leaseOwner()).isEqualTo("worker-batch");
		assertThat(restored.leaseExpiresAt()).isEqualTo(NOW.plusSeconds(61));
		assertThat(restored.leaseGeneration()).isEqualTo(1);
		assertThat(restored.attemptCount()).isEqualTo(1);
		assertThat(outboxRepository.findEventById(future.id()).orElseThrow().status())
			.isEqualTo(OutboxStatus.PENDING);
		assertThat(claimedProcessing.leaseOwner()).isEqualTo("worker-existing");
	}

	@Test
	@DisplayName("두 worker가 같은 due 행을 동시에 claim해도 한 worker만 lease를 획득한다")
	void claimsDueEventOnlyOnceUnderConcurrency() throws Exception {
		OutboxEvent event = outboxRepository.save(event("lease-concurrent", NOW));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Boolean> first = executor.submit(() -> claimBatch(event.id(), "worker-concurrent-a", ready, start));
			Future<Boolean> second = executor.submit(() -> claimBatch(event.id(), "worker-concurrent-b", ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
			assertThat(outboxRepository.findEventById(event.id()).orElseThrow().status())
				.isEqualTo(OutboxStatus.PROCESSING);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("만료된 lease는 generation을 증가시켜 회수하고 stale worker의 완료는 차단한다")
	void reclaimsExpiredLeaseAndFencesStaleWorker() {
		OutboxEvent event = outboxRepository.save(event("lease-reclaim", NOW));
		OutboxEvent firstClaim = outboxRepository.claim(event.id(), "worker-a", NOW.plusSeconds(1),
			NOW.plusSeconds(10)).orElseThrow();

		List<OutboxEvent> reclaimed = outboxRepository.claimDue(10, "worker-b", NOW.plusSeconds(11),
			NOW.plusSeconds(40));

		assertThat(reclaimed).extracting(OutboxEvent::id).containsExactly(event.id());
		OutboxEvent secondClaim = outboxRepository.findEventById(event.id()).orElseThrow();
		assertThat(secondClaim.leaseOwner()).isEqualTo("worker-b");
		assertThat(secondClaim.leaseGeneration()).isEqualTo(firstClaim.leaseGeneration() + 1);
		assertThat(secondClaim.attemptCount()).isEqualTo(firstClaim.attemptCount() + 1);

		assertThat(outboxRepository.complete(event.id(), "worker-a", firstClaim.leaseGeneration(),
			NOW.plusSeconds(12))).isFalse();
		assertThat(outboxRepository.fail(event.id(), "worker-a", firstClaim.leaseGeneration(),
			NOW.plusSeconds(12), NOW.plusSeconds(100), false)).isFalse();
		assertThat(outboxRepository.complete(event.id(), "worker-b", secondClaim.leaseGeneration(),
			NOW.plusSeconds(12))).isTrue();

		OutboxEvent completed = outboxRepository.findEventById(event.id()).orElseThrow();
		assertThat(completed.status()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(completed.leaseOwner()).isNull();
		assertThat(completed.leaseExpiresAt()).isNull();
	}

	@Test
	@DisplayName("FAILED 행은 next_attempt_at 이후 재claim되고 lease failure 전환은 owner와 generation을 검증한다")
	void retriesFailedEventAfterBackoff() {
		OutboxEvent event = outboxRepository.save(event("lease-failed", NOW));
		OutboxEvent firstClaim = outboxRepository.claim(event.id(), "worker-a", NOW.plusSeconds(1),
			NOW.plusSeconds(30)).orElseThrow();
		assertThat(outboxRepository.fail(event.id(), "worker-a", firstClaim.leaseGeneration(),
			NOW.plusSeconds(2), NOW.plusSeconds(20), false)).isTrue();

		assertThat(outboxRepository.claimDue(10, "worker-b", NOW.plusSeconds(10), NOW.plusSeconds(40)))
			.isEmpty();
		List<OutboxEvent> retry = outboxRepository.claimDue(10, "worker-b", NOW.plusSeconds(21),
			NOW.plusSeconds(50));

		assertThat(retry).hasSize(1);
		assertThat(retry.get(0).attemptCount()).isEqualTo(2);
		assertThat(retry.get(0).leaseGeneration()).isEqualTo(2);
	}

	@Test
	@DisplayName("기존 Outbox save·find·legacy claim API는 V11 lease 컬럼과 함께 동작한다")
	void preservesExistingOutboxApi() {
		OutboxEvent event = outboxRepository.save(event("legacy-outbox-api", NOW));

		assertThat(outboxRepository.findByDedupKey("legacy-outbox-api")).contains(event);
		OutboxEvent claimed = outboxRepository.claim(event.id(), NOW.plusSeconds(1)).orElseThrow();

		assertThat(claimed.status()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(claimed.leaseOwner()).isEqualTo("legacy-compat");
		assertThat(claimed.leaseGeneration()).isEqualTo(1);
	}

	private OutboxEvent event(String dedupKey, Instant nextAttemptAt) {
		return OutboxEvent.pending(OutboxAggregateType.ANSWER, 115L,
			OutboxEventType.ANSWER_PUBLISHED, dedupKey, "{\"answerId\":115}", nextAttemptAt);
	}

	private boolean claimBatch(long eventId, String owner, CountDownLatch ready, CountDownLatch start)
		throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return transactionTemplate.execute(status -> !outboxRepository.claimDue(1, owner, NOW.plusSeconds(1),
			NOW.plusSeconds(61)).isEmpty());
	}
}
