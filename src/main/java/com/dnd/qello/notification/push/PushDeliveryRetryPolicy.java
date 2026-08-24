package com.dnd.qello.notification.push;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

/**
 * provider 결과와 claim generation을 bounded delivery decision으로 바꾼다.
 * maxAttempts, baseBackoff, backoffCap은 application configuration으로 주입한다.
 */
public record PushDeliveryRetryPolicy(
	int maxAttempts,
	Duration baseBackoff,
	Duration backoffCap) {

	public PushDeliveryRetryPolicy {
		if (maxAttempts < 1) {
			throw invalid("maxAttempts", "최대 시도 횟수는 1 이상이어야 합니다");
		}
		if (baseBackoff == null || baseBackoff.isZero() || baseBackoff.isNegative()) {
			throw invalid("baseBackoff", "baseBackoff은 양수여야 합니다");
		}
		if (backoffCap == null || backoffCap.compareTo(baseBackoff) < 0) {
			throw invalid("backoffCap", "backoffCap은 baseBackoff 이상이어야 합니다");
		}
	}

	public Decision decide(int generation, PushProviderResult providerResult, Instant at) {
		if (generation < 1) {
			throw invalid("generation", "generation은 1 이상이어야 합니다");
		}
		if (providerResult == null) {
			throw required("providerResult");
		}
		if (at == null) {
			throw required("at");
		}

		if (providerResult instanceof PushProviderResult.Accepted) {
			return terminal(PushDeliveryTerminalResult.SENT, at, Duration.ZERO);
		}
		if (providerResult instanceof PushProviderResult.InvalidToken
			|| providerResult instanceof PushProviderResult.PermanentFailure) {
			return terminal(PushDeliveryTerminalResult.DEAD, at, Duration.ZERO);
		}

		PushProviderResult.RetryableFailure retryable = (PushProviderResult.RetryableFailure) providerResult;
		if (generation >= maxAttempts) {
			return terminal(PushDeliveryTerminalResult.DEAD, at, Duration.ZERO);
		}

		Duration delay = safeRetryAfter(retryable.retryAfter()).orElseGet(() -> boundedExponential(generation));
		try {
			return terminal(PushDeliveryTerminalResult.FAILED, at.plus(delay), delay);
		} catch (DateTimeException | ArithmeticException exception) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "backoff",
				"다음 재시도 시각을 계산할 수 없습니다", exception);
		}
	}

	private java.util.Optional<Duration> safeRetryAfter(Duration retryAfter) {
		if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()
			|| retryAfter.compareTo(backoffCap) > 0) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(retryAfter);
	}

	private Duration boundedExponential(int generation) {
		int shift = Math.min(generation - 1, 62);
		try {
			Duration delay = baseBackoff.multipliedBy(1L << shift);
			return delay.compareTo(backoffCap) > 0 ? backoffCap : delay;
		} catch (ArithmeticException exception) {
			return backoffCap;
		}
	}

	private static Decision terminal(PushDeliveryTerminalResult result, Instant nextAttemptAt, Duration delay) {
		return new Decision(result, nextAttemptAt, delay);
	}

	private static NotificationException required(String field) {
		return new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, field,
			field + "은 필수입니다");
	}

	private static NotificationException invalid(String field, String reason) {
		return new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, field, reason);
	}

	public record Decision(PushDeliveryTerminalResult result, Instant nextAttemptAt, Duration delay) {
		public Decision {
			if (result == null || nextAttemptAt == null || delay == null || delay.isNegative()) {
				throw new IllegalArgumentException("retry decision 값이 올바르지 않습니다");
			}
		}

		public boolean retryable() {
			return result == PushDeliveryTerminalResult.FAILED;
		}
	}
}
