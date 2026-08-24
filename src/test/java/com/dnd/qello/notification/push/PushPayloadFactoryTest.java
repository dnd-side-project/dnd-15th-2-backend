/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-009
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;

class PushPayloadFactoryTest {

	private static final String PAYLOAD = "com.dnd.qello.notification.push.PushPayload";
	private static final String FACTORY = "com.dnd.qello.notification.push.PushPayloadFactory";
	private static final String CONTEXT = "com.dnd.qello.notification.push.PushDispatchContext";

	@Test
	@DisplayName("UNIT-009: PushPayload record은 type, count, hasRemainingTime만 보존하고 asData도 정확히 그대로 노출한다")
	void allowsOnlyTheThreeDataFields() {
		Class<?> payloadType = requiredClass(PAYLOAD);

		assertThat(payloadType.isRecord()).isTrue();
		assertThat(recordComponentNames(payloadType)).containsExactly("type", "count", "hasRemainingTime");

		Object payload = instantiate(payloadType, "DIRECTION_POST_RECEIVED", "1", "true");
		Map<String, String> data = invokeAsData(payloadType, payload);

		assertThat(data).hasSize(3);
		assertThat(data).containsEntry("type", "DIRECTION_POST_RECEIVED");
		assertThat(data).containsEntry("count", "1");
		assertThat(data).containsEntry("hasRemainingTime", "true");
	}

	@Test
	@DisplayName("UNIT-009: payload factory는 PushDispatchContext를 받아 PushPayload를 반환한다")
	void declaresFactoryBoundaryAgainstDispatchContext() {
		Class<?> factory = requiredClass(FACTORY);
		Class<?> payloadType = requiredClass(PAYLOAD);
		Class<?> contextType = requiredClass(CONTEXT);

		assertThat(Arrays.stream(factory.getMethods())
			.filter(method -> payloadType.isAssignableFrom(method.getReturnType()))
			.anyMatch(method -> Arrays.stream(method.getParameterTypes())
				.anyMatch(type -> type.equals(contextType))))
			.isTrue();
	}

	@Test
	@DisplayName("UNIT-009: 남은 시간이 있는 방향 글 payload는 세 개의 allowlist key만 생성한다")
	void createsRemainingTimePayloadFromRealDispatchContext() {
		PushPayload payload = new PushPayloadFactory().create(context(NotificationType.DIRECTION_POST_RECEIVED, true));

		assertThat(payload.asData()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"type", "DIRECTION_POST_RECEIVED",
			"count", "1",
			"hasRemainingTime", "true"));
		assertThat(payload.asData()).doesNotContainKeys(
			"notificationId", "recipientId", "pushDeviceId", "token", "content", "dedupKey");
	}

	@Test
	@DisplayName("UNIT-009: 비만료 알림 payload는 남은 시간 없이 동일한 세 key만 생성한다")
	void createsNonExpiringPayloadFromRealDispatchContext() {
		PushPayload payload = new PushPayloadFactory().create(context(NotificationType.ANSWER_RECEIVED, false));

		assertThat(payload.asData()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"type", "ANSWER_RECEIVED",
			"count", "1",
			"hasRemainingTime", "false"));
		assertThat(payload.asData()).doesNotContainKeys(
			"notificationId", "recipientId", "pushDeviceId", "token", "content", "dedupKey");
	}

	private static PushDispatchContext context(NotificationType type, boolean hasRemainingTime) {
		Instant at = Instant.parse("2026-08-24T12:00:00Z");
		long notificationId = 101L;
		long deviceId = 202L;
		long recipientId = 303L;
		Notification notification = new Notification(notificationId, recipientId, 404L, type, "dedup-key",
			type == NotificationType.DIRECTION_POST_RECEIVED ? 505L : null, null, null,
			NotificationStatus.UNREAD, at, null);
		NotificationDelivery delivery = new NotificationDelivery(606L, notificationId, deviceId,
			DeliveryStatus.PROCESSING, 1, at, at, null, null);
		PushDevice device = new PushDevice(deviceId, recipientId, PushPlatform.ANDROID, new byte[] {1, 2, 3},
			"fingerprint", PushDeviceStatus.ACTIVE, at, null);
		PushDispatchContext.DispatchValiditySnapshot validity = new PushDispatchContext.DispatchValiditySnapshot(
			true, true, true, false, true, hasRemainingTime);
		return new PushDispatchContext(delivery, notification, device, null, validity);
	}

	private static Class<?> requiredClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Missing production API: " + className, e);
		}
	}

	private static List<String> recordComponentNames(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	private static Object instantiate(Class<?> type, Object... values) {
		try {
			Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class, String.class);
			constructor.setAccessible(true);
			return constructor.newInstance(values);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Unable to instantiate " + type.getName(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> invokeAsData(Class<?> payloadType, Object payload) {
		try {
			return (Map<String, String>) payloadType.getMethod("asData").invoke(payload);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Unable to invoke asData()", e);
		}
	}
}
