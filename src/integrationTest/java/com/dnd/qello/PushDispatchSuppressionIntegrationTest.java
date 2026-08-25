/**
 * Created at: 2026-08-25T14:38:00+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-006, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-007, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-008, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-009, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-010, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-011, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-016, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-021
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushBudgetReservation;
import com.dnd.qello.notification.push.group.PushDispatchGroupContext;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;
import com.dnd.qello.notification.push.group.PushDispatchMemberContext;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy.Action;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy.Reason;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
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
class PushDispatchSuppressionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH180-SUPPRESS";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
	private static final NotificationQuietHours SEOUL_QUIET = new NotificationQuietHours(
		LocalTime.of(22, 0), LocalTime.of(7, 0), SEOUL);
	private static final Instant T_23 = LocalDateTime.of(2026, 8, 25, 23, 0).atZone(SEOUL).toInstant();
	private static final Instant T_23_10 = T_23.plus(Duration.ofMinutes(10));
	private static final Instant T_07 = LocalDateTime.of(2026, 8, 26, 7, 0).atZone(SEOUL).toInstant();
	private static final Instant T_22 = LocalDateTime.of(2026, 8, 25, 22, 0).atZone(SEOUL).toInstant();
	private static final Instant T_BUDGET = Instant.parse("2026-08-26T00:00:00Z");
	private static final Instant T_ROLLOVER = Instant.parse("2026-08-25T00:30:00Z");
	private static final Instant T_REC_A = Instant.parse("2026-08-25T01:00:00Z");
	private static final Instant T_REC_B = T_REC_A.plus(Duration.ofHours(23));
	private static final Instant T_REC_C = T_REC_A.plus(Duration.ofHours(24));
	private static final PushPolicyProperties POLICY = new PushPolicyProperties(
		Duration.parse("PT10M"), Duration.parse("PT8H"), 5, 2, Duration.parse("PT24H"));

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private NotificationPreferenceRepository preferences;
	@Autowired
	private PushDispatchGroupRepository groups;
	@Autowired
	private PushDispatchGroupClaimService claimService;
	@Autowired
	private TransactionTemplate transactions;

	private PushDispatchGroupPlanner planner;
	private PushSuppressionPolicy suppressionPolicy;
	private PushBudgetPolicy budgetPolicy;
	private long recipientA;
	private long recipientB;
	private long senderId;
	private long deviceA;
	private long deviceB;

	@BeforeEach
	void resetFixtures() {
		planner = new PushDispatchGroupPlanner(groups, new PushGroupingPolicy(POLICY));
		suppressionPolicy = new PushSuppressionPolicy(POLICY, Clock.systemUTC());
		budgetPolicy = new PushBudgetPolicy(POLICY);
		jdbc.update("""
			DELETE FROM push_daily_budget
			WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
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
		jdbc.update("DELETE FROM outbox_event WHERE dedup_key LIKE 'gh180-suppress-%'");
		jdbc.update("""
			DELETE FROM user_block
			WHERE blocker_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR blocked_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("""
			DELETE FROM question_assignment
			WHERE cycle_id IN (
				SELECT qac.id FROM question_assignment_cycle qac
				JOIN user_account ua ON ua.id = qac.user_id
				WHERE ua.coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM question_assignment_cycle
			WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("""
			DELETE FROM approved_question
			WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("DELETE FROM notification_preference WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM notification_user_setting WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
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
			VALUES (?, 'KR', 'GH180 suppression test', 'REGION')
			""", REGION);

		recipientA = insertUser("gh180-suppress-recipient-a", "Asia/Seoul");
		recipientB = insertUser("gh180-suppress-recipient-b", "America/Los_Angeles");
		senderId = insertUser("gh180-suppress-sender", "Asia/Seoul");
		deviceA = notifications.saveDevice(new PushDevice(null, recipientA, PushPlatform.ANDROID,
			new byte[] {1, 2, 3}, "fp-gh180-suppress-a", PushDeviceStatus.ACTIVE, T_23, null)).id();
		deviceB = notifications.saveDevice(new PushDevice(null, recipientB, PushPlatform.ANDROID,
			new byte[] {4, 5, 6}, "fp-gh180-suppress-b", PushDeviceStatus.ACTIVE, T_23, null)).id();
	}

	@Test
	@DisplayName("INT-006 quiet 23시 group은 첫 dispatch에서 07시까지 지연하고 예산·delivery를 건드리지 않으며 종료 시각 재검사는 발송한다")
	void defersQuietGroupUntilEndThenReservesOnFreshRecheck() {
		assertThat(PushDispatchGroupSql.FIND_GROUP_CONTEXT)
			.contains("token_ciphertext")
			.doesNotContain("body_text")
			.doesNotContain("nickname")
			.doesNotContain("distance_m")
			.doesNotContain("origin_position");

		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		long deliveryId = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "quiet-window",
			T_23, null, null);
		long notificationId = notificationIdOf(deliveryId);

		List<DispatchResult> first = dispatchDue(T_23_10);
		assertThat(first).hasSize(1);
		DispatchResult deferred = first.get(0);
		assertThat(deferred.action()).isEqualTo(Action.DEFER);
		assertThat(deferred.reason()).isEqualTo(Reason.QUIET_HOURS);
		assertThat(deferred.nextAttemptAt()).isEqualTo(T_07);
		assertThat(deferred.context().budgetZone()).isEqualTo(SEOUL);
		assertThat(deferred.context().preference().quietHours()).isEqualTo(SEOUL_QUIET);
		assertThat(deferred.context().members()).extracting(PushDispatchMemberContext::deliveryId)
			.containsExactly(deliveryId);
		assertThat(groupStatus(deferred.groupId())).isEqualTo(PushDispatchGroupStatus.PENDING.name());
		assertThat(groupNextAttemptAt(deferred.groupId())).isEqualTo(T_07);
		assertThat(groupFirstAttemptedAt(deferred.groupId())).isNull();
		assertThat(deliveryStatus(deliveryId)).isEqualTo(DeliveryStatus.PENDING.name());
		assertThat(budgetCount(recipientA)).isZero();
		assertThat(notificationStatus(notificationId)).isEqualTo(NotificationStatus.UNREAD.name());

		List<DispatchResult> second = dispatchDue(T_07);
		assertThat(second).hasSize(1);
		DispatchResult reserved = second.get(0);
		assertThat(reserved.action()).isEqualTo(Action.SEND);
		assertThat(reserved.reservation()).isEqualTo(PushBudgetReservation.RESERVED);
		assertThat(reserved.context().budgetZone()).isEqualTo(SEOUL);
		assertThat(groupFirstAttemptedAt(reserved.groupId())).isEqualTo(T_07);
		assertThat(groupBudgetDate(reserved.groupId())).isEqualTo(LocalDate.of(2026, 8, 26));
		assertThat(groupBudgetConsumedAt(reserved.groupId())).isEqualTo(T_07);
		assertThat(consumed(recipientA, LocalDate.of(2026, 8, 26))).containsExactly(1, 1);
		assertThat(deliveryStatus(deliveryId)).isEqualTo(DeliveryStatus.PENDING.name());
		assertThat(notificationStatus(notificationId)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(claimService.reserveBudget(reserved.groupId(), reserved.generation(),
			LocalDate.of(2026, 8, 26), NotificationType.ANSWER_RECEIVED, POLICY, T_07))
			.isEqualTo(PushBudgetReservation.ALREADY_RESERVED);
		assertThat(consumed(recipientA, LocalDate.of(2026, 8, 26))).containsExactly(1, 1);
	}

	@Test
	@DisplayName("INT-007 quiet 종료가 maxDelay를 넘으면 provider·예산 없이 delivery만 취소하고 원장을 보존한다")
	void cancelsWhenQuietEndExceedsMaxDelayWithoutConsumingBudget() {
		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		long deliveryId = insertDelivery(recipientA, deviceA, NotificationType.REPORT_RESOLVED, "max-delay",
			T_22, null, null);
		long notificationId = notificationIdOf(deliveryId);

		List<DispatchResult> results = dispatchDue(T_22);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).action()).isEqualTo(Action.CANCEL);
		assertThat(results.get(0).reason()).isEqualTo(Reason.MAX_DELAY_EXCEEDED);
		assertThat(groupStatus(results.get(0).groupId())).isEqualTo(PushDispatchGroupStatus.CANCELLED.name());
		assertThat(groupCompletedAt(results.get(0).groupId())).isEqualTo(T_22);
		assertThat(groupFirstAttemptedAt(results.get(0).groupId())).isNull();
		assertThat(deliveryStatus(deliveryId)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(notificationStatus(notificationId)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(budgetCount(recipientA)).isZero();
	}

	@Test
	@DisplayName("cancelGroup은 group generation과 다른 attempt_count의 PROCESSING 미발송도 취소하고 SENT는 남긴다")
	void cancelsProcessingDeliveryWhenAttemptCountDiffersFromGroupGeneration() {
		long processingDelivery = insertDelivery(recipientA, deviceA, NotificationType.REPORT_RESOLVED,
			"cancel-mismatch-processing", T_BUDGET, null, null);
		long notificationId = notificationIdOf(processingDelivery);
		long sentDevice = notifications.saveDevice(new PushDevice(null, recipientA, PushPlatform.IOS,
			new byte[] {7, 8, 9}, "fp-gh180-suppress-a-sent", PushDeviceStatus.ACTIVE, T_BUDGET, null)).id();
		long sentDelivery = notifications.saveDelivery(new NotificationDelivery(
			null, notificationId, sentDevice, DeliveryStatus.PENDING, 0, T_BUDGET, T_BUDGET, null, null)).id();

		assertThat(collect(10, T_BUDGET)).isEqualTo(1);
		List<ClaimedPushDispatchGroup> firstClaim = claimService.claimDueGroups(
			10, T_BUDGET, T_BUDGET.plusSeconds(60));
		assertThat(firstClaim).hasSize(1);
		long groupId = firstClaim.get(0).groupId();
		assertThat(firstClaim.get(0).generation()).isEqualTo(1);
		assertThat(claimService.deferGroup(groupId, 1, T_BUDGET.plusSeconds(60), T_BUDGET)).isTrue();

		List<ClaimedPushDispatchGroup> reclaimed = claimService.claimDueGroups(
			10, T_BUDGET.plusSeconds(60), T_BUDGET.plusSeconds(120));
		assertThat(reclaimed).hasSize(1);
		assertThat(reclaimed.get(0).groupId()).isEqualTo(groupId);
		assertThat(reclaimed.get(0).generation()).isEqualTo(2);

		jdbc.update("""
			UPDATE notification_delivery
			SET status = 'PROCESSING', attempt_count = 1, next_attempt_at = ?
			WHERE id = ?
			""", Timestamp.from(T_BUDGET.plusSeconds(90)), processingDelivery);
		jdbc.update("""
			UPDATE notification_delivery
			SET status = 'SENT', attempt_count = 1, sent_at = ?, next_attempt_at = ?
			WHERE id = ?
			""", Timestamp.from(T_BUDGET.plusSeconds(1)), Timestamp.from(T_BUDGET.plusSeconds(1)), sentDelivery);

		assertThat(claimService.cancelGroup(groupId, 2, T_BUDGET.plusSeconds(90))).isTrue();
		assertThat(groupStatus(groupId)).isEqualTo(PushDispatchGroupStatus.CANCELLED.name());
		assertThat(deliveryStatus(processingDelivery)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(deliveryStatus(sentDelivery)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(notificationStatus(notificationId)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(jdbc.queryForObject(
			"SELECT attempt_count FROM notification_delivery WHERE id = ?", Integer.class, processingDelivery))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("INT-008 global/type OFF는 미발송을 취소하고 quiet 세 필드를 SQL·repository 모두에서 보존하며 재활성화 뒤 같은 quiet로 지연한다")
	void preservesQuietHoursThroughGlobalAndTypeOffThenDefersAfterReenable() {
		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		savePreferences(recipientA, false, SEOUL_QUIET, allTypesEnabled());
		long globalOffDelivery = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED,
			"global-off", T_23, null, null);

		List<DispatchResult> globalOff = dispatchDue(T_23_10);
		assertThat(globalOff).hasSize(1);
		assertThat(globalOff.get(0).action()).isEqualTo(Action.CANCEL);
		assertThat(globalOff.get(0).reason()).isEqualTo(Reason.GLOBAL_OFF);
		assertThat(deliveryStatus(globalOffDelivery)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(notificationStatus(notificationIdOf(globalOffDelivery)))
			.isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(budgetCount(recipientA)).isZero();
		assertQuietUnchanged(recipientA, false);
		assertThat(preferences.findByUserId(recipientA).quietHours()).isEqualTo(SEOUL_QUIET);
		assertThat(preferences.findByUserId(recipientA).pushEnabled()).isFalse();

		savePreferences(recipientA, true, SEOUL_QUIET,
			NotificationPreferenceIntegrationFixtures.typePreferences(NotificationType.ANSWER_RECEIVED, false));
		Instant typeOffAt = T_23.plus(Duration.ofMinutes(11));
		long typeOffDelivery = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED,
			"type-off", typeOffAt, null, null);

		List<DispatchResult> typeOff = dispatchDue(typeOffAt.plus(Duration.ofMinutes(10)));
		assertThat(typeOff).hasSize(1);
		assertThat(typeOff.get(0).action()).isEqualTo(Action.CANCEL);
		assertThat(typeOff.get(0).reason()).isEqualTo(Reason.TYPE_OFF);
		assertThat(deliveryStatus(typeOffDelivery)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertQuietUnchanged(recipientA, true);
		assertThat(preferences.findByUserId(recipientA).quietHours()).isEqualTo(SEOUL_QUIET);

		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		Instant reenabledAt = T_23.plus(Duration.ofMinutes(22));
		insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED, "reenabled-quiet",
			reenabledAt, null, null);

		List<DispatchResult> reenabled = dispatchDue(reenabledAt.plus(Duration.ofMinutes(10)));
		assertThat(reenabled).hasSize(1);
		assertThat(reenabled.get(0).action()).isEqualTo(Action.DEFER);
		assertThat(reenabled.get(0).reason()).isEqualTo(Reason.QUIET_HOURS);
		assertThat(reenabled.get(0).nextAttemptAt()).isEqualTo(T_07);
		assertQuietUnchanged(recipientA, true);
	}

	@Test
	@DisplayName("INT-009 일반 group 10개의 동시 reserve는 정확히 3건만 성공하고 예산 3/3과 나머지 취소를 원장 보존과 함께 남긴다")
	void concurrentGeneralReservesCapAtThreeAndCancelTheRest() throws Exception {
		LocalDate budgetDate = budgetPolicy.budgetDate(T_BUDGET, SEOUL);
		List<Long> deliveryIds = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			deliveryIds.add(insertDelivery(recipientA, deviceA, NotificationType.REPORT_RESOLVED,
				"cap-" + index, T_BUDGET, null, null));
		}
		assertThat(collect(20, T_BUDGET)).isEqualTo(10);
		List<ClaimedPushDispatchGroup> claims = claimService.claimDueGroups(20, T_BUDGET, T_BUDGET.plusSeconds(60));
		assertThat(claims).hasSize(10);
		long notificationCount = countNotifications(recipientA);

		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch ready = new CountDownLatch(10);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<PushBudgetReservation>> futures = new ArrayList<>();
		try {
			for (ClaimedPushDispatchGroup claim : claims) {
				futures.add(executor.submit(() -> reserveConcurrently(ready, start, claim, budgetDate,
					NotificationType.REPORT_RESOLVED, T_BUDGET)));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("all reserve workers ready").isTrue();
			start.countDown();

			List<PushBudgetReservation> reservations = new ArrayList<>();
			for (int index = 0; index < futures.size(); index++) {
				reservations.add(futures.get(index).get(15, TimeUnit.SECONDS));
			}
			assertThat(reservations).filteredOn(value -> value == PushBudgetReservation.RESERVED).hasSize(3);
			assertThat(reservations).filteredOn(value -> value == PushBudgetReservation.LIMIT_EXCEEDED).hasSize(7);

			for (int index = 0; index < claims.size(); index++) {
				if (reservations.get(index) == PushBudgetReservation.LIMIT_EXCEEDED) {
					assertThat(claimService.cancelGroup(claims.get(index).groupId(),
						claims.get(index).generation(), T_BUDGET)).isTrue();
				}
			}
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("reserve workers terminated").isTrue();
		}

		assertThat(consumed(recipientA, budgetDate)).containsExactly(3, 3);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM push_dispatch_group
			WHERE recipient_id = ? AND status = 'PROCESSING' AND budget_consumed_at IS NOT NULL
			""", Long.class, recipientA)).isEqualTo(3L);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM push_dispatch_group
			WHERE recipient_id = ? AND status = 'CANCELLED'
			""", Long.class, recipientA)).isEqualTo(7L);
		assertThat(deliveryIds.stream().map(this::deliveryStatus).filter(DeliveryStatus.CANCELLED.name()::equals))
			.hasSize(7);
		assertThat(deliveryIds.stream().map(this::deliveryStatus).filter(DeliveryStatus.PENDING.name()::equals))
			.hasSize(3);
		assertThat(countNotifications(recipientA)).isEqualTo(notificationCount);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification
			WHERE recipient_id = ? AND status = 'UNREAD'
			""", Long.class, recipientA)).isEqualTo(notificationCount);
		assertThat(jdbc.queryForObject("""
			SELECT consumed_total >= 0 AND consumed_general >= 0
				AND consumed_general <= consumed_total AND consumed_total <= 5
			FROM push_daily_budget WHERE user_id = ? AND budget_date = ?
			""", Boolean.class, recipientA, java.sql.Date.valueOf(budgetDate))).isTrue();
	}

	@Test
	@DisplayName("INT-010 일반 3건을 소비한 뒤 방향글 3개의 동시 reserve는 예약량 2건만 허용하고 total=5로 둔다")
	void concurrentDirectionReservesUsePrioritySlotsOnly() throws Exception {
		LocalDate budgetDate = budgetPolicy.budgetDate(T_BUDGET, SEOUL);
		jdbc.update("""
			INSERT INTO push_daily_budget (user_id, budget_date, consumed_total, consumed_general, updated_at)
			VALUES (?, ?, 3, 3, ?)
			""", recipientA, java.sql.Date.valueOf(budgetDate), Timestamp.from(T_BUDGET));
		for (int index = 0; index < 3; index++) {
			insertDelivery(recipientA, deviceA, NotificationType.DIRECTION_POST_RECEIVED,
				"priority-" + index, T_BUDGET, null, null);
		}
		assertThat(collect(10, T_BUDGET)).isEqualTo(3);
		List<ClaimedPushDispatchGroup> claims = claimService.claimDueGroups(10, T_BUDGET, T_BUDGET.plusSeconds(60));
		assertThat(claims).hasSize(3);

		ExecutorService executor = Executors.newFixedThreadPool(3);
		CountDownLatch ready = new CountDownLatch(3);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<PushBudgetReservation>> futures = new ArrayList<>();
		try {
			for (ClaimedPushDispatchGroup claim : claims) {
				futures.add(executor.submit(() -> reserveConcurrently(ready, start, claim, budgetDate,
					NotificationType.DIRECTION_POST_RECEIVED, T_BUDGET)));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).as("direction workers ready").isTrue();
			start.countDown();
			List<PushBudgetReservation> reservations = new ArrayList<>();
			for (Future<PushBudgetReservation> future : futures) {
				reservations.add(future.get(15, TimeUnit.SECONDS));
			}
			assertThat(reservations).filteredOn(value -> value == PushBudgetReservation.RESERVED).hasSize(2);
			assertThat(reservations).filteredOn(value -> value == PushBudgetReservation.LIMIT_EXCEEDED).hasSize(1);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("direction workers terminated").isTrue();
		}

		assertThat(consumed(recipientA, budgetDate)).containsExactly(5, 3);
	}

	@Test
	@DisplayName("INT-011 같은 UTC 시각도 account timezone local date로 예산을 분리하고 quiet zone은 쓰지 않으며 날짜별 상한이 독립이다")
	void splitsBudgetRowsByAccountTimezoneNotQuietZone() {
		NotificationQuietHours honoluluQuiet = new NotificationQuietHours(
			LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Pacific/Honolulu"));
		savePreferences(recipientA, true, honoluluQuiet, allTypesEnabled());
		savePreferences(recipientB, true, honoluluQuiet, allTypesEnabled());
		insertDelivery(recipientA, deviceA, NotificationType.REPORT_RESOLVED, "tz-seoul", T_ROLLOVER, null, null);
		insertDelivery(recipientB, deviceB, NotificationType.REPORT_RESOLVED, "tz-la", T_ROLLOVER, null, null);

		List<DispatchResult> results = dispatchDue(T_ROLLOVER);
		assertThat(results).hasSize(2);
		assertThat(results).allMatch(result -> result.reservation() == PushBudgetReservation.RESERVED);

		LocalDate seoulDate = budgetPolicy.budgetDate(T_ROLLOVER, SEOUL);
		LocalDate laDate = budgetPolicy.budgetDate(T_ROLLOVER, LOS_ANGELES);
		assertThat(seoulDate).isEqualTo(LocalDate.of(2026, 8, 25));
		assertThat(laDate).isEqualTo(LocalDate.of(2026, 8, 24));
		assertThat(results.stream().map(result -> result.context().budgetZone()).toList())
			.containsExactlyInAnyOrder(SEOUL, LOS_ANGELES);
		assertThat(groupBudgetDate(groupIdFor(recipientA))).isEqualTo(seoulDate);
		assertThat(groupBudgetDate(groupIdFor(recipientB))).isEqualTo(laDate);
		assertThat(consumed(recipientA, seoulDate)).containsExactly(1, 1);
		assertThat(consumed(recipientB, laDate)).containsExactly(1, 1);

		Instant seoulFillAt = T_ROLLOVER.plusSeconds(1);
		for (int index = 0; index < 2; index++) {
			insertDelivery(recipientA, deviceA, NotificationType.REPORT_RESOLVED,
				"tz-seoul-fill-" + index, seoulFillAt, null, null);
		}
		List<DispatchResult> seoulFill = dispatchDue(seoulFillAt);
		assertThat(seoulFill).hasSize(2);
		assertThat(seoulFill).allMatch(result -> result.reservation() == PushBudgetReservation.RESERVED);
		assertThat(consumed(recipientA, seoulDate)).containsExactly(3, 3);

		insertDelivery(recipientA, deviceA, NotificationType.REPORT_RESOLVED,
			"tz-seoul-denied", T_ROLLOVER.plusSeconds(10), null, null);
		List<DispatchResult> seoulDenied = dispatchDue(T_ROLLOVER.plusSeconds(10));
		assertThat(seoulDenied).hasSize(1);
		assertThat(seoulDenied.get(0).reservation()).isEqualTo(PushBudgetReservation.LIMIT_EXCEEDED);
		assertThat(consumed(recipientA, seoulDate)).containsExactly(3, 3);
		assertThat(consumed(recipientB, laDate)).containsExactly(1, 1);
	}

	@Test
	@DisplayName("INT-016 같은 cycle 3건은 한 번만 예약하고 간격 전 cycle은 취소·빈도 미전진, 정각 cycle은 허용한다")
	void recommendationCycleDedupsAndHonorsMinIntervalWithoutAdvancingOnCancel() {
		long questionOne = insertApprovedQuestion("rec-q-1");
		long questionTwo = insertApprovedQuestion("rec-q-2");
		long questionThree = insertApprovedQuestion("rec-q-3");
		long cycleA = insertCycle(recipientA, "cycle-a", T_REC_A);
		long assignmentA1 = insertAssignment(cycleA, questionOne, 1, T_REC_A);
		long assignmentA2 = insertAssignment(cycleA, questionTwo, 2, T_REC_A);
		long assignmentA3 = insertAssignment(cycleA, questionThree, 3, T_REC_A);
		long deliveryA1 = insertRecommended(recipientA, deviceA, assignmentA1, "rec-a-1", T_REC_A);
		long deliveryA2 = insertRecommended(recipientA, deviceA, assignmentA2, "rec-a-2", T_REC_A);
		long deliveryA3 = insertRecommended(recipientA, deviceA, assignmentA3, "rec-a-3", T_REC_A);

		List<DispatchResult> cycleAResult = dispatchDue(T_REC_A);
		assertThat(cycleAResult).hasSize(1);
		assertThat(cycleAResult.get(0).reservation()).isEqualTo(PushBudgetReservation.RESERVED);
		assertThat(cycleAResult.get(0).context().members()).hasSize(3);
		assertThat(groupFirstAttemptedAt(cycleAResult.get(0).groupId())).isEqualTo(T_REC_A);
		assertThat(List.of(deliveryA1, deliveryA2, deliveryA3).stream().map(this::notificationStatus).toList())
			.containsOnly(NotificationStatus.UNREAD.name());

		long cycleB = insertCycle(recipientA, "cycle-b", T_REC_B);
		long assignmentB = insertAssignment(cycleB, questionOne, 1, T_REC_B);
		long deliveryB = insertRecommended(recipientA, deviceA, assignmentB, "rec-b", T_REC_B);
		List<DispatchResult> cycleBResult = dispatchDue(T_REC_B);
		assertThat(cycleBResult).hasSize(1);
		assertThat(cycleBResult.get(0).action()).isEqualTo(Action.CANCEL);
		assertThat(cycleBResult.get(0).reason()).isEqualTo(Reason.RECOMMENDATION_INTERVAL);
		assertThat(cycleBResult.get(0).context().lastRecommendationAttemptAt()).isEqualTo(T_REC_A);
		assertThat(groupFirstAttemptedAt(cycleBResult.get(0).groupId())).isNull();
		assertThat(deliveryStatus(deliveryB)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(notificationStatus(notificationIdOf(deliveryB))).isEqualTo(NotificationStatus.UNREAD.name());

		long cycleC = insertCycle(recipientA, "cycle-c", T_REC_C);
		long assignmentC = insertAssignment(cycleC, questionOne, 1, T_REC_C);
		long deliveryC = insertRecommended(recipientA, deviceA, assignmentC, "rec-c", T_REC_C);
		List<DispatchResult> cycleCResult = dispatchDue(T_REC_C);
		assertThat(cycleCResult).hasSize(1);
		assertThat(cycleCResult.get(0).action()).isEqualTo(Action.SEND);
		assertThat(cycleCResult.get(0).reservation()).isEqualTo(PushBudgetReservation.RESERVED);
		assertThat(cycleCResult.get(0).context().lastRecommendationAttemptAt()).isEqualTo(T_REC_A);
		assertThat(groupFirstAttemptedAt(cycleCResult.get(0).groupId())).isEqualTo(T_REC_C);
		assertThat(notificationStatus(notificationIdOf(deliveryC))).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification
			WHERE recipient_id = ? AND notification_type = 'QUESTION_RECOMMENDED' AND status = 'UNREAD'
			""", Long.class, recipientA)).isEqualTo(5L);
	}

	@Test
	@DisplayName("INT-021 quiet 지연 뒤 global OFF는 새 snapshot으로 취소하고 원장·열람 자격을 보존한다")
	void rechecksGlobalOffAfterQuietDeferAndCancelsWithoutSend() {
		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		long deliveryId = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED,
			"recheck-global", T_23, null, null);
		assertThat(dispatchDue(T_23_10)).extracting(DispatchResult::action).containsExactly(Action.DEFER);

		savePreferences(recipientA, false, SEOUL_QUIET, allTypesEnabled());
		List<DispatchResult> after = dispatchDue(T_07);
		assertThat(after).hasSize(1);
		assertThat(after.get(0).action()).isEqualTo(Action.CANCEL);
		assertThat(after.get(0).reason()).isEqualTo(Reason.GLOBAL_OFF);
		assertThat(after.get(0).context().preference().pushEnabled()).isFalse();
		assertCancelledWithoutSend(deliveryId, after.get(0).groupId());
	}

	@Test
	@DisplayName("INT-021 quiet 지연 뒤 type OFF는 새 snapshot으로 취소하고 원장·열람 자격을 보존한다")
	void rechecksTypeOffAfterQuietDeferAndCancelsWithoutSend() {
		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		long deliveryId = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED,
			"recheck-type", T_23, null, null);
		assertThat(dispatchDue(T_23_10)).extracting(DispatchResult::action).containsExactly(Action.DEFER);

		savePreferences(recipientA, true, SEOUL_QUIET,
			NotificationPreferenceIntegrationFixtures.typePreferences(NotificationType.ANSWER_RECEIVED, false));
		List<DispatchResult> after = dispatchDue(T_07);
		assertThat(after).hasSize(1);
		assertThat(after.get(0).action()).isEqualTo(Action.CANCEL);
		assertThat(after.get(0).reason()).isEqualTo(Reason.TYPE_OFF);
		assertCancelledWithoutSend(deliveryId, after.get(0).groupId());
	}

	@Test
	@DisplayName("INT-021 quiet 지연 뒤 상호 차단은 새 snapshot으로 읽고 미발송을 취소한다")
	void rechecksBlockAfterQuietDeferAndCancelsWithoutSend() {
		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		long questionId = insertApprovedQuestion("recheck-block-q");
		long postId = insertDirectionPost(senderId, questionId, "recheck-block", T_23, T_07.plusSeconds(60));
		long deliveryId = insertDirectionDelivery(recipientA, deviceA, postId, "recheck-block", T_23);
		assertThat(dispatchDue(T_23)).extracting(DispatchResult::action).containsExactly(Action.DEFER);

		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
			recipientA, senderId, Timestamp.from(T_23.plusSeconds(1)));
		List<DispatchResult> after = dispatchDue(T_07);
		assertThat(after).hasSize(1);
		assertThat(after.get(0).action()).isEqualTo(Action.SEND);
		assertThat(after.get(0).reservation()).isNull();
		assertThat(after.get(0).context().members().get(0).dispatchContext().targetValidity()
			.blockedInEitherDirection()).isTrue();
		assertCancelledWithoutSend(deliveryId, after.get(0).groupId());
	}

	@Test
	@DisplayName("INT-021 quiet 지연 뒤 대상 만료는 새 snapshot으로 읽고 미발송을 취소한다")
	void rechecksExpiredTargetAfterQuietDeferAndCancelsWithoutSend() {
		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		long questionId = insertApprovedQuestion("recheck-target-q");
		long postId = insertDirectionPost(senderId, questionId, "recheck-target", T_23, T_07.plusSeconds(60));
		long deliveryId = insertDirectionDelivery(recipientA, deviceA, postId, "recheck-target", T_23);
		assertThat(dispatchDue(T_23)).extracting(DispatchResult::action).containsExactly(Action.DEFER);

		jdbc.update("UPDATE direction_post SET expires_at = ? WHERE id = ?",
			Timestamp.from(T_07.minusSeconds(1)), postId);
		List<DispatchResult> after = dispatchDue(T_07);
		assertThat(after).hasSize(1);
		assertThat(after.get(0).action()).isEqualTo(Action.SEND);
		assertThat(after.get(0).reservation()).isNull();
		assertThat(after.get(0).context().members().get(0).dispatchContext().targetValidity().targetValid())
			.isFalse();
		assertCancelledWithoutSend(deliveryId, after.get(0).groupId());
	}

	@Test
	@DisplayName("INT-021 quiet 지연 뒤 기기 해지는 새 snapshot으로 읽고 미발송을 취소한다")
	void rechecksRevokedDeviceAfterQuietDeferAndCancelsWithoutSend() {
		savePreferences(recipientA, true, SEOUL_QUIET, allTypesEnabled());
		long deliveryId = insertDelivery(recipientA, deviceA, NotificationType.ANSWER_RECEIVED,
			"recheck-device", T_23, null, null);
		assertThat(dispatchDue(T_23_10)).extracting(DispatchResult::action).containsExactly(Action.DEFER);

		jdbc.update("UPDATE push_device SET device_status = 'REVOKED', revoked_at = ? WHERE id = ?",
			Timestamp.from(T_23.plusSeconds(1)), deviceA);
		List<DispatchResult> after = dispatchDue(T_07);
		assertThat(after).hasSize(1);
		assertThat(after.get(0).action()).isEqualTo(Action.SEND);
		assertThat(after.get(0).reservation()).isNull();
		assertThat(after.get(0).context().members().get(0).dispatchContext().device().status())
			.isEqualTo(PushDeviceStatus.REVOKED);
		assertCancelledWithoutSend(deliveryId, after.get(0).groupId());
	}

	private List<DispatchResult> dispatchDue(Instant at) {
		collect(20, at);
		List<ClaimedPushDispatchGroup> claims = claimService.claimDueGroups(20, at, at.plus(Duration.ofDays(30)));
		List<DispatchResult> results = new ArrayList<>();
		for (ClaimedPushDispatchGroup claim : claims) {
			results.add(dispatchClaimed(claim, at));
		}
		return results;
	}

	private DispatchResult dispatchClaimed(ClaimedPushDispatchGroup claim, Instant at) {
		PushDispatchGroupContext context = claimService.loadContext(claim.groupId(), claim.generation(), at)
			.orElseThrow();
		PushSuppressionPolicy.Decision decision = suppressionPolicy.evaluate(
			context.preference(), context.group().notificationType(), at,
			context.group().policyExpiresAt(), context.lastRecommendationAttemptAt());
		if (decision.action() == Action.DEFER) {
			assertThat(claimService.deferGroup(claim.groupId(), claim.generation(), decision.nextAttemptAt(), at))
				.isTrue();
			return new DispatchResult(claim.groupId(), claim.generation(), decision.action(), decision.reason(),
				null, decision.nextAttemptAt(), context);
		}
		if (decision.action() == Action.CANCEL) {
			assertThat(claimService.cancelGroup(claim.groupId(), claim.generation(), at)).isTrue();
			return new DispatchResult(claim.groupId(), claim.generation(), decision.action(), decision.reason(),
				null, null, context);
		}
		boolean eligible = context.members().stream().anyMatch(PushDispatchSuppressionIntegrationTest::memberSendable);
		if (!eligible) {
			assertThat(claimService.cancelGroup(claim.groupId(), claim.generation(), at)).isTrue();
			return new DispatchResult(claim.groupId(), claim.generation(), Action.SEND, Reason.ELIGIBLE,
				null, null, context);
		}
		LocalDate budgetDate = budgetPolicy.budgetDate(at, context.budgetZone());
		PushBudgetReservation reservation = claimService.reserveBudget(
			claim.groupId(), claim.generation(), budgetDate, context.group().notificationType(), POLICY, at);
		if (reservation == PushBudgetReservation.LIMIT_EXCEEDED) {
			assertThat(claimService.cancelGroup(claim.groupId(), claim.generation(), at)).isTrue();
		}
		if (reservation == PushBudgetReservation.STALE_GROUP) {
			throw new AssertionError("STALE_GROUP on claimed dispatch");
		}
		return new DispatchResult(claim.groupId(), claim.generation(), Action.SEND, Reason.ELIGIBLE,
			reservation, null, context);
	}

	private PushBudgetReservation reserveConcurrently(
		CountDownLatch ready, CountDownLatch start, ClaimedPushDispatchGroup claim,
		LocalDate budgetDate, NotificationType type, Instant at) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).as("reserve started").isTrue();
		return claimService.reserveBudget(claim.groupId(), claim.generation(), budgetDate, type, POLICY, at);
	}

	private static boolean memberSendable(PushDispatchMemberContext member) {
		var context = member.dispatchContext();
		var validity = context.targetValidity();
		return context.device().status() == PushDeviceStatus.ACTIVE
			&& context.notification().status() != NotificationStatus.REVOKED
			&& validity.recipientActive()
			&& validity.actorActive()
			&& !validity.blockedInEitherDirection()
			&& validity.targetValid();
	}

	private void assertCancelledWithoutSend(long deliveryId, long groupId) {
		assertThat(groupStatus(groupId)).isEqualTo(PushDispatchGroupStatus.CANCELLED.name());
		assertThat(groupFirstAttemptedAt(groupId)).isNull();
		assertThat(deliveryStatus(deliveryId)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(notificationStatus(notificationIdOf(deliveryId))).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(budgetCount(recipientA)).isZero();
	}

	private void assertQuietUnchanged(long userId, boolean expectedPushEnabled) {
		Map<String, Object> row = jdbc.queryForMap("""
			SELECT CAST(quiet_start AS TEXT) AS quiet_start,
				CAST(quiet_end AS TEXT) AS quiet_end,
				quiet_zone_id,
				push_enabled
			FROM notification_user_setting
			WHERE user_id = ?
			""", userId);
		assertThat(row.get("quiet_start").toString()).startsWith("22:00");
		assertThat(row.get("quiet_end").toString()).startsWith("07:00");
		assertThat(row.get("quiet_zone_id")).isEqualTo("Asia/Seoul");
		assertThat(row.get("push_enabled")).isEqualTo(expectedPushEnabled);
		NotificationPreferenceSnapshot snapshot = preferences.findByUserId(userId);
		assertThat(snapshot.quietHours()).isEqualTo(SEOUL_QUIET);
		assertThat(snapshot.pushEnabled()).isEqualTo(expectedPushEnabled);
	}

	private int collect(int limit, Instant at) {
		Integer collected = transactions.execute(status -> planner.collectUngrouped(limit, at));
		return collected == null ? 0 : collected;
	}

	private void savePreferences(
		long userId, boolean pushEnabled, NotificationQuietHours quietHours,
		Map<NotificationType, Boolean> typeEnabled) {
		transactions.executeWithoutResult(status -> {
			preferences.lockUser(userId);
			preferences.saveUserSetting(userId, pushEnabled, quietHours);
			preferences.replaceTypePreferences(userId, typeEnabled);
		});
	}

	private static Map<NotificationType, Boolean> allTypesEnabled() {
		return NotificationPreferenceIntegrationFixtures.typePreferences(NotificationType.ANSWER_RECEIVED, true);
	}

	private long insertUser(String nickname, String timezone) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', ?, ?)
			RETURNING id
			""", Long.class, REGION, timezone, nickname);
	}

	private long insertDelivery(
		long recipientId, long deviceId, NotificationType type, String suffix, Instant createdAt,
		Long directionPostId, Long answerId) {
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(
			outboxAggregateType(type), 180L, outboxType(type),
			"gh180-suppress-" + suffix, "{}", createdAt));
		Notification notification = notifications.save(new Notification(
			null, recipientId, event.id(), type,
			"gh180-suppress-notification-" + suffix, directionPostId, answerId, null,
			NotificationStatus.UNREAD, createdAt, null));
		return notifications.saveDelivery(new NotificationDelivery(
			null, notification.id(), deviceId, DeliveryStatus.PENDING, 0, createdAt,
			createdAt, null, null)).id();
	}

	private long insertRecommended(
		long recipientId, long deviceId, long assignmentId, String suffix, Instant createdAt) {
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(
			OutboxAggregateType.QUESTION_ASSIGNMENT, assignmentId, OutboxEventType.QUESTION_RECOMMENDED,
			"gh180-suppress-" + suffix, "{}", createdAt));
		Notification notification = notifications.save(new Notification(
			null, recipientId, event.id(), NotificationType.QUESTION_RECOMMENDED,
			"gh180-suppress-notification-" + suffix, null, null, null,
			NotificationStatus.UNREAD, createdAt, null));
		return notifications.saveDelivery(new NotificationDelivery(
			null, notification.id(), deviceId, DeliveryStatus.PENDING, 0, createdAt,
			createdAt, null, null)).id();
	}

	private long insertApprovedQuestion(String text) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', ?, 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, text, Timestamp.from(T_23), Timestamp.from(T_23), senderId);
	}

	private long insertCycle(long userId, String key, Instant at) {
		return jdbc.queryForObject("""
			INSERT INTO question_assignment_cycle
				(user_id, cycle_key, pool_version, status, starts_at, ends_at)
			VALUES (?, ?, 'pool-v1', 'ACTIVE', ?, ?)
			RETURNING id
			""", Long.class, userId, key, Timestamp.from(at), Timestamp.from(at.plus(Duration.ofHours(1))));
	}

	private long insertAssignment(long cycleId, long questionId, int order, Instant at) {
		return jdbc.queryForObject("""
			INSERT INTO question_assignment (cycle_id, approved_question_id, display_order, assigned_at)
			VALUES (?, ?, ?, ?)
			RETURNING id
			""", Long.class, cycleId, questionId, order, Timestamp.from(at));
	}

	private long insertDirectionPost(
		long sender, long questionId, String key, Instant submittedAt, Instant expiresAt) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, sender, questionId, key, REGION,
			Timestamp.from(submittedAt), Timestamp.from(submittedAt), Timestamp.from(expiresAt));
	}

	private long insertDirectionDelivery(
		long recipientId, long deviceId, long postId, String suffix, Instant createdAt) {
		return insertDelivery(recipientId, deviceId, NotificationType.DIRECTION_POST_RECEIVED, suffix,
			createdAt, postId, null);
	}

	private static OutboxEventType outboxType(NotificationType type) {
		return switch (type) {
			case ANSWER_REACTED -> OutboxEventType.ANSWER_REACTED;
			case QUESTION_RECOMMENDED -> OutboxEventType.QUESTION_RECOMMENDED;
			case DIRECTION_POST_RECEIVED -> OutboxEventType.RECIPIENTS_CONFIRMED;
			case REPORT_RESOLVED -> OutboxEventType.REPORT_RESOLVED;
			default -> OutboxEventType.ANSWER_PUBLISHED;
		};
	}

	private static OutboxAggregateType outboxAggregateType(NotificationType type) {
		return switch (type) {
			case QUESTION_RECOMMENDED -> OutboxAggregateType.QUESTION_ASSIGNMENT;
			case DIRECTION_POST_RECEIVED -> OutboxAggregateType.DIRECTION_POST;
			case REPORT_RESOLVED -> OutboxAggregateType.REPORT;
			default -> OutboxAggregateType.ANSWER;
		};
	}

	private long notificationIdOf(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT notification_id FROM notification_delivery WHERE id = ?", Long.class, deliveryId);
	}

	private String deliveryStatus(long deliveryId) {
		return jdbc.queryForObject("SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
	}

	private String notificationStatus(long notificationId) {
		return jdbc.queryForObject("SELECT status FROM notification WHERE id = ?", String.class, notificationId);
	}

	private String groupStatus(long groupId) {
		return jdbc.queryForObject("SELECT status FROM push_dispatch_group WHERE id = ?", String.class, groupId);
	}

	private Instant groupNextAttemptAt(long groupId) {
		Timestamp timestamp = jdbc.queryForObject(
			"SELECT next_attempt_at FROM push_dispatch_group WHERE id = ?", Timestamp.class, groupId);
		return timestamp.toInstant();
	}

	private Instant groupFirstAttemptedAt(long groupId) {
		Timestamp timestamp = jdbc.queryForObject(
			"SELECT first_attempted_at FROM push_dispatch_group WHERE id = ?", Timestamp.class, groupId);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private Instant groupCompletedAt(long groupId) {
		Timestamp timestamp = jdbc.queryForObject(
			"SELECT completed_at FROM push_dispatch_group WHERE id = ?", Timestamp.class, groupId);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private Instant groupBudgetConsumedAt(long groupId) {
		Timestamp timestamp = jdbc.queryForObject(
			"SELECT budget_consumed_at FROM push_dispatch_group WHERE id = ?", Timestamp.class, groupId);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private LocalDate groupBudgetDate(long groupId) {
		java.sql.Date value = jdbc.queryForObject(
			"SELECT budget_local_date FROM push_dispatch_group WHERE id = ?", java.sql.Date.class, groupId);
		return value == null ? null : value.toLocalDate();
	}

	private long groupIdFor(long recipientId) {
		return jdbc.queryForObject(
			"SELECT id FROM push_dispatch_group WHERE recipient_id = ?", Long.class, recipientId);
	}

	private long budgetCount(long userId) {
		return jdbc.queryForObject(
			"SELECT count(*) FROM push_daily_budget WHERE user_id = ?", Long.class, userId);
	}

	private List<Integer> consumed(long userId, LocalDate budgetDate) {
		return jdbc.query("""
			SELECT consumed_total, consumed_general
			FROM push_daily_budget
			WHERE user_id = ? AND budget_date = ?
			""", (rs, row) -> List.of(rs.getInt("consumed_total"), rs.getInt("consumed_general")),
			userId, java.sql.Date.valueOf(budgetDate)).get(0);
	}

	private long countNotifications(long recipientId) {
		return jdbc.queryForObject(
			"SELECT count(*) FROM notification WHERE recipient_id = ?", Long.class, recipientId);
	}

	private record DispatchResult(
		long groupId,
		int generation,
		Action action,
		Reason reason,
		PushBudgetReservation reservation,
		Instant nextAttemptAt,
		PushDispatchGroupContext context) {
	}
}
