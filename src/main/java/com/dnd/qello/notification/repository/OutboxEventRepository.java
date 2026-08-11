package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.notification.domain.OutboxEvent;

public interface OutboxEventRepository {

	OutboxEvent save(OutboxEvent event);

	Optional<OutboxEvent> findEventById(long id);

	Optional<OutboxEvent> findByDedupKey(String dedupKey);

	Optional<OutboxEvent> claim(long id, Instant at);

	Optional<OutboxEvent> claim(long id, String leaseOwner, Instant at, Instant leaseExpiresAt);

	List<OutboxEvent> claimDue(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt);

	boolean complete(long id, String leaseOwner, long leaseGeneration, Instant processedAt);

	boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at,
		Instant nextAttemptAt, boolean dead);

	default boolean markProcessed(long id, String leaseOwner, long leaseGeneration, Instant processedAt) {
		return complete(id, leaseOwner, leaseGeneration, processedAt);
	}

	default boolean markFailed(long id, String leaseOwner, long leaseGeneration, Instant at,
		Instant nextAttemptAt, boolean dead) {
		return fail(id, leaseOwner, leaseGeneration, at, nextAttemptAt, dead);
	}

	boolean update(OutboxEvent event);
}
