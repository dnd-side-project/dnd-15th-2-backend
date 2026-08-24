/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-012
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushDeliveryRetryPolicyTest {

	private static final String RETRY_POLICY = "com.dnd.qello.notification.push.PushDeliveryRetryPolicy";
	private static final String PROVIDER_RESULT = "com.dnd.qello.notification.push.PushProviderResult";

	@Test
	@DisplayName("UNIT-012: provider 결과는 Accepted, InvalidToken, RetryableFailure, PermanentFailure 네 가지만 허용한다")
	void exposesClosedProviderResultHierarchy() {
		Class<?> providerResult = requiredClass(PROVIDER_RESULT);

		assertThat(Arrays.stream(providerResult.getDeclaredClasses()).map(Class::getSimpleName))
			.containsExactlyInAnyOrder("Accepted", "InvalidToken", "RetryableFailure", "PermanentFailure");
	}

	@Test
	@DisplayName("UNIT-012: retryable와 permanent provider 결과는 retryAfter와 safeReasonCode를 각각 보존한다")
	void exposesProviderResultPayloadFields() {
		Class<?> retryableFailure = requiredClass(PROVIDER_RESULT + "$RetryableFailure");
		Class<?> permanentFailure = requiredClass(PROVIDER_RESULT + "$PermanentFailure");

		assertThat(retryableFailure.isRecord()).isTrue();
		assertThat(permanentFailure.isRecord()).isTrue();
		assertThat(recordComponentNames(retryableFailure)).containsExactly("retryAfter");
		assertThat(recordComponentNames(permanentFailure)).containsExactly("safeReasonCode");
	}

	@Test
	@DisplayName("UNIT-012: retry policy는 generation, provider 결과, 시각을 받아 종결 결정을 계산한다")
	void declaresGenerationAndProviderDecisionMethod() {
		Class<?> retryPolicy = requiredClass(RETRY_POLICY);
		Class<?> providerResult = requiredClass(PROVIDER_RESULT);

		assertThat(Arrays.stream(retryPolicy.getMethods())
			.filter(method -> method.getName().equals("decide"))
			.filter(method -> method.getParameterCount() == 3)
			.anyMatch(method -> Arrays.stream(method.getParameterTypes())
				.anyMatch(type -> type.equals(providerResult))
				&& Arrays.stream(method.getParameterTypes()).anyMatch(type -> type.equals(Instant.class))))
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
}
