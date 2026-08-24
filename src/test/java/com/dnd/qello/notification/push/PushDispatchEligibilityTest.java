/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-010
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
