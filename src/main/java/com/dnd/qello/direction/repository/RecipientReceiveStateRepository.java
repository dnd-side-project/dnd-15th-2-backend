package com.dnd.qello.direction.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.direction.domain.RecipientReceiveState;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

public interface RecipientReceiveStateRepository {
	RecipientReceiveState save(RecipientReceiveState state);
	Optional<RecipientReceiveState> findByUserId(long userId);
	boolean reserve(long userId, Instant receivedAt, int activeLimit);
	boolean release(long userId, Instant releasedAt);

	record LockCandidate(long userId, BigDecimal distanceMeters) {
		public LockCandidate {
			if (userId <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다");
			}
			if (distanceMeters == null) {
				throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "distanceMeters",
					"distanceMeters는 필수입니다");
			}
			if (distanceMeters.signum() < 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "distanceMeters",
					"distanceMeters는 음수일 수 없습니다");
			}
		}
	}

	/** 매칭 후보 중 상태 행이 없는 사용자를 제한적으로 멱등 초기화한다. */
	default void ensureForUsers(List<Long> userIds, Instant at) {
		if (userIds == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "userIds", "userIds는 필수입니다");
		}
		if (at == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "at", "at은 필수입니다");
		}
		for (Long userId : userIds) {
			if (userId == null || userId <= 0) {
				throw new DirectionException(DirectionErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다");
			}
		}
	}

	/** 후보 상태 행을 잠그고 현재 수신 상한 미만인 사용자만 반환한다. */
	default List<Long> lockAvailableUserIds(List<LockCandidate> candidates, int limit, int activeLimit) {
		return List.of();
	}
}
