package com.dnd.qello.notification.domain;

import java.time.Instant;
import java.util.Objects;

public record OutboxEvent(Long id, OutboxAggregateType aggregateType, long aggregateId,
	OutboxEventType eventType, String dedupKey, String payload, OutboxStatus status,
	int attemptCount, Instant nextAttemptAt, Instant createdAt, Instant processedAt) {

	public OutboxEvent {
		if (id != null && id <= 0 || aggregateId <= 0) throw new IllegalArgumentException("ID가 유효하지 않습니다");
		Objects.requireNonNull(aggregateType, "aggregateType은 필수입니다");
		Objects.requireNonNull(eventType, "eventType은 필수입니다");
		if (dedupKey == null || dedupKey.isBlank() || dedupKey.length() > 255) throw new IllegalArgumentException("dedupKey가 유효하지 않습니다");
		if (payload == null || payload.isBlank() || !payload.trim().startsWith("{") || !payload.trim().endsWith("}")) throw new IllegalArgumentException("payload는 JSON object여야 합니다");
		Objects.requireNonNull(status, "status는 필수입니다");
		if (attemptCount < 0 || nextAttemptAt == null || createdAt == null) throw new IllegalArgumentException("outbox 시각/시도 횟수가 유효하지 않습니다");
		if ((status == OutboxStatus.PROCESSED) != (processedAt != null)) throw new IllegalArgumentException("PROCESSED와 processedAt은 함께 존재해야 합니다");
	}

	public static OutboxEvent pending(OutboxAggregateType aggregateType, long aggregateId,
		OutboxEventType eventType, String dedupKey, String payload, Instant at) {
		return new OutboxEvent(null, aggregateType, aggregateId, eventType, dedupKey, payload,
			OutboxStatus.PENDING, 0, at, at, null);
	}

	public OutboxEvent claimed(Instant at) {
		if (status != OutboxStatus.PENDING && status != OutboxStatus.FAILED) throw new IllegalStateException("처리 가능한 outbox 상태가 아닙니다");
		return copy(OutboxStatus.PROCESSING, attemptCount + 1, at, processedAt);
	}

	public OutboxEvent processed(Instant at) {
		if (status != OutboxStatus.PROCESSING) throw new IllegalStateException("PROCESSING 상태만 완료할 수 있습니다");
		return copy(OutboxStatus.PROCESSED, attemptCount, nextAttemptAt, Objects.requireNonNull(at, "processedAt은 필수입니다"));
	}

	public OutboxEvent failed(Instant nextAttemptAt, boolean dead) {
		if (status != OutboxStatus.PROCESSING) throw new IllegalStateException("PROCESSING 상태만 실패 처리할 수 있습니다");
		return copy(dead ? OutboxStatus.DEAD : OutboxStatus.FAILED, attemptCount, Objects.requireNonNull(nextAttemptAt), null);
	}

	private OutboxEvent copy(OutboxStatus nextStatus, int nextAttempts, Instant nextAttempt, Instant nextProcessed) {
		return new OutboxEvent(id, aggregateType, aggregateId, eventType, dedupKey, payload, nextStatus,
			nextAttempts, nextAttempt, createdAt, nextProcessed);
	}
}
