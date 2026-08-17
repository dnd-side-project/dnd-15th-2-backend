/**
 * Created at: 2026-08-14T18:01:54+09:00
 * Source scenario: TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-001 through INT-012,
 * INT-017 through INT-019, INT-024 through INT-025, INT-027 through INT-030
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionRequestFingerprint;
import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.feed.service.InboxQueryService;
import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationPreference;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.repository.SafetyRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@ActiveProfiles("test")
class RecipientNotificationFanOutWorkerIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_A = "TEST-GH123-FANOUT-A";
	private static final String REGION_B = "TEST-GH123-FANOUT-B";
	private static final Instant NOW = Instant.parse("2026-08-14T09:00:00Z");
	private static final String OWNER = "gh123-integration-worker";
	private static final AtomicInteger KEYS = new AtomicInteger();

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private RecipientNotificationFanOutWorker worker;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private PostRecipientRepository postRecipients;
	@Autowired
	private DirectionPostRepository posts;
	@Autowired
	private AccountRepository accounts;
	@Autowired
	private SafetyRepository safety;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private InboxQueryService inbox;

	@BeforeEach
	void resetFixtures() {
		dropFailureInjection();
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM notification_preference");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM answer_reaction");
		jdbc.update("DELETE FROM post_reaction");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code IN (?, ?)", REGION_A, REGION_B);
		jdbc.update("DELETE FROM region_code WHERE code IN (?, ?)", REGION_A, REGION_B);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH123 FanOut A', 'REGION'), (?, 'KR', 'GH123 FanOut B', 'REGION')
			""", REGION_A, REGION_B);
	}

	@AfterEach
	void removeFailureInjection() {
		dropFailureInjection();
	}

	@Test
	@DisplayName("설정 행과 ACTIVE 기기가 없어도 인앱 알림을 만들고 source를 완료하며 수신 자격은 보존한다")
	void createsInAppNotificationWithAbsentPreferenceAndNoDevice() {
		Fixture fixture = fixture("int001", PostRecipientStatus.AVAILABLE);
		Map<String, Object> recipientBefore = recipientSnapshot(fixture.postRecipientId());
		Map<String, Object> stateBefore = receiveStateSnapshot(fixture.recipientId());

		RecipientNotificationFanOutWorker.BatchResult result = worker.processBatch(command(10));

		assertThat(result.claimed()).isEqualTo(1);
		assertThat(result.outcomes()).containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(fixture)).isEqualTo(1);
		assertThat(deliveryCount(fixture)).isZero();
		assertThat(sourceStatus(fixture.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(recipientSnapshot(fixture.postRecipientId())).isEqualTo(recipientBefore);
		assertThat(receiveStateSnapshot(fixture.recipientId())).isEqualTo(stateBefore);
	}

	@ParameterizedTest(name = "preference={0}, notification={1}")
	@MethodSource("preferenceCases")
	@DisplayName("설정 행 없음과 enabled는 fan-out하고 disabled는 알림 없이 source만 완료한다")
	void appliesPreferenceDefaultAndSuppression(Boolean preference, long expectedNotifications) {
		Fixture fixture = fixture("int002-" + String.valueOf(preference), PostRecipientStatus.AVAILABLE);
		if (preference != null) {
			notifications.savePreference(new NotificationPreference(NotificationType.DIRECTION_POST_RECEIVED,
				fixture.recipientId(), preference, null, null));
		}

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(fixture)).isEqualTo(expectedNotifications);
		assertThat(deliveryCount(fixture)).isZero();
		assertThat(sourceStatus(fixture.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
	}

	@Test
	@DisplayName("ACTIVE PushDevice만 PENDING Delivery를 만들고 token material은 worker 결과에 포함하지 않는다")
	void createsDeliveriesOnlyForActiveDevices() {
		Fixture fixture = fixture("int003", PostRecipientStatus.AVAILABLE);
		long activeOne = device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int003-active-1");
		device(fixture.recipientId(), PushDeviceStatus.INVALID, "int003-invalid");
		long activeTwo = device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int003-active-2");
		device(fixture.recipientId(), PushDeviceStatus.REVOKED, "int003-revoked");

		RecipientNotificationFanOutWorker.BatchResult result = worker.processBatch(command(10));

		assertThat(result.outcomes()).containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForList("""
			SELECT nd.push_device_id
			FROM notification_delivery nd
			JOIN notification n ON n.id = nd.notification_id
			WHERE n.recipient_id = ?
			ORDER BY nd.push_device_id
			""", Long.class, fixture.recipientId())).containsExactly(activeOne, activeTwo);
		assertThat(jdbc.queryForList("SELECT status FROM notification_delivery ORDER BY id", String.class))
			.containsExactly("PENDING", "PENDING");
		assertThat(result.toString()).doesNotContain("int003-active", "int003-invalid", "int003-revoked");
	}

	@ParameterizedTest(name = "status={0}, allowed={1}")
	@MethodSource("recipientStatusCases")
	@DisplayName("PostRecipient 상태 판정표는 네 미처리 상태만 deadline 전에 fan-out한다")
	void appliesRecipientStatusEligibilityTable(PostRecipientStatus status, boolean allowed) {
		Fixture fixture = fixture("int004-" + status.name().toLowerCase(), status);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(fixture)).isEqualTo(allowed ? 1 : 0);
		assertThat(sourceStatus(fixture.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
	}

	@Test
	@DisplayName("ANSWERED는 질문글 deadline 전·경계·후 모두 새 질문 알림을 억제한다")
	void suppressesAnsweredRecipientRegardlessOfDeadline() {
		Fixture beforeDeadline = fixture("int004-answered-before", PostRecipientStatus.ANSWERED,
			REGION_A, REGION_A, NOW.plusSeconds(1));
		Fixture atDeadline = fixture("int004-answered-boundary", PostRecipientStatus.ANSWERED,
			REGION_A, REGION_A, NOW);
		Fixture afterDeadline = fixture("int004-answered-after", PostRecipientStatus.ANSWERED,
			REGION_A, REGION_A, NOW.minusSeconds(1));

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(beforeDeadline)).isZero();
		assertThat(notificationCount(atDeadline)).isZero();
		assertThat(notificationCount(afterDeadline)).isZero();
	}

	@ParameterizedTest(name = "gate={0}, allowed={1}")
	@MethodSource("eligibilityGateCases")
	@DisplayName("계정·질문글·deadline 현재 상태에서 승인된 조건만 fan-out한다")
	void suppressesLostAccountPostAndDeadlineEligibility(GateCase gate, boolean allowed) {
		Fixture fixture = fixture("int005-" + gate.name().toLowerCase(), PostRecipientStatus.AVAILABLE,
			REGION_A, REGION_A, NOW.plusSeconds(1));
		gate.apply(jdbc, fixture);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(fixture)).isEqualTo(allowed ? 1 : 0);
		assertThat(sourceStatus(fixture.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
	}

	@Test
	@DisplayName("양방향 active block은 알림과 inbox 상세를 숨기고 released block은 둘 다 다시 허용한다")
	void appliesBilateralBlockToNotificationAndInboxVisibility() {
		Fixture recipientBlocks = fixture("int006-recipient-blocks", PostRecipientStatus.AVAILABLE);
		block(recipientBlocks.recipientId(), recipientBlocks.senderId(), null);
		Fixture senderBlocks = fixture("int006-sender-blocks", PostRecipientStatus.AVAILABLE);
		block(senderBlocks.senderId(), senderBlocks.recipientId(), null);
		Fixture released = fixture("int006-released", PostRecipientStatus.AVAILABLE);
		block(released.recipientId(), released.senderId(), NOW.minusSeconds(1));
		Fixture senderReleased = fixture("int006-sender-released", PostRecipientStatus.AVAILABLE);
		block(senderReleased.senderId(), senderReleased.recipientId(), NOW.minusSeconds(1));

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(recipientBlocks)).isZero();
		assertThat(notificationCount(senderBlocks)).isZero();
		assertThat(notificationCount(released)).isEqualTo(1);
		assertThat(notificationCount(senderReleased)).isEqualTo(1);
		assertThat(inbox.detail(recipientBlocks.recipientId(), recipientBlocks.postRecipientId(), NOW)).isEmpty();
		assertThat(inbox.detail(senderBlocks.recipientId(), senderBlocks.postRecipientId(), NOW)).isEmpty();
		assertThat(inbox.detail(released.recipientId(), released.postRecipientId(), NOW)).isPresent();
		assertThat(inbox.detail(senderReleased.recipientId(), senderReleased.postRecipientId(), NOW)).isPresent();
	}

	@Test
	@DisplayName("변조 payload는 파싱하지 않고 POST_RECIPIENT aggregate만 알림 권위값으로 사용한다")
	void usesAggregateAuthorityInsteadOfPayload() {
		Fixture authoritative = fixtureWithoutSource("int007-authority", PostRecipientStatus.AVAILABLE,
			REGION_A, REGION_A, NOW.plusSeconds(3600));
		Fixture unrelated = fixtureWithoutSource("int007-unrelated", PostRecipientStatus.AVAILABLE,
			REGION_A, REGION_B, NOW.plusSeconds(3600));
		long sourceId = source(authoritative.postRecipientId(), """
			{"postRecipientId":%d,"recipientId":%d,"postId":%d,
			 "latitude":37.501,"longitude":127.001,"body":"payload-secret"}
			""".formatted(unrelated.postRecipientId(), unrelated.recipientId(), unrelated.postId()), 0);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		Map<String, Object> notification = jdbc.queryForMap("SELECT * FROM notification WHERE outbox_event_id = ?", sourceId);
		assertThat(notification.get("recipient_id")).isEqualTo(authoritative.recipientId());
		assertThat(notification.get("direction_post_id")).isEqualTo(authoritative.postId());
		assertThat(notification.toString()).doesNotContain("payload-secret", "37.501", "127.001");
		assertThat(notificationCount(unrelated)).isZero();
	}

	@Test
	@DisplayName("같은 source replay는 Notification과 기기별 Delivery를 한 행으로 유지한다")
	void replaysSameSourceIdempotently() {
		Fixture fixture = fixture("int008", PostRecipientStatus.AVAILABLE);
		device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int008-active");
		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		long notificationId = notificationId(fixture);
		long deliveryId = deliveryIds(notificationId).getFirst();
		jdbc.update("""
			UPDATE outbox_event
			SET status = 'FAILED', processed_at = NULL, next_attempt_at = ?
			WHERE id = ?
			""", Timestamp.from(NOW), fixture.sourceId());

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(fixture)).isEqualTo(1);
		assertThat(notificationId(fixture)).isEqualTo(notificationId);
		assertThat(deliveryIds(notificationId)).containsExactly(deliveryId);
	}

	@Test
	@DisplayName("서로 다른 source가 같은 PostRecipient를 가리켜도 logical 알림과 Delivery는 하나다")
	void deduplicatesDifferentSourcesForSameRecipientAggregate() {
		Fixture fixture = fixture("int009", PostRecipientStatus.AVAILABLE);
		device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int009-active");
		long secondSource = source(fixture.postRecipientId(), "{}", 0);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(fixture)).isEqualTo(1);
		assertThat(deliveryCount(fixture)).isEqualTo(1);
		assertThat(sourceStatus(fixture.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(sourceStatus(secondSource)).isEqualTo(OutboxStatus.PROCESSED);
	}

	@Test
	@DisplayName("Delivery 두 번째 insert의 serialization 실패는 domain write를 rollback하고 source만 FAILED로 만든다")
	void rollsBackNotificationAndDeliveriesOnDeliveryInsertFailure() {
		Fixture fixture = fixture("int010", PostRecipientStatus.AVAILABLE);
		device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int010-active-1");
		long failingDevice = device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int010-active-2");
		installDeliveryFailure(failingDevice);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.RETRYABLE);
		assertThat(notificationCount(fixture)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_delivery", Long.class)).isZero();
		assertFailed(fixture.sourceId(), 1, NOW.plusSeconds(1));
	}

	@Test
	@DisplayName("Notification non-dedup integrity 실패는 쓰기를 rollback하고 source를 즉시 DEAD로 만든다")
	void rollsBackAndDeadLettersNotificationIntegrityFailure() {
		Fixture fixture = fixture("int011", PostRecipientStatus.AVAILABLE);
		installNotificationFailures(-1, fixture.recipientId(), -1);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.DEAD);
		assertThat(notificationCount(fixture)).isZero();
		assertThat(sourceStatus(fixture.sourceId())).isEqualTo(OutboxStatus.DEAD);
		assertThat(sourceNextAttemptAt(fixture.sourceId())).isEqualTo(NOW);
	}

	@Test
	@DisplayName("정상·설정 억제·retryable·permanent event를 같은 batch에서 독립적으로 종료한다")
	void isolatesMixedBatchFailures() {
		Fixture normal = fixture("int012-normal", PostRecipientStatus.AVAILABLE);
		Fixture disabled = fixture("int012-disabled", PostRecipientStatus.AVAILABLE);
		notifications.savePreference(new NotificationPreference(NotificationType.DIRECTION_POST_RECEIVED,
			disabled.recipientId(), false, null, null));
		Fixture retryable = fixture("int012-retry", PostRecipientStatus.AVAILABLE);
		Fixture permanent = fixture("int012-permanent", PostRecipientStatus.AVAILABLE);
		installNotificationFailures(retryable.recipientId(), permanent.recipientId(), -1);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.RETRYABLE,
				RecipientNotificationFanOutWorker.Outcome.DEAD);
		assertThat(notificationCount(normal)).isEqualTo(1);
		assertThat(notificationCount(disabled)).isZero();
		assertThat(notificationCount(retryable)).isZero();
		assertThat(notificationCount(permanent)).isZero();
		assertThat(sourceStatus(normal.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(sourceStatus(disabled.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(sourceStatus(retryable.sourceId())).isEqualTo(OutboxStatus.FAILED);
		assertThat(sourceStatus(permanent.sourceId())).isEqualTo(OutboxStatus.DEAD);
	}

	@Test
	@DisplayName("실패 기록 예외가 난 event를 batch에서 격리하고 lease 만료 후 재처리한다")
	void isolatesFailureRecordingExceptionAndReclaimsExpiredLease() {
		Fixture failed = fixture("int030-failure-recording", PostRecipientStatus.AVAILABLE);
		Fixture following = fixture("int030-following", PostRecipientStatus.AVAILABLE);
		installNotificationFailures(failed.recipientId(), -1, -1);
		installFailureRecordingFailure(failed.sourceId());

		assertThat(worker.processBatch(command(10)).outcomes()).containsExactly(
			RecipientNotificationFanOutWorker.Outcome.FAILURE_RECORDING_FAILED,
			RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(failed)).isZero();
		assertThat(notificationCount(following)).isEqualTo(1);
		assertThat(sourceStatus(failed.sourceId())).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(sourceLeaseExpiresAt(failed.sourceId())).isEqualTo(NOW.plusSeconds(60));
		assertThat(sourceStatus(following.sourceId())).isEqualTo(OutboxStatus.PROCESSED);

		dropFailureInjection();
		assertThat(worker.processBatch(commandAt(NOW.plusSeconds(61), 10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(failed)).isEqualTo(1);
		assertThat(sourceStatus(failed.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
	}

	@Test
	@DisplayName("Delivery FAILED와 DEAD 전이는 Notification·PostRecipient·수신 슬롯을 변경하지 않는다")
	void keepsInboxEligibilityIndependentFromDeliveryFailure() {
		Fixture fixture = fixture("int017", PostRecipientStatus.AVAILABLE);
		device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int017-active");
		worker.processBatch(command(10));
		long notificationId = notificationId(fixture);
		long deliveryId = deliveryIds(notificationId).getFirst();
		Map<String, Object> notificationBefore = notificationSnapshot(notificationId);
		Map<String, Object> recipientBefore = recipientSnapshot(fixture.postRecipientId());
		Map<String, Object> stateBefore = receiveStateSnapshot(fixture.recipientId());

		NotificationDelivery processing = notifications.claimDelivery(deliveryId, NOW).orElseThrow();
		assertThat(notifications.updateDelivery(processing.failed(NOW.plusSeconds(1), false))).isTrue();
		NotificationDelivery retry = notifications.claimDelivery(deliveryId, NOW.plusSeconds(1)).orElseThrow();
		assertThat(notifications.updateDelivery(retry.failed(NOW.plusSeconds(1), true))).isTrue();

		assertThat(jdbc.queryForObject("SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId))
			.isEqualTo("DEAD");
		assertThat(notificationSnapshot(notificationId)).isEqualTo(notificationBefore);
		assertThat(recipientSnapshot(fixture.postRecipientId())).isEqualTo(recipientBefore);
		assertThat(receiveStateSnapshot(fixture.recipientId())).isEqualTo(stateBefore);
	}

	@Test
	@DisplayName("Notification 존재와 READ·REVOKED 상태는 block·expiry 상세 권한이나 recipient·slot 상태를 부여하지 않는다")
	void keepsNotificationStateSeparateFromInboxAuthorization() {
		Fixture fixture = fixture("int018", PostRecipientStatus.AVAILABLE);
		worker.processBatch(command(10));
		long notificationId = notificationId(fixture);
		Map<String, Object> stateBefore = receiveStateSnapshot(fixture.recipientId());

		block(fixture.recipientId(), fixture.senderId(), null);
		assertThat(inbox.detail(fixture.recipientId(), fixture.postRecipientId(), NOW)).isEmpty();
		jdbc.update("UPDATE user_block SET released_at = ? WHERE blocker_id = ? AND blocked_id = ?",
			Timestamp.from(NOW), fixture.recipientId(), fixture.senderId());
		jdbc.update("UPDATE direction_post SET expires_at = ? WHERE id = ?", Timestamp.from(NOW), fixture.postId());
		assertThat(inbox.detail(fixture.recipientId(), fixture.postRecipientId(), NOW)).isEmpty();

		jdbc.update("""
			UPDATE post_recipient
			SET status = 'ANSWERED', discovered_at = ?, opened_at = ?, capacity_released_at = ?
			WHERE id = ?
			""", Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW), fixture.postRecipientId());
		assertThat(inbox.detail(fixture.recipientId(), fixture.postRecipientId(), NOW.plusSeconds(1))).isPresent();
		Map<String, Object> answeredRecipient = recipientSnapshot(fixture.postRecipientId());

		jdbc.update("UPDATE notification SET status = 'READ', read_at = ? WHERE id = ?",
			Timestamp.from(NOW), notificationId);
		jdbc.update("UPDATE notification SET status = 'REVOKED', read_at = NULL WHERE id = ?", notificationId);

		assertThat(recipientSnapshot(fixture.postRecipientId())).isEqualTo(answeredRecipient);
		assertThat(receiveStateSnapshot(fixture.recipientId())).isEqualTo(stateBefore);
	}

	@Test
	@DisplayName("Outbox·Notification·Delivery·worker 결과와 로그에 위치·본문·token material을 복제하지 않는다")
	void keepsFanOutArtifactsFreeOfPrivateMatchingAndTokenMaterial() {
		String privateBody = "private-body-int019";
		String tokenFingerprint = "private-token-int019";
		Fixture fixture = fixture("int019", PostRecipientStatus.AVAILABLE, REGION_A, REGION_B,
			NOW.plusSeconds(3600));
		jdbc.update("UPDATE direction_post SET body_text = ? WHERE id = ?", privateBody, fixture.postId());
		jdbc.update("UPDATE post_recipient SET matched_bearing_deg = 12.345, inbound_bearing_deg = 234.567, distance_m = 9876 WHERE id = ?",
			fixture.postRecipientId());
		presence(fixture.senderId(), REGION_A, 127.001, 37.501);
		presence(fixture.recipientId(), REGION_B, 128.002, 36.502);
		device(fixture.recipientId(), PushDeviceStatus.ACTIVE, tokenFingerprint);
		Logger logger = (Logger)LoggerFactory.getLogger(RecipientNotificationFanOutWorker.class);
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);

		RecipientNotificationFanOutWorker.BatchResult result;
		try {
			result = worker.processBatch(command(10));
		} finally {
			logger.detachAppender(logs);
			logs.stop();
		}

		String observed = jdbc.queryForMap("SELECT payload::text, status FROM outbox_event WHERE id = ?",
			fixture.sourceId()).toString()
			+ jdbc.queryForMap("SELECT recipient_id, notification_type, dedup_key, direction_post_id, status FROM notification WHERE recipient_id = ?",
				fixture.recipientId())
			+ jdbc.queryForMap("SELECT status, attempt_count FROM notification_delivery")
			+ result
			+ logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
		assertThat(observed).doesNotContain(privateBody, tokenFingerprint, REGION_A, REGION_B,
			"12.345", "234.567", "9876", "latitude", "longitude");
	}

	@Test
	@DisplayName("기존 logical Notification과 일부 Delivery를 유지하고 누락된 ACTIVE 기기 Delivery만 보충한다")
	void reconcilesPartialFanOutWithoutReplacingExistingRows() {
		Fixture fixture = fixture("int024", PostRecipientStatus.AVAILABLE);
		long firstDevice = device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int024-active-1");
		long secondDevice = device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int024-active-2");
		Notification existing = notifications.saveIfAbsent(notification(fixture));
		NotificationDelivery existingDelivery = notifications.saveDeliveryIfAbsent(
			NotificationDelivery.pending(existing.id(), firstDevice, NOW.minusSeconds(1)));

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationId(fixture)).isEqualTo(existing.id());
		assertThat(deliveryIds(existing.id())).containsExactly(existingDelivery.id(),
			jdbc.queryForObject("SELECT id FROM notification_delivery WHERE notification_id = ? AND push_device_id = ?",
				Long.class, existing.id(), secondDevice));
		assertThat(sourceStatus(fixture.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
	}

	@Test
	@DisplayName("transient는 backoff 후 성공하고 integrity와 최대 시도 transient는 DEAD로 분류한다")
	void appliesRetryPermanentAndMaxAttemptFailureOracle() {
		Fixture transientFixture = fixture("int025-transient", PostRecipientStatus.AVAILABLE);
		Fixture integrityFixture = fixture("int025-integrity", PostRecipientStatus.AVAILABLE);
		Fixture maxAttemptFixture = fixture("int025-max", PostRecipientStatus.AVAILABLE);
		jdbc.update("UPDATE outbox_event SET attempt_count = 2 WHERE id = ?", maxAttemptFixture.sourceId());
		installNotificationFailures(transientFixture.recipientId(), integrityFixture.recipientId(),
			maxAttemptFixture.recipientId());

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.RETRYABLE,
				RecipientNotificationFanOutWorker.Outcome.DEAD,
				RecipientNotificationFanOutWorker.Outcome.DEAD);
		assertFailed(transientFixture.sourceId(), 1, NOW.plusSeconds(1));
		assertThat(sourceStatus(integrityFixture.sourceId())).isEqualTo(OutboxStatus.DEAD);
		assertThat(sourceStatus(maxAttemptFixture.sourceId())).isEqualTo(OutboxStatus.DEAD);

		assertThat(worker.processBatch(commandAt(NOW.plusSeconds(1), 10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(transientFixture)).isEqualTo(1);
		assertThat(sourceStatus(transientFixture.sourceId())).isEqualTo(OutboxStatus.PROCESSED);
	}

	@Test
	@DisplayName("source complete serialization 실패는 Notification·Delivery를 rollback하고 source만 FAILED로 만든다")
	void rollsBackDomainWritesWhenSourceCompleteFails() {
		Fixture fixture = fixture("int027", PostRecipientStatus.AVAILABLE);
		device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int027-active");
		installCompleteFailure(fixture.sourceId());

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.RETRYABLE);
		assertThat(notificationCount(fixture)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_delivery", Long.class)).isZero();
		assertFailed(fixture.sourceId(), 1, NOW.plusSeconds(1));
	}

	@Test
	@DisplayName("GLOBAL matching의 다른 matched region을 유지하고 지역·거리·방위를 다시 계산하지 않고 fan-out한다")
	void fansOutCrossRegionGlobalRecipientFromStoredAggregate() {
		Fixture fixture = fixture("int028", PostRecipientStatus.AVAILABLE, REGION_A, REGION_B,
			NOW.plusSeconds(3600));
		device(fixture.recipientId(), PushDeviceStatus.ACTIVE, "int028-active");
		Map<String, Object> matchingBefore = jdbc.queryForMap("""
			SELECT matched_region_code, matched_bearing_deg, inbound_bearing_deg, distance_m
			FROM post_recipient WHERE id = ?
			""", fixture.postRecipientId());

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(fixture)).isEqualTo(1);
		assertThat(deliveryCount(fixture)).isEqualTo(1);
		assertThat(jdbc.queryForMap("""
			SELECT matched_region_code, matched_bearing_deg, inbound_bearing_deg, distance_m
			FROM post_recipient WHERE id = ?
			""", fixture.postRecipientId())).isEqualTo(matchingBefore);
		assertThat(matchingBefore.get("matched_region_code")).isEqualTo(REGION_B);
	}

	@Test
	@DisplayName("account 변경 commit 순서에 따라 현재 snapshot을 억제하고 이미 읽은 snapshot은 한 번만 반영한다")
	void linearizesAccountSnapshotForCurrentAndSubsequentEvents() throws Exception {
		Fixture committedBefore = fixture("int029-before", PostRecipientStatus.AVAILABLE);
		jdbc.update("UPDATE user_account SET status = 'BLOCKED' WHERE id = ?", committedBefore.recipientId());
		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(committedBefore)).isZero();

		Fixture snapshotBeforeChange = fixture("int029-snapshot", PostRecipientStatus.AVAILABLE);
		CountDownLatch snapshotRead = new CountDownLatch(1);
		CountDownLatch allowWorker = new CountDownLatch(1);
		AccountRepository snapshotAccounts = snapshotAccountRepository(snapshotBeforeChange.recipientId(),
			snapshotRead, allowWorker);
		RecipientNotificationFanOutWorker controlledWorker = new RecipientNotificationFanOutWorker(
			outboxEvents, notifications, postRecipients, posts, snapshotAccounts, safety,
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			var future = executor.submit(() -> controlledWorker.processBatch(command(10)));
			assertThat(snapshotRead.await(10, TimeUnit.SECONDS)).isTrue();
			jdbc.update("UPDATE user_account SET status = 'BLOCKED' WHERE id = ?", snapshotBeforeChange.recipientId());
			allowWorker.countDown();
			assertThat(future.get(10, TimeUnit.SECONDS).outcomes())
				.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		} finally {
			allowWorker.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
		assertThat(notificationCount(snapshotBeforeChange)).isEqualTo(1);

		Fixture subsequent = fixtureForExistingUsers("int029-subsequent", snapshotBeforeChange.senderId(),
			snapshotBeforeChange.recipientId(), REGION_A, REGION_A, PostRecipientStatus.AVAILABLE,
			NOW.plusSeconds(3600));
		subsequent = subsequent.withSource(source(subsequent.postRecipientId(), "{}", 0));
		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(subsequent)).isZero();
	}

	@Test
	@DisplayName("post·deadline 변경 commit 순서에 따라 현재 snapshot을 억제하고 이미 읽은 snapshot은 한 번 반영한다")
	void linearizesPostAndDeadlineSnapshotForCurrentAndSubsequentEvents() throws Exception {
		Fixture committedBefore = fixture("int029-post-before", PostRecipientStatus.AVAILABLE);
		jdbc.update("UPDATE direction_post SET status = 'HIDDEN', expires_at = ? WHERE id = ?",
			Timestamp.from(NOW), committedBefore.postId());
		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(committedBefore)).isZero();

		Fixture snapshotBeforeChange = fixture("int029-post-snapshot", PostRecipientStatus.AVAILABLE);
		CountDownLatch snapshotRead = new CountDownLatch(1);
		CountDownLatch allowWorker = new CountDownLatch(1);
		DirectionPostRepository snapshotPosts = snapshotPostRepository(snapshotBeforeChange.postId(),
			snapshotRead, allowWorker);
		RecipientNotificationFanOutWorker controlledWorker = new RecipientNotificationFanOutWorker(
			outboxEvents, notifications, postRecipients, snapshotPosts, accounts, safety,
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			var future = executor.submit(() -> controlledWorker.processBatch(command(10)));
			assertThat(snapshotRead.await(10, TimeUnit.SECONDS)).isTrue();
			jdbc.update("UPDATE direction_post SET status = 'HIDDEN', expires_at = ? WHERE id = ?",
				Timestamp.from(NOW), snapshotBeforeChange.postId());
			allowWorker.countDown();
			assertThat(future.get(10, TimeUnit.SECONDS).outcomes())
				.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		} finally {
			allowWorker.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
		assertThat(notificationCount(snapshotBeforeChange)).isEqualTo(1);

		Fixture subsequent = fixtureForExistingUsers("int029-post-subsequent",
			snapshotBeforeChange.senderId(), snapshotBeforeChange.recipientId(), REGION_A, REGION_A,
			PostRecipientStatus.AVAILABLE, NOW.plusSeconds(3600));
		jdbc.update("UPDATE direction_post SET status = 'HIDDEN', expires_at = ? WHERE id = ?",
			Timestamp.from(NOW), subsequent.postId());
		subsequent = subsequent.withSource(source(subsequent.postRecipientId(), "{}", 0));
		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(subsequent)).isZero();
	}

	@Test
	@DisplayName("손상된 aggregate 참조는 domain write 없이 permanent DEAD로 격리한다")
	void deadLettersMalformedAggregateReference() {
		long malformed = sourceWithAggregate(OutboxAggregateType.DIRECTION_POST, Long.MAX_VALUE, "{}", 0);

		assertThat(worker.processBatch(command(10)).outcomes())
			.containsExactly(RecipientNotificationFanOutWorker.Outcome.DEAD);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isZero();
		assertThat(sourceStatus(malformed)).isEqualTo(OutboxStatus.DEAD);
	}

	private static Stream<Arguments> preferenceCases() {
		return Stream.of(Arguments.of(null, 1L), Arguments.of(true, 1L), Arguments.of(false, 0L));
	}

	private static Stream<Arguments> recipientStatusCases() {
		return Stream.of(
			Arguments.of(PostRecipientStatus.AVAILABLE, true),
			Arguments.of(PostRecipientStatus.DISCOVERED, true),
			Arguments.of(PostRecipientStatus.OPENED, true),
			Arguments.of(PostRecipientStatus.SKIP_PENDING, true),
			Arguments.of(PostRecipientStatus.ANSWERED, false),
			Arguments.of(PostRecipientStatus.SKIPPED, false),
			Arguments.of(PostRecipientStatus.EXPIRED, false),
			Arguments.of(PostRecipientStatus.BLOCKED, false));
	}

	private static Stream<Arguments> eligibilityGateCases() {
		return Stream.of(GateCase.values()).map(gate -> Arguments.of(gate, gate == GateCase.ELIGIBLE));
	}

	private Fixture fixture(String prefix, PostRecipientStatus status) {
		return fixture(prefix, status, REGION_A, REGION_A, NOW.plusSeconds(3600));
	}

	private Fixture fixture(String prefix, PostRecipientStatus status, String postRegion, String matchedRegion,
		Instant expiresAt) {
		Fixture withoutSource = fixtureWithoutSource(prefix, status, postRegion, matchedRegion, expiresAt);
		long sourceId = source(withoutSource.postRecipientId(), "{}", 0);
		return withoutSource.withSource(sourceId);
	}

	private Fixture fixtureWithoutSource(String prefix, PostRecipientStatus status, String postRegion,
		String matchedRegion, Instant expiresAt) {
		long senderId = account(prefix + "-sender", postRegion);
		long recipientId = account(prefix + "-recipient", matchedRegion);
		return fixtureForExistingUsers(prefix, senderId, recipientId, postRegion, matchedRegion, status, expiresAt)
			.withSource(0);
	}

	private Fixture fixtureForExistingUsers(String prefix, long senderId, long recipientId, String postRegion,
		String matchedRegion, PostRecipientStatus status, Instant expiresAt) {
		long questionId = question(senderId, prefix);
		long postId = post(senderId, questionId, prefix, postRegion, "ACTIVE", expiresAt, null);
		long postRecipientId = recipient(postId, recipientId, status, matchedRegion);
		jdbc.update("""
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at,
				 last_received_at, updated_at)
			VALUES (?, 1, 7, ?, ?, ?)
			ON CONFLICT (user_id) DO NOTHING
			""", recipientId, Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW.minusSeconds(30)),
			Timestamp.from(NOW));
		return new Fixture(senderId, recipientId, postId, postRecipientId, 0);
	}

	private long account(String nickname, String region) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, region, nickname);
	}

	private long question(long approverId, String prefix) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', ?, 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, "GH123 " + prefix, Timestamp.from(NOW.minusSeconds(100)),
			Timestamp.from(NOW.minusSeconds(90)), approverId);
	}

	private long post(long senderId, long questionId, String prefix, String region, String status,
		Instant expiresAt, Instant deletedAt) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at, deleted_at)
			VALUES (?, ?, ?, ?, ?, ?, 'PASSED', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, status, prefix + "-post-" + KEYS.incrementAndGet(),
			"GH123 body " + prefix, region, Timestamp.from(NOW.minusSeconds(100)),
			Timestamp.from(NOW.minusSeconds(90)), Timestamp.from(expiresAt),
			deletedAt == null ? null : Timestamp.from(deletedAt));
	}

	private long recipient(long postId, long recipientId, PostRecipientStatus status, String matchedRegion) {
		String[] columns = switch (status) {
			case DISCOVERED -> new String[] {"discovered_at"};
			case OPENED -> new String[] {"discovered_at", "opened_at"};
			case ANSWERED -> new String[] {"discovered_at", "opened_at", "capacity_released_at"};
			case SKIP_PENDING -> new String[] {"skip_requested_at"};
			case SKIPPED -> new String[] {"skip_requested_at", "skipped_at", "capacity_released_at"};
			case EXPIRED -> new String[] {"expired_at", "capacity_released_at"};
			case BLOCKED -> new String[] {"blocked_at", "capacity_released_at"};
			default -> new String[0];
		};
		String columnList = columns.length == 0 ? "" : ", " + String.join(", ", columns);
		String placeholderList = columns.length == 0 ? "" : ", "
			+ Arrays.stream(columns).map(column -> "?").collect(Collectors.joining(", "));
		Object[] base = {postId, recipientId, status.name(), matchedRegion, Timestamp.from(NOW.minusSeconds(60))};
		Object[] params = Arrays.copyOf(base, base.length + columns.length);
		Arrays.fill(params, base.length, params.length, Timestamp.from(NOW.minusSeconds(30)));
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg,
				 matched_region_code, matched_at, inbound_bearing_deg, distance_m%s)
			VALUES (?, ?, ?, 'NEAR', 12.345, ?, ?, 234.567, 9876%s)
			RETURNING id
			""".formatted(columnList, placeholderList), Long.class, params);
	}

	private long source(long postRecipientId, String payload, int attemptCount) {
		return sourceWithAggregate(OutboxAggregateType.POST_RECIPIENT, postRecipientId, payload, attemptCount);
	}

	private long sourceWithAggregate(OutboxAggregateType aggregateType, long aggregateId, String payload,
		int attemptCount) {
		OutboxEvent saved = outboxEvents.save(OutboxEvent.pending(aggregateType, aggregateId,
			OutboxEventType.RECIPIENTS_CONFIRMED, "gh123-source-" + KEYS.incrementAndGet(), payload, NOW));
		if (attemptCount > 0) {
			jdbc.update("UPDATE outbox_event SET attempt_count = ? WHERE id = ?", attemptCount, saved.id());
		}
		return saved.id();
	}

	private long device(long userId, PushDeviceStatus status, String fingerprint) {
		return notifications.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID,
			new byte[] {1, 2, 3, 4}, fingerprint, status, NOW,
			status == PushDeviceStatus.REVOKED ? NOW : null)).id();
	}

	private void presence(long userId, String region, double longitude, double latitude) {
		jdbc.update("""
			INSERT INTO active_user_presence
				(user_id, position, coarse_region_code, accuracy_m, receive_allowed,
				 location_at, expires_at)
			VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, 5, TRUE, ?, ?)
			""", userId, longitude, latitude, region, Timestamp.from(NOW.minusSeconds(10)),
			Timestamp.from(NOW.plusSeconds(3600)));
	}

	private void block(long blockerId, long blockedId, Instant releasedAt) {
		jdbc.update("""
			INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at)
			VALUES (?, ?, ?, ?)
			""", blockerId, blockedId, Timestamp.from(NOW.minusSeconds(10)),
			releasedAt == null ? null : Timestamp.from(releasedAt));
	}

	private Notification notification(Fixture fixture) {
		return new Notification(null, fixture.recipientId(), fixture.sourceId(),
			NotificationType.DIRECTION_POST_RECEIVED,
			"direction-post-received:" + fixture.postRecipientId(), fixture.postId(), null,
			NotificationStatus.UNREAD, NOW.minusSeconds(1), null);
	}

	private RecipientNotificationFanOutWorker.BatchCommand command(int limit) {
		return commandAt(NOW, limit);
	}

	private RecipientNotificationFanOutWorker.BatchCommand commandAt(Instant at, int limit) {
		return new RecipientNotificationFanOutWorker.BatchCommand(limit, OWNER, at, at.plusSeconds(60),
			new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(attempt)));
	}

	private long notificationCount(Fixture fixture) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM notification
			WHERE recipient_id = ? AND dedup_key = ?
			""", Long.class, fixture.recipientId(), "direction-post-received:" + fixture.postRecipientId());
	}

	private long deliveryCount(Fixture fixture) {
		return jdbc.queryForObject("""
			SELECT count(*)
			FROM notification_delivery nd
			JOIN notification n ON n.id = nd.notification_id
			WHERE n.recipient_id = ? AND n.dedup_key = ?
			""", Long.class, fixture.recipientId(), "direction-post-received:" + fixture.postRecipientId());
	}

	private long notificationId(Fixture fixture) {
		return jdbc.queryForObject("""
			SELECT id FROM notification WHERE recipient_id = ? AND dedup_key = ?
			""", Long.class, fixture.recipientId(), "direction-post-received:" + fixture.postRecipientId());
	}

	private List<Long> deliveryIds(long notificationId) {
		return jdbc.queryForList("""
			SELECT id FROM notification_delivery WHERE notification_id = ? ORDER BY id
			""", Long.class, notificationId);
	}

	private OutboxStatus sourceStatus(long sourceId) {
		return OutboxStatus.valueOf(jdbc.queryForObject(
			"SELECT status FROM outbox_event WHERE id = ?", String.class, sourceId));
	}

	private Instant sourceNextAttemptAt(long sourceId) {
		return jdbc.queryForObject("SELECT next_attempt_at FROM outbox_event WHERE id = ?", Timestamp.class, sourceId)
			.toInstant();
	}

	private Instant sourceLeaseExpiresAt(long sourceId) {
		return jdbc.queryForObject("SELECT lease_expires_at FROM outbox_event WHERE id = ?", Timestamp.class, sourceId)
			.toInstant();
	}

	private void assertFailed(long sourceId, int attemptCount, Instant nextAttemptAt) {
		Map<String, Object> source = jdbc.queryForMap("""
			SELECT status, attempt_count, next_attempt_at, processed_at, lease_owner, lease_expires_at
			FROM outbox_event WHERE id = ?
			""", sourceId);
		assertThat(source.get("status")).isEqualTo("FAILED");
		assertThat(source.get("attempt_count")).isEqualTo(attemptCount);
		assertThat(((Timestamp)source.get("next_attempt_at")).toInstant()).isEqualTo(nextAttemptAt);
		assertThat(source.get("processed_at")).isNull();
		assertThat(source.get("lease_owner")).isNull();
		assertThat(source.get("lease_expires_at")).isNull();
	}

	private Map<String, Object> recipientSnapshot(long postRecipientId) {
		return jdbc.queryForMap("SELECT * FROM post_recipient WHERE id = ?", postRecipientId);
	}

	private Map<String, Object> receiveStateSnapshot(long recipientId) {
		return jdbc.queryForMap("SELECT * FROM recipient_receive_state WHERE user_id = ?", recipientId);
	}

	private Map<String, Object> notificationSnapshot(long notificationId) {
		return jdbc.queryForMap("SELECT * FROM notification WHERE id = ?", notificationId);
	}

	private void installDeliveryFailure(long pushDeviceId) {
		jdbc.execute("""
			CREATE OR REPLACE FUNCTION gh123_fail_delivery() RETURNS trigger LANGUAGE plpgsql AS $$
			BEGIN
			  IF NEW.push_device_id = %d THEN
			    RAISE EXCEPTION 'gh123 delivery retry' USING ERRCODE = '40001';
			  END IF;
			  RETURN NEW;
			END $$
			""".formatted(pushDeviceId));
		jdbc.execute("""
			CREATE TRIGGER gh123_fail_delivery_trigger BEFORE INSERT ON notification_delivery
			FOR EACH ROW EXECUTE FUNCTION gh123_fail_delivery()
			""");
	}

	private void installNotificationFailures(long transientRecipientId, long integrityRecipientId,
		long maxAttemptRecipientId) {
		jdbc.execute("CREATE SEQUENCE gh123_transient_once_seq START 1");
		jdbc.execute("""
			CREATE OR REPLACE FUNCTION gh123_fail_notification() RETURNS trigger LANGUAGE plpgsql AS $$
			BEGIN
			  IF NEW.recipient_id = %d AND nextval('gh123_transient_once_seq') = 1 THEN
			    RAISE EXCEPTION 'gh123 transient notification failure' USING ERRCODE = '40001';
			  ELSIF NEW.recipient_id = %d THEN
			    RAISE EXCEPTION 'gh123 integrity notification failure' USING ERRCODE = '23514';
			  ELSIF NEW.recipient_id = %d THEN
			    RAISE EXCEPTION 'gh123 max attempt notification failure' USING ERRCODE = '40001';
			  END IF;
			  RETURN NEW;
			END $$
			""".formatted(transientRecipientId, integrityRecipientId, maxAttemptRecipientId));
		jdbc.execute("""
			CREATE TRIGGER gh123_fail_notification_trigger BEFORE INSERT ON notification
			FOR EACH ROW EXECUTE FUNCTION gh123_fail_notification()
			""");
	}

	private void installCompleteFailure(long sourceId) {
		jdbc.execute("""
			CREATE OR REPLACE FUNCTION gh123_fail_complete() RETURNS trigger LANGUAGE plpgsql AS $$
			BEGIN
			  IF OLD.id = %d AND NEW.status = 'PROCESSED' THEN
			    RAISE EXCEPTION 'gh123 complete retry' USING ERRCODE = '40001';
			  END IF;
			  RETURN NEW;
			END $$
			""".formatted(sourceId));
		jdbc.execute("""
			CREATE TRIGGER gh123_fail_complete_trigger BEFORE UPDATE ON outbox_event
			FOR EACH ROW EXECUTE FUNCTION gh123_fail_complete()
			""");
	}

	private void installFailureRecordingFailure(long sourceId) {
		jdbc.execute("""
			CREATE OR REPLACE FUNCTION gh123_fail_recording() RETURNS trigger LANGUAGE plpgsql AS $$
			BEGIN
			  IF OLD.id = %d AND NEW.status IN ('FAILED', 'DEAD') THEN
			    RAISE EXCEPTION 'gh123 failure recording error' USING ERRCODE = '40001';
			  END IF;
			  RETURN NEW;
			END $$
			""".formatted(sourceId));
		jdbc.execute("""
			CREATE TRIGGER gh123_fail_recording_trigger BEFORE UPDATE ON outbox_event
			FOR EACH ROW EXECUTE FUNCTION gh123_fail_recording()
			""");
	}

	private void dropFailureInjection() {
		jdbc.execute("DROP TRIGGER IF EXISTS gh123_fail_delivery_trigger ON notification_delivery");
		jdbc.execute("DROP TRIGGER IF EXISTS gh123_fail_notification_trigger ON notification");
		jdbc.execute("DROP TRIGGER IF EXISTS gh123_fail_complete_trigger ON outbox_event");
		jdbc.execute("DROP TRIGGER IF EXISTS gh123_fail_recording_trigger ON outbox_event");
		jdbc.execute("DROP FUNCTION IF EXISTS gh123_fail_delivery()");
		jdbc.execute("DROP FUNCTION IF EXISTS gh123_fail_notification()");
		jdbc.execute("DROP FUNCTION IF EXISTS gh123_fail_complete()");
		jdbc.execute("DROP FUNCTION IF EXISTS gh123_fail_recording()");
		jdbc.execute("DROP SEQUENCE IF EXISTS gh123_transient_once_seq");
	}

	private AccountRepository snapshotAccountRepository(long targetRecipientId, CountDownLatch snapshotRead,
		CountDownLatch allowWorker) {
		return new AccountRepository() {
			@Override
			public Account save(Account account) {
				return accounts.save(account);
			}

			@Override
			public Account updateProfile(Account account) {
				return accounts.updateProfile(account);
			}

			@Override
			public Account updateStatus(Account account) {
				return accounts.updateStatus(account);
			}

			@Override
			public Optional<Account> findById(long id) {
				Optional<Account> snapshot = accounts.findById(id);
				if (id == targetRecipientId) {
					snapshotRead.countDown();
					try {
						if (!allowWorker.await(10, TimeUnit.SECONDS)) {
							throw new IllegalStateException("account snapshot barrier timed out");
						}
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException("account snapshot barrier interrupted", interrupted);
					}
				}
				return snapshot;
			}
		};
	}

	private DirectionPostRepository snapshotPostRepository(long targetPostId, CountDownLatch snapshotRead,
		CountDownLatch allowWorker) {
		return new DirectionPostRepository() {
			@Override
			public DirectionPost save(DirectionPost post) {
				return posts.save(post);
			}

			@Override
			public Optional<DirectionPost> findById(long id) {
				Optional<DirectionPost> snapshot = posts.findById(id);
				if (id == targetPostId) {
					snapshotRead.countDown();
					awaitBarrier(allowWorker, "post snapshot");
				}
				return snapshot;
			}

			@Override
			public Optional<DirectionPost> findBySenderAndIdempotencyKey(long senderId, String idempotencyKey) {
				return posts.findBySenderAndIdempotencyKey(senderId, idempotencyKey);
			}

			@Override
			public Optional<DirectionPost> updateRequestFingerprintIfNull(long id,
				DirectionRequestFingerprint requestFingerprint) {
				return posts.updateRequestFingerprintIfNull(id, requestFingerprint);
			}

			@Override
			public Optional<DirectionPost> findByIdAndSenderId(long id, long senderId) {
				return posts.findByIdAndSenderId(id, senderId);
			}

			@Override
			public DirectionPost advanceAnswersReadAt(long id, Instant at) {
				return posts.advanceAnswersReadAt(id, at);
			}
		};
	}

	private void awaitBarrier(CountDownLatch barrier, String name) {
		try {
			if (!barrier.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException(name + " barrier timed out");
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(name + " barrier interrupted", interrupted);
		}
	}

	private record Fixture(long senderId, long recipientId, long postId, long postRecipientId, long sourceId) {
		private Fixture withSource(long sourceId) {
			return new Fixture(senderId, recipientId, postId, postRecipientId, sourceId);
		}
	}

	private enum GateCase {
		ELIGIBLE {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
			}
		},
		SENDER_BLOCKED {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE user_account SET status = 'BLOCKED' WHERE id = ?", fixture.senderId());
			}
		},
		RECIPIENT_BLOCKED {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE user_account SET status = 'BLOCKED' WHERE id = ?", fixture.recipientId());
			}
		},
		SENDER_DELETED {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE user_account SET status = 'DELETED', deleted_at = ? WHERE id = ?",
					Timestamp.from(NOW), fixture.senderId());
			}
		},
		RECIPIENT_DELETED {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE user_account SET status = 'DELETED', deleted_at = ? WHERE id = ?",
					Timestamp.from(NOW), fixture.recipientId());
			}
		},
		POST_HIDDEN {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE direction_post SET status = 'HIDDEN' WHERE id = ?", fixture.postId());
			}
		},
		POST_DELETED {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE direction_post SET status = 'DELETED', deleted_at = ? WHERE id = ?",
					Timestamp.from(NOW), fixture.postId());
			}
		},
		DEADLINE_BOUNDARY {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE direction_post SET expires_at = ? WHERE id = ?", Timestamp.from(NOW), fixture.postId());
			}
		},
		DEADLINE_PAST {
			@Override
			void apply(JdbcTemplate jdbc, Fixture fixture) {
				jdbc.update("UPDATE direction_post SET expires_at = ? WHERE id = ?",
					Timestamp.from(NOW.minusSeconds(1)), fixture.postId());
			}
		};

		abstract void apply(JdbcTemplate jdbc, Fixture fixture);
	}
}
