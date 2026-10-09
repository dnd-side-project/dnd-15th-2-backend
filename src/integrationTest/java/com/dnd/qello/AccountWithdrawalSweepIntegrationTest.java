/**
 * Created at: 2026-10-09T18:41:10+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-010 through INT-012, INT-015
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dnd.qello.account.service.AccountWithdrawalCleanup;
import com.dnd.qello.account.service.AccountWithdrawalService;
import com.dnd.qello.account.sweep.AccountWithdrawalSweepWorker;
import com.dnd.qello.account.sweep.AccountWithdrawalSweepWorker.BatchCommand;
import com.dnd.qello.account.sweep.AccountWithdrawalSweepWorker.BatchResult;
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
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 탈퇴 유예 만료 sweep과 탈퇴 요청 트랜잭션 rollback을 실제 PostGIS로 검증한다(#337).
//
// 유예 경계는 시계를 옮기거나 BatchCommand.at을 직접 넘겨 고정한다. 시계는 실제 현재 시각에서 시작해 앱 액세스 토큰이 계속
// 유효하다. 실패 주입용 정리 bean은 이 컨텍스트에만 등록되고, 켜지 않으면 아무것도 하지 않는다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AccountWithdrawalSweepIntegrationTest.TestConfig.class)
class AccountWithdrawalSweepIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String COUNTRY_CODE = "KR";
	private static final Duration GRACE_PERIOD = Duration.ofDays(30);
	private static final String CURRENT_KEY_ID = "gh337-sweep-current";
	private static final byte[] ENCRYPTION_KEY = fixedKey((byte) 0x63);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte) 0x73);
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@Autowired
	private ActiveUserPresenceRepository presenceRepository;

	@Autowired
	private AccountWithdrawalService withdrawalService;

	@Autowired
	private AccountWithdrawalSweepWorker sweepWorker;

	@Autowired
	private FailingWithdrawalCleanup failingCleanup;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetFixtures() {
		clock.reset();
		failingCleanup.disarm();
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
	void disarmFailureInjection() {
		failingCleanup.disarm();
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-010: 유예가 지났거나 정확히 끝난 계정만 DELETED·닉네임 NULL·자격증명 전부 REVOKED·유예 중 재등록 푸시 기기 REVOKED가 되고 재발급은 401이며, 1초 남은 계정은 그대로다")
	void completesOnlyDueWithdrawalsAndRevokesEverything() throws Exception {
		Instant start = clock.instant();
		Registered elapsed = register("유예지남");
		Registered remaining = register("유예남음");
		long boundary = account("유예경계");
		long secondCredential = credential(elapsed.userId(), "gh337-int010-second-device");
		pushDevice(elapsed.userId(), PushPlatform.ANDROID, "gh337-int010-before-request");

		withdrawalService.request(elapsed.userId());
		clock.advance(Duration.ofSeconds(1));
		withdrawalService.request(boundary);
		clock.advance(Duration.ofSeconds(1));
		withdrawalService.request(remaining.userId());
		// 푸시 등록 API는 계정 상태를 보지 않으므로 유예 중에도 기기가 다시 생길 수 있다(A4).
		PushDevice reRegistered = pushDevice(elapsed.userId(), PushPlatform.IOS, "gh337-int010-during-grace");
		Instant at = start.plus(GRACE_PERIOD).plusSeconds(1);

		BatchResult result = sweepWorker.processBatch(new BatchCommand(10, at));

		assertThat(result).isEqualTo(new BatchResult(2, 2, 0, 0));
		Map<String, Object> deleted = account(elapsed.userId());
		assertThat(deleted.get("status")).isEqualTo("DELETED");
		assertThat(instant(deleted.get("deleted_at"))).isEqualTo(at);
		assertThat(deleted.get("nickname")).isNull();
		assertThat(deleted.get("withdrawal_requested_at")).isNull();
		assertThat(jdbc.queryForList("""
				SELECT credential_status FROM device_credential WHERE user_id = ? ORDER BY id
				""", String.class, elapsed.userId())).containsExactly("REVOKED", "REVOKED");
		assertThat(jdbc.queryForList("SELECT revoked_at FROM device_credential WHERE user_id = ?",
				OffsetDateTime.class, elapsed.userId())).extracting(OffsetDateTime::toInstant).containsOnly(at);
		assertThat(jdbc.queryForObject("SELECT credential_status FROM device_credential WHERE id = ?", String.class,
				secondCredential)).isEqualTo("REVOKED");
		assertThat(jdbc.queryForObject("SELECT device_status FROM push_device WHERE id = ?", String.class,
				reRegistered.id())).isEqualTo("REVOKED");
		assertThat(jdbc.queryForObject("SELECT revoked_at FROM push_device WHERE id = ?", OffsetDateTime.class,
				reRegistered.id()).toInstant()).isEqualTo(at);
		assertThat(activePushDevices(elapsed.userId())).isZero();

		Map<String, Object> exactBoundary = account(boundary);
		assertThat(exactBoundary.get("status")).isEqualTo("DELETED");
		assertThat(exactBoundary.get("nickname")).isNull();

		Map<String, Object> stillPending = account(remaining.userId());
		assertThat(stillPending.get("status")).isEqualTo("WITHDRAWAL_PENDING");
		assertThat(stillPending.get("nickname")).isEqualTo("유예남음");
		assertThat(instant(stillPending.get("withdrawal_requested_at"))).isEqualTo(start.plusSeconds(2));
		assertThat(stillPending.get("deleted_at")).isNull();
		assertThat(jdbc.queryForList("SELECT credential_status FROM device_credential WHERE user_id = ?",
				String.class, remaining.userId())).containsExactly("ACTIVE");

		mockMvc.perform(reissue(elapsed))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorDetail.code").value("AUT-APP-006"));
		mockMvc.perform(reissue(remaining))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accountStatus").value("WITHDRAWAL_PENDING"));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-011: limit보다 후보가 많으면 요청 시각·id 순서로 나눠 모두 소진하고, 같은 at으로 다시 실행하면 scanned 0이고 처리된 계정을 다시 바꾸지 않는다")
	void drainsDueCandidatesInDeterministicOrderAndIsIdempotent() {
		Instant start = clock.instant();
		long laterLowId = account("순서1");
		long firstTie = account("순서2");
		long secondTie = account("순서3");
		long middle = account("순서4");
		long laterHighId = account("순서5");
		long notDue = account("순서6");
		withdrawalService.request(secondTie);
		withdrawalService.request(firstTie);
		clock.advance(Duration.ofSeconds(1));
		withdrawalService.request(middle);
		clock.advance(Duration.ofSeconds(1));
		withdrawalService.request(laterHighId);
		withdrawalService.request(laterLowId);
		clock.advance(Duration.ofSeconds(1));
		withdrawalService.request(notDue);
		Instant at = start.plus(GRACE_PERIOD).plusSeconds(2);

		assertThat(sweepWorker.processBatch(new BatchCommand(2, at))).isEqualTo(new BatchResult(2, 2, 0, 0));
		assertThat(deletedIds()).containsExactlyInAnyOrder(firstTie, secondTie);
		assertThat(sweepWorker.processBatch(new BatchCommand(2, at))).isEqualTo(new BatchResult(2, 2, 0, 0));
		assertThat(deletedIds()).containsExactlyInAnyOrder(firstTie, secondTie, middle, laterLowId);
		assertThat(sweepWorker.processBatch(new BatchCommand(2, at))).isEqualTo(new BatchResult(1, 1, 0, 0));
		assertThat(deletedIds()).containsExactlyInAnyOrder(firstTie, secondTie, middle, laterLowId, laterHighId);
		List<Map<String, Object>> afterDrain = jdbc.queryForList(
				"SELECT id, status, deleted_at, version FROM user_account ORDER BY id");

		assertThat(sweepWorker.processBatch(new BatchCommand(2, at))).isEqualTo(new BatchResult(0, 0, 0, 0));
		assertThat(jdbc.queryForList("SELECT id, status, deleted_at, version FROM user_account ORDER BY id"))
				.isEqualTo(afterDrain);
		assertThat(account(notDue).get("status")).isEqualTo("WITHDRAWAL_PENDING");
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-012: 삭제 예정 시각과 같거나 지난 뒤 sweep 전 철회는 409 ACC-DOM-004로 상태를 바꾸지 않고 다음 sweep이 완료한다")
	void rejectsCancelAfterDeadlineBeforeSweepAndSweepCompletes() throws Exception {
		Registered user = register("기한지남");
		Instant requestedAt = clock.instant();
		mockMvc.perform(post("/api/v1/users/me/withdrawal")
				.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
				.andExpect(status().isOk());
		Map<String, Object> before = account(user.userId());

		clock.advance(GRACE_PERIOD);
		mockMvc.perform(cancelWithdrawal(user.accessToken()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorDetail.code").value("ACC-DOM-004"));
		clock.advance(Duration.ofSeconds(1));
		mockMvc.perform(cancelWithdrawal(user.accessToken()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorDetail.code").value("ACC-DOM-004"));

		Map<String, Object> unchanged = account(user.userId());
		assertThat(unchanged.get("status")).isEqualTo("WITHDRAWAL_PENDING");
		assertThat(instant(unchanged.get("withdrawal_requested_at"))).isEqualTo(requestedAt);
		assertThat(unchanged.get("version")).isEqualTo(before.get("version"));

		assertThat(sweepWorker.processBatch(new BatchCommand(10, null))).isEqualTo(new BatchResult(1, 1, 0, 0));
		Map<String, Object> completed = account(user.userId());
		assertThat(completed.get("status")).isEqualTo("DELETED");
		assertThat(instant(completed.get("deleted_at"))).isEqualTo(requestedAt.plus(GRACE_PERIOD).plusSeconds(1));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-015: 탈퇴 요청 중 마지막 정리가 실패하면 예외가 전파되고 계정·푸시 기기·전달·위치가 모두 요청 전 상태로 rollback된다")
	void rollsBackWholeWithdrawalRequestWhenCleanupFails() {
		long userId = account("롤백대상");
		presenceRepository.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0),
				null, COUNTRY_CODE, BigDecimal.ONE, true, clock.instant(), clock.instant().plus(Duration.ofHours(1))));
		PushDevice device = pushDevice(userId, PushPlatform.IOS, "gh337-int015-device");
		long pendingDelivery = pendingDelivery(userId, device.id());
		Map<String, Object> before = account(userId);
		failingCleanup.arm();

		assertThatThrownBy(() -> withdrawalService.request(userId))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("INT-015");

		// 실패 직전 같은 트랜잭션 안에서는 푸시 해지·전달 취소·위치 삭제가 이미 실행된 상태였다.
		assertThat(failingCleanup.observedBeforeFailure())
				.containsEntry("active_push_devices", 0L)
				.containsEntry("pending_deliveries", 0L)
				.containsEntry("presence_rows", 0L);
		Map<String, Object> after = account(userId);
		assertThat(after.get("status")).isEqualTo("ACTIVE");
		assertThat(after.get("withdrawal_requested_at")).isNull();
		assertThat(after.get("version")).isEqualTo(before.get("version"));
		assertThat(jdbc.queryForMap("SELECT device_status, revoked_at FROM push_device WHERE id = ?", device.id()))
				.containsEntry("device_status", "ACTIVE")
				.containsEntry("revoked_at", null);
		assertThat(jdbc.queryForObject("SELECT status FROM notification_delivery WHERE id = ?", String.class,
				pendingDelivery)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence WHERE user_id = ?", Integer.class,
				userId)).isEqualTo(1);
	}

	private Registered register(String nickname) throws Exception {
		String installationId = "gh337-sweep-" + SEQUENCE.incrementAndGet();
		MvcResult result = mockMvc.perform(post("/api/v1/auth/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"installationId":"%s","platform":"IOS","countryCode":"%s",
						 "locale":"ko-KR","timezone":"Asia/Seoul","nickname":"%s"}
						""".formatted(installationId, COUNTRY_CODE, nickname)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
		return new Registered(data.get("userId").asLong(), installationId, data.get("deviceSecret").asText(),
				data.get("accessToken").asText());
	}

	private MockHttpServletRequestBuilder reissue(Registered user) {
		return post("/api/v1/auth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"installationId":"%s","deviceSecret":"%s"}
						""".formatted(user.installationId(), user.deviceSecret()));
	}

	private MockHttpServletRequestBuilder cancelWithdrawal(String accessToken) {
		return delete("/api/v1/users/me/withdrawal").header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', ?, 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
				RETURNING id
				""", Long.class, COUNTRY_CODE, COUNTRY_CODE, nickname);
	}

	private long credential(long userId, String installationId) {
		return jdbc.queryForObject("""
				INSERT INTO device_credential (user_id, installation_id, secret_hash, platform)
				VALUES (?, ?, encode(sha256(convert_to(?, 'UTF8')), 'hex'), 'ANDROID')
				RETURNING id
				""", Long.class, userId, installationId, installationId);
	}

	private PushDevice pushDevice(long userId, PushPlatform platform, String token) {
		ProtectedPushToken protectedToken = new AesGcmPushTokenProtector(
				new PushTokenKeyRing(CURRENT_KEY_ID, Map.of(CURRENT_KEY_ID, ENCRYPTION_KEY), FINGERPRINT_KEY))
				.protect(PushToken.of(token));
		return notifications.saveDevice(new PushDevice(null, userId, platform, protectedToken.envelope(),
				protectedToken.fingerprint(), PushDeviceStatus.ACTIVE, clock.instant(), null));
	}

	private long pendingDelivery(long userId, long deviceId) {
		int sequence = SEQUENCE.incrementAndGet();
		Instant at = clock.instant();
		OutboxEvent outboxEvent = outboxEvents.save(OutboxEvent.pending(OutboxAggregateType.POST_RECIPIENT, userId,
				OutboxEventType.RECIPIENTS_CONFIRMED, "gh337-sweep-" + userId + "-" + sequence,
				"{\"source\":\"gh337\"}", at));
		Notification notification = notifications.save(new Notification(null, userId, outboxEvent.id(),
				NotificationType.DIRECTION_POST_RECEIVED, "gh337-sweep-notification-" + sequence, null, null, null,
				NotificationStatus.UNREAD, at, null));
		return notifications.saveDelivery(NotificationDelivery.pending(notification.id(), deviceId, at)).id();
	}

	private int activePushDevices(long userId) {
		return jdbc.queryForObject("SELECT count(*) FROM push_device WHERE user_id = ? AND device_status = 'ACTIVE'",
				Integer.class, userId);
	}

	private List<Long> deletedIds() {
		return jdbc.queryForList("SELECT id FROM user_account WHERE status = 'DELETED'", Long.class);
	}

	private Map<String, Object> account(long userId) {
		return jdbc.queryForMap("""
				SELECT status, nickname, withdrawal_requested_at, deleted_at, version
				FROM user_account WHERE id = ?
				""", userId);
	}

	private static Instant instant(Object timestamp) {
		return ((Timestamp) timestamp).toInstant();
	}

	private static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private static byte[] fixedKey(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}

	private record Registered(long userId, String installationId, String deviceSecret, String accessToken) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		@Primary
		MutableClock accountWithdrawalSweepTestClock() {
			return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
		}

		@Bean
		FailingWithdrawalCleanup failingWithdrawalCleanup(JdbcTemplate jdbc) {
			return new FailingWithdrawalCleanup(jdbc);
		}
	}

	// 스캔으로 등록되는 정리 bean보다 뒤에 등록되고 순서도 가장 뒤라, 켜면 푸시 해지·위치 삭제가 끝난 뒤 같은 트랜잭션에서
	// 실패한다. 실패 직전 트랜잭션 안의 상태를 남겨 정리가 정말 먼저 실행됐는지 확인한다.
	static final class FailingWithdrawalCleanup implements AccountWithdrawalCleanup, Ordered {

		private final JdbcTemplate jdbc;
		private final AtomicBoolean armed = new AtomicBoolean();
		private final AtomicReference<Map<String, Object>> observed = new AtomicReference<>();

		FailingWithdrawalCleanup(JdbcTemplate jdbc) {
			this.jdbc = jdbc;
		}

		void arm() {
			observed.set(null);
			armed.set(true);
		}

		void disarm() {
			armed.set(false);
		}

		Map<String, Object> observedBeforeFailure() {
			return observed.get();
		}

		@Override
		public void onWithdrawalRequested(long userId, Instant requestedAt) {
			if (!armed.get()) {
				return;
			}
			observed.set(jdbc.queryForMap("""
					SELECT (SELECT count(*) FROM push_device
					        WHERE user_id = ? AND device_status = 'ACTIVE') AS active_push_devices,
					       (SELECT count(*) FROM notification_delivery nd
					        JOIN push_device pd ON pd.id = nd.push_device_id
					        WHERE pd.user_id = ? AND nd.status = 'PENDING') AS pending_deliveries,
					       (SELECT count(*) FROM active_user_presence WHERE user_id = ?) AS presence_rows
					""", userId, userId, userId));
			throw new IllegalStateException("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-015 injected cleanup failure");
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}
	}

	static final class MutableClock extends Clock {

		private final Instant base;
		private final AtomicReference<Instant> current;

		MutableClock(Instant base) {
			this.base = base;
			this.current = new AtomicReference<>(base);
		}

		void reset() {
			current.set(base);
		}

		void advance(Duration duration) {
			current.updateAndGet(instant -> instant.plus(duration));
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
			return current.get();
		}
	}
}
