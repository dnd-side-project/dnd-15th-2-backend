package com.dnd.qello.notification.push.group;

import java.time.Instant;
import java.time.LocalDate;

import com.dnd.qello.notification.domain.NotificationType;

/** push_dispatch_group 행의 상태와 V28 제약을 보존하는 순수 domain record다. */
public record PushDispatchGroup(
	Long id,
	long recipientId,
	NotificationType notificationType,
	String aggregationKey,
	PushDispatchGroupStatus status,
	Instant windowStartedAt,
	Instant collectUntil,
	Instant policyExpiresAt,
	int attemptCount,
	Instant nextAttemptAt,
	LocalDate budgetLocalDate,
	Instant budgetConsumedAt,
	Instant firstAttemptedAt,
	Instant createdAt,
	Instant completedAt
) {

	public PushDispatchGroup {
		if (id != null && id <= 0 || recipientId <= 0) {
			throw new IllegalArgumentException("group 식별자는 양수여야 합니다");
		}
		if (notificationType == null || status == null) {
			throw new IllegalArgumentException("group 유형과 상태는 필수입니다");
		}
		if (aggregationKey == null || aggregationKey.isBlank() || aggregationKey.length() > 255) {
			throw new IllegalArgumentException("aggregationKey가 올바르지 않습니다");
		}
		requireTimestamp(windowStartedAt, "windowStartedAt");
		requireTimestamp(collectUntil, "collectUntil");
		requireTimestamp(policyExpiresAt, "policyExpiresAt");
		requireTimestamp(nextAttemptAt, "nextAttemptAt");
		requireTimestamp(createdAt, "createdAt");
		if (attemptCount < 0) {
			throw new IllegalArgumentException("attemptCount는 음수일 수 없습니다");
		}
		if (collectUntil.isBefore(windowStartedAt) || policyExpiresAt.isBefore(collectUntil)) {
			throw new IllegalArgumentException("group 정책 시각 순서가 올바르지 않습니다");
		}
		if ((budgetLocalDate == null) != (budgetConsumedAt == null)) {
			throw new IllegalArgumentException("budget date와 consumed 시각은 함께 존재해야 합니다");
		}
		if (firstAttemptedAt != null && budgetConsumedAt == null) {
			throw new IllegalArgumentException("첫 시도에는 예산 소비 시각이 필요합니다");
		}
		if (status.isTerminal() != (completedAt != null)) {
			throw new IllegalArgumentException("terminal 상태와 completedAt은 함께 존재해야 합니다");
		}
	}

	public boolean isTerminal() {
		return status.isTerminal();
	}

	public boolean hasConsumedBudget() {
		return budgetConsumedAt != null;
	}

	private static void requireTimestamp(Instant timestamp, String field) {
		if (timestamp == null) {
			throw new IllegalArgumentException(field + "은 필수입니다");
		}
	}
}
