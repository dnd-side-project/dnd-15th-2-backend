/**
 * Created at: 2026-08-24T20:55:16+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-INT-006 through INT-007,
 * TEST-PLAN-GH-179-PUSH-DELIVERY-INT-019,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-005,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-019
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
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

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;
import com.dnd.qello.notification.repository.jdbc.sql.PushDispatchGroupSql;
import com.dnd.qello.notification.service.PushDispatchGroupClaimService;

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
	private PushDispatchGroupClaimService claimService;

	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("""
			DELETE FROM push_dispatch_group
			WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
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
	}

	@Test
	@DisplayName("INT-006 due group batch는 PENDING·due FAILED를 next_attempt_at과 id 순서로 claim하고 batch size 밖의 행을 남긴다")
	void claimsDueRowsInOrderAndHonorsBatchSize() {
		long failedEarlier = group("failed-earlier", "FAILED", 2, NOW.minusSeconds(20));
		long pendingEarlier = group("pending-earlier", "PENDING", 0, NOW.minusSeconds(10));
		long failedLater = group("failed-later", "FAILED", 3, NOW.minusSeconds(5));
		long pendingLater = group("pending-later", "PENDING", 0, NOW);
		long future = group("future", "PENDING", 0, NOW.plusSeconds(30));
		long processingBeforeLease = group("processing-before-lease", "PROCESSING", 1, NOW.plusSeconds(30));
		long completed = terminalGroup("completed", "COMPLETED", 1, NOW.minusSeconds(1));
		long dead = terminalGroup("dead", "DEAD", 3, NOW.minusSeconds(1));

		List<ClaimedPushDispatchGroup> claimed = claim(2, NOW, NOW.plusSeconds(60));

		assertThat(claimed).extracting(ClaimedPushDispatchGroup::groupId)
			.containsExactly(failedEarlier, pendingEarlier);
		assertThat(claimed).allSatisfy(item -> assertThat(item.generation()).isIn(1, 3));
		assertThat(storedStatus(failedEarlier)).isEqualTo("PROCESSING");
		assertThat(storedAttemptCount(failedEarlier)).isEqualTo(3);
		assertThat(storedNextAttemptAt(failedEarlier)).isEqualTo(NOW.plusSeconds(60));
		assertThat(storedStatus(pendingEarlier)).isEqualTo("PROCESSING");
		assertThat(storedAttemptCount(pendingEarlier)).isEqualTo(1);
		assertThat(storedStatus(failedLater)).isEqualTo("FAILED");
		assertThat(storedStatus(pendingLater)).isEqualTo("PENDING");
		assertThat(storedStatus(future)).isEqualTo("PENDING");
		assertThat(storedStatus(processingBeforeLease)).isEqualTo("PROCESSING");
		assertThat(storedStatus(completed)).isEqualTo("COMPLETED");
		assertThat(storedStatus(dead)).isEqualTo("DEAD");
	}

	@Test
	@DisplayName("INT-006 두 worker의 동일 due group claim은 중복 없이 전체 due 행을 한 번씩만 점유한다")
	void claimsDueRowsExclusivelyAcrossTwoWorkers() throws Exception {
		long first = group("concurrent-1", "PENDING", 0, NOW);
		long second = group("concurrent-2", "FAILED", 1, NOW);
		long third = group("concurrent-3", "PENDING", 0, NOW);
		long fourth = group("concurrent-4", "FAILED", 2, NOW);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<List<ClaimedPushDispatchGroup>> firstWorker = executor.submit(
				() -> claimConcurrently(ready, start, "worker-a"));
			Future<List<ClaimedPushDispatchGroup>> secondWorker = executor.submit(
				() -> claimConcurrently(ready, start, "worker-b"));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both workers ready").isTrue();
			start.countDown();

			List<Long> firstIds = firstWorker.get(15, TimeUnit.SECONDS).stream()
				.map(ClaimedPushDispatchGroup::groupId).toList();
			List<Long> secondIds = secondWorker.get(15, TimeUnit.SECONDS).stream()
				.map(ClaimedPushDispatchGroup::groupId).toList();
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
		long groupId = group("lease-fencing", "PENDING", 0, NOW);
		Instant firstLeaseUntil = NOW.plusSeconds(30);
		Instant secondLeaseUntil = NOW.plusSeconds(90);

		List<ClaimedPushDispatchGroup> firstClaims = claim(10, NOW, firstLeaseUntil);
		assertThat(firstClaims).extracting(ClaimedPushDispatchGroup::groupId).containsExactly(groupId);
		ClaimedPushDispatchGroup firstClaim = firstClaims.get(0);

		assertThat(claim(10, NOW.plusSeconds(29), secondLeaseUntil)).isEmpty();

		List<ClaimedPushDispatchGroup> reclaimed = claim(10, NOW.plusSeconds(31), secondLeaseUntil);
		assertThat(reclaimed).extracting(ClaimedPushDispatchGroup::groupId).containsExactly(groupId);
		ClaimedPushDispatchGroup secondClaim = reclaimed.get(0);
		assertThat(secondClaim.generation()).isEqualTo(firstClaim.generation() + 1);
		assertThat(storedAttemptCount(groupId)).isEqualTo(2);
		assertThat(storedNextAttemptAt(groupId)).isEqualTo(secondLeaseUntil);

		assertThat(claimService.transition(groupId, firstClaim.generation(), PushDispatchGroupStatus.COMPLETED,
			NOW.plusSeconds(32), NOW.plusSeconds(32), NOW.plusSeconds(32))).isFalse();
		assertThat(storedStatus(groupId)).isEqualTo("PROCESSING");
		assertThat(claimService.transition(groupId, secondClaim.generation(), PushDispatchGroupStatus.COMPLETED,
			NOW.plusSeconds(32), NOW.plusSeconds(32), NOW.plusSeconds(32))).isTrue();
		assertThat(storedStatus(groupId)).isEqualTo("COMPLETED");
	}

	@Test
	@DisplayName("INT-019 운영 group claim SQL 상수의 EXPLAIN은 predicate·locking·update 계획과 row estimate를 남긴다")
	void explainsDueAndStaleClaimPlanWithBoundedFixture() {
		for (int index = 0; index < 120; index++) {
			group("planner-pending-" + index, "PENDING", 0, NOW.minusSeconds(index));
		}
		for (int index = 0; index < 40; index++) {
			group("planner-failed-" + index, "FAILED", 1, NOW.minusSeconds(index));
		}
		for (int index = 0; index < 20; index++) {
			group("planner-processing-" + index, "PROCESSING", 2, NOW.minusSeconds(120));
		}
		for (int index = 0; index < 20; index++) {
			group("planner-future-" + index, "PENDING", 0, NOW.plusSeconds(600));
		}
		jdbc.execute("ANALYZE push_dispatch_group");

		List<String> planLines = namedJdbc.queryForList(
			"EXPLAIN (COSTS TRUE)\n" + PushDispatchGroupSql.CLAIM_DUE_GROUPS,
			new MapSqlParameterSource()
				.addValue("now", Timestamp.from(NOW))
				.addValue("limit", 50)
				.addValue("leaseUntil", Timestamp.from(NOW.plusSeconds(60))),
			String.class);

		String plan = String.join("\n", planLines);
		assertThat(planLines).as("INT-019 EXPLAIN must return a planner result").isNotEmpty();
		assertThat(plan).contains("push_dispatch_group");
		assertThat(plan).contains("Update on push_dispatch_group");
		assertThat(plan).contains("LockRows");
		assertThat(plan).contains("Limit");
		assertThat(plan).containsAnyOf("Sort", "Incremental Sort");
		assertThat(plan).containsAnyOf("Index Scan", "Bitmap Heap Scan", "Seq Scan");
		assertThat(plan).containsPattern("rows=\\d+");
	}

	private List<ClaimedPushDispatchGroup> claim(int batchSize, Instant at, Instant leaseUntil) {
		return claimService.claimDueGroups(batchSize, at, leaseUntil);
	}

	private List<ClaimedPushDispatchGroup> claimConcurrently(
		CountDownLatch ready, CountDownLatch start, String workerName) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).as(workerName + " started").isTrue();
		return claim(2, NOW, NOW.plusSeconds(60));
	}

	private long group(String suffix, String status, int attemptCount, Instant nextAttemptAt) {
		return insertGroup(suffix, status, attemptCount, nextAttemptAt, null);
	}

	private long terminalGroup(String suffix, String status, int attemptCount, Instant nextAttemptAt) {
		return insertGroup(suffix, status, attemptCount, nextAttemptAt, NOW);
	}

	private long insertGroup(
		String suffix, String status, int attemptCount, Instant nextAttemptAt, Instant completedAt) {
		return jdbc.queryForObject("""
			INSERT INTO push_dispatch_group (
				recipient_id, notification_type, aggregation_key, status, window_started_at,
				collect_until, policy_expires_at, attempt_count, next_attempt_at, completed_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			RETURNING id
			""", Long.class, recipientId, NotificationType.ANSWER_RECEIVED.name(),
			"gh179-lease-" + suffix, status, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(Duration.ofHours(8))), attemptCount, Timestamp.from(nextAttemptAt),
			completedAt == null ? null : Timestamp.from(completedAt));
	}

	private String storedStatus(long groupId) {
		return jdbc.queryForObject(
			"SELECT status FROM push_dispatch_group WHERE id = ?", String.class, groupId);
	}

	private int storedAttemptCount(long groupId) {
		return jdbc.queryForObject(
			"SELECT attempt_count FROM push_dispatch_group WHERE id = ?", Integer.class, groupId);
	}

	private Instant storedNextAttemptAt(long groupId) {
		Timestamp timestamp = jdbc.queryForObject(
			"SELECT next_attempt_at FROM push_dispatch_group WHERE id = ?", Timestamp.class, groupId);
		return timestamp.toInstant();
	}
}
