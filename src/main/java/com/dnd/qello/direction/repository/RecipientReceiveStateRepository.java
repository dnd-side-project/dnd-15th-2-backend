package com.dnd.qello.direction.repository;

import java.time.Instant;
import java.util.Optional;

import com.dnd.qello.direction.domain.RecipientReceiveState;

public interface RecipientReceiveStateRepository {
	RecipientReceiveState save(RecipientReceiveState state);
	Optional<RecipientReceiveState> findByUserId(long userId);
	boolean reserve(long userId, Instant receivedAt);
	boolean release(long userId, Instant releasedAt);
}
