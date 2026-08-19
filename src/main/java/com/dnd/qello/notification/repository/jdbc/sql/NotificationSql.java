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
		RETURNING *
		""";

	public static final String CLAIM_DUE_OUTBOX_EVENTS = """
		WITH due AS MATERIALIZED (
			SELECT id
			FROM outbox_event
			WHERE (
				(status IN ('PENDING', 'FAILED') AND next_attempt_at <= :at)
				OR (status = 'PROCESSING' AND lease_expires_at <= :at)
			)
			  AND event_type IN (:eventTypes)
			ORDER BY next_attempt_at, id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
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
			 direction_post_id, answer_id, report_id, status, created_at, read_at)
		VALUES (:recipientId, :outboxEventId, :notificationType, :dedupKey,
			:directionPostId, :answerId, :reportId, :status, :createdAt, :readAt)
		RETURNING id
		""";

	/**
	 * 동일 수신자와 dedup key의 기존 알림은 내용을 덮어쓰지 않고 그대로 반환한다.
	 * no-op UPDATE는 동시 insert 경합에서도 하나의 행을 원자적으로 반환하기 위해 사용한다.
	 */
	public static final String INSERT_NOTIFICATION_IF_ABSENT = """
		INSERT INTO notification
			(recipient_id, outbox_event_id, notification_type, dedup_key,
			 direction_post_id, answer_id, report_id, status, created_at, read_at)
		VALUES (:recipientId, :outboxEventId, :notificationType, :dedupKey,
			:directionPostId, :answerId, :reportId, :status, :createdAt, :readAt)
		ON CONFLICT (recipient_id, dedup_key) DO UPDATE
		SET dedup_key = notification.dedup_key
		RETURNING notification.*
		""";

	/** ck_notification_read_at이 REVOKED 상태에 read_at을 허용하지 않으므로 함께 비운다. */
	public static final String REVOKE_NOTIFICATIONS_BY_ANSWER_ID = """
		UPDATE notification
		SET status = 'REVOKED', read_at = NULL
		WHERE answer_id = :answerId AND status <> 'REVOKED'
		""";

	public static final String INSERT_NOTIFICATION_DELIVERY = """
		INSERT INTO notification_delivery
			(notification_id, push_device_id, status, attempt_count, next_attempt_at,
			 created_at, sent_at, provider_message_id)
		VALUES (:notificationId, :pushDeviceId, :status, :attemptCount, :nextAttemptAt,
			:createdAt, :sentAt, :providerMessageId)
		RETURNING id
		""";

	/** 기존 알림/기기 전달 행을 보존하면서 동시 재처리에 같은 행을 반환한다. */
	public static final String INSERT_NOTIFICATION_DELIVERY_IF_ABSENT = """
		INSERT INTO notification_delivery
			(notification_id, push_device_id, status, attempt_count, next_attempt_at,
			 created_at, sent_at, provider_message_id)
		VALUES (:notificationId, :pushDeviceId, :status, :attemptCount, :nextAttemptAt,
			:createdAt, :sentAt, :providerMessageId)
		ON CONFLICT (notification_id, push_device_id) DO UPDATE
		SET push_device_id = notification_delivery.push_device_id
		RETURNING notification_delivery.*
		""";

	public static final String FIND_ACTIVE_PUSH_DEVICE_IDS = """
		SELECT id
		FROM push_device
		WHERE user_id = :userId AND device_status = 'ACTIVE'
		ORDER BY id
		""";

	public static final String FIND_NOTIFICATION_PREFERENCE_ENABLED = """
		SELECT COALESCE((
			SELECT enabled
			FROM notification_preference
			WHERE notification_type = :notificationType AND user_id = :userId
		), TRUE)
		""";

	public static final String UPSERT_NOTIFICATION_PREFERENCE = """
		INSERT INTO notification_preference
			(notification_type, user_id, enabled, quiet_start, quiet_end)
		VALUES (:notificationType, :userId, :enabled, :quietStart, :quietEnd)
		ON CONFLICT (notification_type, user_id) DO UPDATE SET
			enabled = EXCLUDED.enabled, quiet_start = EXCLUDED.quiet_start,
			quiet_end = EXCLUDED.quiet_end, updated_at = clock_timestamp()
		""";

	public static final String INSERT_NOTIFICATION_EVENT = """
		INSERT INTO notification_event
			(case_id, admin_link_path, status, attempt_count, next_attempt_at, created_at,
			 processed_at, lease_owner, lease_expires_at, lease_generation)
		VALUES (:caseId, :adminLinkPath, :status, :attemptCount, :nextAttemptAt, :createdAt,
			:processedAt, :leaseOwner, :leaseExpiresAt, :leaseGeneration)
		RETURNING id
		""";

	public static final String CLAIM_DUE_NOTIFICATION_EVENTS = """
		WITH due AS MATERIALIZED (
			SELECT id
			FROM notification_event
			WHERE (
				(status IN ('PENDING', 'FAILED') AND next_attempt_at <= :at)
				OR (status = 'PROCESSING' AND lease_expires_at <= :at)
			)
			ORDER BY next_attempt_at, id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
		)
		UPDATE notification_event AS ne
		SET status = 'PROCESSING', attempt_count = ne.attempt_count + 1, next_attempt_at = :at,
			lease_owner = :leaseOwner, lease_expires_at = :leaseExpiresAt,
			lease_generation = ne.lease_generation + 1
		FROM due
		WHERE ne.id = due.id
		RETURNING ne.*
		""";

	public static final String COMPLETE_NOTIFICATION_EVENT = """
		UPDATE notification_event
		SET status = 'PROCESSED', processed_at = :processedAt,
			lease_owner = NULL, lease_expires_at = NULL
		WHERE id = :id
		  AND status = 'PROCESSING'
		  AND lease_owner = :leaseOwner
		  AND lease_generation = :leaseGeneration
		  AND lease_expires_at > :processedAt
		""";

	public static final String FAIL_NOTIFICATION_EVENT = """
		UPDATE notification_event
		SET status = :nextStatus, next_attempt_at = :nextAttemptAt, processed_at = NULL,
			lease_owner = NULL, lease_expires_at = NULL
		WHERE id = :id
		  AND status = 'PROCESSING'
		  AND lease_owner = :leaseOwner
		  AND lease_generation = :leaseGeneration
		  AND lease_expires_at > :at
		""";
}
