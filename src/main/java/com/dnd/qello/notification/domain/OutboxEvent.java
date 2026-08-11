package com.dnd.qello.notification.domain;

import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public record OutboxEvent(Long id, OutboxAggregateType aggregateType, long aggregateId,
		OutboxEventType eventType, String dedupKey, String payload, OutboxStatus status,
		int attemptCount, Instant nextAttemptAt, Instant createdAt, Instant processedAt,
		Integer matchRound, String leaseOwner, Instant leaseExpiresAt, long leaseGeneration) {

	private static final int DEDUP_KEY_MAX_LENGTH = 255;
	private static final int LEASE_OWNER_MAX_LENGTH = 100;
	private static final String LEGACY_CLAIM_OWNER = "legacy-compat";

	public OutboxEvent(Long id, OutboxAggregateType aggregateType, long aggregateId,
		OutboxEventType eventType, String dedupKey, String payload, OutboxStatus status,
		int attemptCount, Instant nextAttemptAt, Instant createdAt, Instant processedAt) {
		this(id, aggregateType, aggregateId, eventType, dedupKey, payload, status,
			attemptCount, nextAttemptAt, createdAt, processedAt,
			defaultMatchRound(aggregateType, eventType), null, null, 0);
	}

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
		boolean matchingEvent = isMatchingEvent(aggregateType, eventType);
		if (matchingEvent != (matchRound != null) || matchRound != null && matchRound <= 0) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "matchRound", "매칭 라운드가 유효하지 않습니다");
		}
		if (leaseGeneration < 0) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "leaseGeneration", "lease 세대가 유효하지 않습니다");
		}
		if (leaseOwner != null && (leaseOwner.isBlank() || leaseOwner.length() > LEASE_OWNER_MAX_LENGTH)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_TEXT, "leaseOwner", "lease 소유자가 유효하지 않습니다");
		}
		boolean processing = status == OutboxStatus.PROCESSING;
		if (processing != (leaseOwner != null && leaseExpiresAt != null)
			|| (leaseOwner == null) != (leaseExpiresAt == null)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATE, "leaseOwner", "lease 상태와 소유자 정보가 맞지 않습니다");
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

	public static OutboxEvent pending(OutboxAggregateType aggregateType, long aggregateId,
		OutboxEventType eventType, String dedupKey, String payload, int matchRound, Instant at) {
		return new OutboxEvent(null, aggregateType, aggregateId, eventType, dedupKey, payload,
			OutboxStatus.PENDING, 0, at, at, null, matchRound, null, null, 0);
	}

	public static OutboxEvent matchingPending(long postId, int matchRound, String dedupKey,
		String payload, Instant at) {
		return pending(OutboxAggregateType.DIRECTION_POST, postId,
			OutboxEventType.RECIPIENT_MATCH_REQUESTED, dedupKey, payload, matchRound, at);
	}

	public OutboxEvent claimed(Instant at) {
		return claimed(LEGACY_CLAIM_OWNER, at, Instant.MAX);
	}

	public OutboxEvent claimed(String owner, Instant at, Instant expiresAt) {
		requireValue(owner, "leaseOwner");
		requireValue(at, "claimedAt");
		requireValue(expiresAt, "leaseExpiresAt");
		boolean reclaimable = status == OutboxStatus.PROCESSING
			&& leaseExpiresAt != null && !leaseExpiresAt.isAfter(at);
		if (status != OutboxStatus.PENDING && status != OutboxStatus.FAILED && !reclaimable) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "처리 가능한 outbox 상태가 아닙니다");
		}
		if (expiresAt.isBefore(at) || expiresAt.equals(at)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "leaseExpiresAt", "lease 만료 시각은 현재 시각 이후여야 합니다");
		}
		if (leaseGeneration == Long.MAX_VALUE) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "leaseGeneration", "lease 세대가 상한을 초과합니다");
		}
		return new OutboxEvent(id, aggregateType, aggregateId, eventType, dedupKey, payload,
			OutboxStatus.PROCESSING, attemptCount + 1, at, createdAt, null,
			matchRound, owner, expiresAt, leaseGeneration + 1);
	}

	public OutboxEvent processed(Instant at) {
		if (status != OutboxStatus.PROCESSING) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "PROCESSING 상태만 완료할 수 있습니다");
		}
		return copy(OutboxStatus.PROCESSED, attemptCount, nextAttemptAt, requireValue(at, "processedAt"),
			null, null, leaseGeneration);
	}

	public OutboxEvent failed(Instant nextAttemptAt, boolean dead) {
		if (status != OutboxStatus.PROCESSING) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "PROCESSING 상태만 실패 처리할 수 있습니다");
		}
		return copy(dead ? OutboxStatus.DEAD : OutboxStatus.FAILED, attemptCount,
			requireValue(nextAttemptAt, "nextAttemptAt"), null, null, null, leaseGeneration);
	}

	private OutboxEvent copy(OutboxStatus nextStatus, int nextAttempts, Instant nextAttempt, Instant nextProcessed,
		String nextLeaseOwner, Instant nextLeaseExpiresAt, long nextLeaseGeneration) {
		return new OutboxEvent(id, aggregateType, aggregateId, eventType, dedupKey, payload, nextStatus,
			nextAttempts, nextAttempt, createdAt, nextProcessed, matchRound, nextLeaseOwner,
			nextLeaseExpiresAt, nextLeaseGeneration);
	}

	private static Integer defaultMatchRound(OutboxAggregateType aggregateType, OutboxEventType eventType) {
		return isMatchingEvent(aggregateType, eventType) ? 1 : null;
	}

	private static boolean isMatchingEvent(OutboxAggregateType aggregateType, OutboxEventType eventType) {
		return aggregateType == OutboxAggregateType.DIRECTION_POST
			&& eventType == OutboxEventType.RECIPIENT_MATCH_REQUESTED;
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new NotificationException(
				NotificationErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}
}
