/**
 * Created at: 2026-08-24T23:03:32+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-INT-008 through INT-017
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import com.dnd.qello.notification.push.PushDeliveryRetryPolicy;
import com.dnd.qello.notification.push.PushDispatchEligibility;
import com.dnd.qello.notification.push.PushPayload;
import com.dnd.qello.notification.push.PushPayloadFactory;
import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.PushProviderResult;
import com.dnd.qello.notification.push.PushSendCommand;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenKeyRing;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.service.PushDeliveryClaimService;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PushDeliveryDispatchIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH179-DISPATCH";
	private static final String TOKEN_SENTINEL = "fake-gh179-dispatch-token-sentinel";
	private static final String PRIVATE_SENTINEL = "gh179-private-content-sentinel";
	private static final Instant NOW = Instant.parse("2026-08-24T14:00:00Z");
	private static final Duration LEASE = Duration.ofSeconds(60);
	private static final byte[] ENCRYPTION_KEY = fixedKey((byte)0x31);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte)0x51);

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private PushDeliveryClaimService claims;

	private AesGcmPushTokenProtector realProtector;
	private int sequence;

	@BeforeEach
	void resetFixtures() {
		cleanupFixtures();
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH179 Dispatch Test', 'REGION')
			""", REGION);
		realProtector = new AesGcmPushTokenProtector(new PushTokenKeyRing(
			"gh179-dispatch-key", Map.of("gh179-dispatch-key", ENCRYPTION_KEY), FINGERPRINT_KEY));
		sequence = 0;
	}

	@AfterEach
	void cleanupAfterTest() {
		jdbc.execute("DROP TRIGGER IF EXISTS gh179_dispatch_cancel_failure ON notification_delivery");
		jdbc.execute("DROP FUNCTION IF EXISTS gh179_dispatch_reject_cancel()");
		cleanupFixtures();
	}

	@Test
	@DisplayName("INT-008/015 eligible delivery는 복호화 후 transaction 밖에서 provider를 호출하고 allowlist payload로 SENT 처리한다")
	void sendsEligibleDeliveryWithPrivateAllowlistedPayloadOutsideTransaction() {
		long recipientId = account("accepted-recipient");
		long deviceId = device(recipientId, "accepted-device");
		long deliveryId = targetlessDelivery(recipientId, deviceId, "accepted", DeliveryStatus.PENDING, 0, NOW);
		CountingProtector protector = new CountingProtector(realProtector);
		ScriptedProvider provider = new ScriptedProvider(new PushProviderResult.Accepted());

		PushDeliveryDispatchWorker.BatchResult result = worker(provider, protector).dispatchBatch(command(10, NOW));

		assertThat(result.claimed()).isEqualTo(1);
		assertThat(result.outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.deliveryId()).isEqualTo(deliveryId);
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.SENT);
			assertThat(outcome.safeReasonCode()).isEqualTo("ACCEPTED");
		});
		assertThat(protector.decryptCalls()).isEqualTo(1);
		assertThat(provider.calls()).isEqualTo(1);
		assertThat(provider.transactionObserved()).isFalse();
		assertThat(provider.payloads()).singleElement().satisfies(data -> {
			assertThat(data).containsOnlyKeys("type", "count", "hasRemainingTime");
			assertThat(data).containsEntry("count", "1");
			assertThat(data.toString()).doesNotContain(TOKEN_SENTINEL, PRIVATE_SENTINEL,
				Long.toString(deliveryId), "notificationId", "title", "body");
		});
		assertThat(status(deliveryId)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(sentAt(deliveryId)).isEqualTo(NOW);
		assertThat(notificationStatus(deliveryId)).isEqualTo(NotificationStatus.UNREAD.name());
	}

	@Test
	@DisplayName("INT-009 retryable 결과는 due 전 claim되지 않고 due 뒤 재발송되어 SENT가 된다")
	void retriesOnlyWhenFailedDeliveryBecomesDue() {
		long recipientId = account("retry-recipient");
		long deviceId = device(recipientId, "retry-device");
		long deliveryId = targetlessDelivery(recipientId, deviceId, "retry", DeliveryStatus.PENDING, 0, NOW);
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.RetryableFailure(Duration.ofSeconds(20)),
			new PushProviderResult.Accepted());
		PushDeliveryDispatchWorker worker = worker(provider, new CountingProtector(realProtector));

		PushDeliveryDispatchWorker.BatchResult first = worker.dispatchBatch(command(10, NOW));
		PushDeliveryDispatchWorker.BatchResult early = worker.dispatchBatch(command(10, NOW.plusSeconds(19)));
		PushDeliveryDispatchWorker.BatchResult due = worker.dispatchBatch(command(10, NOW.plusSeconds(20)));

		assertThat(first.outcomes()).singleElement().satisfies(outcome ->
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.RETRY_SCHEDULED));
		assertThat(early.claimed()).isZero();
		assertThat(due.outcomes()).singleElement().satisfies(outcome ->
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.SENT));
		assertThat(provider.calls()).isEqualTo(2);
		assertThat(status(deliveryId)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(attemptCount(deliveryId)).isEqualTo(2);
		assertThat(notificationStatus(deliveryId)).isEqualTo(NotificationStatus.UNREAD.name());
	}

	@Test
	@DisplayName("INT-010 permanent failure와 max-attempt retryable failure는 fenced DEAD로 종결되고 다시 claim되지 않는다")
	void makesPermanentAndMaxAttemptFailuresDead() {
		long recipientId = account("dead-recipient");
		long permanentDevice = device(recipientId, "permanent-device");
		long maxAttemptDevice = device(recipientId, "max-attempt-device");
		long permanentId = targetlessDelivery(
			recipientId, permanentDevice, "permanent", DeliveryStatus.PENDING, 0, NOW);
		long maxAttemptId = targetlessDelivery(
			recipientId, maxAttemptDevice, "max-attempt", DeliveryStatus.FAILED, 2, NOW);
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.PermanentFailure("PROVIDER_REJECTED"),
			new PushProviderResult.RetryableFailure(Duration.ofSeconds(5)));

		PushDeliveryDispatchWorker.BatchResult result = worker(provider, new CountingProtector(realProtector))
			.dispatchBatch(command(10, NOW));
		PushDeliveryDispatchWorker.BatchResult repeated = worker(provider, new CountingProtector(realProtector))
			.dispatchBatch(command(10, NOW.plusSeconds(120)));

		assertThat(result.outcomes()).extracting(PushDeliveryDispatchWorker.DeliveryOutcome::outcome)
			.containsExactly(PushDeliveryDispatchWorker.Outcome.DEAD, PushDeliveryDispatchWorker.Outcome.DEAD);
		assertThat(status(permanentId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(status(maxAttemptId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(attemptCount(maxAttemptId)).isEqualTo(3);
		assertThat(repeated.claimed()).isZero();
		assertThat(provider.calls()).isEqualTo(2);
	}

	@Test
	@DisplayName("INT-011 invalid token은 현재 claim DEAD, device INVALID, 같은 device PENDING/FAILED 취소를 원자적으로 반영한다")
	void invalidTokenAtomicallyInvalidatesDeviceAndCancelsSiblings() {
		long recipientId = account("invalid-recipient");
		long deviceId = device(recipientId, "invalid-device");
		long currentId = targetlessDelivery(recipientId, deviceId, "invalid-current", DeliveryStatus.PENDING, 0, NOW);
		long pendingSibling = targetlessDelivery(
			recipientId, deviceId, "invalid-pending", DeliveryStatus.PENDING, 0, NOW.plusSeconds(1));
		long failedSibling = targetlessDelivery(
			recipientId, deviceId, "invalid-failed", DeliveryStatus.FAILED, 1, NOW.plusSeconds(1));
		ScriptedProvider provider = new ScriptedProvider(new PushProviderResult.InvalidToken());

		PushDeliveryDispatchWorker.BatchResult result = worker(provider, new CountingProtector(realProtector))
			.dispatchBatch(command(1, NOW));

		assertThat(result.outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.DEAD);
			assertThat(outcome.safeReasonCode()).isEqualTo("INVALID_TOKEN");
		});
		assertThat(status(currentId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(status(pendingSibling)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(status(failedSibling)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(deviceStatus(deviceId)).isEqualTo(PushDeviceStatus.INVALID.name());
		assertThat(notificationStatus(currentId)).isEqualTo(NotificationStatus.UNREAD.name());
	}

	@Test
	@DisplayName("INT-011 invalid-token sibling 취소 실패는 device/current 변경을 rollback하고 현재 claim을 안전한 retry로 기록한 뒤 batch를 계속한다")
	void rollsBackInvalidTokenTransactionAndContinuesAfterTerminalFailure() {
		long recipientId = account("invalid-rollback-recipient");
		long failingDeviceId = device(recipientId, "invalid-rollback-device");
		long succeedingDeviceId = device(recipientId, "after-rollback-device");
		long failingId = targetlessDelivery(
			recipientId, failingDeviceId, "invalid-rollback-current", DeliveryStatus.PENDING, 0, NOW);
		long siblingId = targetlessDelivery(
			recipientId, failingDeviceId, "invalid-rollback-sibling", DeliveryStatus.PENDING, 0, NOW.plusSeconds(1));
		long succeedingId = targetlessDelivery(
			recipientId, succeedingDeviceId, "after-rollback", DeliveryStatus.PENDING, 0, NOW);
		installCancellationFailureTrigger(failingDeviceId);
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.InvalidToken(), new PushProviderResult.Accepted());

		PushDeliveryDispatchWorker.BatchResult result = worker(provider, new CountingProtector(realProtector))
			.dispatchBatch(command(2, NOW));

		assertThat(result.outcomes()).extracting(PushDeliveryDispatchWorker.DeliveryOutcome::outcome)
			.containsExactly(
				PushDeliveryDispatchWorker.Outcome.RETRY_SCHEDULED,
				PushDeliveryDispatchWorker.Outcome.SENT);
		assertThat(status(failingId)).isEqualTo(DeliveryStatus.FAILED.name());
		assertThat(status(siblingId)).isEqualTo(DeliveryStatus.PENDING.name());
		assertThat(deviceStatus(failingDeviceId)).isEqualTo(PushDeviceStatus.ACTIVE.name());
		assertThat(status(succeedingId)).isEqualTo(DeliveryStatus.SENT.name());
	}

	@Test
	@DisplayName("INT-012/014 preference, device, notification, target 변경은 복호화/provider 호출 없이 fenced CANCELLED로 종결된다")
	void cancelsIneligibleSnapshotsBeforeDecryptionOrProviderCall() {
		long globalOffRecipient = account("global-off-recipient");
		long typeOffRecipient = account("type-off-recipient");
		long inactiveDeviceRecipient = account("inactive-device-recipient");
		long revokedNotificationRecipient = account("revoked-notification-recipient");
		long expiredTargetRecipient = account("expired-target-recipient");
		long actorId = account("expired-target-actor");

		long globalOff = targetlessDelivery(globalOffRecipient, device(globalOffRecipient, "global-off-device"),
			"global-off", DeliveryStatus.PENDING, 0, NOW);
		long typeOff = targetlessDelivery(typeOffRecipient, device(typeOffRecipient, "type-off-device"),
			"type-off", DeliveryStatus.PENDING, 0, NOW);
		long inactiveDeviceId = device(inactiveDeviceRecipient, "inactive-device");
		long inactiveDevice = targetlessDelivery(
			inactiveDeviceRecipient, inactiveDeviceId, "inactive-device", DeliveryStatus.PENDING, 0, NOW);
		long revokedNotification = targetlessDelivery(revokedNotificationRecipient,
			device(revokedNotificationRecipient, "revoked-notification-device"),
			"revoked-notification", DeliveryStatus.PENDING, 0, NOW);
		long postId = directionPost(actorId, "expired-target", NOW.plusSeconds(600));
		long expiredTarget = directionPostDelivery(expiredTargetRecipient,
			device(expiredTargetRecipient, "expired-target-device"), postId, "expired-target", NOW);

		jdbc.update("INSERT INTO notification_user_setting (user_id, push_enabled) VALUES (?, FALSE)",
			globalOffRecipient);
		jdbc.update("""
			INSERT INTO notification_preference (notification_type, user_id, enabled)
			VALUES ('QUESTION_PROPOSAL_REVIEWED', ?, FALSE)
			""", typeOffRecipient);
		jdbc.update("UPDATE push_device SET device_status = 'INVALID' WHERE id = ?", inactiveDeviceId);
		jdbc.update("""
			UPDATE notification SET status = 'REVOKED', read_at = NULL
			WHERE id = (SELECT notification_id FROM notification_delivery WHERE id = ?)
			""", revokedNotification);
		jdbc.update("UPDATE direction_post SET status = 'EXPIRED' WHERE id = ?", postId);
		CountingProtector protector = new CountingProtector(realProtector);
		ScriptedProvider provider = new ScriptedProvider();

		PushDeliveryDispatchWorker.BatchResult result = worker(provider, protector).dispatchBatch(command(10, NOW));

		assertThat(result.outcomes()).hasSize(5).allSatisfy(outcome ->
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.CANCELLED));
		assertThat(List.of(globalOff, typeOff, inactiveDevice, revokedNotification, expiredTarget))
			.allSatisfy(id -> assertThat(status(id)).isEqualTo(DeliveryStatus.CANCELLED.name()));
		assertThat(protector.decryptCalls()).isZero();
		assertThat(provider.calls()).isZero();
	}

	@Test
	@DisplayName("INT-013 actor-recipient 어느 방향의 활성 block도 복호화/provider 호출 없이 CANCELLED 처리한다")
	void cancelsBothBlockDirectionsBeforeProviderCall() {
		long firstRecipient = account("blocked-recipient-first");
		long firstActor = account("blocked-actor-first");
		long secondRecipient = account("blocked-recipient-second");
		long secondActor = account("blocked-actor-second");
		long firstDelivery = directionPostDelivery(firstRecipient, device(firstRecipient, "blocked-device-first"),
			directionPost(firstActor, "blocked-post-first", NOW.plusSeconds(600)), "blocked-first", NOW);
		long secondDelivery = directionPostDelivery(secondRecipient, device(secondRecipient, "blocked-device-second"),
			directionPost(secondActor, "blocked-post-second", NOW.plusSeconds(600)), "blocked-second", NOW);
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
			firstRecipient, firstActor, Timestamp.from(NOW));
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
			secondActor, secondRecipient, Timestamp.from(NOW));
		CountingProtector protector = new CountingProtector(realProtector);
		ScriptedProvider provider = new ScriptedProvider();

		PushDeliveryDispatchWorker.BatchResult result = worker(provider, protector).dispatchBatch(command(10, NOW));

		assertThat(result.outcomes()).extracting(PushDeliveryDispatchWorker.DeliveryOutcome::safeReasonCode)
			.containsExactly("BLOCKED_ACTOR", "BLOCKED_ACTOR");
		assertThat(status(firstDelivery)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(status(secondDelivery)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(protector.decryptCalls()).isZero();
		assertThat(provider.calls()).isZero();
	}

	@Test
	@DisplayName("INT-016 blocking provider 동안 worker는 DB transaction/lock을 유지하지 않아 별도 connection update가 완료된다")
	void holdsNoDatabaseTransactionOrLockDuringProviderCall() throws Exception {
		long recipientId = account("lock-recipient");
		long deviceId = device(recipientId, "lock-device");
		long deliveryId = targetlessDelivery(recipientId, deviceId, "lock", DeliveryStatus.PENDING, 0, NOW);
		BlockingProvider provider = new BlockingProvider();
		PushDeliveryDispatchWorker worker = worker(provider, new CountingProtector(realProtector));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<PushDeliveryDispatchWorker.BatchResult> dispatch = executor.submit(
				() -> worker.dispatchBatch(command(10, NOW)));
			assertThat(provider.entered().await(10, TimeUnit.SECONDS)).as("provider entered").isTrue();
			Future<Integer> update = executor.submit(() -> jdbc.update(
				"UPDATE push_device SET last_seen_at = ? WHERE id = ?", Timestamp.from(NOW.plusSeconds(1)), deviceId));

			assertThat(update.get(3, TimeUnit.SECONDS)).isEqualTo(1);
			assertThat(provider.transactionObserved()).isFalse();
			provider.release().countDown();
			assertThat(dispatch.get(10, TimeUnit.SECONDS).outcomes()).singleElement().satisfies(outcome ->
				assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.SENT));
		} finally {
			provider.release().countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("executor terminated").isTrue();
		}
		assertThat(status(deliveryId)).isEqualTo(DeliveryStatus.SENT.name());
	}

	@Test
	@DisplayName("INT-017 decrypt/provider 실패와 success·invalid·policy cancel 혼합 batch는 건별 실패를 격리하고 안전한 reason만 기록한다")
	void isolatesMixedBatchFailuresAndDoesNotLogSensitiveFailureText(CapturedOutput output) {
		long recipientId = account("mixed-recipient");
		long brokenDevice = malformedDevice(recipientId, "mixed-broken-device");
		long providerFailureDevice = device(recipientId, "mixed-provider-failure-device");
		long acceptedDevice = device(recipientId, "mixed-accepted-device");
		long invalidDevice = device(recipientId, "mixed-invalid-device");
		long cancelledRecipient = account("mixed-cancelled-recipient");
		long cancelledDevice = device(cancelledRecipient, "mixed-cancelled-device");
		long brokenId = targetlessDelivery(recipientId, brokenDevice, "mixed-broken", DeliveryStatus.PENDING, 0, NOW);
		long providerFailureId = targetlessDelivery(
			recipientId, providerFailureDevice, "mixed-provider-failure", DeliveryStatus.PENDING, 0, NOW);
		long acceptedId = targetlessDelivery(
			recipientId, acceptedDevice, "mixed-accepted", DeliveryStatus.PENDING, 0, NOW);
		long invalidId = targetlessDelivery(
			recipientId, invalidDevice, "mixed-invalid", DeliveryStatus.PENDING, 0, NOW);
		long cancelledId = targetlessDelivery(
			cancelledRecipient, cancelledDevice, "mixed-cancelled", DeliveryStatus.PENDING, 0, NOW);
		jdbc.update("INSERT INTO notification_user_setting (user_id, push_enabled) VALUES (?, FALSE)",
			cancelledRecipient);
		ScriptedProvider provider = new ScriptedProvider(
			new IllegalStateException(TOKEN_SENTINEL + PRIVATE_SENTINEL),
			new PushProviderResult.Accepted(),
			new PushProviderResult.InvalidToken());

		PushDeliveryDispatchWorker.BatchResult result = worker(provider, new CountingProtector(realProtector))
			.dispatchBatch(command(10, NOW));

		assertThat(result.outcomes()).extracting(PushDeliveryDispatchWorker.DeliveryOutcome::outcome)
			.containsExactly(
				PushDeliveryDispatchWorker.Outcome.DEAD,
				PushDeliveryDispatchWorker.Outcome.RETRY_SCHEDULED,
				PushDeliveryDispatchWorker.Outcome.SENT,
				PushDeliveryDispatchWorker.Outcome.DEAD,
				PushDeliveryDispatchWorker.Outcome.CANCELLED);
		assertThat(status(brokenId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(status(providerFailureId)).isEqualTo(DeliveryStatus.FAILED.name());
		assertThat(status(acceptedId)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(status(invalidId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(status(cancelledId)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(provider.calls()).isEqualTo(3);
		assertThat(output.getOut()).doesNotContain(TOKEN_SENTINEL, PRIVATE_SENTINEL);
		assertThat(output.getErr()).doesNotContain(TOKEN_SENTINEL, PRIVATE_SENTINEL);
	}

	private PushDeliveryDispatchWorker worker(PushProvider provider, PushTokenProtector protector) {
		return new PushDeliveryDispatchWorker(
			claims,
			new PushDispatchEligibility(),
			new PushPayloadFactory(),
			new PushDeliveryRetryPolicy(3, Duration.ofSeconds(10), Duration.ofSeconds(60)),
			protector,
			provider);
	}

	private static PushDeliveryDispatchWorker.BatchCommand command(int batchSize, Instant at) {
		return new PushDeliveryDispatchWorker.BatchCommand(batchSize, at, at.plus(LEASE));
	}

	private long account(String suffix) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, "gh179-dispatch-" + suffix + "-" + (++sequence));
	}

	private long device(long userId, String suffix) {
		ProtectedPushToken protectedToken = realProtector.protect(PushToken.of(TOKEN_SENTINEL + "-" + suffix));
		return notifications.saveDevice(new PushDevice(
			null, userId, PushPlatform.ANDROID, protectedToken.envelope(), protectedToken.fingerprint(),
			PushDeviceStatus.ACTIVE, NOW, null)).id();
	}

	private long malformedDevice(long userId, String suffix) {
		return notifications.saveDevice(new PushDevice(
			null, userId, PushPlatform.ANDROID, new byte[] {1, 2, 3}, "fp-" + suffix,
			PushDeviceStatus.ACTIVE, NOW, null)).id();
	}

	private long targetlessDelivery(long recipientId, long deviceId, String suffix,
		DeliveryStatus status, int attemptCount, Instant nextAttemptAt) {
		Notification notification = notification(recipientId, NotificationType.QUESTION_PROPOSAL_REVIEWED,
			suffix, null);
		return delivery(notification.id(), deviceId, status, attemptCount, nextAttemptAt);
	}

	private long directionPostDelivery(long recipientId, long deviceId, long postId, String suffix, Instant at) {
		Notification notification = notification(recipientId, NotificationType.DIRECTION_POST_RECEIVED, suffix, postId);
		return delivery(notification.id(), deviceId, DeliveryStatus.PENDING, 0, at);
	}

	private Notification notification(long recipientId, NotificationType type, String suffix, Long directionPostId) {
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(
			OutboxAggregateType.ANSWER, 179L, OutboxEventType.ANSWER_PUBLISHED,
			"gh179-dispatch-outbox-" + suffix + "-" + (++sequence), "{}", NOW));
		return notifications.save(new Notification(
			null, recipientId, event.id(), type, "gh179-dispatch-notification-" + suffix + "-" + sequence,
			directionPostId, null, null, NotificationStatus.UNREAD, NOW, null));
	}

	private long delivery(long notificationId, long deviceId,
		DeliveryStatus status, int attemptCount, Instant nextAttemptAt) {
		return notifications.saveDelivery(new NotificationDelivery(
			null, notificationId, deviceId, status, attemptCount, nextAttemptAt, NOW, null, null)).id();
	}

	private long directionPost(long actorId, String suffix, Instant expiresAt) {
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', ?, 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, "GH179 Dispatch question " + suffix,
			Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.minusSeconds(120)), actorId);
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, ?, ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, actorId, questionId, "gh179-dispatch-post-" + suffix + "-" + (++sequence),
			PRIVATE_SENTINEL, REGION, Timestamp.from(NOW.minusSeconds(60)),
			Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(expiresAt));
	}

	private void installCancellationFailureTrigger(long deviceId) {
		jdbc.execute("""
			CREATE OR REPLACE FUNCTION gh179_dispatch_reject_cancel()
			RETURNS TRIGGER LANGUAGE plpgsql AS $$
			BEGIN
				IF NEW.push_device_id = %d AND OLD.status IN ('PENDING', 'FAILED')
					AND NEW.status = 'CANCELLED' THEN
					RAISE EXCEPTION 'GH179_SAFE_CANCEL_FAILURE';
				END IF;
				RETURN NEW;
			END
			$$
			""".formatted(deviceId));
		jdbc.execute("""
			CREATE TRIGGER gh179_dispatch_cancel_failure
			BEFORE UPDATE ON notification_delivery
			FOR EACH ROW EXECUTE FUNCTION gh179_dispatch_reject_cancel()
			""");
	}

	private String status(long deliveryId) {
		return jdbc.queryForObject("SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
	}

	private int attemptCount(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT attempt_count FROM notification_delivery WHERE id = ?", Integer.class, deliveryId);
	}

	private Instant sentAt(long deliveryId) {
		Timestamp value = jdbc.queryForObject(
			"SELECT sent_at FROM notification_delivery WHERE id = ?", Timestamp.class, deliveryId);
		return value == null ? null : value.toInstant();
	}

	private String notificationStatus(long deliveryId) {
		return jdbc.queryForObject("""
			SELECT n.status FROM notification n
			JOIN notification_delivery nd ON nd.notification_id = n.id
			WHERE nd.id = ?
			""", String.class, deliveryId);
	}

	private String deviceStatus(long deviceId) {
		return jdbc.queryForObject(
			"SELECT device_status FROM push_device WHERE id = ?", String.class, deviceId);
	}

	private void cleanupFixtures() {
		jdbc.update("""
			DELETE FROM notification_delivery
			WHERE notification_id IN (
				SELECT id FROM notification WHERE recipient_id IN (
					SELECT id FROM user_account WHERE coarse_region_code = ?))
			   OR push_device_id IN (
				SELECT id FROM push_device WHERE user_id IN (
					SELECT id FROM user_account WHERE coarse_region_code = ?))
			""", REGION, REGION);
		jdbc.update("DELETE FROM notification WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM push_device WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM outbox_event WHERE dedup_key LIKE 'gh179-dispatch-%'");
		jdbc.update("""
			DELETE FROM user_block WHERE blocker_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR blocked_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM approved_question WHERE question_text LIKE 'GH179 Dispatch question %'");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
	}

	private static byte[] fixedKey(byte value) {
		byte[] key = new byte[32];
		java.util.Arrays.fill(key, value);
		return key;
	}

	private static final class CountingProtector implements PushTokenProtector {

		private final PushTokenProtector delegate;
		private final AtomicInteger decryptCalls = new AtomicInteger();

		private CountingProtector(PushTokenProtector delegate) {
			this.delegate = delegate;
		}

		@Override
		public ProtectedPushToken protect(PushToken token) {
			return delegate.protect(token);
		}

		@Override
		public PushToken decrypt(byte[] envelope) {
			decryptCalls.incrementAndGet();
			return delegate.decrypt(envelope);
		}

		@Override
		public String fingerprint(PushToken token) {
			return delegate.fingerprint(token);
		}

		private int decryptCalls() {
			return decryptCalls.get();
		}
	}

	private static final class ScriptedProvider implements PushProvider {

		private final Deque<Object> results = new ArrayDeque<>();
		private final List<Map<String, String>> payloads = new ArrayList<>();
		private final AtomicInteger calls = new AtomicInteger();
		private final AtomicBoolean transactionObserved = new AtomicBoolean();

		private ScriptedProvider(Object... results) {
			this.results.addAll(List.of(results));
		}

		@Override
		public synchronized PushProviderResult send(PushSendCommand command) {
			calls.incrementAndGet();
			transactionObserved.compareAndSet(false, TransactionSynchronizationManager.isActualTransactionActive());
			PushPayload payload = command.payload();
			payloads.add(Map.copyOf(payload.asData()));
			Object next = results.isEmpty() ? new PushProviderResult.Accepted() : results.removeFirst();
			if (next instanceof RuntimeException failure) {
				throw failure;
			}
			return (PushProviderResult)next;
		}

		private int calls() {
			return calls.get();
		}

		private boolean transactionObserved() {
			return transactionObserved.get();
		}

		private List<Map<String, String>> payloads() {
			return List.copyOf(payloads);
		}
	}

	private static final class BlockingProvider implements PushProvider {

		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicBoolean transactionObserved = new AtomicBoolean();

		@Override
		public PushProviderResult send(PushSendCommand command) {
			transactionObserved.set(TransactionSynchronizationManager.isActualTransactionActive());
			entered.countDown();
			try {
				if (!release.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("provider release timeout");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("provider interrupted");
			}
			return new PushProviderResult.Accepted();
		}

		private CountDownLatch entered() {
			return entered;
		}

		private CountDownLatch release() {
			return release;
		}

		private boolean transactionObserved() {
			return transactionObserved.get();
		}
	}
}
