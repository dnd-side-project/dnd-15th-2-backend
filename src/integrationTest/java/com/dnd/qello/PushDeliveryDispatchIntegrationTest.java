/**
 * Created at: 2026-08-24T23:03:32+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-INT-008 through INT-017,
 * TEST-PLAN-GH-179-PUSH-DELIVERY-INT-007 (dispatch 경계의 lease 재확인),
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-012,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-013,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-014,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-015,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-017
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

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
import com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenKeyRing;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.config.PushPolicyProperties;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.push.policy.PushSuppressionPolicy;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.repository.PushDispatchGroupRepository;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.notification.service.PushDispatchGroupClaimService;
import com.dnd.qello.notification.service.PushDispatchGroupPlanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(properties = {
	"qello.notification.push.policy.bundle-window=PT10M",
	"qello.notification.push.policy.max-delay=PT8H",
	"qello.notification.push.policy.daily-limit=5",
	"qello.notification.push.policy.direction-reserved=2",
	"qello.notification.push.policy.recommendation-min-interval=PT24H"
})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PushDeliveryDispatchIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH179-DISPATCH";
	private static final String TOKEN_SENTINEL = "fake-gh179-dispatch-token-sentinel";
	private static final String PRIVATE_SENTINEL = "gh179-private-content-sentinel";
	private static final Instant NOW = Instant.parse("2026-08-24T14:00:00Z");
	private static final Instant WINDOW_END = NOW.plus(Duration.ofMinutes(10));
	private static final Duration LEASE = Duration.ofSeconds(60);
	private static final PushPolicyProperties POLICY = new PushPolicyProperties(
		Duration.parse("PT10M"), Duration.parse("PT8H"), 5, 2, Duration.parse("PT24H"));
	private static final byte[] ENCRYPTION_KEY = fixedKey((byte)0x31);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte)0x51);

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private PushDispatchGroupRepository groups;
	@Autowired
	private PushDispatchGroupClaimService groupClaims;
	@Autowired
	private TransactionTemplate transactions;

	private AesGcmPushTokenProtector realProtector;
	private MutableClock clock;
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
		clock = new MutableClock(NOW, ZoneOffset.UTC);
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
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.Accepted("projects/test/messages/accepted-delivery"));

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
		assertThat(providerMessageId(deliveryId)).isEqualTo("projects/test/messages/accepted-delivery");
		assertThat(notificationStatus(deliveryId)).isEqualTo(NotificationStatus.UNREAD.name());
	}

	@Test
	@DisplayName("INT-008/015 real FCM wire boundary는 path/auth/data allowlist를 보내고 success name을 provider_message_id로 저장한다")
	void sendsThroughRealFcmAdapterAndPersistsSuccessName() throws Exception {
		long recipientId = account("wire-recipient");
		long deviceId = device(recipientId, "wire-device");
		long deliveryId = targetlessDelivery(recipientId, deviceId, "wire", DeliveryStatus.PENDING, 0, NOW);
		try (WireFcmServer server = new WireFcmServer(
			"{\"name\":\"projects/test-project/messages/test-message-wire-179\"}")) {
			server.start();
			FcmHttpV1PushProvider realProvider = new FcmHttpV1PushProvider(
				restClient(server.baseUrl()),
				() -> "test-only-bearer",
				new ObjectMapper(),
				"test-project");

			PushDeliveryDispatchWorker.BatchResult result = worker(realProvider, realProtector)
				.dispatchBatch(command(10, NOW));

			assertThat(result.outcomes()).singleElement().satisfies(outcome ->
				assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.SENT));
			assertThat(status(deliveryId)).isEqualTo(DeliveryStatus.SENT.name());
			assertThat(providerMessageId(deliveryId))
				.isEqualTo("projects/test-project/messages/test-message-wire-179");
			assertThat(server.requestPath()).isEqualTo("/v1/projects/test-project/messages:send");
			assertThat(server.requestHeader("Authorization")).isEqualTo("Bearer test-only-bearer");
			JsonNode message = new ObjectMapper().readTree(server.requestBody()).path("message");
			assertThat(message.fieldNames()).toIterable().containsExactlyInAnyOrder("token", "data");
			assertThat(message.path("token").asText()).isEqualTo(TOKEN_SENTINEL + "-wire-device");
			assertThat(message.path("data").fieldNames()).toIterable()
				.containsExactlyInAnyOrder("type", "count", "hasRemainingTime");
			assertThat(message.path("data").path("type").asText()).isEqualTo("QUESTION_PROPOSAL_REVIEWED");
			assertThat(message.path("data").path("count").asText()).isEqualTo("1");
			assertThat(message.path("data").path("hasRemainingTime").asText()).isEqualTo("false");
		}
	}

	@Test
	@DisplayName("INT-009 retryable 결과는 due 전 claim되지 않고 due 뒤 재발송되어 SENT가 된다")
	void retriesOnlyWhenFailedDeliveryBecomesDue() {
		long recipientId = account("retry-recipient");
		long deviceId = device(recipientId, "retry-device");
		long deliveryId = targetlessDelivery(recipientId, deviceId, "retry", DeliveryStatus.PENDING, 0, NOW);
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.RetryableFailure(Duration.ofSeconds(20)),
			new PushProviderResult.Accepted("projects/test/messages/retry-accepted"));
		PushDeliveryDispatchWorker worker = worker(provider, new CountingProtector(realProtector));

		PushDeliveryDispatchWorker.BatchResult first = worker.dispatchBatch(command(10, NOW));
		clock.set(NOW.plusSeconds(19));
		PushDeliveryDispatchWorker.BatchResult early = worker.dispatchBatch(command(10, NOW.plusSeconds(19)));
		clock.set(NOW.plusSeconds(20));
		PushDeliveryDispatchWorker.BatchResult due = worker.dispatchBatch(command(10, NOW.plusSeconds(20)));

		assertThat(first.outcomes()).singleElement().satisfies(outcome ->
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.RETRY_SCHEDULED));
		assertThat(early.claimed()).isZero();
		assertThat(due.outcomes()).singleElement().satisfies(outcome ->
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.SENT));
		assertThat(provider.calls()).isEqualTo(2);
		assertThat(status(deliveryId)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(attemptCount(deliveryId)).isEqualTo(2);
		assertThat(providerMessageId(deliveryId)).isEqualTo("projects/test/messages/retry-accepted");
		assertThat(notificationStatus(deliveryId)).isEqualTo(NotificationStatus.UNREAD.name());
	}

	@Test
	@DisplayName("INT-009/014 delayed provider failure uses completion time for retry and fresh time for the next delivery eligibility")
	void usesFreshTimeAfterDelayedProviderAndBeforeEachDeliveryEligibility() {
		long retryRecipientId = account("fresh-time-retry-recipient");
		long retryDeviceId = device(retryRecipientId, "fresh-time-retry-device");
		long retryDeliveryId = targetlessDelivery(
			retryRecipientId, retryDeviceId, "fresh-time-retry", DeliveryStatus.PENDING, 0, NOW);
		long targetRecipientId = account("fresh-time-target-recipient");
		long actorId = account("fresh-time-target-actor");
		long expiringPostId = directionPost(actorId, "fresh-time-target", NOW.plusSeconds(20));
		long targetDeliveryId = directionPostDelivery(
			targetRecipientId,
			device(targetRecipientId, "fresh-time-target-device"),
			expiringPostId,
			"fresh-time-target",
			NOW);
		AtomicInteger providerCalls = new AtomicInteger();
		PushProvider delayedFailure = command -> {
			providerCalls.incrementAndGet();
			clock.advance(Duration.ofSeconds(30));
			throw new IllegalStateException("SAFE_DELAYED_PROVIDER_FAILURE");
		};

		PushDeliveryDispatchWorker.BatchResult result = worker(delayedFailure, realProtector)
			.dispatchBatch(new PushDeliveryDispatchWorker.BatchCommand(10, NOW, NOW.plusSeconds(90)));

		assertThat(result.outcomes()).extracting(PushDeliveryDispatchWorker.DeliveryOutcome::outcome)
			.containsExactly(
				PushDeliveryDispatchWorker.Outcome.RETRY_SCHEDULED,
				PushDeliveryDispatchWorker.Outcome.CANCELLED);
		assertThat(nextAttemptAt(retryDeliveryId)).isEqualTo(NOW.plusSeconds(40));
		assertThat(providerMessageId(retryDeliveryId)).isNull();
		assertThat(status(targetDeliveryId)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(providerMessageId(targetDeliveryId)).isNull();
		assertThat(providerCalls).hasValue(1);
	}

	@Test
	@DisplayName("INT-017 decrypt failure uses a fresh failure time for fenced terminal completion")
	void usesFreshTimeAfterDecryptFailure() {
		long recipientId = account("decrypt-time-recipient");
		long deviceId = malformedDevice(recipientId, "decrypt-time-device");
		long deliveryId = targetlessDelivery(
			recipientId, deviceId, "decrypt-time", DeliveryStatus.PENDING, 0, NOW);
		PushTokenProtector delayedFailureProtector = new AdvancingDecryptProtector(
			realProtector, clock, Duration.ofSeconds(15));

		PushDeliveryDispatchWorker.BatchResult result = worker(new ScriptedProvider(), delayedFailureProtector)
			.dispatchBatch(command(10, NOW));

		assertThat(result.outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.DEAD);
			assertThat(outcome.safeReasonCode()).isEqualTo("TOKEN_DECRYPTION_FAILED");
		});
		assertThat(nextAttemptAt(deliveryId)).isEqualTo(NOW.plusSeconds(15));
		assertThat(providerMessageId(deliveryId)).isNull();
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
		clock.set(NOW.plusSeconds(120));
		PushDeliveryDispatchWorker.BatchResult repeated = worker(provider, new CountingProtector(realProtector))
			.dispatchBatch(command(10, NOW.plusSeconds(120)));

		assertThat(result.outcomes()).extracting(PushDeliveryDispatchWorker.DeliveryOutcome::outcome)
			.containsExactly(PushDeliveryDispatchWorker.Outcome.DEAD, PushDeliveryDispatchWorker.Outcome.DEAD);
		assertThat(status(permanentId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(status(maxAttemptId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(providerMessageId(permanentId)).isNull();
		assertThat(providerMessageId(maxAttemptId)).isNull();
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
	@DisplayName("INT-011 발송 도중 해지된 device의 invalid token도 현재 claim을 종결하고 사용자의 REVOKED 상태를 유지한다")
	void completesInvalidTokenClaimWhenDeviceWasRevokedDuringSend() {
		long recipientId = account("invalid-revoked-recipient");
		long deviceId = device(recipientId, "invalid-revoked-device");
		long currentId = targetlessDelivery(
			recipientId, deviceId, "invalid-revoked", DeliveryStatus.PENDING, 0, NOW);
		PushProvider revokingProvider = command -> {
			jdbc.update("UPDATE push_device SET device_status = 'REVOKED', revoked_at = ? WHERE id = ?",
				Timestamp.from(NOW), deviceId);
			return new PushProviderResult.InvalidToken();
		};

		PushDeliveryDispatchWorker.BatchResult result =
			worker(revokingProvider, new CountingProtector(realProtector)).dispatchBatch(command(1, NOW));

		assertThat(result.outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.DEAD);
			assertThat(outcome.safeReasonCode()).isEqualTo("INVALID_TOKEN");
		});
		assertThat(status(currentId)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(deviceStatus(deviceId)).isEqualTo(PushDeviceStatus.REVOKED.name());
		assertThat(notificationStatus(currentId)).isEqualTo(NotificationStatus.UNREAD.name());
	}

	@Test
	@DisplayName("INT-007 lease가 만료된 claim은 provider를 호출하지 않고 회수 대상으로 남는다")
	void skipsProviderCallWhenLeaseExpiredBeforeSend() {
		long recipientId = account("lease-expired-recipient");
		long deviceId = device(recipientId, "lease-expired-device");
		long deliveryId = targetlessDelivery(
			recipientId, deviceId, "lease-expired", DeliveryStatus.PENDING, 0, NOW);
		ScriptedProvider provider = new ScriptedProvider();
		PushTokenProtector expiringProtector = new AdvancingDecryptProtector(
			realProtector, clock, LEASE.plusSeconds(1));

		PushDeliveryDispatchWorker.BatchResult expired =
			worker(provider, expiringProtector).dispatchBatch(command(10, NOW));

		assertThat(expired.outcomes()).singleElement().satisfies(outcome -> {
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.STALE_CLAIM);
			assertThat(outcome.safeReasonCode()).isEqualTo("LEASE_EXPIRED");
		});
		assertThat(provider.calls()).isZero();
		assertThat(status(deliveryId)).isEqualTo(DeliveryStatus.PROCESSING.name());
		assertThat(nextAttemptAt(deliveryId)).isEqualTo(NOW.plus(LEASE));

		Instant recovery = NOW.plus(LEASE).plusSeconds(1);
		clock.set(recovery);
		PushDeliveryDispatchWorker.BatchResult reclaimed =
			worker(provider, new CountingProtector(realProtector)).dispatchBatch(command(10, recovery));

		assertThat(reclaimed.outcomes()).singleElement().satisfies(outcome ->
			assertThat(outcome.outcome()).isEqualTo(PushDeliveryDispatchWorker.Outcome.SENT));
		assertThat(provider.calls()).isEqualTo(1);
		assertThat(status(deliveryId)).isEqualTo(DeliveryStatus.SENT.name());
	}

	@Test
	@DisplayName("INT-011 invalid-token sibling 취소 실패는 device/current 변경을 rollback하고 현재 claim을 안전한 retry로 기록한 뒤 batch를 계속한다")
	void rollsBackInvalidTokenTransactionAndContinuesAfterTerminalFailure() {
		long recipientId = account("invalid-rollback-recipient");
		long failingDeviceId = device(recipientId, "invalid-rollback-device");
		long succeedingDeviceId = device(recipientId, "after-rollback-device");
		long failingId = targetlessDelivery(
			recipientId, failingDeviceId, "invalid-rollback-current", DeliveryStatus.PENDING, 0, NOW);
		long succeedingId = targetlessDelivery(
			recipientId, succeedingDeviceId, "after-rollback", DeliveryStatus.PENDING, 0, NOW);
		long siblingId = targetlessDelivery(
			recipientId, failingDeviceId, "invalid-rollback-sibling", DeliveryStatus.PENDING, 0, NOW.plusSeconds(1));
		installCancellationFailureTrigger(failingDeviceId);
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.InvalidToken(),
			new PushProviderResult.Accepted("projects/test/messages/after-rollback"));

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
		long brokenRecipient = account("mixed-broken-recipient");
		long failureRecipient = account("mixed-failure-recipient");
		long acceptedRecipient = account("mixed-accepted-recipient");
		long invalidRecipient = account("mixed-invalid-recipient");
		long brokenDevice = malformedDevice(brokenRecipient, "mixed-broken-device");
		long providerFailureDevice = device(failureRecipient, "mixed-provider-failure-device");
		long acceptedDevice = device(acceptedRecipient, "mixed-accepted-device");
		long invalidDevice = device(invalidRecipient, "mixed-invalid-device");
		long cancelledRecipient = account("mixed-cancelled-recipient");
		long cancelledDevice = device(cancelledRecipient, "mixed-cancelled-device");
		long brokenId = targetlessDelivery(brokenRecipient, brokenDevice, "mixed-broken", DeliveryStatus.PENDING, 0, NOW);
		long providerFailureId = targetlessDelivery(
			failureRecipient, providerFailureDevice, "mixed-provider-failure", DeliveryStatus.PENDING, 0, NOW);
		long acceptedId = targetlessDelivery(
			acceptedRecipient, acceptedDevice, "mixed-accepted", DeliveryStatus.PENDING, 0, NOW);
		long invalidId = targetlessDelivery(
			invalidRecipient, invalidDevice, "mixed-invalid", DeliveryStatus.PENDING, 0, NOW);
		long cancelledId = targetlessDelivery(
			cancelledRecipient, cancelledDevice, "mixed-cancelled", DeliveryStatus.PENDING, 0, NOW);
		jdbc.update("INSERT INTO notification_user_setting (user_id, push_enabled) VALUES (?, FALSE)",
			cancelledRecipient);
		ScriptedProvider provider = new ScriptedProvider(
			new IllegalStateException(TOKEN_SENTINEL + PRIVATE_SENTINEL),
			new PushProviderResult.Accepted("projects/test/messages/mixed-accepted"),
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

	@Test
	@DisplayName("INT-012 알림 3개와 ACTIVE 기기 2대는 device별 provider 1회, count=3, delivery 6개 SENT, 예산 1만 소비한다")
	void sendsGroupedPayloadPerDeviceAndConsumesBudgetOnce() {
		long recipientId = account("bundle-recipient");
		long deviceA = device(recipientId, "bundle-device-a");
		long deviceB = device(recipientId, "bundle-device-b");
		List<Long> notifications = List.of(
			answerReceived(recipientId, "bundle-n1"),
			answerReceived(recipientId, "bundle-n2"),
			answerReceived(recipientId, "bundle-n3"));
		List<Long> deliveries = new ArrayList<>();
		for (long notificationId : notifications) {
			deliveries.add(delivery(notificationId, deviceA, DeliveryStatus.PENDING, 0, NOW));
			deliveries.add(delivery(notificationId, deviceB, DeliveryStatus.PENDING, 0, NOW));
		}
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.Accepted("projects/test/messages/bundle-a"),
			new PushProviderResult.Accepted("projects/test/messages/bundle-b"));

		clock.set(WINDOW_END);
		PushDeliveryDispatchWorker.BatchResult result = worker(provider, new CountingProtector(realProtector))
			.dispatchBatch(command(10, WINDOW_END));

		assertThat(result.claimed()).isEqualTo(1);
		assertThat(provider.calls()).isEqualTo(2);
		assertThat(provider.payloads()).allSatisfy(data -> {
			assertThat(data).containsOnlyKeys("type", "count", "hasRemainingTime");
			assertThat(data).containsEntry("type", "ANSWER_RECEIVED");
			assertThat(data).containsEntry("count", "3");
			assertThat(data).containsEntry("hasRemainingTime", "false");
			assertThat(data.toString()).doesNotContain(TOKEN_SENTINEL, PRIVATE_SENTINEL, "notificationId");
		});
		assertThat(deliveries).allSatisfy(id -> {
			assertThat(status(id)).isEqualTo(DeliveryStatus.SENT.name());
			assertThat(notificationStatus(id)).isEqualTo(NotificationStatus.UNREAD.name());
		});
		assertThat(providerMessageId(deliveries.get(0))).isEqualTo("projects/test/messages/bundle-a");
		assertThat(providerMessageId(deliveries.get(1))).isEqualTo("projects/test/messages/bundle-b");
		assertThat(consumedTotal(recipientId)).isEqualTo(1);
		assertThat(groupStatusFor(recipientId)).isEqualTo("COMPLETED");
	}

	@Test
	@DisplayName("INT-013 answer member 3개 중 block·hidden은 취소하고 유효 1개만 count=1로 SENT하며 notification 3개를 보존한다")
	void cancelsBlockedAndHiddenAnswerMembersAndSendsDistinctCountOne() {
		long recipientId = account("eligibility-recipient");
		long deviceId = device(recipientId, "eligibility-device");
		long validAuthor = account("eligibility-valid-author");
		long blockedAuthor = account("eligibility-blocked-author");
		long hiddenAuthor = account("eligibility-hidden-author");
		long postId = directionPost(recipientId, "eligibility-post", NOW.plusSeconds(3600));
		long validAnswer = answerFor(postId, validAuthor, "eligibility-valid", "PUBLISHED");
		long blockedAnswer = answerFor(postId, blockedAuthor, "eligibility-blocked", "PUBLISHED");
		long hiddenAnswer = answerFor(postId, hiddenAuthor, "eligibility-hidden", "HIDDEN");
		long validDelivery = answerReceivedDelivery(recipientId, deviceId, validAnswer, "eligibility-valid");
		long blockedDelivery = answerReceivedDelivery(recipientId, deviceId, blockedAnswer, "eligibility-blocked");
		long hiddenDelivery = answerReceivedDelivery(recipientId, deviceId, hiddenAnswer, "eligibility-hidden");
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
			recipientId, blockedAuthor, Timestamp.from(NOW));
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.Accepted("projects/test/messages/eligibility-valid"));

		clock.set(WINDOW_END);
		worker(provider, new CountingProtector(realProtector)).dispatchBatch(command(10, WINDOW_END));

		assertThat(provider.calls()).isEqualTo(1);
		assertThat(provider.payloads()).singleElement().satisfies(data ->
			assertThat(data).containsEntry("count", "1"));
		assertThat(status(validDelivery)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(status(blockedDelivery)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(status(hiddenDelivery)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(notificationStatus(validDelivery)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(notificationStatus(blockedDelivery)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(notificationStatus(hiddenDelivery)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(consumedTotal(recipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-014 기기 A accepted·B retryable 후 accepted면 A 1회/B 2회, 성공 재전송 없음, 예산 1, group COMPLETED")
	void retriesOnlyFailedDeviceAndKeepsBudgetAtOne() {
		long recipientId = account("partial-recipient");
		long deviceA = device(recipientId, "partial-device-a");
		long deviceB = device(recipientId, "partial-device-b");
		List<Long> notifications = List.of(
			answerReceived(recipientId, "partial-n1"),
			answerReceived(recipientId, "partial-n2"),
			answerReceived(recipientId, "partial-n3"));
		List<Long> sentOnA = new ArrayList<>();
		List<Long> retriedOnB = new ArrayList<>();
		for (long notificationId : notifications) {
			sentOnA.add(delivery(notificationId, deviceA, DeliveryStatus.PENDING, 0, NOW));
			retriedOnB.add(delivery(notificationId, deviceB, DeliveryStatus.PENDING, 0, NOW));
		}
		ScriptedProvider provider = new ScriptedProvider(
			new PushProviderResult.Accepted("projects/test/messages/partial-a"),
			new PushProviderResult.RetryableFailure(Duration.ofSeconds(20)),
			new PushProviderResult.Accepted("projects/test/messages/partial-b"));
		PushDeliveryDispatchWorker worker = worker(provider, new CountingProtector(realProtector));

		clock.set(WINDOW_END);
		worker.dispatchBatch(command(10, WINDOW_END));
		assertThat(provider.calls()).isEqualTo(2);
		assertThat(sentOnA).allSatisfy(id -> {
			assertThat(status(id)).isEqualTo(DeliveryStatus.SENT.name());
			assertThat(providerMessageId(id)).isEqualTo("projects/test/messages/partial-a");
		});
		assertThat(retriedOnB).allSatisfy(id -> assertThat(status(id)).isEqualTo(DeliveryStatus.FAILED.name()));
		assertThat(consumedTotal(recipientId)).isEqualTo(1);

		Instant retryAt = WINDOW_END.plusSeconds(20);
		clock.set(retryAt);
		worker.dispatchBatch(command(10, retryAt));

		assertThat(provider.calls()).isEqualTo(3);
		assertThat(provider.tokens()).containsExactly(
			TOKEN_SENTINEL + "-partial-device-a",
			TOKEN_SENTINEL + "-partial-device-b",
			TOKEN_SENTINEL + "-partial-device-b");
		assertThat(sentOnA).allSatisfy(id -> {
			assertThat(status(id)).isEqualTo(DeliveryStatus.SENT.name());
			assertThat(providerMessageId(id)).isEqualTo("projects/test/messages/partial-a");
		});
		assertThat(retriedOnB).allSatisfy(id -> {
			assertThat(status(id)).isEqualTo(DeliveryStatus.SENT.name());
			assertThat(providerMessageId(id)).isEqualTo("projects/test/messages/partial-b");
		});
		assertThat(consumedTotal(recipientId)).isEqualTo(1);
		assertThat(groupStatusFor(recipientId)).isEqualTo("COMPLETED");
	}

	@Test
	@DisplayName("INT-015 첫 group invalid token은 그 기기 미발송을 모든 group에서 취소하고 다른 기기와 notification은 보존한다")
	void invalidTokenCancelsDeviceAcrossGroupsWithoutTouchingOtherDevices() {
		long recipientId = account("cross-group-recipient");
		long deviceA = device(recipientId, "cross-group-device-a");
		long deviceB = device(recipientId, "cross-group-device-b");
		long firstNotification = answerReceived(recipientId, "cross-group-first");
		long secondNotification = answerReacted(recipientId, "cross-group-second");
		long firstA = delivery(firstNotification, deviceA, DeliveryStatus.PENDING, 0, NOW);
		long firstB = delivery(firstNotification, deviceB, DeliveryStatus.PENDING, 0, NOW);
		long secondA = delivery(secondNotification, deviceA, DeliveryStatus.PENDING, 0, NOW);
		PushProvider provider = new DeviceScriptedProvider(Map.of(
			TOKEN_SENTINEL + "-cross-group-device-a", List.of(new PushProviderResult.InvalidToken()),
			TOKEN_SENTINEL + "-cross-group-device-b",
			List.of(new PushProviderResult.Accepted("projects/test/messages/cross-group-b"))));

		clock.set(WINDOW_END);
		worker(provider, new CountingProtector(realProtector)).dispatchBatch(command(1, WINDOW_END));

		assertThat(status(firstA)).isEqualTo(DeliveryStatus.DEAD.name());
		assertThat(status(secondA)).isEqualTo(DeliveryStatus.CANCELLED.name());
		assertThat(status(firstB)).isEqualTo(DeliveryStatus.SENT.name());
		assertThat(deviceStatus(deviceA)).isEqualTo(PushDeviceStatus.INVALID.name());
		assertThat(deviceStatus(deviceB)).isEqualTo(PushDeviceStatus.ACTIVE.name());
		assertThat(notificationStatus(firstA)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(notificationStatus(secondA)).isEqualTo(NotificationStatus.UNREAD.name());
		assertThat(providerMessageId(firstB)).isEqualTo("projects/test/messages/cross-group-b");
	}

	@Test
	@DisplayName("INT-017 유효 member 4개의 FCM wire는 count=4와 세 data key만 보내고 accepted ID를 member delivery에 반영한다")
	void groupedWirePayloadUsesDistinctCountAndOmitsPrivacySentinels() throws Exception {
		long recipientId = account("wire-bundle-recipient");
		long deviceId = device(recipientId, "wire-bundle-device");
		List<Long> deliveries = new ArrayList<>();
		for (int index = 1; index <= 4; index++) {
			deliveries.add(delivery(
				answerReceived(recipientId, "wire-bundle-n" + index),
				deviceId, DeliveryStatus.PENDING, 0, NOW));
		}
		try (WireFcmServer server = new WireFcmServer(
			"{\"name\":\"projects/test-project/messages/test-message-wire-180\"}")) {
			server.start();
			FcmHttpV1PushProvider realProvider = new FcmHttpV1PushProvider(
				restClient(server.baseUrl()),
				() -> "test-only-bearer",
				new ObjectMapper(),
				"test-project");

			clock.set(WINDOW_END);
			worker(realProvider, realProtector).dispatchBatch(command(10, WINDOW_END));

			assertThat(deliveries).allSatisfy(id -> {
				assertThat(status(id)).isEqualTo(DeliveryStatus.SENT.name());
				assertThat(providerMessageId(id))
					.isEqualTo("projects/test-project/messages/test-message-wire-180");
			});
			JsonNode message = new ObjectMapper().readTree(server.requestBody()).path("message");
			assertThat(message.fieldNames()).toIterable().containsExactlyInAnyOrder("token", "data");
			assertThat(message.path("token").asText()).isEqualTo(TOKEN_SENTINEL + "-wire-bundle-device");
			assertThat(message.path("data").fieldNames()).toIterable()
				.containsExactlyInAnyOrder("type", "count", "hasRemainingTime");
			assertThat(message.path("data").path("type").asText()).isEqualTo("ANSWER_RECEIVED");
			assertThat(message.path("data").path("count").asText()).isEqualTo("4");
			assertThat(message.path("data").path("hasRemainingTime").asText()).isEqualTo("false");
			assertThat(server.requestBody()).doesNotContain(PRIVATE_SENTINEL, "notificationId", "nickname", "body");
		}
	}

	private PushDeliveryDispatchWorker worker(PushProvider provider, PushTokenProtector protector) {
		return new PushDeliveryDispatchWorker(
			new PushDispatchGroupPlanner(groups, new PushGroupingPolicy(POLICY)),
			groupClaims,
			transactions,
			new PushDispatchEligibility(),
			new PushSuppressionPolicy(POLICY, clock),
			new PushBudgetPolicy(POLICY),
			POLICY,
			new PushPayloadFactory(),
			new PushDeliveryRetryPolicy(3, Duration.ofSeconds(10), Duration.ofSeconds(60)),
			protector,
			provider,
			clock);
	}

	private static RestClient restClient(String baseUrl) {
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk()
			.build(ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(1))
				.withReadTimeout(Duration.ofSeconds(1)));
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
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

	private long answerReceived(long recipientId, String suffix) {
		return notification(recipientId, NotificationType.ANSWER_RECEIVED, suffix, null).id();
	}

	private long answerReacted(long recipientId, String suffix) {
		return notification(recipientId, NotificationType.ANSWER_REACTED, suffix, null).id();
	}

	private long answerReceivedDelivery(long recipientId, long deviceId, long answerId, String suffix) {
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(
			OutboxAggregateType.ANSWER, answerId, OutboxEventType.ANSWER_PUBLISHED,
			"gh179-dispatch-outbox-" + suffix + "-" + (++sequence), "{}", NOW));
		Notification saved = notifications.save(new Notification(
			null, recipientId, event.id(), NotificationType.ANSWER_RECEIVED,
			"gh179-dispatch-notification-" + suffix + "-" + sequence,
			null, answerId, null, NotificationStatus.UNREAD, NOW, null));
		return delivery(saved.id(), deviceId, DeliveryStatus.PENDING, 0, NOW);
	}

	private long answerFor(long postId, long authorId, String suffix, String status) {
		long postRecipientId = jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'OPENED', 'NEAR', 45, ?, ?, ?, ?, 225, 5000)
			RETURNING id
			""", Long.class, postId, authorId, REGION,
			Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
		boolean published = "PUBLISHED".equals(status);
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at, published_at)
			VALUES (?, ?, ?, ?, ?, ?, 45, 'NEAR', 5000, 'PASSED', ?, ?)
			RETURNING id
			""", Long.class, postRecipientId, authorId, status, "gh179-dispatch-answer-" + suffix + "-" + (++sequence),
			PRIVATE_SENTINEL, REGION, Timestamp.from(NOW), published ? Timestamp.from(NOW) : null);
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

	private Instant nextAttemptAt(long deliveryId) {
		Timestamp value = jdbc.queryForObject(
			"SELECT next_attempt_at FROM notification_delivery WHERE id = ?", Timestamp.class, deliveryId);
		return value.toInstant();
	}

	private String providerMessageId(long deliveryId) {
		return jdbc.queryForObject(
			"SELECT provider_message_id FROM notification_delivery WHERE id = ?", String.class, deliveryId);
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

	private int consumedTotal(long userId) {
		List<Integer> totals = jdbc.query(
			"SELECT consumed_total FROM push_daily_budget WHERE user_id = ?",
			(rs, row) -> rs.getInt(1), userId);
		return totals.isEmpty() ? 0 : totals.getFirst();
	}

	private String groupStatusFor(long recipientId) {
		return jdbc.queryForObject(
			"SELECT status FROM push_dispatch_group WHERE recipient_id = ?", String.class, recipientId);
	}

	private void cleanupFixtures() {
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
		jdbc.update("DELETE FROM answer WHERE coarse_region_code = ?", REGION);
		jdbc.update("""
			DELETE FROM post_recipient
			WHERE post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
			""", REGION);
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

	private static final class AdvancingDecryptProtector implements PushTokenProtector {

		private final PushTokenProtector delegate;
		private final MutableClock clock;
		private final Duration delay;

		private AdvancingDecryptProtector(PushTokenProtector delegate, MutableClock clock, Duration delay) {
			this.delegate = delegate;
			this.clock = clock;
			this.delay = delay;
		}

		@Override
		public ProtectedPushToken protect(PushToken token) {
			return delegate.protect(token);
		}

		@Override
		public PushToken decrypt(byte[] envelope) {
			clock.advance(delay);
			return delegate.decrypt(envelope);
		}

		@Override
		public String fingerprint(PushToken token) {
			return delegate.fingerprint(token);
		}
	}

	private static final class ScriptedProvider implements PushProvider {

		private final Deque<Object> results = new ArrayDeque<>();
		private final List<Map<String, String>> payloads = new ArrayList<>();
		private final List<String> tokens = new ArrayList<>();
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
			tokens.add(command.token().exposeForProvider());
			Object next = results.isEmpty()
				? new PushProviderResult.Accepted("projects/test/messages/default-scripted")
				: results.removeFirst();
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

		private List<String> tokens() {
			return List.copyOf(tokens);
		}
	}

	private static final class DeviceScriptedProvider implements PushProvider {

		private final Map<String, Deque<PushProviderResult>> resultsByToken = new LinkedHashMap<>();
		private final AtomicInteger calls = new AtomicInteger();

		private DeviceScriptedProvider(Map<String, List<PushProviderResult>> resultsByToken) {
			resultsByToken.forEach((token, results) ->
				this.resultsByToken.put(token, new ArrayDeque<>(results)));
		}

		@Override
		public synchronized PushProviderResult send(PushSendCommand command) {
			calls.incrementAndGet();
			Deque<PushProviderResult> results = resultsByToken.get(command.token().exposeForProvider());
			if (results == null || results.isEmpty()) {
				throw new IllegalStateException("SAFE_UNSCRIPTED_DEVICE_TOKEN");
			}
			return results.removeFirst();
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
			return new PushProviderResult.Accepted("projects/test/messages/blocking-provider");
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

	private static final class MutableClock extends Clock {

		private final AtomicReference<Instant> current;
		private final ZoneId zone;

		private MutableClock(Instant initial, ZoneId zone) {
			this(new AtomicReference<>(initial), zone);
		}

		private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
			this.current = current;
			this.zone = zone;
		}

		private void set(Instant instant) {
			current.set(instant);
		}

		private void advance(Duration duration) {
			current.updateAndGet(value -> value.plus(duration));
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId newZone) {
			return new MutableClock(current, newZone);
		}

		@Override
		public Instant instant() {
			return current.get();
		}
	}

	private static final class WireFcmServer implements AutoCloseable {

		private final HttpServer server;
		private final ExecutorService executor;
		private final String responseBody;
		private volatile String requestPath;
		private volatile String requestBody;
		private volatile com.sun.net.httpserver.Headers requestHeaders;

		private WireFcmServer(String responseBody) throws IOException {
			this.responseBody = responseBody;
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", this::handle);
			executor = Executors.newSingleThreadExecutor();
			server.setExecutor(executor);
		}

		private void start() {
			server.start();
		}

		private String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		private String requestPath() {
			return requestPath;
		}

		private String requestBody() {
			return requestBody;
		}

		private String requestHeader(String name) {
			return requestHeaders.getFirst(name);
		}

		private void handle(HttpExchange exchange) throws IOException {
			requestPath = exchange.getRequestURI().getPath();
			requestHeaders = exchange.getRequestHeaders();
			requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		}

		@Override
		public void close() {
			server.stop(0);
			executor.shutdownNow();
		}
	}
}
