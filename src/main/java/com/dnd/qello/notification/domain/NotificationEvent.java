package com.dnd.qello.notification.domain;

import java.time.Instant;

import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

// manual review case 생성을 관리자 채널(Slack)에 알리기 위한 전용 outbox
// 성격의 record다(#111). 기존 outbox_event/OutboxEvent와 별개 테이블에
// 매핑되며, 허용목록을 컴럼 스키마로 강제하기 위해 payload를 caseId·
// adminLinkPath 두 필드로만 제한한다.
public record NotificationEvent(Long id, long caseId, String adminLinkPath, NotificationEventStatus status,
		int attemptCount, Instant nextAttemptAt, Instant createdAt, Instant processedAt,
		String leaseOwner, Instant leaseExpiresAt, long leaseGeneration) {

	private static final int ADMIN_LINK_PATH_MAX_LENGTH = 255;
	private static final int LEASE_OWNER_MAX_LENGTH = 100;

	public NotificationEvent {
		if (id != null && id <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "id", "ID가 유효하지 않습니다");
		}
		if (caseId <= 0) {
			throw new NotificationException(NotificationErrorCode.INVALID_ID, "caseId", "caseId가 유효하지 않습니다");
		}
		if (adminLinkPath == null || adminLinkPath.isBlank() || adminLinkPath.length() > ADMIN_LINK_PATH_MAX_LENGTH) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_TEXT, "adminLinkPath", "adminLinkPath가 유효하지 않습니다");
		}
		requireValue(status, "status");
		if (attemptCount < 0 || nextAttemptAt == null || createdAt == null) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, null, "시각/시도 횟수가 유효하지 않습니다");
		}
		if (leaseGeneration < 0) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "leaseGeneration", "lease 세대가 유효하지 않습니다");
		}
		if (leaseOwner != null && (leaseOwner.isBlank() || leaseOwner.length() > LEASE_OWNER_MAX_LENGTH)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_TEXT, "leaseOwner", "lease 소유자가 유효하지 않습니다");
		}
		boolean processing = status == NotificationEventStatus.PROCESSING;
		if (processing != (leaseOwner != null && leaseExpiresAt != null)
			|| (leaseOwner == null) != (leaseExpiresAt == null)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATE, "leaseOwner", "lease 상태와 소유자 정보가 맞지 않습니다");
		}
		if ((status == NotificationEventStatus.PROCESSED) != (processedAt != null)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATE, "processedAt", "PROCESSED와 processedAt은 함께 존재해야 합니다");
		}
	}

	public static NotificationEvent pending(long caseId, String adminLinkPath, Instant at) {
		return new NotificationEvent(null, caseId, adminLinkPath, NotificationEventStatus.PENDING,
			0, at, at, null, null, null, 0);
	}

	public NotificationEvent claimed(String owner, Instant at, Instant expiresAt) {
		requireValue(owner, "leaseOwner");
		requireValue(at, "claimedAt");
		requireValue(expiresAt, "leaseExpiresAt");
		boolean reclaimable = status == NotificationEventStatus.PROCESSING
			&& leaseExpiresAt != null && !leaseExpiresAt.isAfter(at);
		if (status != NotificationEventStatus.PENDING && status != NotificationEventStatus.FAILED && !reclaimable) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "처리 가능한 상태가 아닙니다");
		}
		if (expiresAt.isBefore(at) || expiresAt.equals(at)) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "leaseExpiresAt", "lease 만료 시각은 현재 시각 이후여야 합니다");
		}
		if (leaseGeneration == Long.MAX_VALUE) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_VALUE_RANGE, "leaseGeneration", "lease 세대가 상한을 초과합니다");
		}
		return new NotificationEvent(id, caseId, adminLinkPath, NotificationEventStatus.PROCESSING,
			attemptCount + 1, at, createdAt, null, owner, expiresAt, leaseGeneration + 1);
	}

	public NotificationEvent processed(Instant at) {
		if (status != NotificationEventStatus.PROCESSING) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "PROCESSING 상태만 완료할 수 있습니다");
		}
		return copy(NotificationEventStatus.PROCESSED, attemptCount, nextAttemptAt,
			requireValue(at, "processedAt"), null, null, leaseGeneration);
	}

	public NotificationEvent failed(Instant nextAttemptAt, boolean dead) {
		if (status != NotificationEventStatus.PROCESSING) {
			throw new NotificationException(
				NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status", "PROCESSING 상태만 실패 처리할 수 있습니다");
		}
		return copy(dead ? NotificationEventStatus.DEAD : NotificationEventStatus.FAILED, attemptCount,
			requireValue(nextAttemptAt, "nextAttemptAt"), null, null, null, leaseGeneration);
	}

	private NotificationEvent copy(NotificationEventStatus nextStatus, int nextAttempts, Instant nextAttempt,
			Instant nextProcessed, String nextLeaseOwner, Instant nextLeaseExpiresAt, long nextLeaseGeneration) {
		return new NotificationEvent(id, caseId, adminLinkPath, nextStatus, nextAttempts, nextAttempt, createdAt,
			nextProcessed, nextLeaseOwner, nextLeaseExpiresAt, nextLeaseGeneration);
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new NotificationException(
				NotificationErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}
}
