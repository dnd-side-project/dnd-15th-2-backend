/**
 * Created at: 2026-08-24T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-012
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Test
	@DisplayName("UNIT-012: Accepted 결과는 SENT로 종결되고 재시도 지연은 0이다")
	void acceptedResultBecomesSent() {
		Instant at = Instant.parse("2026-08-24T12:00:00Z");
		PushDeliveryRetryPolicy policy = policy();

		PushDeliveryRetryPolicy.Decision decision = policy.decide(
			1, new PushProviderResult.Accepted("projects/test/messages/unit-accepted"), at);

		assertThat(decision.result()).isEqualTo(PushDeliveryTerminalResult.SENT);
		assertThat(decision.nextAttemptAt()).isEqualTo(at);
		assertThat(decision.delay()).isZero();
		assertThat(decision.retryable()).isFalse();
	}

	@Test
	@DisplayName("UNIT-012: Accepted providerMessageId는 255자 이하 비공백·비제어 문자열만 허용한다")
	void validatesAcceptedProviderMessageId() {
		String maxLength = "m".repeat(255);

		assertThat(new PushProviderResult.Accepted(maxLength).providerMessageId()).isEqualTo(maxLength);
		assertThatThrownBy(() -> new PushProviderResult.Accepted(null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushProviderResult.Accepted(""))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushProviderResult.Accepted("contains whitespace"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushProviderResult.Accepted("contains\ncontrol"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushProviderResult.Accepted("contains\u00a0space"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PushProviderResult.Accepted("m".repeat(256)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-012: 안전한 Retry-After는 FAILED와 지정된 다음 시각으로 보존된다")
	void safeRetryAfterSchedulesTheSuppliedNextAttempt() {
		Instant at = Instant.parse("2026-08-24T12:00:00Z");
		Duration retryAfter = Duration.ofSeconds(17);

		PushDeliveryRetryPolicy.Decision decision = policy().decide(1,
			new PushProviderResult.RetryableFailure(retryAfter), at);

		assertThat(decision.result()).isEqualTo(PushDeliveryTerminalResult.FAILED);
		assertThat(decision.nextAttemptAt()).isEqualTo(at.plus(retryAfter));
		assertThat(decision.delay()).isEqualTo(retryAfter);
		assertThat(decision.retryable()).isTrue();
	}

	@Test
	@DisplayName("UNIT-012: invalid token, permanent failure, 최대 시도 횟수 초과는 DEAD로 종결된다")
	void terminalProviderResultsAndMaxAttemptBecomeDead() {
		Instant at = Instant.parse("2026-08-24T12:00:00Z");
		PushDeliveryRetryPolicy policy = policy();

		assertThat(policy.decide(1, new PushProviderResult.InvalidToken(), at).result())
			.isEqualTo(PushDeliveryTerminalResult.DEAD);
		assertThat(policy.decide(1, new PushProviderResult.PermanentFailure("PROVIDER_REJECTED"), at).result())
			.isEqualTo(PushDeliveryTerminalResult.DEAD);
		assertThat(policy.decide(3, new PushProviderResult.RetryableFailure(Duration.ofSeconds(5)), at).result())
			.isEqualTo(PushDeliveryTerminalResult.DEAD);
	}

	@Test
	@DisplayName("UNIT-012: 범위를 벗어난 Retry-After는 bounded exponential backoff로 대체된다")
	void unsafeRetryAfterUsesBoundedExponentialFallback() {
		Instant at = Instant.parse("2026-08-24T12:00:00Z");
		PushDeliveryRetryPolicy policy = new PushDeliveryRetryPolicy(8, Duration.ofSeconds(10), Duration.ofSeconds(25));

		PushDeliveryRetryPolicy.Decision decision = policy.decide(3,
			new PushProviderResult.RetryableFailure(Duration.ofSeconds(60)), at);

		assertThat(decision.result()).isEqualTo(PushDeliveryTerminalResult.FAILED);
		assertThat(decision.delay()).isEqualTo(Duration.ofSeconds(25));
		assertThat(decision.nextAttemptAt()).isEqualTo(at.plusSeconds(25));
	}

	private static PushDeliveryRetryPolicy policy() {
		return new PushDeliveryRetryPolicy(3, Duration.ofSeconds(10), Duration.ofSeconds(40));
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
