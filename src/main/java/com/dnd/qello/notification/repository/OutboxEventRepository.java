package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public interface OutboxEventRepository {

	OutboxEvent save(OutboxEvent event);

	Optional<OutboxEvent> findEventById(long id);

	Optional<OutboxEvent> findByDedupKey(String dedupKey);

	Optional<OutboxEvent> claim(long id, Instant at);

	Optional<OutboxEvent> claim(long id, String leaseOwner, Instant at, Instant leaseExpiresAt);

	List<OutboxEvent> claimDue(Set<OutboxEventType> eventTypes, int limit, String leaseOwner,
		Instant at, Instant leaseExpiresAt);

	boolean complete(long id, String leaseOwner, long leaseGeneration, Instant processedAt);

	boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at,
		Instant nextAttemptAt, boolean dead);

	default boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at,
		OutboxRetryDecision decision) {
		if (decision == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "decision",
				"retry decision은 필수입니다.");
		}
		return fail(id, leaseOwner, leaseGeneration, at, decision.nextAttemptAt(), decision.dead());
	}

	default boolean markProcessed(long id, String leaseOwner, long leaseGeneration, Instant processedAt) {
		return complete(id, leaseOwner, leaseGeneration, processedAt);
	}

	default boolean markFailed(long id, String leaseOwner, long leaseGeneration, Instant at,
		Instant nextAttemptAt, boolean dead) {
		return fail(id, leaseOwner, leaseGeneration, at, nextAttemptAt, dead);
	}

	boolean update(OutboxEvent event);
}
