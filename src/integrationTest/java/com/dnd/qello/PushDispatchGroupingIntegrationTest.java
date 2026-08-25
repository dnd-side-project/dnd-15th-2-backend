/**
 * Created at: 2026-08-25T13:53:12+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-002, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-003, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-004, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-005, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-018, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-019
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.repository.PushDispatchGroupRepository;
import com.dnd.qello.notification.repository.jdbc.sql.PushDispatchGroupSql;
import com.dnd.qello.notification.service.PushDispatchGroupClaimService;
import com.dnd.qello.notification.service.PushDispatchGroupPlanner;

@SpringBootTest(properties = {
	"qello.notification.push.policy.bundle-window=PT10M",
	"qello.notification.push.policy.max-delay=PT8H",
	"qello.notification.push.policy.daily-limit=5",
	"qello.notification.push.policy.direction-reserved=2",
	"qello.notification.push.policy.recommendation-min-interval=PT24H"
})
@ActiveProfiles("test")
class PushDispatchGroupingIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH180-GROUPING";
	private static final Instant T = Instant.parse("2026-08-25T04:00:00Z");
	private static final Instant T_PLUS_5M = T.plus(Duration.ofMinutes(5));
	private static final Instant T_PLUS_10M = T.plus(Duration.ofMinutes(10));
	private static final PushPolicyProperties POLICY = new PushPolicyProperties(
		Duration.parse("PT10M"), Duration.parse("PT8H"), 5, 2, Duration.parse("PT24H"));

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NamedParameterJdbcTemplate namedJdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private PushDispatchGroupRepository groups;
	@Autowired
	private PushDispatchGroupClaimService claimService;
	@Autowired
	private TransactionTemplate transactions;

	private PushDispatchGroupPlanner planner;
	private long recipientA;
	private long recipientB;
	private long deviceA;
	private long deviceB;

	@BeforeEach
	void resetFixtures() {
		planner = new PushDispatchGroupPlanner(groups, new PushGroupingPolicy(POLICY));
		jdbc.update("""
			DELETE FROM push_dispatch_group_member
			WHERE group_id IN (
				SELECT id FROM push_dispatch_group WHERE recipient_id IN (
					SELECT id FROM user_account WHERE coarse_region_code = ?))
			""", REGION);
		jdbc.update("""
			DELETE FROM push_dispatch_group
			WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM notification_delivery
			WHERE notification_id IN (
				SELECT id FROM notification WHERE recipient_id IN (
					SELECT id FROM user_account WHERE coarse_region_code = ?))
			""", REGION);
		jdbc.update("DELETE FROM notification WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM outbox_event WHERE dedup_key LIKE 'gh180-group-%'");
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
			VALUES (?, 'KR', 'GH180 grouping test', 'REGION')
			""", REGION);

		recipientA = insertUser("gh180-group-recipient-a");
		recipientB = insertUser("gh180-group-recipient-b");
		deviceA = notifications.saveDevice(new PushDevice(null, recipientA, PushPlatform.ANDROID,
			new byte[] {1, 2, 3}, "fp-gh180-group-a", PushDeviceStatus.ACTIVE, T, null)).id();
		deviceB = notifications.saveDevice(new PushDevice(null, recipientB, PushPlatform.ANDROID,
			new byte[] {4, 5, 6}, "fp-gh180-group-b", PushDeviceStatus.ACTIVE, T, null)).id();
	}

	@Test
	@DisplayName("INT-002 같은 수신자·종류의 T·T+5m·T+10m 알림은 창 정각까지 한 group·member 3개이고 직전 claim은 0건이다")
	void bundlesWindowInclusiveAndClaimsOnlyAtCollectUntil() {
		long first = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "window-t",
			DeliveryStatus.PENDING, 0, T, T);
		long second = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "window-t5",
			DeliveryStatus.PENDING, 0, T_PLUS_5M, T_PLUS_5M);
		long third = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "window-t10",
			DeliveryStatus.PENDING, 0, T_PLUS_10M, T_PLUS_10M);
		long notificationCount = countNotifications();
		long deliveryCount = countDeliveries();

		assertThat(collect(20, T_PLUS_10M)).isEqualTo(3);
		assertThat(collect(20, T_PLUS_10M)).isZero();

		assertThat(countNotifications()).isEqualTo(notificationCount);
		assertThat(countDeliveries()).isEqualTo(deliveryCount);
		assertThat(memberCount()).isEqualTo(3);
		assertThat(openGroupCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
		assertThat(groupCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
		assertThat(memberNotificationIds()).containsExactlyInAnyOrder(
			notificationIdOf(first), notificationIdOf(second), notificationIdOf(third));
		assertThat(groupStatus(recipientA, NotificationType.ANSWER_RECEIVED))
			.isEqualTo(PushDispatchGroupStatus.COLLECTING.name());
		assertThat(groupCollectUntil(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(T_PLUS_10M);

		assertThat(claim(10, T_PLUS_10M.minusMillis(1), T_PLUS_10M.plusSeconds(60))).isEmpty();
		List<ClaimedPushDispatchGroup> claimed = claim(10, T_PLUS_10M, T_PLUS_10M.plusSeconds(60));
		assertThat(claimed).hasSize(1);
		assertThat(claimed.get(0).generation()).isEqualTo(1);
		assertThat(claimed.get(0).leaseUntil()).isEqualTo(T_PLUS_10M.plusSeconds(60));
		assertThat(groupStatus(recipientA, NotificationType.ANSWER_RECEIVED))
			.isEqualTo(PushDispatchGroupStatus.PROCESSING.name());
		assertThat(groupAttemptCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-003 같은 사용자의 답변·공감과 다른 사용자 답변은 동시에 편입해도 recipient/type별 group만 만든다")
	void separatesRecipientAndTypeGroupsUnderConcurrentPlan() throws Exception {
		long answerA = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "type-answer-a",
			DeliveryStatus.PENDING, 0, T, T);
		long reactedA = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_REACTED, "type-reacted-a",
			DeliveryStatus.PENDING, 0, T, T);
		long answerB = insertDelivery(recipientB, deviceB, NotificationType.ANSWER_RECEIVED, "type-answer-b",
			DeliveryStatus.PENDING, 0, T, T);

		runTwoPlanners(10, T);

		assertThat(groupCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
		assertThat(groupCount(recipientA, NotificationType.ANSWER_REACTED)).isEqualTo(1);
		assertThat(groupCount(recipientB, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
		assertThat(memberCount()).isEqualTo(3);
		assertThat(memberGroupKeys()).containsExactlyInAnyOrder(
			recipientA + ":ANSWER_RECEIVED:" + notificationIdOf(answerA),
			recipientA + ":ANSWER_REACTED:" + notificationIdOf(reactedA),
			recipientB + ":ANSWER_RECEIVED:" + notificationIdOf(answerB));
	}

	@Test
	@DisplayName("INT-004 두 planner가 같은 미편입 묶음을 동시에 처리해도 notification당 member 1개와 열린 group 1개만 남고 unique 오류를 밖으로 내지 않는다")
	void concurrentPlannersKeepSingleMemberAndOpenGroupWithoutUniqueErrors() throws Exception {
		insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "race-1",
			DeliveryStatus.PENDING, 0, T, T);
		insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "race-2",
			DeliveryStatus.PENDING, 0, T.plusSeconds(30), T.plusSeconds(30));
		insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "race-3",
			DeliveryStatus.PENDING, 0, T.plusSeconds(60), T.plusSeconds(60));
		insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "race-4",
			DeliveryStatus.PENDING, 0, T_PLUS_5M, T_PLUS_5M);

		assertThatCode(() -> runTwoPlanners(10, T_PLUS_5M)).doesNotThrowAnyException();

		assertThat(memberCount()).isEqualTo(4);
		assertThat(openGroupCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
		assertThat(groupCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
		assertThat(duplicateMemberNotificationCount()).isZero();
	}

	@Test
	@DisplayName("INT-005 두 worker의 due group claim은 같은 group ID를 중복하지 않고 전체 due 행을 한 번씩만 점유한다")
	void claimsDueGroupsExclusivelyAcrossTwoWorkers() throws Exception {
		long first = insertDueGroup("due-1");
		long second = insertDueGroup("due-2");
		long third = insertDueGroup("due-3");
		long fourth = insertDueGroup("due-4");
		long processingLease = insertGroup(recipientA, NotificationType.ANSWER_RECEIVED, "gh180-group-lease-hold",
			"PROCESSING", T, T, T.plus(Duration.ofHours(8)), 1, T.plusSeconds(30));
		long future = insertGroup(recipientA, NotificationType.ANSWER_RECEIVED, "gh180-group-future",
			"PENDING", T, T, T.plus(Duration.ofHours(8)), 0, T.plusSeconds(60));

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
			assertThat(allClaimed).doesNotContain(processingLease, future);
			assertThat(storedGroupStatus(processingLease)).isEqualTo("PROCESSING");
			assertThat(storedGroupStatus(future)).isEqualTo("PENDING");
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("workers terminated").isTrue();
		}
	}

	@Test
	@DisplayName("INT-005 lease 만료 전에는 회수하지 않고 만료 후 generation을 1 올려 회수하며 이전 generation transition은 0행이다")
	void reclaimsOnlyAfterLeaseExpiryAndFencesStaleGeneration() {
		long groupId = insertDueGroup("lease-fence");
		Instant firstLeaseUntil = T.plusSeconds(30);
		Instant secondLeaseUntil = T.plusSeconds(90);

		List<ClaimedPushDispatchGroup> firstClaims = claim(10, T, firstLeaseUntil);
		assertThat(firstClaims).extracting(ClaimedPushDispatchGroup::groupId).containsExactly(groupId);
		ClaimedPushDispatchGroup firstClaim = firstClaims.get(0);
		assertThat(firstClaim.generation()).isEqualTo(1);

		assertThat(claim(10, T.plusSeconds(29), secondLeaseUntil)).isEmpty();

		List<ClaimedPushDispatchGroup> reclaimed = claim(10, T.plusSeconds(31), secondLeaseUntil);
		assertThat(reclaimed).extracting(ClaimedPushDispatchGroup::groupId).containsExactly(groupId);
		ClaimedPushDispatchGroup secondClaim = reclaimed.get(0);
		assertThat(secondClaim.generation()).isEqualTo(firstClaim.generation() + 1);
		assertThat(storedGroupAttemptCount(groupId)).isEqualTo(2);
		assertThat(storedGroupNextAttemptAt(groupId)).isEqualTo(secondLeaseUntil);

		assertThat(claimService.transition(groupId, firstClaim.generation(), PushDispatchGroupStatus.COMPLETED,
			T.plusSeconds(32), T.plusSeconds(32), T.plusSeconds(32))).isFalse();
		assertThat(storedGroupStatus(groupId)).isEqualTo("PROCESSING");
		assertThat(claimService.transition(groupId, secondClaim.generation(), PushDispatchGroupStatus.COMPLETED,
			T.plusSeconds(32), T.plusSeconds(32), T.plusSeconds(32))).isTrue();
		assertThat(storedGroupStatus(groupId)).isEqualTo("COMPLETED");
		assertThat(claimService.transition(groupId, secondClaim.generation(), PushDispatchGroupStatus.FAILED,
			T.plusSeconds(33), null, T.plusSeconds(33))).isFalse();
		assertThat(storedGroupStatus(groupId)).isEqualTo("COMPLETED");
		assertThat(storedGroupAttemptCount(groupId)).isEqualTo(2);
	}

	@Test
	@DisplayName("INT-005 COMPLETED group은 현재 generation이어도 PROCESSING이 아니면 다시 transition되지 않는다")
	void completedGroupRejectsCurrentGenerationTransition() {
		long groupId = insertDueGroup("completed-fence");
		List<ClaimedPushDispatchGroup> claimed = claim(10, T, T.plusSeconds(60));
		assertThat(claimed).extracting(ClaimedPushDispatchGroup::groupId).containsExactly(groupId);
		int generation = claimed.get(0).generation();
		assertThat(claimService.transition(groupId, generation, PushDispatchGroupStatus.COMPLETED,
			T.plusSeconds(1), T.plusSeconds(1), T.plusSeconds(1))).isTrue();

		assertThat(claimService.transition(groupId, generation, PushDispatchGroupStatus.FAILED,
			T.plusSeconds(2), null, T.plusSeconds(2))).isFalse();
		assertThat(storedGroupStatus(groupId)).isEqualTo("COMPLETED");
	}

	@Test
	@DisplayName("INT-018 기존 PENDING·FAILED와 만료된 PROCESSING delivery는 별도 이관 없이 편입하고 유효 lease는 제외하며 attempt_count를 보존한다")
	void groupsLegacyDeliveriesWithoutBackfillAndPreservesAttemptCounts() {
		long pending = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "legacy-pending",
			DeliveryStatus.PENDING, 0, T.plusSeconds(120), T);
		long failed = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "legacy-failed",
			DeliveryStatus.FAILED, 2, T, T.plusSeconds(1));
		long expiredProcessing = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED,
			"legacy-expired-processing", DeliveryStatus.PROCESSING, 3, T, T.plusSeconds(2));
		long activeLease = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "legacy-active-lease",
			DeliveryStatus.PROCESSING, 4, T.plusSeconds(600), T.plusSeconds(3));

		assertThat(collect(20, T)).isEqualTo(3);

		assertThat(memberNotificationIds()).containsExactlyInAnyOrder(
			notificationIdOf(pending), notificationIdOf(failed), notificationIdOf(expiredProcessing));
		assertThat(memberNotificationIds()).doesNotContain(notificationIdOf(activeLease));
		assertThat(storedDeliveryAttemptCount(pending)).isEqualTo(0);
		assertThat(storedDeliveryAttemptCount(failed)).isEqualTo(2);
		assertThat(storedDeliveryAttemptCount(expiredProcessing)).isEqualTo(3);
		assertThat(storedDeliveryAttemptCount(activeLease)).isEqualTo(4);
		assertThat(storedDeliveryStatus(activeLease)).isEqualTo(DeliveryStatus.PROCESSING.name());
	}

	@Test
	@DisplayName("같은 millisecond leftover는 닫힌 windowed group에 붙지 않고 새 COLLECTING group을 연다")
	void windowedInsertDoesNotJoinClosedGroupOnSameAggregationKey() {
		long first = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "closed-window-first",
			DeliveryStatus.PENDING, 0, T, T);
		assertThat(collect(10, T)).isEqualTo(1);
		long closedGroupId = jdbc.queryForObject("""
			SELECT id FROM push_dispatch_group
			WHERE recipient_id = ? AND notification_type = 'ANSWER_RECEIVED'
			""", Long.class, recipientA);
		jdbc.update("""
			UPDATE push_dispatch_group
			SET status = 'COMPLETED', completed_at = ?, attempt_count = 1
			WHERE id = ?
			""", Timestamp.from(T.plusSeconds(1)), closedGroupId);

		long leftover = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "closed-window-leftover",
			DeliveryStatus.PENDING, 0, T, T);
		assertThatCode(() -> collect(10, T)).doesNotThrowAnyException();

		assertThat(memberGroupId(notificationIdOf(first))).isEqualTo(closedGroupId);
		assertThat(storedGroupStatus(closedGroupId)).isEqualTo("COMPLETED");
		long leftoverGroupId = memberGroupId(notificationIdOf(leftover));
		assertThat(leftoverGroupId).isNotEqualTo(closedGroupId);
		assertThat(storedGroupStatus(leftoverGroupId)).isEqualTo("COLLECTING");
		assertThat(groupCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(2);
	}

	@Test
	@DisplayName("cycle ID가 없는 QUESTION_RECOMMENDED는 건너뛰고 같은 batch의 나머지 notification은 편입한다")
	void skipsRecommendationWithoutCycleAndContinuesBatch() {
		long answer = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "rec-skip-answer",
			DeliveryStatus.PENDING, 0, T, T);
		long orphanRecommended = insertDelivery(recipientA, deviceA, NotificationType.QUESTION_RECOMMENDED,
			"rec-skip-orphan", DeliveryStatus.PENDING, 0, T, T.plusSeconds(1));

		assertThatCode(() -> collect(20, T)).doesNotThrowAnyException();

		assertThat(memberNotificationIds()).contains(notificationIdOf(answer));
		assertThat(memberNotificationIds()).doesNotContain(notificationIdOf(orphanRecommended));
		assertThat(groupCount(recipientA, NotificationType.ANSWER_RECEIVED)).isEqualTo(1);
		assertThat(groupCount(recipientA, NotificationType.QUESTION_RECOMMENDED)).isZero();
	}

	@Test
	@DisplayName("INT-019 CLAIM_DUE_GROUPS와 member lookup EXPLAIN은 Task 1 index를 쓰고 LOCK_UNGROUPED는 bounded Limit이다")
	void explainsGroupingPlansWithBoundedFixture() {
		long memberNotificationId = notificationIdOf(insertDelivery(
			recipientB, deviceB, NotificationType.ANSWER_RECEIVED, "explain-member",
			DeliveryStatus.PENDING, 0, T, T));
		assertThat(collect(5, T)).isEqualTo(1);
		for (int index = 0; index < 40; index++) {
			insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "explain-ungrouped-" + index,
				DeliveryStatus.PENDING, 0, T.minusSeconds(index), T.minusSeconds(index));
		}
		for (int index = 0; index < 80; index++) {
			insertGroup(recipientA, NotificationType.ANSWER_RECEIVED, "gh180-group-explain-due-" + index,
				"PENDING", T, T, T.plus(Duration.ofHours(8)), 0, T.minusSeconds(index));
		}
		jdbc.execute("ANALYZE push_dispatch_group");
		jdbc.execute("ANALYZE push_dispatch_group_member");
		jdbc.execute("ANALYZE notification");
		jdbc.execute("ANALYZE notification_delivery");

		String ungroupedPlan = explain(PushDispatchGroupSql.LOCK_UNGROUPED, new MapSqlParameterSource()
			.addValue("limit", 20)
			.addValue("at", Timestamp.from(T)));
		String claimPlan = explainUsingIndexes(PushDispatchGroupSql.CLAIM_DUE_GROUPS, new MapSqlParameterSource()
			.addValue("limit", 20)
			.addValue("now", Timestamp.from(T))
			.addValue("leaseUntil", Timestamp.from(T.plusSeconds(60))));
		String memberPlan = explainUsingIndexes(PushDispatchGroupSql.FIND_GROUP_MEMBER_BY_NOTIFICATION,
			new MapSqlParameterSource("notificationId", memberNotificationId));

		assertBoundedExplain("LOCK_UNGROUPED", ungroupedPlan);
		assertThat(ungroupedPlan).contains("Limit");
		assertThat(claimPlan).contains("push_dispatch_group_due_idx");
		assertThat(memberPlan).contains("uq_push_dispatch_group_member_notification");
	}

	private int collect(int limit, Instant at) {
		Integer collected = transactions.execute(status -> planner.collectUngrouped(limit, at));
		return collected == null ? 0 : collected;
	}

	private void runTwoPlanners(int limit, Instant at) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> firstWorker = executor.submit(() -> collectConcurrently(ready, start, "planner-a", limit, at));
			Future<Integer> secondWorker = executor.submit(() -> collectConcurrently(ready, start, "planner-b", limit, at));
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("both planners ready").isTrue();
			start.countDown();
			firstWorker.get(15, TimeUnit.SECONDS);
			secondWorker.get(15, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("planners terminated").isTrue();
		}
	}

	private int collectConcurrently(
		CountDownLatch ready, CountDownLatch start, String workerName, int limit, Instant at) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).as(workerName + " started").isTrue();
		return collect(limit, at);
	}

	private List<ClaimedPushDispatchGroup> claim(int limit, Instant now, Instant leaseUntil) {
		return claimService.claimDueGroups(limit, now, leaseUntil);
	}

	private List<ClaimedPushDispatchGroup> claimConcurrently(
		CountDownLatch ready, CountDownLatch start, String workerName) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).as(workerName + " started").isTrue();
		return claim(2, T, T.plusSeconds(60));
	}

	private long insertUser(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long insertDelivery(
		long recipientId, long deviceId, NotificationType type, String suffix,
		DeliveryStatus status, int attemptCount, Instant nextAttemptAt, Instant createdAt) {
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(
			outboxAggregateType(type), 180L, outboxType(type),
			"gh180-group-" + suffix, "{}", createdAt));
		Notification notification = notifications.save(new Notification(
			null, recipientId, event.id(), type,
			"gh180-group-notification-" + suffix, null, null, null,
			NotificationStatus.UNREAD, createdAt, null));
		return notifications.saveDelivery(new NotificationDelivery(
			null, notification.id(), deviceId, status, attemptCount, nextAttemptAt,
			createdAt, null, null)).id();
	}

	private long insertDueGroup(String suffix) {
		return insertGroup(recipientA, NotificationType.ANSWER_RECEIVED, "gh180-group-" + suffix,
			"PENDING", T, T, T.plus(Duration.ofHours(8)), 0, T);
	}

	private long insertGroup(
		long recipientId, NotificationType type, String aggregationKey, String status,
		Instant windowStartedAt, Instant collectUntil, Instant policyExpiresAt,
		int attemptCount, Instant nextAttemptAt) {
		return jdbc.queryForObject("""
			INSERT INTO push_dispatch_group (
				recipient_id, notification_type, aggregation_key, status, window_started_at,
				collect_until, policy_expires_at, attempt_count, next_attempt_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			RETURNING id
			""", Long.class, recipientId, type.name(), aggregationKey, status,
			Timestamp.from(windowStartedAt), Timestamp.from(collectUntil), Timestamp.from(policyExpiresAt),
			attemptCount, Timestamp.from(nextAttemptAt));
	}

	private static OutboxEventType outboxType(NotificationType type) {
		return switch (type) {
			case ANSWER_REACTED -> OutboxEventType.ANSWER_REACTED;
			case QUESTION_RECOMMENDED -> OutboxEventType.QUESTION_RECOMMENDED;
			default -> OutboxEventType.ANSWER_PUBLISHED;
		};
	}

	private static OutboxAggregateType outboxAggregateType(NotificationType type) {
		return type == NotificationType.QUESTION_RECOMMENDED
			? OutboxAggregateType.QUESTION_ASSIGNMENT
			: OutboxAggregateType.ANSWER;
	}

	private long countNotifications() {
		return jdbc.queryForObject(
			"SELECT count(*) FROM notification WHERE recipient_id IN (?, ?)", Long.class, recipientA, recipientB);
	}

	private long countDeliveries() {
		return jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery
			WHERE notification_id IN (SELECT id FROM notification WHERE recipient_id IN (?, ?))
			""", Long.class, recipientA, recipientB);
	}

	private long memberCount() {
		return jdbc.queryForObject("""
			SELECT count(*) FROM push_dispatch_group_member
			WHERE group_id IN (SELECT id FROM push_dispatch_group WHERE recipient_id IN (?, ?))
			""", Long.class, recipientA, recipientB);
	}

	private long groupCount(long recipientId, NotificationType type) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM push_dispatch_group
			WHERE recipient_id = ? AND notification_type = ?
			""", Long.class, recipientId, type.name());
	}

	private long openGroupCount(long recipientId, NotificationType type) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM push_dispatch_group
			WHERE recipient_id = ? AND notification_type = ? AND status = 'COLLECTING'
			""", Long.class, recipientId, type.name());
	}

	private List<Long> memberNotificationIds() {
		return jdbc.queryForList("""
			SELECT m.notification_id
			FROM push_dispatch_group_member m
			JOIN push_dispatch_group g ON g.id = m.group_id
			WHERE g.recipient_id IN (?, ?)
			""", Long.class, recipientA, recipientB);
	}

	private List<String> memberGroupKeys() {
		return jdbc.queryForList("""
			SELECT g.recipient_id || ':' || g.notification_type || ':' || n.id
			FROM push_dispatch_group_member m
			JOIN push_dispatch_group g ON g.id = m.group_id
			JOIN notification n ON n.id = m.notification_id
			WHERE g.recipient_id IN (?, ?)
			""", String.class, recipientA, recipientB);
	}

	private long duplicateMemberNotificationCount() {
		return jdbc.queryForObject("""
			SELECT count(*) FROM (
				SELECT m.notification_id
				FROM push_dispatch_group_member m
				JOIN push_dispatch_group g ON g.id = m.group_id
				WHERE g.recipient_id IN (?, ?)
				GROUP BY m.notification_id
				HAVING count(*) > 1
			) duplicated
			""", Long.class, recipientA, recipientB);
	}

	private String groupStatus(long recipientId, NotificationType type) {
		return jdbc.queryForObject("""
			SELECT status FROM push_dispatch_group
			WHERE recipient_id = ? AND notification_type = ?
			""", String.class, recipientId, type.name());
	}

	private Instant groupCollectUntil(long recipientId, NotificationType type) {
		Timestamp timestamp = jdbc.queryForObject("""
			SELECT collect_until FROM push_dispatch_group
			WHERE recipient_id = ? AND notification_type = ?
			""", Timestamp.class, recipientId, type.name());
		return timestamp.toInstant();
	}

	private int groupAttemptCount(long recipientId, NotificationType type) {
		return jdbc.queryForObject("""
			SELECT attempt_count FROM push_dispatch_group
			WHERE recipient_id = ? AND notification_type = ?
			""", Integer.class, recipientId, type.name());
	}

	private long notificationIdOf(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT notification_id FROM notification_delivery WHERE id = ?", Long.class, deliveryId);
	}

	private long memberGroupId(long notificationId) {
		return jdbc.queryForObject("""
			SELECT group_id FROM push_dispatch_group_member
			WHERE notification_id = ?
			""", Long.class, notificationId);
	}

	private String storedGroupStatus(long groupId) {
		return jdbc.queryForObject("SELECT status FROM push_dispatch_group WHERE id = ?", String.class, groupId);
	}

	private int storedGroupAttemptCount(long groupId) {
		return jdbc.queryForObject("SELECT attempt_count FROM push_dispatch_group WHERE id = ?", Integer.class, groupId);
	}

	private Instant storedGroupNextAttemptAt(long groupId) {
		Timestamp timestamp = jdbc.queryForObject(
			"SELECT next_attempt_at FROM push_dispatch_group WHERE id = ?", Timestamp.class, groupId);
		return timestamp.toInstant();
	}

	private int storedDeliveryAttemptCount(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT attempt_count FROM notification_delivery WHERE id = ?", Integer.class, deliveryId);
	}

	private String storedDeliveryStatus(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
	}

	private String explain(String sql, MapSqlParameterSource params) {
		List<String> planLines = namedJdbc.queryForList("EXPLAIN (COSTS TRUE)\n" + sql, params, String.class);
		assertThat(planLines).as("INT-019 EXPLAIN must return a planner result").isNotEmpty();
		return String.join("\n", planLines);
	}

	private String explainUsingIndexes(String sql, MapSqlParameterSource params) {
		return transactions.execute(status -> {
			jdbc.execute("SET LOCAL enable_seqscan = off");
			return explain(sql, params);
		});
	}

	private static void assertBoundedExplain(String sqlName, String plan) {
		assertThat(plan).as(sqlName + " EXPLAIN").isNotBlank();
		assertThat(plan).containsPattern("rows=\\d+");
		boolean usesIndexAccess = plan.contains("Index Scan")
			|| plan.contains("Index Only Scan")
			|| plan.contains("Bitmap Heap Scan")
			|| plan.contains("Bitmap Index Scan");
		boolean hasLimit = plan.contains("Limit");
		int maxRows = maxEstimatedRows(plan);
		boolean unboundedSeqScan = plan.contains("Seq Scan") && !usesIndexAccess && !hasLimit && maxRows >= 10_000;
		assertThat(unboundedSeqScan)
			.as(sqlName + " unbounded full scan must fail\n" + plan)
			.isFalse();
		assertThat(usesIndexAccess || hasLimit || maxRows < 10_000)
			.as(sqlName + " must use an index or a bounded row estimate\n" + plan)
			.isTrue();
	}

	private static int maxEstimatedRows(String plan) {
		int maxRows = 0;
		Matcher matcher = Pattern.compile("rows=(\\d+)").matcher(plan);
		while (matcher.find()) {
			maxRows = Math.max(maxRows, Integer.parseInt(matcher.group(1)));
		}
		return maxRows;
	}
}
