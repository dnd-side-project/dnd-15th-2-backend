/**
 * Created at: 2026-08-14T18:01:50+09:00
 * Source scenario: TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-013 through INT-016,
 * TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-026, INT-029
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecipientNotificationFanOutWorkerConcurrencyIntegrationTest
	extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH123-CONCURRENCY";
	private static final Instant NOW = Instant.parse("2026-08-14T09:00:00Z");
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long NOTIFICATION_GATE_KEY = 123_160_001L;
	private static final long DELIVERY_GATE_KEY = 123_260_001L;

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private DataSource dataSource;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private RecipientNotificationFanOutWorker worker;

	@BeforeEach
	void resetFixtures() {
		dropGate("gh123_notification_gate", "notification", "gh123_pause_notification_insert");
		dropGate("gh123_delivery_gate", "notification_delivery", "gh123_pause_delivery_insert");
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM notification_preference");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH123 Concurrency', 'REGION')
			""", REGION);
	}

	@Test
	@DisplayName("같은 source Outbox를 두 worker가 동시에 claim해도 logical fan-out은 한 번만 커밋한다")
	void claimsSameSourceEventOnceAcrossWorkers() throws Exception {
		Fixture fixture = fixture("same-source", 1, 1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<RecipientNotificationFanOutWorker.BatchResult> first = executor.submit(
				() -> processAfterSignal("same-source-a", NOW, ready, start));
			Future<RecipientNotificationFanOutWorker.BatchResult> second = executor.submit(
				() -> processAfterSignal("same-source-b", NOW, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			RecipientNotificationFanOutWorker.BatchResult firstResult = first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
			RecipientNotificationFanOutWorker.BatchResult secondResult = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

			assertThat(firstResult.claimed() + secondResult.claimed()).isEqualTo(1);
			assertThat(List.of(firstResult, secondResult))
				.flatExtracting(RecipientNotificationFanOutWorker.BatchResult::outcomes)
				.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
			assertFanOut(fixture, 1, 1);
			assertSource(fixture.sourceIds().get(0), OutboxStatus.PROCESSED, 1, 1);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("만료 lease를 reclaim하면 stale worker의 fan-out은 rollback되고 새 generation만 커밋한다")
	void rollsBackStaleWorkerAfterLeaseReclaim() throws Exception {
		Fixture fixture = fixture("stale-lease", 1, 1);
		long sourceId = fixture.sourceIds().get(0);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
				boolean previousAutoCommit = connection.getAutoCommit();
				connection.setAutoCommit(false);
				try {
					lockPostRecipient(connection, fixture.postRecipientId());
					Future<RecipientNotificationFanOutWorker.BatchResult> stale = executor.submit(
						() -> worker.processBatch(command("stale-owner", NOW, NOW.plusSeconds(1))));
					awaitLease(sourceId, 1, "stale-owner");
					Future<RecipientNotificationFanOutWorker.BatchResult> reclaimed = executor.submit(
						() -> worker.processBatch(command("reclaim-owner", NOW.plusSeconds(2), NOW.plusSeconds(62))));
					awaitLease(sourceId, 2, "reclaim-owner");
					connection.commit();

					assertThat(stale.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).outcomes())
						.containsExactly(RecipientNotificationFanOutWorker.Outcome.STALE_LEASE);
					assertThat(reclaimed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).outcomes())
						.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
				} catch (Exception exception) {
					connection.rollback();
					throw new AssertionError(exception);
				} finally {
					connection.setAutoCommit(previousAutoCommit);
				}
				return null;
			});

			assertFanOut(fixture, 1, 1);
			assertThat(jdbc.queryForObject("""
				SELECT created_at FROM notification
				WHERE recipient_id = ? AND dedup_key = ?
				""", Timestamp.class, fixture.recipientId(), notificationDedupKey(fixture)))
				.isEqualTo(Timestamp.from(NOW.plusSeconds(2)));
			assertSource(sourceId, OutboxStatus.PROCESSED, 2, 2);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("서로 다른 source event가 같은 logical Notification을 동시에 만들면 두 source와 한 결과로 대사된다")
	void deduplicatesDistinctSourceEventsForSameLogicalNotification() throws Exception {
		Fixture fixture = fixture("logical-dedup", 2, 1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<RecipientNotificationFanOutWorker.BatchResult> first = executor.submit(
				() -> processAfterSignal("logical-a", NOW, ready, start));
			Future<RecipientNotificationFanOutWorker.BatchResult> second = executor.submit(
				() -> processAfterSignal("logical-b", NOW, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			RecipientNotificationFanOutWorker.BatchResult firstResult = first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
			RecipientNotificationFanOutWorker.BatchResult secondResult = second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

			assertThat(firstResult.claimed()).isEqualTo(1);
			assertThat(secondResult.claimed()).isEqualTo(1);
			assertThat(firstResult.outcomes()).containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
			assertThat(secondResult.outcomes()).containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
			assertFanOut(fixture, 1, 1);
			fixture.sourceIds().forEach(sourceId -> assertSource(sourceId, OutboxStatus.PROCESSED, 1, 1));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("preference·block·recipient·account·post 변경이 eligibility read 전에 commit되면 알림을 억제한다")
	void suppressesWhenEligibilityChangesCommitBeforeRead() throws Exception {
		Fixture preference = fixture("before-preference", 1, 1);
		runCommittedChangeBeforeWorker(preference, "before-preference", () -> jdbc.update("""
			INSERT INTO notification_preference (notification_type, user_id, enabled)
			VALUES ('DIRECTION_POST_RECEIVED', ?, FALSE)
			""", preference.recipientId()));

		Fixture block = fixture("before-block", 1, 1);
		runCommittedChangeBeforeWorker(block, "before-block", () -> jdbc.update("""
			INSERT INTO user_block (blocker_id, blocked_id, created_at)
			VALUES (?, ?, ?)
			""", block.recipientId(), block.senderId(), Timestamp.from(NOW)));

		Fixture status = fixture("before-status", 1, 1);
		runCommittedChangeBeforeWorker(status, "before-status", () -> jdbc.update("""
			UPDATE post_recipient
			SET status = 'BLOCKED', blocked_at = ?, capacity_released_at = ?
			WHERE id = ?
			""", Timestamp.from(NOW), Timestamp.from(NOW), status.postRecipientId()));

		Fixture account = fixture("before-account", 1, 1);
		runCommittedChangeBeforeWorker(account, "before-account", () -> jdbc.update("""
			UPDATE user_account
			SET status = 'BLOCKED', version = version + 1, updated_at = ?
			WHERE id = ?
			""", Timestamp.from(NOW), account.senderId()));

		Fixture post = fixture("before-post", 1, 1);
		runCommittedChangeBeforeWorker(post, "before-post", () -> jdbc.update(
			"UPDATE direction_post SET status = 'HIDDEN' WHERE id = ?", post.postId()));

		List.of(preference, block, status, account, post).forEach(fixture -> {
			assertFanOut(fixture, 0, 0);
			assertSource(fixture.sourceIds().get(0), OutboxStatus.PROCESSED, 1, 1);
		});
	}

	@Test
	@DisplayName("preference·block·account·post 변경이 eligibility snapshot 뒤 commit되면 현재 event는 허용한다")
	void keepsEligibilitySnapshotWhenChangesCommitAfterRead() throws Exception {
		Fixture preference = fixture("after-preference", 1, 1);
		runChangeAfterEligibilityRead(preference, "after-preference", () -> jdbc.update("""
			INSERT INTO notification_preference (notification_type, user_id, enabled)
			VALUES ('DIRECTION_POST_RECEIVED', ?, FALSE)
			""", preference.recipientId()));

		Fixture block = fixture("after-block", 1, 1);
		runChangeAfterEligibilityRead(block, "after-block", () -> jdbc.update("""
			INSERT INTO user_block (blocker_id, blocked_id, created_at)
			VALUES (?, ?, ?)
			""", block.senderId(), block.recipientId(), Timestamp.from(NOW)));

		Fixture account = fixture("after-account", 1, 1);
		runChangeAfterEligibilityRead(account, "after-account", () -> jdbc.update("""
			UPDATE user_account
			SET status = 'BLOCKED', version = version + 1, updated_at = ?
			WHERE id = ?
			""", Timestamp.from(NOW), account.senderId()));

		Fixture post = fixture("after-post", 1, 1);
		runChangeAfterEligibilityRead(post, "after-post", () -> jdbc.update(
			"UPDATE direction_post SET status = 'HIDDEN' WHERE id = ?", post.postId()));

		List.of(preference, block, account, post).forEach(fixture -> {
			assertFanOut(fixture, 1, 1);
			assertSource(fixture.sourceIds().get(0), OutboxStatus.PROCESSED, 1, 1);
		});
	}

	@Test
	@DisplayName("recipient status writer는 fan-out row lock 뒤에 직렬화되고 현재 snapshot 결과를 보존한다")
	void serializesRecipientStatusWriterBehindFanOutLock() throws Exception {
		Fixture fixture = fixture("status-lock", 1, 1);
		installGate("gh123_notification_gate", "notification", "gh123_pause_notification_insert",
			NOTIFICATION_GATE_KEY);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch writerStarted = new CountDownLatch(1);
		try (Connection gateConnection = dataSource.getConnection()) {
			lockAdvisory(gateConnection, NOTIFICATION_GATE_KEY);
			try {
				Future<RecipientNotificationFanOutWorker.BatchResult> processing = executor.submit(
					() -> worker.processBatch(command("status-lock-worker", NOW, NOW.plusSeconds(60))));
				awaitAdvisoryWait();
				Future<Integer> statusWriter = executor.submit(() -> {
					writerStarted.countDown();
					return jdbc.update("""
						UPDATE post_recipient
						SET status = 'BLOCKED', blocked_at = ?, capacity_released_at = ?
						WHERE id = ?
						""", Timestamp.from(NOW.plusSeconds(1)), Timestamp.from(NOW.plusSeconds(1)),
						fixture.postRecipientId());
				});
				assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
				unlockAdvisory(gateConnection, NOTIFICATION_GATE_KEY);

				assertThat(processing.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).outcomes())
					.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
				assertThat(statusWriter.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(1);
			} finally {
				unlockAdvisory(gateConnection, NOTIFICATION_GATE_KEY);
			}
		} finally {
			executor.shutdownNow();
			dropGate("gh123_notification_gate", "notification", "gh123_pause_notification_insert");
		}

		assertFanOut(fixture, 1, 1);
		assertThat(jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class,
			fixture.postRecipientId())).isEqualTo("BLOCKED");
		assertSource(fixture.sourceIds().get(0), OutboxStatus.PROCESSED, 1, 1);
	}

	@Test
	@DisplayName("PushDevice revoke는 active 조회 전에는 제외되고 조회 뒤에는 PENDING snapshot을 보존한다")
	void appliesPushDeviceRevokeAtActiveDeviceSnapshot() throws Exception {
		Fixture beforeRead = fixture("device-before", 1, 1);
		runCommittedChangeBeforeWorker(beforeRead, "device-before", () -> revoke(beforeRead.deviceIds().get(0)));
		assertFanOut(beforeRead, 1, 0);
		assertSource(beforeRead.sourceIds().get(0), OutboxStatus.PROCESSED, 1, 1);

		Fixture afterRead = fixture("device-after", 1, 1);
		installGate("gh123_delivery_gate", "notification_delivery", "gh123_pause_delivery_insert",
			DELIVERY_GATE_KEY);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try (Connection gateConnection = dataSource.getConnection()) {
			lockAdvisory(gateConnection, DELIVERY_GATE_KEY);
			try {
				Future<RecipientNotificationFanOutWorker.BatchResult> processing = executor.submit(
					() -> worker.processBatch(command("device-after", NOW, NOW.plusSeconds(60))));
				awaitAdvisoryWait();
				Future<Integer> revoke = executor.submit(() -> revoke(afterRead.deviceIds().get(0)));
				assertThat(revoke.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(1);
				unlockAdvisory(gateConnection, DELIVERY_GATE_KEY);
				assertThat(processing.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).outcomes())
					.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
			} finally {
				unlockAdvisory(gateConnection, DELIVERY_GATE_KEY);
			}
		} finally {
			executor.shutdownNow();
			dropGate("gh123_delivery_gate", "notification_delivery", "gh123_pause_delivery_insert");
		}

		assertFanOut(afterRead, 1, 1);
		assertThat(jdbc.queryForObject("SELECT device_status FROM push_device WHERE id = ?", String.class,
			afterRead.deviceIds().get(0))).isEqualTo("REVOKED");
		assertThat(jdbc.queryForObject("""
			SELECT status FROM notification_delivery WHERE push_device_id = ?
			""", String.class, afterRead.deviceIds().get(0))).isEqualTo("PENDING");
		assertSource(afterRead.sourceIds().get(0), OutboxStatus.PROCESSED, 1, 1);
	}

	private void runCommittedChangeBeforeWorker(Fixture fixture, String owner, SqlChange change) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch committed = new CountDownLatch(1);
		try {
			Future<Integer> writer = executor.submit(() -> {
				ready.countDown();
				assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
				try {
					return change.execute();
				} finally {
					committed.countDown();
				}
			});
			Future<RecipientNotificationFanOutWorker.BatchResult> processing = executor.submit(() -> {
				ready.countDown();
				assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
				assertThat(committed.await(5, TimeUnit.SECONDS)).isTrue();
				return worker.processBatch(command(owner, NOW, NOW.plusSeconds(60)));
			});
			assertThat(writer.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(1);
			assertThat(processing.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).outcomes())
				.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		} finally {
			committed.countDown();
			executor.shutdownNow();
		}
	}

	private void runChangeAfterEligibilityRead(Fixture fixture, String owner, SqlChange change) throws Exception {
		installGate("gh123_notification_gate", "notification", "gh123_pause_notification_insert",
			NOTIFICATION_GATE_KEY);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try (Connection gateConnection = dataSource.getConnection()) {
			lockAdvisory(gateConnection, NOTIFICATION_GATE_KEY);
			try {
				Future<RecipientNotificationFanOutWorker.BatchResult> processing = executor.submit(
					() -> worker.processBatch(command(owner, NOW, NOW.plusSeconds(60))));
				awaitAdvisoryWait();
				Future<Integer> writer = executor.submit(change::execute);
				assertThat(writer.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(1);
				unlockAdvisory(gateConnection, NOTIFICATION_GATE_KEY);
				assertThat(processing.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).outcomes())
					.containsExactly(RecipientNotificationFanOutWorker.Outcome.PROCESSED);
			} finally {
				unlockAdvisory(gateConnection, NOTIFICATION_GATE_KEY);
			}
		} finally {
			executor.shutdownNow();
			dropGate("gh123_notification_gate", "notification", "gh123_pause_notification_insert");
		}
	}

	private RecipientNotificationFanOutWorker.BatchResult processAfterSignal(String owner, Instant at,
		CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker start barrier timed out");
		return worker.processBatch(command(owner, at, at.plusSeconds(60)));
	}

	private RecipientNotificationFanOutWorker.BatchCommand command(String owner, Instant at,
		Instant leaseExpiresAt) {
		return new RecipientNotificationFanOutWorker.BatchCommand(1, owner, at, leaseExpiresAt,
			new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1)));
	}

	private Fixture fixture(String key, int sourceCount, int deviceCount) {
		long senderId = account("sender-" + key);
		long recipientId = account("recipient-" + key);
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, active_until,
				 approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', ?, 'TEXT', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, "question-" + key, Timestamp.from(NOW.minusSeconds(60)),
			Timestamp.from(NOW.plusSeconds(7200)), Timestamp.from(NOW.minusSeconds(60)), senderId);
		long postId = jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, 'concurrency fixture', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, "post-" + key, REGION,
			Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(30)),
			Timestamp.from(NOW.plusSeconds(3600)));
		long postRecipientId = jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg,
				 matched_region_code, matched_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION, Timestamp.from(NOW.minusSeconds(10)));
		List<Long> sourceIds = java.util.stream.IntStream.range(0, sourceCount)
			.mapToObj(index -> sourceEvent(postRecipientId, key + "-source-" + index).id())
			.toList();
		List<Long> deviceIds = java.util.stream.IntStream.range(0, deviceCount)
			.mapToObj(index -> jdbc.queryForObject("""
				INSERT INTO push_device
					(user_id, platform, token_ciphertext, token_fingerprint, device_status, last_seen_at)
				VALUES (?, 'ANDROID', ?, ?, 'ACTIVE', ?)
				RETURNING id
				""", Long.class, recipientId, new byte[] {1, 2, (byte) index},
				"gh123-concurrency-" + key + "-" + index, Timestamp.from(NOW)))
			.toList();
		return new Fixture(senderId, recipientId, postId, postRecipientId, sourceIds, deviceIds);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private OutboxEvent sourceEvent(long postRecipientId, String dedupKey) {
		return outboxEvents.save(OutboxEvent.pending(OutboxAggregateType.POST_RECIPIENT, postRecipientId,
			OutboxEventType.RECIPIENTS_CONFIRMED, dedupKey, "{}", NOW));
	}

	private int revoke(long deviceId) {
		return jdbc.update("""
			UPDATE push_device
			SET device_status = 'REVOKED', revoked_at = ?
			WHERE id = ?
			""", Timestamp.from(NOW.plusSeconds(1)), deviceId);
	}

	private void assertFanOut(Fixture fixture, long notificationCount, long deliveryCount) {
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification
			WHERE recipient_id = ? AND dedup_key = ?
			""", Long.class, fixture.recipientId(), notificationDedupKey(fixture))).isEqualTo(notificationCount);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery nd
			JOIN notification n ON n.id = nd.notification_id
			WHERE n.recipient_id = ? AND n.dedup_key = ?
			""", Long.class, fixture.recipientId(), notificationDedupKey(fixture))).isEqualTo(deliveryCount);
	}

	private String notificationDedupKey(Fixture fixture) {
		return "direction-post-received:" + fixture.postRecipientId();
	}

	private void assertSource(long sourceId, OutboxStatus status, int attemptCount, long leaseGeneration) {
		assertThat(jdbc.queryForMap("""
			SELECT status, attempt_count, lease_generation, lease_owner, lease_expires_at
			FROM outbox_event WHERE id = ?
			""", sourceId))
			.containsEntry("status", status.name())
			.containsEntry("attempt_count", attemptCount)
			.containsEntry("lease_generation", leaseGeneration)
			.containsEntry("lease_owner", null)
			.containsEntry("lease_expires_at", null);
	}

	private void lockPostRecipient(Connection connection, long postRecipientId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT id FROM post_recipient WHERE id = ? FOR UPDATE")) {
			statement.setLong(1, postRecipientId);
			try (ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).isTrue();
			}
		}
	}

	private void awaitLease(long sourceId, long generation, String owner) throws InterruptedException {
		long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (System.nanoTime() < deadline) {
			List<String> owners = jdbc.queryForList("""
				SELECT lease_owner FROM outbox_event
				WHERE id = ? AND lease_generation = ?
				""", String.class, sourceId, generation);
			if (owners.contains(owner)) return;
			Thread.sleep(10);
		}
		throw new AssertionError("lease was not acquired: generation=" + generation + ", owner=" + owner);
	}

	private void installGate(String triggerName, String tableName, String functionName, long advisoryKey) {
		jdbc.execute("""
			CREATE OR REPLACE FUNCTION %s()
			RETURNS TRIGGER
			LANGUAGE plpgsql
			AS $function$
			BEGIN
				PERFORM pg_advisory_xact_lock(%d);
				RETURN NEW;
			END;
			$function$
			""".formatted(functionName, advisoryKey));
		jdbc.execute("""
			CREATE TRIGGER %s
			BEFORE INSERT ON %s
			FOR EACH ROW EXECUTE FUNCTION %s()
			""".formatted(triggerName, tableName, functionName));
	}

	private void dropGate(String triggerName, String tableName, String functionName) {
		jdbc.execute("DROP TRIGGER IF EXISTS %s ON %s".formatted(triggerName, tableName));
		jdbc.execute("DROP FUNCTION IF EXISTS %s()".formatted(functionName));
	}

	private void lockAdvisory(Connection connection, long key) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
			statement.setLong(1, key);
			statement.execute();
		}
	}

	private void unlockAdvisory(Connection connection, long key) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
			statement.setLong(1, key);
			statement.execute();
		}
	}

	private void awaitAdvisoryWait() throws InterruptedException {
		long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (System.nanoTime() < deadline) {
			Long waiting = jdbc.queryForObject("""
				SELECT count(*) FROM pg_locks
				WHERE locktype = 'advisory' AND NOT granted
				""", Long.class);
			if (waiting != null && waiting > 0) return;
			Thread.sleep(10);
		}
		throw new AssertionError("worker did not reach the advisory gate");
	}

	@FunctionalInterface
	private interface SqlChange {
		int execute();
	}

	private record Fixture(long senderId, long recipientId, long postId, long postRecipientId,
		List<Long> sourceIds, List<Long> deviceIds) {
	}
}
