/**
 * Created at: 2026-08-14T23:40:00+09:00
 * Source scenario: TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY-UNIT-007, UNIT-008
 */
package com.dnd.qello.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.ExponentialJitterBackoffStrategy;

class ExponentialJitterBackoffStrategyTest {

	private static final Duration BASE = Duration.ofSeconds(1);
	private static final Duration CAP = Duration.ofSeconds(30);

	@Test
	@DisplayName("attempt가 커져도 반환 지연이 cap을 넘지 않는다")
	void neverExceedsCap() {
		ExponentialJitterBackoffStrategy strategy =
			new ExponentialJitterBackoffStrategy(BASE, CAP, fixedJitter(0.999));

		Duration earlyAttempt = strategy.delayForAttempt(1);
		Duration lateAttempt = strategy.delayForAttempt(20);

		assertThat(earlyAttempt).isLessThanOrEqualTo(CAP);
		assertThat(lateAttempt).isLessThanOrEqualTo(CAP);
	}

	@Test
	@DisplayName("jitterSource가 0에 가까운 값을 반환해도 지연은 항상 양수다")
	void neverReturnsZeroOrNegativeDelay() {
		ExponentialJitterBackoffStrategy strategy =
			new ExponentialJitterBackoffStrategy(BASE, CAP, fixedJitter(0.0));

		Duration delay = strategy.delayForAttempt(1);

		assertThat(delay).isPositive();
	}

	@Test
	@DisplayName("attempt가 커질수록 jitter 상한 자체는 단조 증가하다가 cap에서 멈춘다")
	void upperBoundGrowsMonotonicallyUntilCap() {
		ExponentialJitterBackoffStrategy strategy =
			new ExponentialJitterBackoffStrategy(BASE, CAP, fixedJitter(0.999));

		Duration attempt1 = strategy.delayForAttempt(1);
		Duration attempt2 = strategy.delayForAttempt(2);
		Duration attempt3 = strategy.delayForAttempt(3);
		Duration attempt10 = strategy.delayForAttempt(10);
		// attempt=10이면 base(1s) * 2^9 = 512s가 cap(30s)을 넘어서므로, 상한 자체가 cap으로
		// 고정되고 jitter만 그 위에서 적용된다 — jitterRatio=0.999로 고정했으니 cap*0.999.
		Duration expectedAtCap = Duration.ofMillis(Math.round(CAP.toMillis() * 0.999));

		assertThat(attempt1).isLessThan(attempt2);
		assertThat(attempt2).isLessThan(attempt3);
		assertThat(attempt10).isEqualTo(expectedAtCap).isLessThanOrEqualTo(CAP);
	}

	@Test
	@DisplayName("동일 attempt를 반복 호출해도 jitterSource가 다른 값을 주면 지연이 매번 달라진다")
	void variesAcrossCallsForSameAttemptWhenJitterVaries() {
		ExponentialJitterBackoffStrategy strategy =
			new ExponentialJitterBackoffStrategy(BASE, CAP, sequencedJitter(List.of(0.1, 0.5, 0.9)));

		Duration first = strategy.delayForAttempt(5);
		Duration second = strategy.delayForAttempt(5);
		Duration third = strategy.delayForAttempt(5);

		assertThat(List.of(first, second, third)).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("baseDelay나 maxDelay가 0 이하이거나 순서가 뒤바뀌면 생성 시점에 거절된다")
	void rejectsInvalidConstructionArguments() {
		assertThatThrownBy(() -> new ExponentialJitterBackoffStrategy(Duration.ZERO, CAP, fixedJitter(0.5)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ExponentialJitterBackoffStrategy(BASE, BASE.minusMillis(1), fixedJitter(0.5)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("attemptCount가 1 미만이면 거절된다")
	void rejectsNonPositiveAttemptCount() {
		ExponentialJitterBackoffStrategy strategy =
			new ExponentialJitterBackoffStrategy(BASE, CAP, fixedJitter(0.5));

		assertThatThrownBy(() -> strategy.delayForAttempt(0)).isInstanceOf(IllegalArgumentException.class);
	}

	private static java.util.function.DoubleSupplier fixedJitter(double value) {
		return () -> value;
	}

	private static java.util.function.DoubleSupplier sequencedJitter(List<Double> values) {
		Iterator<Double> iterator = values.iterator();
		return iterator::next;
	}
}
