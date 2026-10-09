/**
 * Created at: 2026-10-09T18:43:07+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-013, INT-014, INT-018
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.service.AccountWithdrawal;
import com.dnd.qello.account.service.AccountWithdrawalCleanup;
import com.dnd.qello.account.service.AccountWithdrawalCompletionService;
import com.dnd.qello.account.service.AccountWithdrawalService;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
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
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenKeyRing;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.service.PushDeviceCommand;
import com.dnd.qello.notification.service.PushDeviceService;

import static org.assertj.core.api.Assertions.assertThat;

// 탈퇴 철회·만료·요청과 푸시 재등록이 같은 계정에서 겹칠 때의 최종 상태를 실제 PostGIS 트랜잭션으로 검증한다(#337).
//
// 두 작업이 모두 계정을 읽은 뒤 커밋 전에 만나도록 barrier를 둔다. 철회와 탈퇴 요청은 계정을 읽은 직후 처음 부르는
// clock.instant()에서, 만료 처리는 마지막 정리 단계에서 기다린다. 그래서 순서대로 실행돼 우연히 통과하는 경우 없이 매번
// 같은 version을 읽은 두 쓰기가 경합한다.
@SpringBootTest
@ActiveProfiles("test")
@Import(AccountWithdrawalConcurrencyIntegrationTest.TestConfig.class)
class AccountWithdrawalConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String COUNTRY_CODE = "KR";
	private static final Duration GRACE_PERIOD = Duration.ofDays(30);
	private static final String CURRENT_KEY_ID = "gh337-race-current";
	private static final byte[] ENCRYPTION_KEY = fixedKey((byte) 0x64);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte) 0x74);
	private static final int ROUNDS = 5;
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AccountWithdrawalService withdrawalService;

	@Autowired
	private AccountWithdrawalCompletionService completionService;

	@Autowired
	private PushDeviceService pushDeviceService;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@Autowired
	private ActiveUserPresenceRepository presenceRepository;

	@Autowired
	private RacePause pause;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetFixtures() {
		pause.reset();
		clock.reset();
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM device_credential");
		jdbc.update("DELETE FROM user_account");
		jdbc.update("""
				INSERT INTO region_code (code, parent_code, display_name, level)
				VALUES (?, NULL, 'Korea', 'COUNTRY')
				ON CONFLICT DO NOTHING
				""", COUNTRY_CODE);
	}

	@AfterEach
	void releasePause() {
		pause.reset();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-013: 예정 시각 직전 철회와 직후 만료 처리가 겹치면 하나만 성공하고 최종 상태는 ACTIVE+자격증명 ACTIVE 또는 DELETED+자격증명 전부 REVOKED 중 하나다")
	void cancelAndCompletionRaceLeavesOneConsistentState() throws Exception {
		for (int round = 0; round < ROUNDS * 2; round++) {
			// 짝수 회차는 그대로 경합시킨다. 홀수 회차는 만료 처리가 자격증명·푸시 기기를 폐기한 뒤 철회 커밋을 기다리게 해
			// 철회가 먼저 커밋하는 순서를 만든다. 이때 만료 처리의 폐기가 함께 rollback되는지가 위험 R1이다.
			boolean cancelCommitsFirst = round % 2 == 1;
			clock.reset();
			String nickname = "경합철회" + round;
			long userId = account(nickname);
			credential(userId);
			credential(userId);
			withdrawalService.request(userId);
			PushDevice reRegistered = pushDevice(userId, "gh337-int013-" + round);
			Instant deadline = clock.instant().plus(GRACE_PERIOD);
			Instant completedAt = deadline.plusSeconds(1);
			clock.set(deadline.minusSeconds(1));
			CountDownLatch cancelFinished = new CountDownLatch(1);
			pause.prepare(2);
			if (cancelCommitsFirst) {
				pause.holdCleanupUntil(cancelFinished);
			}

			Race<AccountWithdrawal, Boolean> race = race(
					() -> {
						pause.armClockForCurrentThread();
						try {
							return withdrawalService.cancel(userId);
						} finally {
							cancelFinished.countDown();
						}
					},
					() -> {
						pause.armCleanupForCurrentThread();
						return completionService.complete(userId, completedAt);
					});

			assertThat(pause.arrivals()).as("round %d overlap", round).isEqualTo(2);
			assertThat(race.successCount()).as("round %d", round).isEqualTo(1);
			if (cancelCommitsFirst) {
				assertThat(race.first().succeeded()).as("round %d cancel committed first", round).isTrue();
			}
			if (race.first().succeeded()) {
				assertCancelWon(race, userId, nickname, reRegistered.id());
			} else {
				assertCompletionWon(race, userId, completedAt, reRegistered.id());
			}
		}
	}

	private void assertCancelWon(Race<AccountWithdrawal, Boolean> race, long userId, String nickname,
			long pushDeviceId) {
		Map<String, Object> account = account(userId);
		assertThat(race.second().failure()).isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(account.get("status")).isEqualTo("ACTIVE");
		assertThat(account.get("withdrawal_requested_at")).isNull();
		assertThat(account.get("deleted_at")).isNull();
		assertThat(account.get("nickname")).isEqualTo(nickname);
		assertThat(credentialStatuses(userId)).containsExactly("ACTIVE", "ACTIVE");
		assertThat(pushStatus(pushDeviceId)).isEqualTo("ACTIVE");
	}

	private void assertCompletionWon(
			Race<AccountWithdrawal, Boolean> race, long userId, Instant completedAt, long pushDeviceId) {
		Map<String, Object> account = account(userId);
		assertThat(race.first().failure()).isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(race.second().value()).isTrue();
		assertThat(account.get("status")).isEqualTo("DELETED");
		assertThat(((Timestamp) account.get("deleted_at")).toInstant()).isEqualTo(completedAt);
		assertThat(account.get("nickname")).isNull();
		assertThat(credentialStatuses(userId)).containsExactly("REVOKED", "REVOKED");
		assertThat(pushStatus(pushDeviceId)).isEqualTo("REVOKED");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-014: 같은 ACTIVE 계정의 탈퇴 요청 두 개가 겹쳐도 유예 중 상태와 요청 시각은 하나이고 실패는 낙관적 잠금 충돌뿐이다")
	void concurrentWithdrawalRequestsKeepSingleSchedule() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			clock.reset();
			Instant requestedAt = clock.instant();
			long userId = account("경합요청" + round);
			presenceRepository.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(37.5),
					BigDecimal.valueOf(127.0), null, COUNTRY_CODE, BigDecimal.ONE, true, requestedAt,
					requestedAt.plus(Duration.ofHours(1))));
			PushDevice device = pushDevice(userId, "gh337-int014-" + round);
			long delivery = pendingDelivery(userId, device.id());
			long versionBefore = version(userId);
			pause.prepare(2);

			Race<AccountWithdrawal, AccountWithdrawal> race = race(
					() -> {
						pause.armClockForCurrentThread();
						return withdrawalService.request(userId);
					},
					() -> {
						pause.armClockForCurrentThread();
						return withdrawalService.request(userId);
					});

			assertThat(pause.arrivals()).as("round %d overlap", round).isEqualTo(2);
			assertThat(race.successCount()).as("round %d", round).isGreaterThanOrEqualTo(1);
			for (Attempt<AccountWithdrawal> attempt : List.of(race.first(), race.second())) {
				if (attempt.succeeded()) {
					assertThat(attempt.value().scheduledDeletionAt()).isEqualTo(requestedAt.plus(GRACE_PERIOD));
				} else {
					assertThat(attempt.failure()).isInstanceOf(OptimisticLockingFailureException.class);
				}
			}
			Map<String, Object> account = account(userId);
			assertThat(account.get("status")).isEqualTo("WITHDRAWAL_PENDING");
			assertThat(((Timestamp) account.get("withdrawal_requested_at")).toInstant()).isEqualTo(requestedAt);
			assertThat(version(userId)).isEqualTo(versionBefore + 1);
			assertThat(pushStatus(device.id())).isEqualTo("REVOKED");
			assertThat(jdbc.queryForObject("SELECT status FROM notification_delivery WHERE id = ?", String.class,
					delivery)).isEqualTo("CANCELLED");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence WHERE user_id = ?",
					Integer.class, userId)).isZero();
		}
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-018: 탈퇴 요청과 같은 사용자의 푸시 재등록이 겹쳐도 10초 안에 둘 다 끝나고, 기존 기기는 해지되며 ACTIVE 기기는 요청 뒤에 새로 생긴 것 하나뿐이다")
	void withdrawalRequestAndPushReRegistrationDoNotDeadlock() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			clock.reset();
			long userId = account("푸시경합" + round);
			String token = "gh337-int018-token-" + round;
			PushDevice existing = pushDeviceService.registerOrTransferDevice(userId,
					new PushDeviceCommand(PushPlatform.IOS, PushToken.of(token)));
			pause.prepare(2);

			// 홀수 회차는 재등록을 조금 늦게 풀어 탈퇴 요청이 user-platform 잠금을 먼저 잡는 쪽도 겪게 한다.
			Duration registrationDelay = round % 2 == 1 ? Duration.ofMillis(200) : Duration.ZERO;

			Race<AccountWithdrawal, PushDevice> race = race(
					() -> {
						pause.armClockForCurrentThread();
						return withdrawalService.request(userId);
					},
					() -> {
						pause.armClockForCurrentThread(registrationDelay);
						return pushDeviceService.registerOrTransferDevice(userId,
								new PushDeviceCommand(PushPlatform.IOS, PushToken.of(token)));
					});

			assertThat(pause.arrivals()).as("round %d overlap", round).isEqualTo(2);
			assertThat(race.first().failure()).as("round %d withdrawal", round).isNull();
			assertThat(race.second().failure()).as("round %d push registration", round).isNull();
			assertThat(account(userId).get("status")).isEqualTo("WITHDRAWAL_PENDING");
			assertThat(pushStatus(existing.id())).isEqualTo("REVOKED");
			List<Long> activeDevices = jdbc.queryForList(
					"SELECT id FROM push_device WHERE user_id = ? AND device_status = 'ACTIVE'", Long.class, userId);
			// 재등록이 먼저면 같은 행을 갱신한 뒤 탈퇴 요청이 해지해 ACTIVE가 없다. 탈퇴 요청이 먼저면 재등록이 새 행을
			// 만든다. 그 행은 유예 만료 처리가 다시 해지한다(INT-010).
			assertThat(activeDevices).hasSizeLessThanOrEqualTo(1).doesNotContain(existing.id());
			if (!activeDevices.isEmpty()) {
				assertThat(activeDevices).containsExactly(race.second().value().id());
			}
		}
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', ?, 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
				RETURNING id
				""", Long.class, COUNTRY_CODE, COUNTRY_CODE, nickname);
	}

	private void credential(long userId) {
		String installationId = "gh337-race-" + SEQUENCE.incrementAndGet();
		jdbc.update("""
				INSERT INTO device_credential (user_id, installation_id, secret_hash, platform)
				VALUES (?, ?, encode(sha256(convert_to(?, 'UTF8')), 'hex'), 'IOS')
				""", userId, installationId, installationId);
	}

	private PushDevice pushDevice(long userId, String token) {
		ProtectedPushToken protectedToken = testProtector().protect(PushToken.of(token));
		return notifications.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID, protectedToken.envelope(),
				protectedToken.fingerprint(), PushDeviceStatus.ACTIVE, clock.instant(), null));
	}

	private long pendingDelivery(long userId, long deviceId) {
		int sequence = SEQUENCE.incrementAndGet();
		Instant at = clock.instant();
		OutboxEvent outboxEvent = outboxEvents.save(OutboxEvent.pending(OutboxAggregateType.POST_RECIPIENT, userId,
				OutboxEventType.RECIPIENTS_CONFIRMED, "gh337-race-" + userId + "-" + sequence,
				"{\"source\":\"gh337\"}", at));
		Notification notification = notifications.save(new Notification(null, userId, outboxEvent.id(),
				NotificationType.DIRECTION_POST_RECEIVED, "gh337-race-notification-" + sequence, null, null, null,
				NotificationStatus.UNREAD, at, null));
		return notifications.saveDelivery(NotificationDelivery.pending(notification.id(), deviceId, at)).id();
	}

	private Map<String, Object> account(long userId) {
		return jdbc.queryForMap("""
				SELECT status, nickname, withdrawal_requested_at, deleted_at
				FROM user_account WHERE id = ?
				""", userId);
	}

	private long version(long userId) {
		return jdbc.queryForObject("SELECT version FROM user_account WHERE id = ?", Long.class, userId);
	}

	private List<String> credentialStatuses(long userId) {
		return jdbc.queryForList("SELECT credential_status FROM device_credential WHERE user_id = ? ORDER BY id",
				String.class, userId);
	}

	private String pushStatus(long deviceId) {
		return jdbc.queryForObject("SELECT device_status FROM push_device WHERE id = ?", String.class, deviceId);
	}

	private static <A, B> Race<A, B> race(Callable<A> first, Callable<B> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Attempt<A>> firstFuture = executor.submit(() -> attempt(first, start));
			Future<Attempt<B>> secondFuture = executor.submit(() -> attempt(second, start));
			start.countDown();
			return new Race<>(firstFuture.get(10, TimeUnit.SECONDS), secondFuture.get(10, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	private static <T> Attempt<T> attempt(Callable<T> action, CountDownLatch start) {
		try {
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("race start latch timed out");
			}
			return new Attempt<>(action.call(), null);
		} catch (Throwable failure) {
			return new Attempt<>(null, failure);
		}
	}

	private static PushTokenProtector testProtector() {
		return new AesGcmPushTokenProtector(
				new PushTokenKeyRing(CURRENT_KEY_ID, Map.of(CURRENT_KEY_ID, ENCRYPTION_KEY), FINGERPRINT_KEY));
	}

	private static byte[] fixedKey(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}

	private record Attempt<T>(T value, Throwable failure) {

		boolean succeeded() {
			return failure == null;
		}
	}

	private record Race<A, B>(Attempt<A> first, Attempt<B> second) {

		int successCount() {
			return (first.succeeded() ? 1 : 0) + (second.succeeded() ? 1 : 0);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		RacePause racePause() {
			return new RacePause();
		}

		@Bean
		@Primary
		MutableClock accountWithdrawalRaceTestClock(RacePause racePause) {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS), racePause);
		}

		// test profile에는 push token protector가 없어 PushDeviceService가 등록을 거절한다. 고정 테스트
		// 키를 쓴다.
		@Bean
		PushTokenProtector accountWithdrawalRacePushTokenProtector() {
			return testProtector();
		}

		@Bean
		RaceCleanupHook raceCleanupHook(RacePause racePause) {
			return new RaceCleanupHook(racePause);
		}
	}

	// 만료 처리가 자격증명·푸시 기기 폐기를 마친 뒤, 커밋 전에 철회 쪽을 기다리게 한다. 켜지 않은 스레드에서는 아무것도 하지 않는다.
	static final class RaceCleanupHook implements AccountWithdrawalCleanup, Ordered {

		private final RacePause pause;

		RaceCleanupHook(RacePause pause) {
			this.pause = pause;
		}

		@Override
		public void onWithdrawalCompleted(long userId, Instant completedAt) {
			pause.atCleanup();
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}
	}

	static final class RacePause {

		private final Map<Thread, Duration> clockArmed = new ConcurrentHashMap<>();
		private final Set<Thread> cleanupArmed = ConcurrentHashMap.newKeySet();
		private final AtomicReference<CyclicBarrier> barrier = new AtomicReference<>();
		private final AtomicReference<CountDownLatch> cleanupHold = new AtomicReference<>();
		private final AtomicInteger arrivals = new AtomicInteger();

		void prepare(int parties) {
			clockArmed.clear();
			cleanupArmed.clear();
			cleanupHold.set(null);
			arrivals.set(0);
			barrier.set(new CyclicBarrier(parties));
		}

		void reset() {
			clockArmed.clear();
			cleanupArmed.clear();
			cleanupHold.set(null);
			CyclicBarrier current = barrier.getAndSet(null);
			if (current != null) {
				current.reset();
			}
		}

		// 두 작업이 모두 커밋 전 지점에 도착했는지 확인한다. 2가 아니면 경합 없이 순서대로 실행된 것이다.
		int arrivals() {
			return arrivals.get();
		}

		void armClockForCurrentThread() {
			armClockForCurrentThread(Duration.ZERO);
		}

		void armClockForCurrentThread(Duration delayAfterBarrier) {
			clockArmed.put(Thread.currentThread(), delayAfterBarrier);
		}

		void armCleanupForCurrentThread() {
			cleanupArmed.add(Thread.currentThread());
		}

		void holdCleanupUntil(CountDownLatch release) {
			cleanupHold.set(release);
		}

		void atClock() {
			Duration delay = clockArmed.remove(Thread.currentThread());
			if (delay != null) {
				awaitOtherParty();
				sleep(delay);
			}
		}

		void atCleanup() {
			if (!cleanupArmed.remove(Thread.currentThread())) {
				return;
			}
			awaitOtherParty();
			CountDownLatch hold = cleanupHold.get();
			if (hold != null) {
				awaitHold(hold);
			}
		}

		private void awaitOtherParty() {
			arrivals.incrementAndGet();
			try {
				barrier.get().await(10, TimeUnit.SECONDS);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("race pause interrupted", interrupted);
			} catch (BrokenBarrierException | TimeoutException failure) {
				throw new IllegalStateException("race pause barrier failed", failure);
			}
		}

		// 시간이 지나면 그냥 진행한다. 상대가 이 스레드의 잠금을 기다리는 중이어도 교착으로 멈추지 않는다.
		private static void awaitHold(CountDownLatch hold) {
			try {
				hold.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("race hold interrupted", interrupted);
			}
		}

		private static void sleep(Duration delay) {
			if (delay.isZero()) {
				return;
			}
			try {
				Thread.sleep(delay.toMillis());
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("race delay interrupted", interrupted);
			}
		}
	}

	static final class MutableClock extends Clock {

		private final Instant base;
		private final AtomicReference<Instant> current;
		private final RacePause pause;

		MutableClock(Instant base, RacePause pause) {
			this.base = base;
			this.current = new AtomicReference<>(base);
			this.pause = pause;
		}

		void reset() {
			current.set(base);
		}

		void set(Instant instant) {
			current.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			pause.atClock();
			return current.get();
		}
	}
}
