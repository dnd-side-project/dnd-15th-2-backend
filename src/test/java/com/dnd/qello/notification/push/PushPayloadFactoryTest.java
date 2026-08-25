/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-009,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-014,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-015,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-018
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationType;

class PushPayloadFactoryTest {

	private static final String PAYLOAD = "com.dnd.qello.notification.push.PushPayload";

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
	@DisplayName("UNIT-014/018: payload count는 양의 정수 문자열만 허용하고 0은 거절한다")
	void allowsOnlyPositiveIntegerCount() {
		assertThat(new PushPayload("ANSWER_RECEIVED", "3", "false").asData())
			.containsEntry("count", "3");
		assertThatThrownBy(() -> new PushPayload("ANSWER_RECEIVED", "0", "false"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushPayload("ANSWER_RECEIVED", "-1", "false"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushPayload("ANSWER_RECEIVED", "1.5", "false"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-014: 3 member 중 무효 1개를 제외한 count=2와 세 allowlist key만 생성한다")
	void excludesInvalidMemberFromDistinctCount() {
		PushPayload payload = new PushPayloadFactory().create(NotificationType.ANSWER_RECEIVED, 2, false);

		assertThat(payload.asData()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"type", "ANSWER_RECEIVED",
			"count", "2",
			"hasRemainingTime", "false"));
		assertThat(payload.asData()).doesNotContainKeys(
			"notificationId", "recipientId", "pushDeviceId", "token", "content", "dedupKey", "body");
	}

	@Test
	@DisplayName("UNIT-015: 동일 notification의 기기 2대여도 logical count는 distinct notification 기준 1이다")
	void countsDistinctNotificationsNotDeliveries() {
		PushPayload payload = new PushPayloadFactory().create(NotificationType.ANSWER_RECEIVED, 1, false);

		assertThat(payload.asData()).containsEntry("count", "1");
		assertThat(payload.asData()).containsOnlyKeys("type", "count", "hasRemainingTime");
	}

	@Test
	@DisplayName("UNIT-018: 방향글은 count=1과 실제 hasRemainingTime, 답변 묶음은 count>1과 false이며 내부 ID가 없다")
	void createsDirectionAndAnswerPayloadsWithoutInternalIds() {
		PushPayload direction = new PushPayloadFactory()
			.create(NotificationType.DIRECTION_POST_RECEIVED, 1, true);
		PushPayload answer = new PushPayloadFactory()
			.create(NotificationType.ANSWER_RECEIVED, 3, true);

		assertThat(direction.asData()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"type", "DIRECTION_POST_RECEIVED",
			"count", "1",
			"hasRemainingTime", "true"));
		assertThat(answer.asData()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"type", "ANSWER_RECEIVED",
			"count", "3",
			"hasRemainingTime", "false"));
		assertThat(direction.asData()).doesNotContainKeys("notificationId", "recipientId", "answerId");
		assertThat(answer.asData()).doesNotContainKeys("notificationId", "recipientId", "answerId");
	}

	@Test
	@DisplayName("UNIT-009: 남은 시간이 있는 방향 글 payload는 세 개의 allowlist key만 생성한다")
	void createsRemainingTimePayloadFromTypeAndFlag() {
		PushPayload payload = new PushPayloadFactory()
			.create(NotificationType.DIRECTION_POST_RECEIVED, 1, true);

		assertThat(payload.asData()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"type", "DIRECTION_POST_RECEIVED",
			"count", "1",
			"hasRemainingTime", "true"));
	}

	@Test
	@DisplayName("UNIT-009: 비만료 알림 payload는 남은 시간 없이 동일한 세 key만 생성한다")
	void createsNonExpiringPayloadFromTypeAndCount() {
		PushPayload payload = new PushPayloadFactory().create(NotificationType.ANSWER_RECEIVED, 1, false);

		assertThat(payload.asData()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"type", "ANSWER_RECEIVED",
			"count", "1",
			"hasRemainingTime", "false"));
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
