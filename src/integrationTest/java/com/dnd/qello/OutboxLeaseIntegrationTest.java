/**
 * Created at: 2026-08-13T01:45:09+09:00
 * Source scenario: TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-001 through INT-009;
 * regression coverage from TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-005, INT-006, INT-009
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
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
import com.dnd.qello.notification.domain.OutboxFailureKind;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
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

		List<OutboxEvent> claimed = outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 10, "worker-batch", NOW.plusSeconds(1),
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
		OutboxEvent stillLeased = outboxRepository.findEventById(claimedProcessing.id()).orElseThrow();
		assertThat(stillLeased.leaseOwner()).isEqualTo("worker-existing");
		assertThat(stillLeased.leaseGeneration()).isEqualTo(claimedProcessing.leaseGeneration());
		assertThat(stillLeased.leaseExpiresAt()).isEqualTo(claimedProcessing.leaseExpiresAt());
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

		List<OutboxEvent> reclaimed = outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 10, "worker-b", NOW.plusSeconds(11),
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

		assertThat(outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 10, "worker-b", NOW.plusSeconds(10), NOW.plusSeconds(40)))
			.isEmpty();
		List<OutboxEvent> retry = outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 10, "worker-b", NOW.plusSeconds(21),
			NOW.plusSeconds(50));

		assertThat(retry).hasSize(1);
		assertThat(retry.get(0).attemptCount()).isEqualTo(2);
		assertThat(retry.get(0).leaseGeneration()).isEqualTo(2);
	}

	@Test
	@DisplayName("기존 Outbox save·find·legacy claim API는 V12 lease 컬럼과 함께 동작한다")
	void preservesExistingOutboxApi() {
		OutboxEvent event = outboxRepository.save(event("legacy-outbox-api", NOW));

		assertThat(outboxRepository.findByDedupKey("legacy-outbox-api")).contains(event);
		OutboxEvent claimed = outboxRepository.claim(event.id(), NOW.plusSeconds(1)).orElseThrow();

		assertThat(claimed.status()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(claimed.leaseOwner()).isEqualTo("legacy-compat");
		assertThat(claimed.leaseGeneration()).isEqualTo(1);
	}

	@Test
	@DisplayName("batch claim은 요청한 event type만 점유하고 다른 due event는 변경하지 않는다")
	void claimsOnlyRequestedEventTypes() {
		OutboxEvent answer = outboxRepository.save(event("type-answer", OutboxAggregateType.ANSWER,
			OutboxEventType.ANSWER_PUBLISHED, 201L, NOW));
		OutboxEvent matching = outboxRepository.save(OutboxEvent.matchingPending(202L, 1,
			"type-matching", "{\"postId\":202}", NOW));
		OutboxEvent confirmed = outboxRepository.save(event("type-confirmed", OutboxAggregateType.POST_RECIPIENT,
			OutboxEventType.RECIPIENTS_CONFIRMED, 203L, NOW));

		List<OutboxEvent> claimed = outboxRepository.claimDue(Set.of(OutboxEventType.RECIPIENT_MATCH_REQUESTED),
			10, "matching-worker", NOW, NOW.plusSeconds(30));

		assertThat(claimed).extracting(OutboxEvent::id).containsExactly(matching.id());
		assertThat(outboxRepository.findEventById(answer.id()).orElseThrow().status())
			.isEqualTo(OutboxStatus.PENDING);
		assertThat(outboxRepository.findEventById(confirmed.id()).orElseThrow().status())
			.isEqualTo(OutboxStatus.PENDING);
	}

	@Test
	@DisplayName("여러 worker의 동일 event type batch claim 결과는 중복되지 않는다")
	void claimsBatchWithoutOverlapUnderConcurrency() throws Exception {
		for (int index = 0; index < 4; index++) {
			outboxRepository.save(event("batch-concurrent-" + index, OutboxAggregateType.ANSWER,
				OutboxEventType.ANSWER_PUBLISHED, 220L + index, NOW));
		}
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<List<OutboxEvent>> first = executor.submit(() -> claimBatchEvents("batch-worker-a", ready, start));
			Future<List<OutboxEvent>> second = executor.submit(() -> claimBatchEvents("batch-worker-b", ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Long> firstIds = first.get(10, TimeUnit.SECONDS).stream().map(OutboxEvent::id).toList();
			List<Long> secondIds = second.get(10, TimeUnit.SECONDS).stream().map(OutboxEvent::id).toList();
			assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
			assertThat(firstIds.size() + secondIds.size()).isEqualTo(4);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("retryable 실패는 backoff 후 재claim되고 최대 횟수와 permanent 실패는 DEAD로 끝난다")
	void appliesRetryPolicyToFailedAndDeadEvents() {
		OutboxRetryPolicy policy = new OutboxRetryPolicy(2, attempt -> Duration.ofSeconds(10));
		OutboxEvent retryEvent = outboxRepository.save(event("policy-retry", OutboxAggregateType.ANSWER,
			OutboxEventType.ANSWER_PUBLISHED, 240L, NOW));
		OutboxEvent firstClaim = outboxRepository.claim(retryEvent.id(), "policy-a", NOW,
			NOW.plusSeconds(30)).orElseThrow();
		OutboxRetryDecision retry = policy.decide(firstClaim, OutboxFailureKind.RETRYABLE, NOW.plusSeconds(1));
		assertThat(outboxRepository.fail(firstClaim.id(), "policy-a", firstClaim.leaseGeneration(),
			NOW.plusSeconds(1), retry)).isTrue();
		assertThat(outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 10, "policy-b",
			NOW.plusSeconds(10), NOW.plusSeconds(40))).isEmpty();
		assertThat(outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 10, "policy-b",
			NOW.plusSeconds(11), NOW.plusSeconds(50))).hasSize(1);

		OutboxEvent deadEvent = outboxRepository.save(event("policy-dead", OutboxAggregateType.ANSWER,
			OutboxEventType.ANSWER_PUBLISHED, 241L, NOW));
		OutboxEvent deadClaim = outboxRepository.claim(deadEvent.id(), "policy-dead", NOW,
			NOW.plusSeconds(30)).orElseThrow();
		OutboxRetryDecision dead = policy.decide(deadClaim, OutboxFailureKind.PERMANENT, NOW.plusSeconds(2));
		assertThat(outboxRepository.fail(deadClaim.id(), "policy-dead", deadClaim.leaseGeneration(),
			NOW.plusSeconds(2), dead)).isTrue();
		OutboxEvent storedDead = outboxRepository.findEventById(deadEvent.id()).orElseThrow();
		assertThat(storedDead.status()).isEqualTo(OutboxStatus.DEAD);
		assertThat(storedDead.nextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
		assertThat(outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 10, "policy-c",
			NOW.plusSeconds(3), NOW.plusSeconds(40))).isEmpty();
	}

	@Test
	@DisplayName("batch claim 입력 경계는 SQL 실행 전에 잘못된 요청을 거절한다")
	void rejectsInvalidBatchClaimInputs() {
		Set<OutboxEventType> nullElement = new HashSet<>();
		nullElement.add(null);

		assertThatThrownBy(() -> outboxRepository.claimDue(Set.of(), 10, "worker", NOW, NOW.plusSeconds(1)))
			.isInstanceOfSatisfying(NotificationException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.REQUIRED_VALUE_MISSING));
		assertThatThrownBy(() -> outboxRepository.claimDue(null, 10, "worker", NOW, NOW.plusSeconds(1)))
			.isInstanceOfSatisfying(NotificationException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.REQUIRED_VALUE_MISSING));
		assertThatThrownBy(() -> outboxRepository.claimDue(nullElement, 10, "worker", NOW, NOW.plusSeconds(1)))
			.isInstanceOfSatisfying(NotificationException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.REQUIRED_VALUE_MISSING));
		assertThatThrownBy(() -> outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 0,
				"worker", NOW, NOW.plusSeconds(1)))
			.isInstanceOfSatisfying(NotificationException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.INVALID_VALUE_RANGE));
		assertThatThrownBy(() -> outboxRepository.fail(1L, "worker", 1L, NOW, null))
			.isInstanceOfSatisfying(NotificationException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.REQUIRED_VALUE_MISSING));
	}

	private OutboxEvent event(String dedupKey, Instant nextAttemptAt) {
		return event(dedupKey, OutboxAggregateType.ANSWER, OutboxEventType.ANSWER_PUBLISHED, 115L, nextAttemptAt);
	}

	private OutboxEvent event(String dedupKey, OutboxAggregateType aggregateType, OutboxEventType eventType,
		long aggregateId, Instant nextAttemptAt) {
		return OutboxEvent.pending(aggregateType, aggregateId, eventType, dedupKey,
			"{\"aggregateId\":" + aggregateId + "}", nextAttemptAt);
	}

	private List<OutboxEvent> claimBatchEvents(String owner, CountDownLatch ready, CountDownLatch start)
		throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		return transactionTemplate.execute(status -> outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED),
			2, owner, NOW, NOW.plusSeconds(30)));
	}

	private boolean claimBatch(long eventId, String owner, CountDownLatch ready, CountDownLatch start)
		throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		return transactionTemplate.execute(status -> !outboxRepository.claimDue(Set.of(OutboxEventType.ANSWER_PUBLISHED), 1, owner, NOW.plusSeconds(1),
			NOW.plusSeconds(61)).isEmpty());
	}
}
