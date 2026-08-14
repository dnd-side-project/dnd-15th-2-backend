package com.dnd.qello.notification.domain;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

// capped exponential backoff에 jitter를 더한 OutboxBackoffStrategy 구현체(#108).
// #119는 OutboxBackoffStrategy 인터페이스만 남기고 구현체를 두지 않았다 — 이 클래스가
// 그 자리를 채우며, 다른 outbox 소비자(예: DirectionMatchingWorker)도 재사용할 수
// 있도록 filtering 패키지가 아닌 notification.domain에 둔다.
//
// "full jitter" 방식 — attempt별 상한(base * 2^(attempt-1), cap으로 제한) 사이에서
// 균등 분포로 지연을 뽑는다. 동시에 실패한 여러 job이 같은 시각에 한꺼번에 재시도를
// 몰아 보내는 thundering herd를 완화한다. base/cap 수치 자체는 #108 이슈 본문에서
// "미결정"으로 명시돼 있어 이 클래스가 정하지 않고 호출자가 주입한다.
public final class ExponentialJitterBackoffStrategy implements OutboxBackoffStrategy {

	private final Duration baseDelay;
	private final Duration maxDelay;
	private final DoubleSupplier jitterSource;

	/**
	 * @param jitterSource [0, 1) 범위의 값을 반환해야 한다. 테스트는 결정적 값을 주입해
	 *                      지연 계산을 재현 가능하게 만든다.
	 */
	public ExponentialJitterBackoffStrategy(Duration baseDelay, Duration maxDelay, DoubleSupplier jitterSource) {
		if (baseDelay == null || baseDelay.isZero() || baseDelay.isNegative()) {
			throw new IllegalArgumentException("baseDelay는 양수여야 합니다");
		}
		if (maxDelay == null || maxDelay.compareTo(baseDelay) < 0) {
			throw new IllegalArgumentException("maxDelay는 baseDelay 이상이어야 합니다");
		}
		if (jitterSource == null) {
			throw new IllegalArgumentException("jitterSource는 필수입니다");
		}
		this.baseDelay = baseDelay;
		this.maxDelay = maxDelay;
		this.jitterSource = jitterSource;
	}

	public static ExponentialJitterBackoffStrategy withRandomJitter(Duration baseDelay, Duration maxDelay) {
		return new ExponentialJitterBackoffStrategy(baseDelay, maxDelay, ThreadLocalRandom.current()::nextDouble);
	}

	@Override
	public Duration delayForAttempt(int attemptCount) {
		if (attemptCount < 1) {
			throw new IllegalArgumentException("attemptCount는 1 이상이어야 합니다");
		}
		Duration cap = cappedExponential(attemptCount);
		double jitterRatio = jitterSource.getAsDouble();
		if (jitterRatio < 0 || jitterRatio >= 1) {
			throw new IllegalStateException("jitterSource는 [0, 1) 범위를 반환해야 합니다: " + jitterRatio);
		}
		// 0 지연은 OutboxRetryPolicy가 거부하므로(양수 강제) 최소 1ms를 보장한다.
		long jitteredMillis = Math.max(Math.round(cap.toMillis() * jitterRatio), 1L);
		return Duration.ofMillis(jitteredMillis);
	}

	private Duration cappedExponential(int attemptCount) {
		int shift = Math.min(attemptCount - 1, 62);
		Duration exponential;
		try {
			exponential = baseDelay.multipliedBy(1L << shift);
		} catch (ArithmeticException overflow) {
			exponential = maxDelay;
		}
		return exponential.compareTo(maxDelay) > 0 ? maxDelay : exponential;
	}
}
