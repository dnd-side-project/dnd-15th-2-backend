package com.dnd.qello.notification.domain;

import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record OutboxEvent(Long id, OutboxAggregateType aggregateType, long aggregateId,
	OutboxEventType eventType, String dedupKey, String payload, OutboxStatus status,
	int attemptCount, Instant nextAttemptAt, Instant createdAt, Instant processedAt) {

	private static final int DEDUP_KEY_MAX_LENGTH = 255;

	public OutboxEvent {
		if (id != null && id <= 0 || aggregateId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, null, "ID가 유효하지 않습니다");
		}
		requireValue(aggregateType, "aggregateType");
		requireValue(eventType, "eventType");
		if (dedupKey == null || dedupKey.isBlank() || dedupKey.length() > DEDUP_KEY_MAX_LENGTH) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_TEXT, "dedupKey", "dedupKey가 유효하지 않습니다");
		}
		if (payload == null || payload.isBlank() || !payload.trim().startsWith("{") || !payload.trim().endsWith("}")) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_PAYLOAD, "payload", "payload는 JSON object여야 합니다");
		}
		requireValue(status, "status");
		if (attemptCount < 0 || nextAttemptAt == null || createdAt == null) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, null, "outbox 시각/시도 횟수가 유효하지 않습니다");
		}
		if ((status == OutboxStatus.PROCESSED) != (processedAt != null)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATE, "processedAt", "PROCESSED와 processedAt은 함께 존재해야 합니다");
		}
	}

	public static OutboxEvent pending(OutboxAggregateType aggregateType, long aggregateId,
		OutboxEventType eventType, String dedupKey, String payload, Instant at) {
		return new OutboxEvent(null, aggregateType, aggregateId, eventType, dedupKey, payload,
			OutboxStatus.PENDING, 0, at, at, null);
	}

	public OutboxEvent claimed(Instant at) {
		if (status != OutboxStatus.PENDING && status != OutboxStatus.FAILED) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "처리 가능한 outbox 상태가 아닙니다");
		}
		return copy(OutboxStatus.PROCESSING, attemptCount + 1, at, processedAt);
	}

	public OutboxEvent processed(Instant at) {
		if (status != OutboxStatus.PROCESSING) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "PROCESSING 상태만 완료할 수 있습니다");
		}
		return copy(OutboxStatus.PROCESSED, attemptCount, nextAttemptAt, requireValue(at, "processedAt"));
	}

	public OutboxEvent failed(Instant nextAttemptAt, boolean dead) {
		if (status != OutboxStatus.PROCESSING) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "PROCESSING 상태만 실패 처리할 수 있습니다");
		}
		return copy(dead ? OutboxStatus.DEAD : OutboxStatus.FAILED, attemptCount,
			requireValue(nextAttemptAt, "nextAttemptAt"), null);
	}

	private OutboxEvent copy(OutboxStatus nextStatus, int nextAttempts, Instant nextAttempt, Instant nextProcessed) {
		return new OutboxEvent(id, aggregateType, aggregateId, eventType, dedupKey, payload, nextStatus,
			nextAttempts, nextAttempt, createdAt, nextProcessed);
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new NotificationException(
				NotificationErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}
}
