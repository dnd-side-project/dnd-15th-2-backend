package com.dnd.qello.safety.domain;

import java.time.Instant;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

public record UserBlock(long blockerId, long blockedId, Instant createdAt, Instant releasedAt) {

	public UserBlock {
		requirePositive(blockerId, "blockerId");
		requirePositive(blockedId, "blockedId");
		if (blockerId == blockedId) {
			throw new SafetyException(
				SafetyErrorCode.SELF_BLOCK_NOT_ALLOWED, "blockedId", "자기 자신을 차단할 수 없습니다");
		}
		requireValue(createdAt, "createdAt");
		if (releasedAt != null && releasedAt.isBefore(createdAt)) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_TIME_ORDER, "releasedAt", "releasedAt은 createdAt보다 빠를 수 없습니다");
		}
	}

	public static UserBlock create(long blockerId, long blockedId, Instant at) {
		return new UserBlock(blockerId, blockedId, at, null);
	}

	public UserBlock release(Instant at) {
		return new UserBlock(blockerId, blockedId, createdAt, requireValue(at, "releasedAt"));
	}

	public boolean active() { return releasedAt == null; }

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static void requirePositive(long value, String field) {
		if (value <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
	}
}
