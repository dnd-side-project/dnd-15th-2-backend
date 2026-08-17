package com.dnd.qello.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.notification.domain.NotificationEvent;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

public interface NotificationEventRepository {

	NotificationEvent save(NotificationEvent event);

	Optional<NotificationEvent> findByCaseId(long caseId);

	List<NotificationEvent> claimDue(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt);

	boolean complete(long id, String leaseOwner, long leaseGeneration, Instant processedAt);

	boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at, Instant nextAttemptAt, boolean dead);

	default boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at, OutboxRetryDecision decision) {
		if (decision == null) {
			throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "decision",
				"retry decision은 필수입니다.");
		}
		return fail(id, leaseOwner, leaseGeneration, at, decision.nextAttemptAt(), decision.dead());
	}
}
