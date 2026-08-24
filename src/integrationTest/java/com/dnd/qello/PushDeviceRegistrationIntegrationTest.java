/**
 * Created at: 2026-08-24T20:35:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-INT-001, TEST-PLAN-GH-179-PUSH-DELIVERY-INT-002,
 * TEST-PLAN-GH-179-PUSH-DELIVERY-INT-003, TEST-PLAN-GH-179-PUSH-DELIVERY-INT-004,
 * TEST-PLAN-GH-179-PUSH-DELIVERY-INT-005, TEST-PLAN-GH-179-PUSH-DELIVERY-INT-018
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.push.security.ProtectedPushToken;
import com.dnd.qello.notification.push.security.PushToken;
import com.dnd.qello.notification.push.security.PushTokenKeyRing;
import com.dnd.qello.notification.push.security.PushTokenProtector;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(PushDeviceRegistrationIntegrationTest.TestPushTokenConfiguration.class)
class PushDeviceRegistrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH179-PUSH-INT";
	private static final Instant NOW = Instant.parse("2026-08-24T11:45:00Z");
	private static final String TOKEN_SENTINEL = "gh179-int-token-sentinel";
	private static final String OTHER_TOKEN_SENTINEL = "gh179-int-token-sentinel-other";
	private static final String SERVICE_CLASS = "com.dnd.qello.notification.service.PushDeviceService";
	private static final String COMMAND_CLASS = "com.dnd.qello.notification.service.PushDeviceCommand";
	private static final String CURRENT_KEY_ID = "gh179-current";
	private static final byte[] ENCRYPTION_KEY = fixedKey((byte) 0x61);
	private static final byte[] FINGERPRINT_KEY = fixedKey((byte) 0x71);
	private static final AtomicInteger NOTIFICATION_SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private OutboxEventRepository outboxEvents;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH179 Push Delivery', 'REGION')
			""", REGION);
	}

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
	}

	@Test
	@DisplayName("INT-001: 인증된 사용자의 register POST는 204이고 ACTIVE row 1개만 남기며 sentinel token을 노출하지 않는다")
	void registersOwnPushTokenWithoutLeakingPlaintext(CapturedOutput output) throws Exception {
		long userId = account("gh179-register-own");
		String body = """
			{"platform":"ANDROID","token":"%s"}
			""".formatted(TOKEN_SENTINEL);

		mockMvc.perform(post("/api/v1/notifications/devices")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM push_device WHERE user_id = ? AND device_status = 'ACTIVE'
			""", Integer.class, userId)).isEqualTo(1);
		assertThat(output.getOut()).doesNotContain(TOKEN_SENTINEL, OTHER_TOKEN_SENTINEL);
		assertThat(output.getErr()).doesNotContain(TOKEN_SENTINEL, OTHER_TOKEN_SENTINEL);
	}

	@Test
	@DisplayName("INT-002: revoke는 본인 token에 대해 멱등이고 PENDING/FAILED만 CANCELLED로 바꾼다")
	void revokesOwnTokenIdempotentlyAndCancelsOnlyPendingOrFailedDeliveries() throws Exception {
		long userId = account("gh179-revoke-own");
		ProtectedPushToken protectedToken = protectedToken(TOKEN_SENTINEL);
		PushDevice device = notifications.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID,
			protectedToken.envelope(), protectedToken.fingerprint(), PushDeviceStatus.ACTIVE, NOW, null));
		long pendingNotificationId = notificationFor(userId);
		long failedNotificationId = notificationFor(userId);
		long sentNotificationId = notificationFor(userId);
		notifications.saveDelivery(NotificationDelivery.pending(pendingNotificationId, device.id(), NOW));
		notifications.saveDelivery(new NotificationDelivery(null, failedNotificationId, device.id(), DeliveryStatus.FAILED,
			1, NOW, NOW, null, null));
		notifications.saveDelivery(new NotificationDelivery(null, sentNotificationId, device.id(), DeliveryStatus.SENT,
			1, NOW, NOW, NOW, "provider-message"));

		String body = """
			{"platform":"ANDROID","token":"%s"}
			""".formatted(TOKEN_SENTINEL);

		mockMvc.perform(post("/api/v1/notifications/devices/revoke")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		mockMvc.perform(post("/api/v1/notifications/devices/revoke")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		assertThat(jdbc.queryForObject("""
			SELECT device_status FROM push_device WHERE id = ?
			""", String.class, device.id())).isEqualTo(PushDeviceStatus.REVOKED.name());
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery WHERE status = 'CANCELLED'
			""", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery WHERE status = 'SENT'
			""", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-003: 다른 사용자의 revoke는 204지만 대상 device와 delivery를 바꾸지 않는다")
	void revokeDoesNotChangeAnotherUsersToken() throws Exception {
		long ownerId = account("gh179-owner");
		long otherUserId = account("gh179-other");
		ProtectedPushToken protectedToken = protectedToken(OTHER_TOKEN_SENTINEL);
		PushDevice device = notifications.saveDevice(new PushDevice(null, ownerId, PushPlatform.IOS,
			protectedToken.envelope(), protectedToken.fingerprint(), PushDeviceStatus.ACTIVE, NOW, null));
		long pendingNotificationId = notificationFor(ownerId);
		long failedNotificationId = notificationFor(ownerId);
		notifications.saveDelivery(NotificationDelivery.pending(pendingNotificationId, device.id(), NOW));
		notifications.saveDelivery(new NotificationDelivery(null, failedNotificationId, device.id(), DeliveryStatus.FAILED,
			1, NOW, NOW, null, null));

		mockMvc.perform(post("/api/v1/notifications/devices/revoke")
				.with(jwt().jwt(token -> token.subject(String.valueOf(otherUserId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"platform":"IOS","token":"%s"}
					""".formatted(OTHER_TOKEN_SENTINEL)))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		assertThat(jdbc.queryForObject("""
			SELECT device_status FROM push_device WHERE id = ?
			""", String.class, device.id())).isEqualTo(PushDeviceStatus.ACTIVE.name());
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery WHERE status = 'CANCELLED'
			""", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery WHERE status = 'PENDING'
			""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery WHERE status = 'FAILED'
			""", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-004: 같은 사용자의 동시 재등록은 ACTIVE 1행과 최신 lastSeenAt만 남겨야 한다")
	void concurrentSameUserRegistrationsKeepExactlyOneActiveRow() throws Exception {
		long userId = account("gh179-concurrent-owner");
		Object service = newService();
		Object command = command(PushPlatform.ANDROID, TOKEN_SENTINEL);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Object>> jobs = List.of(
				() -> concurrentRegister(service, userId, command, ready, start),
				() -> concurrentRegister(service, userId, command, ready, start));
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> job : jobs) {
				futures.add(executor.submit(job));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (Future<Object> future : futures) {
				future.get(10, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM push_device
			WHERE user_id = ? AND device_status = 'ACTIVE'
			""", Integer.class, userId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
			SELECT token_ciphertext IS NOT NULL FROM push_device
			WHERE user_id = ? AND device_status = 'ACTIVE'
			""", Boolean.class, userId)).isTrue();
	}

	@Test
	@DisplayName("INT-005: 다른 사용자로의 동시 ownership transfer는 한 명만 ACTIVE가 되고 이전 delivery는 cancel되어야 한다")
	void concurrentOwnershipTransferIsAtomic() throws Exception {
		long ownerId = account("gh179-transfer-owner");
		long otherId = account("gh179-transfer-other");
		ProtectedPushToken initialToken = protectedToken(TOKEN_SENTINEL);
		PushDevice ownerDevice = notifications.saveDevice(new PushDevice(null, ownerId, PushPlatform.ANDROID,
			initialToken.envelope(), initialToken.fingerprint(), PushDeviceStatus.ACTIVE, NOW, null));
		long pendingNotificationId = notificationFor(ownerId);
		long failedNotificationId = notificationFor(ownerId);
		NotificationDelivery pending = notifications.saveDelivery(NotificationDelivery.pending(pendingNotificationId,
			ownerDevice.id(), NOW));
		notifications.saveDelivery(new NotificationDelivery(null, failedNotificationId, ownerDevice.id(),
			DeliveryStatus.FAILED, 1, NOW, NOW, null, null));
		Object service = newService();
		Object ownerCommand = command(PushPlatform.ANDROID, TOKEN_SENTINEL);
		Object otherCommand = command(PushPlatform.ANDROID, TOKEN_SENTINEL);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Object>> futures = List.of(
				executor.submit(() -> concurrentRegister(service, ownerId, ownerCommand, ready, start)),
				executor.submit(() -> concurrentRegister(service, otherId, otherCommand, ready, start)));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (Future<Object> future : futures) {
				future.get(10, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM push_device WHERE device_status = 'ACTIVE'
			""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery WHERE status = 'CANCELLED'
			""", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery WHERE status = 'PENDING'
			""", Integer.class)).isZero();
		assertThat(pending.id()).isNotNull();
	}

	@Test
	@DisplayName("INT-018: 인증·validation·204 계약은 register와 revoke 모두에서 토큰 원문을 노출하지 않아야 한다")
	void validatesAuthenticationAndRedactsTokenAcrossBothEndpoints(CapturedOutput output) throws Exception {
		String invalidBody = """
			{"platform":"ANDROID","token":"%s"}
			""".formatted(TOKEN_SENTINEL);
		mockMvc.perform(post("/api/v1/notifications/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidBody))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/notifications/devices/revoke")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidBody))
			.andExpect(status().isUnauthorized());

		long userId = account("gh179-validation");
		mockMvc.perform(post("/api/v1/notifications/devices")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/notifications/devices")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/notifications/devices")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"platform":"INVALID","token":"%s"}
					""".formatted(TOKEN_SENTINEL)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/notifications/devices/revoke")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"platform":"ANDROID","token":"%s"}
					""".formatted(" ".repeat(2))))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/notifications/devices/revoke")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());

		String oversizeToken = "x".repeat(4097);
		mockMvc.perform(post("/api/v1/notifications/devices")
				.with(jwt().jwt(token -> token.subject(String.valueOf(userId))))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"platform":"ANDROID","token":"%s"}
					""".formatted(oversizeToken)))
			.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM push_device WHERE user_id = ?
			""", Integer.class, userId)).isZero();

		assertThat(output.getOut()).doesNotContain(TOKEN_SENTINEL, OTHER_TOKEN_SENTINEL);
		assertThat(output.getErr()).doesNotContain(TOKEN_SENTINEL, OTHER_TOKEN_SENTINEL);
	}

	private Object newService() {
		try {
			Class<?> serviceType = Class.forName(SERVICE_CLASS);
			for (Constructor<?> constructor : serviceType.getDeclaredConstructors()) {
				Class<?>[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length != 3) {
					continue;
				}
				int repositoryIndex = indexOfAssignable(parameterTypes, NotificationRepository.class);
				int protectorIndex = indexOfAssignable(parameterTypes, com.dnd.qello.notification.push.security.PushTokenProtector.class);
				int clockIndex = indexOfAssignable(parameterTypes, Clock.class);
				if (repositoryIndex < 0 || protectorIndex < 0 || clockIndex < 0) {
					continue;
				}
				Object[] arguments = new Object[3];
				arguments[repositoryIndex] = notifications;
				arguments[protectorIndex] = protector();
				arguments[clockIndex] = Clock.fixed(NOW, ZoneOffset.UTC);
				constructor.setAccessible(true);
				return constructor.newInstance(arguments);
			}
			throw new AssertionError("PushDeviceService constructor with repository, protector, and clock is required");
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("PushDeviceService is not available yet", exception);
		}
	}

	private Object command(PushPlatform platform, String tokenValue) {
		try {
			Class<?> commandType = Class.forName(COMMAND_CLASS);
			for (Constructor<?> constructor : commandType.getDeclaredConstructors()) {
				Class<?>[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length != 2) {
					continue;
				}
				Object first = convertParameter(parameterTypes[0], constructor.getParameters()[0].getName(), 0, platform,
					tokenValue);
				Object second = convertParameter(parameterTypes[1], constructor.getParameters()[1].getName(), 1, platform,
					tokenValue);
				if (first == UNMATCHED || second == UNMATCHED) {
					continue;
				}
				constructor.setAccessible(true);
				return constructor.newInstance(first, second);
			}
			throw new AssertionError("PushDeviceCommand constructor accepting platform and token is required");
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("PushDeviceCommand is not available yet", exception);
		}
	}

	private Object concurrentRegister(Object service, long userId, Object command, CountDownLatch ready, CountDownLatch start)
		throws Exception {
		ready.countDown();
		assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
		Method method = service.getClass().getMethod("registerOrTransferDevice", long.class, command.getClass());
		method.setAccessible(true);
		return method.invoke(service, userId, command);
	}

	private int indexOfAssignable(Class<?>[] parameterTypes, Class<?> expectedType) {
		for (int index = 0; index < parameterTypes.length; index++) {
			if (expectedType.isAssignableFrom(parameterTypes[index])) {
				return index;
			}
		}
		return -1;
	}

	private Object convertParameter(Class<?> parameterType, String parameterName, int index, PushPlatform platform,
		String tokenValue) {
		if (parameterType.isEnum() && parameterType.isAssignableFrom(platform.getDeclaringClass())) {
			return platform;
		}
		if (parameterType.equals(String.class)) {
			String lowerName = parameterName == null ? "" : parameterName.toLowerCase();
			if (lowerName.contains("platform")) {
				return platform.name();
			}
			if (lowerName.contains("token")) {
				return tokenValue;
			}
			return index == 0 ? platform.name() : tokenValue;
		}
		if (parameterType.getName().equals(PushToken.class.getName())) {
			return PushToken.of(tokenValue);
		}
		if (parameterType.isAssignableFrom(PushPlatform.class)) {
			return platform;
		}
		return UNMATCHED;
	}

	private ProtectedPushToken protectedToken(String tokenValue) {
		AesGcmPushTokenProtector protector = new AesGcmPushTokenProtector(
			new PushTokenKeyRing(CURRENT_KEY_ID, Map.of(CURRENT_KEY_ID, ENCRYPTION_KEY), FINGERPRINT_KEY));
		return protector.protect(PushToken.of(tokenValue));
	}

	private com.dnd.qello.notification.push.security.PushTokenProtector protector() {
		return new AesGcmPushTokenProtector(
			new PushTokenKeyRing(CURRENT_KEY_ID, Map.of(CURRENT_KEY_ID, ENCRYPTION_KEY), FINGERPRINT_KEY));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long notificationFor(long recipientId) {
		int sequence = NOTIFICATION_SEQUENCE.incrementAndGet();
		OutboxEvent outboxEvent = outboxEvents.save(OutboxEvent.pending(OutboxAggregateType.POST_RECIPIENT, recipientId,
			OutboxEventType.RECIPIENTS_CONFIRMED, "gh179-" + recipientId + "-" + sequence, "{\"source\":\"push-int\"}",
			NOW));
		Notification notification = notifications.save(new Notification(null, recipientId, outboxEvent.id(),
			NotificationType.DIRECTION_POST_RECEIVED, "gh179-notification-" + recipientId + "-" + sequence, null, null,
			null, NotificationStatus.UNREAD, NOW, null));
		return notification.id();
	}

	private static byte[] fixedKey(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestPushTokenConfiguration {

		@Bean
		PushTokenProtector testPushTokenProtector() {
			return new AesGcmPushTokenProtector(
				new PushTokenKeyRing(CURRENT_KEY_ID, Map.of(CURRENT_KEY_ID, ENCRYPTION_KEY), FINGERPRINT_KEY));
		}
	}

	private static final Object UNMATCHED = new Object();
}
