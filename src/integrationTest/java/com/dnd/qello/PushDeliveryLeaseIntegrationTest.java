/**
 * Created at: 2026-08-24T20:55:16+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-INT-006 through INT-007,
 * TEST-PLAN-GH-179-PUSH-DELIVERY-INT-019
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.push.ClaimedPushDelivery;
import com.dnd.qello.notification.push.PushDeliveryTerminalResult;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.repository.jdbc.sql.NotificationSql;

@SpringBootTest
@ActiveProfiles("test")
class PushDeliveryLeaseIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH179-LEASE";
	private static final Instant NOW = Instant.parse("2026-08-24T11:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private TransactionTemplate transactions;

	private long recipientId;
	private long pushDeviceId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("""
			DELETE FROM notification_delivery
			WHERE notification_id IN (
				SELECT id FROM notification WHERE recipient_id IN (
					SELECT id FROM user_account WHERE coarse_region_code = ?))
			""", REGION);
		jdbc.update("DELETE FROM notification WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM outbox_event WHERE dedup_key LIKE 'gh179-lease-%'");
		jdbc.update("DELETE FROM push_device WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH179 Lease Test', 'REGION')
			""", REGION);

		recipientId = jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', 'gh179-lease-recipient')
			RETURNING id
			""", Long.class, REGION);
		pushDeviceId = notifications.saveDevice(new PushDevice(null, recipientId, PushPlatform.ANDROID,
			new byte[] {1, 2, 3}, "fp-gh179-lease-device", PushDeviceStatus.ACTIVE, NOW, null)).id();
	}

	@Test
	@DisplayName("INT-006 due batch는 PENDING·due FAILED를 next_attempt_at과 id 순서로 claim하고 batch size 밖의 행을 남긴다")
	void claimsDueRowsInOrderAndHonorsBatchSize() {
		long failedEarlier = delivery("failed-earlier", DeliveryStatus.FAILED, 2, NOW.minusSeconds(20));
		long pendingEarlier = delivery("pending-earlier", DeliveryStatus.PENDING, 0, NOW.minusSeconds(10));
		long failedLater = delivery("failed-later", DeliveryStatus.FAILED, 3, NOW.minusSeconds(5));
		long pendingLater = delivery("pending-later", DeliveryStatus.PENDING, 0, NOW);
		long future = delivery("future", DeliveryStatus.PENDING, 0, NOW.plusSeconds(30));
		long processingBeforeLease = delivery("processing-before-lease", DeliveryStatus.PROCESSING, 1, NOW.plusSeconds(30));
		long sent = delivery("sent", DeliveryStatus.SENT, 1, NOW.minusSeconds(1));
		long dead = delivery("dead", DeliveryStatus.DEAD, 3, NOW.minusSeconds(1));

		List<ClaimedPushDelivery> claimed = claim(2, NOW, NOW.plusSeconds(60));

		assertThat(claimed).extracting(ClaimedPushDelivery::deliveryId)
			.containsExactly(failedEarlier, pendingEarlier);
		assertThat(claimed).allSatisfy(item -> assertThat(item.generation()).isIn(1, 3));
		assertThat(storedStatus(failedEarlier)).isEqualTo(DeliveryStatus.PROCESSING.name());
		assertThat(storedAttemptCount(failedEarlier)).isEqualTo(3);
		assertThat(storedNextAttemptAt(failedEarlier)).isEqualTo(NOW.plusSeconds(60));
		assertThat(storedStatus(pendingEarlier)).isEqualTo(DeliveryStatus.PROCESSING.name());
		assertThat(storedAttemptCount(pendingEarlier)).isEqualTo(1);
		assertThat(storedStatus(failedLater)).isEqualTo(DeliveryStatus.FAILED.name());
		assertThat(storedStatus(pendingLater)).isEqualTo(DeliveryStatus.PENDING.name());
		assertThat(storedStatus(future)).isEqualTo(DeliveryStatus.PENDING.name());
		assertThat(storedStatus(processingBeforeLease)).isEqualTo(DeliveryStatus.PROCESSING.name());
		assertThat(storedStatus(sent)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(storedStatus(dead)).isEqualTo(DeliveryStatus.DEAD.name());
	}

	@Test
	@DisplayName("INT-006 두 worker의 동일 due batch claim은 중복 없이 전체 due 행을 한 번씩만 점유한다")
	void claimsDueRowsExclusivelyAcrossTwoWorkers() throws Exception {
		long first = delivery("concurrent-1", DeliveryStatus.PENDING, 0, NOW);
		long second = delivery("concurrent-2", DeliveryStatus.FAILED, 1, NOW);
		long third = delivery("concurrent-3", DeliveryStatus.PENDING, 0, NOW);
		long fourth = delivery("concurrent-4", DeliveryStatus.FAILED, 2, NOW);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<List<ClaimedPushDelivery>> firstWorker = executor.submit(
				() -> claimConcurrently(ready, start, "worker-a"));
			Future<List<ClaimedPushDelivery>> secondWorker = executor.submit(
				() -> claimConcurrently(ready, start, "worker-b"));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both workers ready").isTrue();
			start.countDown();

			List<Long> firstIds = firstWorker.get(15, TimeUnit.SECONDS).stream()
				.map(ClaimedPushDelivery::deliveryId).toList();
			List<Long> secondIds = secondWorker.get(15, TimeUnit.SECONDS).stream()
				.map(ClaimedPushDelivery::deliveryId).toList();
			Set<Long> allClaimed = new HashSet<>(firstIds);
			allClaimed.addAll(secondIds);

			assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
			assertThat(firstIds.size() + secondIds.size()).isEqualTo(4);
			assertThat(allClaimed).containsExactlyInAnyOrder(first, second, third, fourth);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("workers terminated").isTrue();
		}
	}

	@Test
	@DisplayName("INT-007 lease 만료 전에는 회수하지 않고 만료 후 generation을 올려 회수하며 stale terminal update를 차단한다")
	void reclaimsOnlyAfterLeaseExpiryAndFencesStaleGeneration() {
		long deliveryId = delivery("lease-fencing", DeliveryStatus.PENDING, 0, NOW);
		Instant firstLeaseUntil = NOW.plusSeconds(30);
		Instant secondLeaseUntil = NOW.plusSeconds(90);

		List<ClaimedPushDelivery> firstClaims = claim(10, NOW, firstLeaseUntil);
		assertThat(firstClaims).extracting(ClaimedPushDelivery::deliveryId).containsExactly(deliveryId);
		ClaimedPushDelivery firstClaim = firstClaims.get(0);

		assertThat(claim(10, NOW.plusSeconds(29), secondLeaseUntil)).isEmpty();

		List<ClaimedPushDelivery> reclaimed = claim(10, NOW.plusSeconds(31), secondLeaseUntil);
		assertThat(reclaimed).extracting(ClaimedPushDelivery::deliveryId).containsExactly(deliveryId);
		ClaimedPushDelivery secondClaim = reclaimed.get(0);
		assertThat(secondClaim.generation()).isEqualTo(firstClaim.generation() + 1);
		assertThat(storedAttemptCount(deliveryId)).isEqualTo(2);
		assertThat(storedNextAttemptAt(deliveryId)).isEqualTo(secondLeaseUntil);

		assertThat(notifications.completeClaim(deliveryId, firstClaim.generation(),
			PushDeliveryTerminalResult.SENT, NOW.plusSeconds(32))).isFalse();
		assertThat(storedStatus(deliveryId)).isEqualTo(DeliveryStatus.PROCESSING.name());
		assertThat(notifications.completeClaim(deliveryId, secondClaim.generation(),
			PushDeliveryTerminalResult.SENT, NOW.plusSeconds(32))).isTrue();
		assertThat(storedStatus(deliveryId)).isEqualTo(DeliveryStatus.SENT.name());
	}

	@Test
	@DisplayName("INT-019 운영 claim SQL 상수의 EXPLAIN은 predicate·locking·update 계획과 row estimate를 남긴다")
	void explainsDueAndStaleClaimPlanWithBoundedFixture() {
		for (int index = 0; index < 120; index++) {
			delivery("planner-pending-" + index, DeliveryStatus.PENDING, 0, NOW.minusSeconds(index));
		}
		for (int index = 0; index < 40; index++) {
			delivery("planner-failed-" + index, DeliveryStatus.FAILED, 1, NOW.minusSeconds(index));
		}
		for (int index = 0; index < 20; index++) {
			delivery("planner-processing-" + index, DeliveryStatus.PROCESSING, 2, NOW.minusSeconds(120));
		}
		for (int index = 0; index < 20; index++) {
			delivery("planner-future-" + index, DeliveryStatus.PENDING, 0, NOW.plusSeconds(600));
		}

		List<String> planLines = namedJdbc.queryForList(
			"EXPLAIN (COSTS TRUE)\n" + NotificationSql.CLAIM_DUE_PUSH_DELIVERIES,
			new MapSqlParameterSource()
				.addValue("now", Timestamp.from(NOW))
				.addValue("batchSize", 50)
				.addValue("leaseUntil", Timestamp.from(NOW.plusSeconds(60))),
			String.class);

		String plan = String.join("\n", planLines);
		assertThat(planLines).as("INT-019 EXPLAIN must return a planner result").isNotEmpty();
		assertThat(plan).contains("notification_delivery");
		assertThat(plan).contains("Update on notification_delivery");
		assertThat(plan).contains("LockRows");
		assertThat(plan).contains("Limit");
		assertThat(plan).containsAnyOf("Sort", "Incremental Sort");
		assertThat(plan).containsAnyOf("Index Scan", "Bitmap Heap Scan", "Seq Scan");
		assertThat(plan).containsPattern("rows=\\d+");
	}

	private List<ClaimedPushDelivery> claim(int batchSize, Instant at, Instant leaseUntil) {
		return transactions.execute(status -> notifications.claimDueDeliveries(batchSize, at, leaseUntil));
	}

	private List<ClaimedPushDelivery> claimConcurrently(
		CountDownLatch ready, CountDownLatch start, String workerName) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).as(workerName + " started").isTrue();
		return claim(2, NOW, NOW.plusSeconds(60));
	}

	private long delivery(String suffix, DeliveryStatus status, int attemptCount, Instant nextAttemptAt) {
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(
			OutboxAggregateType.ANSWER, 179L, OutboxEventType.ANSWER_PUBLISHED,
			"gh179-lease-" + suffix, "{}", NOW));
		Notification notification = notifications.save(new Notification(
			null, recipientId, event.id(), NotificationType.ANSWER_RECEIVED,
			"gh179-lease-notification-" + suffix, null, null, null,
			NotificationStatus.UNREAD, NOW, null));
		Instant sentAt = status == DeliveryStatus.SENT ? NOW : null;
		return notifications.saveDelivery(new NotificationDelivery(
			null, notification.id(), pushDeviceId, status, attemptCount, nextAttemptAt,
			NOW, sentAt, null)).id();
	}

	private String storedStatus(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
	}

	private int storedAttemptCount(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT attempt_count FROM notification_delivery WHERE id = ?", Integer.class, deliveryId);
	}

	private Instant storedNextAttemptAt(long deliveryId) {
		Timestamp timestamp = jdbc.queryForObject(
			"SELECT next_attempt_at FROM notification_delivery WHERE id = ?", Timestamp.class, deliveryId);
		return timestamp.toInstant();
	}
}
