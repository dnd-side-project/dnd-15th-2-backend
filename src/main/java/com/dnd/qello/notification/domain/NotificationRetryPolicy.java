package com.dnd.qello.notification.domain;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

/**
 * {@link NotificationEvent} dispatch 실패의 재시도/dead 여부만 결정한다.
 * {@link OutboxRetryPolicy}와 동일한 판정 규칙을 {@link NotificationEvent}에
 * 적용한다 — 두 record가 서로 다른 테이블에 매핑되어 있어 판정 로직 자체는
 * 공유하지 못하지만, {@link OutboxBackoffStrategy}/{@link OutboxFailureKind}는
 * 그대로 재사용한다.
 */
public record NotificationRetryPolicy(int maxAttempts, OutboxBackoffStrategy backoffStrategy) {

	public NotificationRetryPolicy {
		if (maxAttempts < 1) {
			throw invalid("maxAttempts", "최대 시도 횟수는 1 이상이어야 합니다");
		}
		if (backoffStrategy == null) {
			throw required("backoffStrategy");
		}
	}

	public OutboxRetryDecision decide(NotificationEvent event, OutboxFailureKind failureKind, Instant at) {
		if (event == null) {
			throw required("event");
		}
		if (event.status() != NotificationEventStatus.PROCESSING) {
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
