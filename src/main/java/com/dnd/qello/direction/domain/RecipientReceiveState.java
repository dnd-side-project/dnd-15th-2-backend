package com.dnd.qello.direction.domain;

import java.time.Instant;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

public final class RecipientReceiveState {

	public static final int MAX_ACTIVE_UNHANDLED = 5;
	private final Long userId;
	private final int activeUnhandledCount;
	private final int recentReceivedCount;
	private final Instant recentWindowStartedAt;
	private final Instant lastReceivedAt;
	private final Instant updatedAt;

	private RecipientReceiveState(Long userId, int activeUnhandledCount, int recentReceivedCount,
		Instant recentWindowStartedAt, Instant lastReceivedAt, Instant updatedAt) {
		if (userId == null || userId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다");
		}
		if (activeUnhandledCount < 0 || activeUnhandledCount > MAX_ACTIVE_UNHANDLED) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "activeUnhandledCount", "activeUnhandledCount 범위가 유효하지 않습니다");
		}
		if (recentReceivedCount < 0) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_VALUE_RANGE, "recentReceivedCount", "recentReceivedCount는 음수일 수 없습니다");
		}
		this.userId = userId;
		this.activeUnhandledCount = activeUnhandledCount;
		this.recentReceivedCount = recentReceivedCount;
		if (recentWindowStartedAt == null) {
			throw new DirectionException(
				DirectionErrorCode.REQUIRED_VALUE_MISSING, "recentWindowStartedAt", "recentWindowStartedAt은 필수입니다");
		}
		this.recentWindowStartedAt = recentWindowStartedAt;
		if (lastReceivedAt != null && lastReceivedAt.isBefore(recentWindowStartedAt)) {
			throw new DirectionException(
				DirectionErrorCode.INVALID_TIME_ORDER, "lastReceivedAt", "lastReceivedAt이 window보다 빠릅니다");
		}
		this.lastReceivedAt = lastReceivedAt;
		this.updatedAt = updatedAt;
	}

	public static RecipientReceiveState restore(Long userId, int activeUnhandledCount, int recentReceivedCount,
		Instant recentWindowStartedAt, Instant lastReceivedAt, Instant updatedAt) {
		return new RecipientReceiveState(userId, activeUnhandledCount, recentReceivedCount,
			recentWindowStartedAt, lastReceivedAt, updatedAt);
	}

	public boolean canReserve() { return activeUnhandledCount < MAX_ACTIVE_UNHANDLED; }

	public Long getUserId() { return userId; }
	public int getActiveUnhandledCount() { return activeUnhandledCount; }
	public int getRecentReceivedCount() { return recentReceivedCount; }
	public Instant getRecentWindowStartedAt() { return recentWindowStartedAt; }
	public Instant getLastReceivedAt() { return lastReceivedAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
