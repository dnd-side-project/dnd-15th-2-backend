package com.dnd.qello.notification.domain;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

/**
 * Outbox worker의 실패 상태만 결정한다. 구체적인 업무 예외를 분류하거나 실제
 * 재시도 작업을 실행하는 책임은 각 worker에 있다.
 */
public record OutboxRetryPolicy(int maxAttempts, OutboxBackoffStrategy backoffStrategy) {

	public OutboxRetryPolicy {
		if (maxAttempts < 1) {
			throw invalid("maxAttempts", "최대 시도 횟수는 1 이상이어야 합니다");
		}
		if (backoffStrategy == null) {
			throw required("backoffStrategy");
		}
	}

	public OutboxRetryDecision decide(OutboxEvent event, OutboxFailureKind failureKind, Instant at) {
		if (event == null) {
			throw required("event");
		}
		if (event.status() != OutboxStatus.PROCESSING) {
			throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status",
				"PROCESSING 상태만 실패 판정을 할 수 있습니다");
		}
		if (failureKind == null) {
			throw required("failureKind");
		}
		if (at == null) {
			throw required("at");
		}
		if (failureKind == OutboxFailureKind.PERMANENT || event.attemptCount() >= maxAttempts) {
			return new OutboxRetryDecision(true, at);
		}

		Duration delay = backoffStrategy.delayForAttempt(event.attemptCount());
		if (delay == null || delay.isZero() || delay.isNegative()) {
			throw invalid("backoff", "backoff은 양수여야 합니다");
		}
		try {
			return new OutboxRetryDecision(false, at.plus(delay));
		} catch (DateTimeException | ArithmeticException exception) {
			throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "backoff",
				"다음 재시도 시각을 계산할 수 없습니다", exception);
		}
	}

	private static NotificationException required(String field) {
		return new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, field,
			field + "은 필수입니다");
	}

	private static NotificationException invalid(String field, String reason) {
		return new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, field, reason);
	}
}
