/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-010
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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

class PushDispatchEligibilityTest {

	private static final String CONTEXT = "com.dnd.qello.notification.push.PushDispatchContext";
	private static final String DECISION = "com.dnd.qello.notification.push.PushDispatchDecision";
	private static final String ELIGIBILITY = "com.dnd.qello.notification.push.PushDispatchEligibility";

	@Test
	@DisplayName("UNIT-010: dispatch context는 delivery, notification, device, actorId, targetValidity만 담는다")
	void exposesOnlyTheDispatchSnapshotFields() {
		Class<?> contextType = requiredClass(CONTEXT);

		assertThat(contextType.isRecord()).isTrue();
		assertThat(recordComponentNames(contextType))
			.containsExactly("delivery", "notification", "device", "actorId", "targetValidity");
	}

	@Test
	@DisplayName("UNIT-010: dispatch decision은 SEND, CANCELLED, DEAD의 제한된 outcomes만 노출한다")
	void exposesClosedDecisionOutcomes() {
		Class<?> decisionType = requiredClass(DECISION);

		assertThat(normalizedNames(decisionType)).containsExactlyInAnyOrder("SEND", "CANCELLED", "DEAD");
	}

	@Test
	@DisplayName("UNIT-010: eligibility service는 PushDispatchContext를 받아 decision을 돌려준다")
	void declaresContextDecisionBoundary() {
		Class<?> eligibilityType = requiredClass(ELIGIBILITY);
		Class<?> contextType = requiredClass(CONTEXT);

		assertThat(Arrays.stream(eligibilityType.getMethods())
			.anyMatch(method -> method.getParameterCount() == 1
				&& method.getParameterTypes()[0].equals(contextType)
				&& (method.getReturnType().getSimpleName().equals("PushDispatchDecision")
					|| method.getReturnType().equals(boolean.class))))
			.isTrue();
	}

	@Test
	@DisplayName("UNIT-010: 모든 snapshot 조건이 유효하면 SEND와 ELIGIBLE을 반환한다")
	void sendsWhenContextIsEligible() {
		PushDispatchEligibility.Evaluation evaluation = new PushDispatchEligibility().evaluate(context(
			PushDeviceStatus.ACTIVE, true, true, false, true));

		assertThat(evaluation.decision()).isEqualTo(PushDispatchDecision.SEND);
		assertThat(evaluation.reasonCode()).isEqualTo(PushDispatchEligibility.ReasonCode.ELIGIBLE);
	}

	@Test
	@DisplayName("UNIT-010: 비활성 device는 DEVICE_INACTIVE 사유로 CANCELLED 된다")
	void cancelsInactiveDevice() {
		PushDispatchEligibility.Evaluation evaluation = new PushDispatchEligibility().evaluate(context(
			PushDeviceStatus.INVALID, true, true, false, true));

		assertThat(evaluation.decision()).isEqualTo(PushDispatchDecision.CANCELLED);
		assertThat(evaluation.reasonCode()).isEqualTo(PushDispatchEligibility.ReasonCode.DEVICE_INACTIVE);
	}

	@Test
	@DisplayName("UNIT-010: preference가 비활성화되면 PREFERENCE_DISABLED 사유로 CANCELLED 된다")
	void cancelsDisabledPreference() {
		PushDispatchEligibility.Evaluation evaluation = new PushDispatchEligibility().evaluate(context(
			PushDeviceStatus.ACTIVE, true, false, false, true));

		assertThat(evaluation.decision()).isEqualTo(PushDispatchDecision.CANCELLED);
		assertThat(evaluation.reasonCode()).isEqualTo(PushDispatchEligibility.ReasonCode.PREFERENCE_DISABLED);
	}

	@Test
	@DisplayName("UNIT-010: 양방향 차단 snapshot은 BLOCKED_ACTOR 사유로 CANCELLED 된다")
	void cancelsBlockedActor() {
		PushDispatchEligibility.Evaluation evaluation = new PushDispatchEligibility().evaluate(context(
			PushDeviceStatus.ACTIVE, true, true, true, true));

		assertThat(evaluation.decision()).isEqualTo(PushDispatchDecision.CANCELLED);
		assertThat(evaluation.reasonCode()).isEqualTo(PushDispatchEligibility.ReasonCode.BLOCKED_ACTOR);
	}

	@Test
	@DisplayName("UNIT-010: 잘못된 target snapshot은 TARGET_INVALID 사유로 CANCELLED 된다")
	void cancelsInvalidTarget() {
		PushDispatchEligibility.Evaluation evaluation = new PushDispatchEligibility().evaluate(context(
			PushDeviceStatus.ACTIVE, true, true, false, false));

		assertThat(evaluation.decision()).isEqualTo(PushDispatchDecision.CANCELLED);
		assertThat(evaluation.reasonCode()).isEqualTo(PushDispatchEligibility.ReasonCode.TARGET_INVALID);
	}

	private static PushDispatchContext context(PushDeviceStatus deviceStatus, boolean recipientActive,
		boolean preferenceEnabled, boolean blockedInEitherDirection, boolean targetValid) {
		Instant at = Instant.parse("2026-08-24T12:00:00Z");
		long notificationId = 101L;
		long deviceId = 202L;
		long recipientId = 303L;
		Notification notification = new Notification(notificationId, recipientId, 404L,
			NotificationType.ANSWER_RECEIVED, "dedup-key", null, null, null, NotificationStatus.UNREAD, at, null);
		NotificationDelivery delivery = new NotificationDelivery(606L, notificationId, deviceId,
			DeliveryStatus.PROCESSING, 1, at, at, null, null);
		PushDevice device = new PushDevice(deviceId, recipientId, PushPlatform.ANDROID, new byte[] {1, 2, 3},
			"fingerprint", deviceStatus, at, null);
		PushDispatchContext.DispatchValiditySnapshot validity = new PushDispatchContext.DispatchValiditySnapshot(
			recipientActive, true, preferenceEnabled, blockedInEitherDirection, targetValid, false);
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

	private static List<String> normalizedNames(Class<?> type) {
		if (type.isEnum()) {
			return Arrays.stream(type.getEnumConstants()).map(Enum.class::cast).map(Enum::name).toList();
		}
		return Arrays.stream(type.getDeclaredClasses()).map(Class::getSimpleName).map(String::toUpperCase).toList();
	}
}
