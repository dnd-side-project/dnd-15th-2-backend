package com.dnd.qello.notification.repository.jdbc.sql;

/**
 * JdbcNotificationRepository가 쓰는 긴 SQL 상수.
 */
public final class NotificationSql {

	private NotificationSql() {
	}

	public static final String INSERT_OUTBOX_EVENT = """
		INSERT INTO outbox_event
			(aggregate_type, aggregate_id, event_type, dedup_key, payload, status,
			 attempt_count, next_attempt_at, created_at, processed_at, match_round,
			 lease_owner, lease_expires_at, lease_generation)
		VALUES (:aggregateType, :aggregateId, :eventType, :dedupKey, CAST(:payload AS jsonb),
			:status, :attemptCount, :nextAttemptAt, :createdAt, :processedAt, :matchRound,
			:leaseOwner, :leaseExpiresAt, :leaseGeneration)
		RETURNING id
		""";

	public static final String CLAIM_OUTBOX_EVENT = """
		UPDATE outbox_event
		SET status = 'PROCESSING', attempt_count = attempt_count + 1, next_attempt_at = :at,
			lease_owner = :leaseOwner, lease_expires_at = :leaseExpiresAt,
			lease_generation = lease_generation + 1
		WHERE id = :id
		  AND (
			(status IN ('PENDING', 'FAILED') AND next_attempt_at <= :at)
			OR (status = 'PROCESSING' AND lease_expires_at <= :at)
		  )
		""";

	public static final String CLAIM_DUE_OUTBOX_EVENTS = """
		WITH due AS (
			SELECT id
			FROM outbox_event
			WHERE (
				(status IN ('PENDING', 'FAILED') AND next_attempt_at <= :at)
				OR (status = 'PROCESSING' AND lease_expires_at <= :at)
			)
			ORDER BY next_attempt_at, id
			FOR UPDATE SKIP LOCKED
			LIMIT :limit
		)
		UPDATE outbox_event AS oe
		SET status = 'PROCESSING', attempt_count = oe.attempt_count + 1, next_attempt_at = :at,
			lease_owner = :leaseOwner, lease_expires_at = :leaseExpiresAt,
			lease_generation = oe.lease_generation + 1
		FROM due
		WHERE oe.id = due.id
		RETURNING oe.*
		""";

	public static final String COMPLETE_OUTBOX_EVENT = """
		UPDATE outbox_event
		SET status = 'PROCESSED', processed_at = :processedAt,
			lease_owner = NULL, lease_expires_at = NULL
		WHERE id = :id
		  AND status = 'PROCESSING'
		  AND lease_owner = :leaseOwner
		  AND lease_generation = :leaseGeneration
		  AND lease_expires_at > :processedAt
		""";

	public static final String FAIL_OUTBOX_EVENT = """
		UPDATE outbox_event
		SET status = :nextStatus, next_attempt_at = :nextAttemptAt, processed_at = NULL,
			lease_owner = NULL, lease_expires_at = NULL
		WHERE id = :id
		  AND status = 'PROCESSING'
		  AND lease_owner = :leaseOwner
		  AND lease_generation = :leaseGeneration
		  AND lease_expires_at > :at
		""";

	public static final String UPDATE_PROCESSING_OUTBOX_EVENT = """
		UPDATE outbox_event
		SET attempt_count = :attemptCount, next_attempt_at = :nextAttemptAt
		WHERE id = :id AND status = 'PROCESSING'
		  AND lease_owner = :leaseOwner AND lease_generation = :leaseGeneration
		  AND lease_expires_at > :at
		""";

	public static final String INSERT_NOTIFICATION = """
		INSERT INTO notification
			(recipient_id, outbox_event_id, notification_type, dedup_key,
			 direction_post_id, answer_id, status, created_at, read_at)
		VALUES (:recipientId, :outboxEventId, :notificationType, :dedupKey,
			:directionPostId, :answerId, :status, :createdAt, :readAt)
		RETURNING id
		""";

	public static final String INSERT_NOTIFICATION_DELIVERY = """
		INSERT INTO notification_delivery
			(notification_id, push_device_id, status, attempt_count, next_attempt_at,
			 created_at, sent_at, provider_message_id)
		VALUES (:notificationId, :pushDeviceId, :status, :attemptCount, :nextAttemptAt,
			:createdAt, :sentAt, :providerMessageId)
		RETURNING id
		""";

	public static final String UPSERT_NOTIFICATION_PREFERENCE = """
		INSERT INTO notification_preference
			(notification_type, user_id, enabled, quiet_start, quiet_end)
		VALUES (:notificationType, :userId, :enabled, :quietStart, :quietEnd)
		ON CONFLICT (notification_type, user_id) DO UPDATE SET
			enabled = EXCLUDED.enabled, quiet_start = EXCLUDED.quiet_start,
			quiet_end = EXCLUDED.quiet_end, updated_at = clock_timestamp()
		""";
}
