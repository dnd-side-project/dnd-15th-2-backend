package com.dnd.qello.notification.domain;

import java.time.Instant;

/** Outbox 실패를 저장할 다음 상태와 시각을 표현한다. */
public record OutboxRetryDecision(boolean dead, Instant nextAttemptAt) {

	public OutboxRetryDecision {
		if (nextAttemptAt == null) {
			throw new IllegalArgumentException("nextAttemptAt must not be null");
		}
	}

	public OutboxStatus status() {
		return dead ? OutboxStatus.DEAD : OutboxStatus.FAILED;
	}
}
