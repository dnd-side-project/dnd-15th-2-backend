package com.dnd.qello.direction.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.direction.domain.RecipientReceiveState;

public interface RecipientReceiveStateRepository {
	RecipientReceiveState save(RecipientReceiveState state);
	Optional<RecipientReceiveState> findByUserId(long userId);
	boolean reserve(long userId, Instant receivedAt, int activeLimit);
	boolean release(long userId, Instant releasedAt);

	/** 매칭 후보 중 상태 행이 없는 사용자를 제한적으로 멱등 초기화한다. */
	default void ensureForUsers(List<Long> userIds, Instant at) {
		if (userIds == null || at == null) throw new IllegalArgumentException("userIds and at are required");
	}

	/** 후보 상태 행을 잠그고 현재 수신 상한 미만인 사용자만 반환한다. */
	default List<Long> lockAvailableUserIds(List<Long> userIds, int limit, int activeLimit) {
		return List.of();
	}
}
