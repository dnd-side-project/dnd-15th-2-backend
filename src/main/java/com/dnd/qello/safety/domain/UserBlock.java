package com.dnd.qello.safety.domain;

import java.time.Instant;
import java.util.Objects;

public record UserBlock(long blockerId, long blockedId, Instant createdAt, Instant releasedAt) {

	public UserBlock {
		requirePositive(blockerId, "blockerId");
		requirePositive(blockedId, "blockedId");
		if (blockerId == blockedId) throw new IllegalArgumentException("자기 자신을 차단할 수 없습니다");
		Objects.requireNonNull(createdAt, "createdAt은 필수입니다");
		if (releasedAt != null && releasedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("releasedAt은 createdAt보다 빠를 수 없습니다");
		}
	}

	public static UserBlock create(long blockerId, long blockedId, Instant at) {
		return new UserBlock(blockerId, blockedId, at, null);
	}

	public UserBlock release(Instant at) {
		return new UserBlock(blockerId, blockedId, createdAt, Objects.requireNonNull(at, "releasedAt은 필수입니다"));
	}

	public boolean active() { return releasedAt == null; }

	private static void requirePositive(long value, String field) {
		if (value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
	}
}
