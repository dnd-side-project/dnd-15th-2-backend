/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-011
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushDeliveryStateTest {

	private static final String CLAIM_SERVICE = "com.dnd.qello.notification.service.PushDeliveryClaimService";
	private static final String CLAIMED_DELIVERY = "com.dnd.qello.notification.push.ClaimedPushDelivery";
	private static final String TERMINAL_RESULT = "com.dnd.qello.notification.push.PushDeliveryTerminalResult";

	@Test
	@DisplayName("UNIT-011: claim service는 배치 claim과 generation fencing terminal API를 노출한다")
	void exposesBatchClaimAndFencedTerminalApi() throws Exception {
		Class<?> claimService = requiredClass(CLAIM_SERVICE);
		Class<?> terminalResult = requiredClass(TERMINAL_RESULT);

		assertThat(claimService.getMethod("claimDueDeliveries", int.class, Instant.class, Instant.class)
			.getReturnType()).isEqualTo(List.class);
		assertThat(claimService.getMethod("completeClaim", long.class, int.class, terminalResult, Instant.class)
			.getReturnType()).isEqualTo(boolean.class);
	}

	@Test
	@DisplayName("UNIT-011: claimed delivery는 deliveryId, generation, leaseUntil을 record component로 노출한다")
	void exposesClaimedDeliveryBoundaryFields() {
		Class<?> claimedDelivery = requiredClass(CLAIMED_DELIVERY);

		assertThat(claimedDelivery.isRecord()).isTrue();
		assertThat(recordComponentNames(claimedDelivery)).contains("deliveryId", "generation", "leaseUntil");
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
