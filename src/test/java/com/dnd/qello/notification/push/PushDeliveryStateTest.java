/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-011,
 * TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-016
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushDeliveryStateTest {

	private static final String CLAIM_SERVICE = "com.dnd.qello.notification.service.PushDispatchGroupClaimService";
	private static final String CLAIMED_GROUP = "com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup";
	private static final String TERMINAL_RESULT = "com.dnd.qello.notification.push.PushDeliveryTerminalResult";

	@Test
	@DisplayName("UNIT-011: group claim service는 due group claim과 generation fencing terminal API를 노출한다")
	void exposesBatchClaimAndFencedTerminalApi() throws Exception {
		Class<?> claimService = requiredClass(CLAIM_SERVICE);
		Class<?> terminalResult = requiredClass(TERMINAL_RESULT);

		assertThat(claimService.getMethod("claimDueGroups", int.class, Instant.class, Instant.class)
			.getReturnType()).isEqualTo(List.class);
		assertThat(claimService.getMethod("completeDevice", long.class, int.class, long.class, Map.class,
			terminalResult, Instant.class, Instant.class, String.class).getReturnType()).isEqualTo(boolean.class);
	}

	@Test
	@DisplayName("UNIT-011: claimed group은 groupId, generation, leaseUntil을 record component로 노출한다")
	void exposesClaimedDeliveryBoundaryFields() {
		Class<?> claimedGroup = requiredClass(CLAIMED_GROUP);

		assertThat(claimedGroup.isRecord()).isTrue();
		assertThat(recordComponentNames(claimedGroup)).contains("groupId", "generation", "leaseUntil");
	}

	@Test
	@DisplayName("UNIT-011: terminal result는 SENT, FAILED, DEAD, CANCELLED 중 하나로 종결 상태를 표현한다")
	void exposesTerminalStates() {
		Class<?> terminalResult = requiredClass(TERMINAL_RESULT);

		assertThat(normalizedNames(terminalResult)).containsExactlyInAnyOrder("SENT", "FAILED", "DEAD", "CANCELLED");
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
