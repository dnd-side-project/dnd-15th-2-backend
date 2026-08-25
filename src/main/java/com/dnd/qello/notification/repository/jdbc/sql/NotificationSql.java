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

	/**
	 * 전역 숨김된 답변을 가리키던 알림의 미발송 push 전달만 취소한다. SENT·PROCESSING·DEAD는
	 * 이미 끝났거나 진행 중이라 건드리지 않는다 — 발송 worker가 아직 없어 지금은 죽은 코드지만,
	 * worker가 붙었을 때 숨긴 콘텐츠를 가리키는 push가 나가지 않도록 미리 막아 둔다.
	 */
	public static final String CANCEL_DELIVERIES_BY_ANSWER_ID = """
		UPDATE notification_delivery
		SET status = 'CANCELLED'
		WHERE status IN ('PENDING', 'FAILED')
		  AND notification_id IN (SELECT id FROM notification WHERE answer_id = :answerId)
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

	/**
	 * attempt_count를 lease generation으로 사용한다. due 후보를 먼저 잠근 뒤 같은 문장에서
	 * 상태·시도 횟수·lease 시각을 갱신하여 두 worker가 같은 행을 claim하지 않게 한다.
	 * data-modifying CTE의 RETURNING 순서는 보장되지 않으므로 due의 정렬 키를 보존하고
	 * 최종 SELECT에서 다시 정렬한다.
	 */
	public static final String CLAIM_DUE_PUSH_DELIVERIES = """
		WITH due AS MATERIALIZED (
			SELECT id, next_attempt_at AS due_next_attempt_at
			FROM notification_delivery
			WHERE status IN ('PENDING', 'FAILED', 'PROCESSING')
			  AND next_attempt_at <= :now
			ORDER BY next_attempt_at, id
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
		), claimed AS (
			UPDATE notification_delivery AS nd
		SET status = 'PROCESSING',
			attempt_count = nd.attempt_count + 1,
			next_attempt_at = :leaseUntil,
			sent_at = NULL,
			provider_message_id = NULL
		FROM due
		WHERE nd.id = due.id
		RETURNING nd.id AS delivery_id, nd.attempt_count AS generation,
			nd.next_attempt_at AS lease_until
		)
		SELECT claimed.delivery_id, claimed.generation, claimed.lease_until
		FROM claimed
		JOIN due ON due.id = claimed.delivery_id
		ORDER BY due.due_next_attempt_at, due.id
		""";

	/**
	 * provider 호출 직전 권위값을 한 read snapshot으로 조립한다. 대상 콘텐츠의 본문·좌표와
	 * token 원문은 선택하지 않고, eligibility에 필요한 boolean과 기존 domain 필드만 반환한다.
	 */
	public static final String FIND_PUSH_DISPATCH_CONTEXT = """
		SELECT
			nd.id AS delivery_id,
			nd.notification_id AS delivery_notification_id,
			nd.push_device_id AS delivery_push_device_id,
			nd.status AS delivery_status,
			nd.attempt_count AS delivery_attempt_count,
			nd.next_attempt_at AS delivery_next_attempt_at,
			nd.created_at AS delivery_created_at,
			nd.sent_at AS delivery_sent_at,
			nd.provider_message_id AS delivery_provider_message_id,
			n.id AS notification_id,
			n.recipient_id AS notification_recipient_id,
			n.outbox_event_id AS notification_outbox_event_id,
			n.notification_type,
			n.dedup_key AS notification_dedup_key,
			n.direction_post_id AS notification_direction_post_id,
			n.answer_id AS notification_answer_id,
			n.report_id AS notification_report_id,
			n.status AS notification_status,
			n.created_at AS notification_created_at,
			n.read_at AS notification_read_at,
			pd.id AS device_id,
			pd.user_id AS device_user_id,
			pd.platform AS device_platform,
			pd.token_ciphertext AS device_token_ciphertext,
			pd.token_fingerprint AS device_token_fingerprint,
			pd.device_status,
			pd.last_seen_at AS device_last_seen_at,
			pd.revoked_at AS device_revoked_at,
			actor_ref.actor_id,
			(recipient.status = 'ACTIVE' AND recipient.deleted_at IS NULL) AS recipient_active,
			(actor_ref.actor_id IS NULL OR (actor.status = 'ACTIVE' AND actor.deleted_at IS NULL)) AS actor_active,
			COALESCE(nus.push_enabled, TRUE)
				AND COALESCE(np.enabled, TRUE) AS preference_enabled,
			(actor_ref.actor_id IS NOT NULL AND EXISTS (
				SELECT 1
				FROM user_block ub
				WHERE ub.released_at IS NULL
				  AND ((ub.blocker_id = n.recipient_id AND ub.blocked_id = actor_ref.actor_id)
					OR (ub.blocker_id = actor_ref.actor_id AND ub.blocked_id = n.recipient_id))
			)) AS blocked_in_either_direction,
			CASE
				WHEN n.direction_post_id IS NULL AND n.answer_id IS NULL AND n.report_id IS NULL THEN TRUE
				WHEN n.direction_post_id IS NOT NULL
					THEN dp.id IS NOT NULL AND dp.status = 'ACTIVE'
						AND dp.deleted_at IS NULL AND dp.expires_at > :at
				WHEN n.answer_id IS NOT NULL
					THEN a.id IS NOT NULL AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL
				WHEN n.report_id IS NOT NULL
					THEN r.id IS NOT NULL AND r.resolved_at IS NOT NULL
						AND r.status IN ('ACTIONED', 'NO_VIOLATION', 'MORE_INFO_REQUIRED')
				ELSE FALSE
			END AS target_valid,
			(n.notification_type = 'DIRECTION_POST_RECEIVED'
				AND dp.id IS NOT NULL AND dp.status = 'ACTIVE'
				AND dp.deleted_at IS NULL AND dp.expires_at > :at) AS has_remaining_time
		FROM notification_delivery nd
		JOIN notification n ON n.id = nd.notification_id
		JOIN push_device pd ON pd.id = nd.push_device_id
		JOIN user_account recipient ON recipient.id = n.recipient_id
		LEFT JOIN direction_post dp ON dp.id = n.direction_post_id
		LEFT JOIN answer a ON a.id = n.answer_id
		LEFT JOIN report r ON r.id = n.report_id
		LEFT JOIN LATERAL (
			SELECT CASE
				WHEN n.direction_post_id IS NOT NULL THEN dp.sender_id
				WHEN n.answer_id IS NOT NULL THEN a.author_id
				ELSE NULL
			END AS actor_id
		) actor_ref ON TRUE
		LEFT JOIN user_account actor ON actor.id = actor_ref.actor_id
		LEFT JOIN notification_user_setting nus ON nus.user_id = n.recipient_id
		LEFT JOIN notification_preference np
			ON np.user_id = n.recipient_id AND np.notification_type = n.notification_type
		WHERE nd.id = :deliveryId
		  AND nd.status = 'PROCESSING'
		  AND nd.attempt_count = :generation
		  AND nd.next_attempt_at > :now
		""";

	/** generation fence를 통과한 결과만 반영하며 provider payload나 token은 이 경계에 들어오지 않는다. */
	public static final String COMPLETE_PUSH_DELIVERY = """
		UPDATE notification_delivery
		SET status = CASE :terminalStatus
			WHEN 'SENT' THEN 'SENT'
			WHEN 'FAILED' THEN 'FAILED'
			WHEN 'DEAD' THEN 'DEAD'
			WHEN 'CANCELLED' THEN 'CANCELLED'
			END,
			next_attempt_at = CASE WHEN :terminalStatus = 'FAILED' THEN CAST(:nextAttemptAt AS TIMESTAMPTZ)
				ELSE CAST(:at AS TIMESTAMPTZ) END,
			sent_at = CASE WHEN :terminalStatus = 'SENT' THEN CAST(:at AS TIMESTAMPTZ)
				ELSE CAST(NULL AS TIMESTAMPTZ) END,
			provider_message_id = CASE WHEN :terminalStatus = 'SENT' THEN :providerMessageId ELSE NULL END
		WHERE id = :deliveryId
		  AND status = 'PROCESSING'
		  AND attempt_count = :generation
		""";

	/**
	 * 현재 claim generation을 DEAD로 종결한 뒤 device INVALID와 sibling 취소를 원자적으로 수행한다.
	 * 사용자가 발송 도중 해지해 device가 이미 REVOKED면 device 상태는 사용자 의도대로 두고
	 * 현재 claim만 종결한다. 종결을 device 갱신 결과에 묶으면 PROCESSING 행이 lease 만료까지 남는다.
	 */
	public static final String INVALIDATE_PUSH_DEVICE = """
		WITH matched AS MATERIALIZED (
			SELECT nd.id, nd.push_device_id
			FROM notification_delivery nd
			WHERE nd.id = :deliveryId
			  AND nd.push_device_id = :pushDeviceId
			  AND nd.status = 'PROCESSING'
			  AND nd.attempt_count = :generation
			FOR UPDATE
		), invalidated AS (
			UPDATE push_device pd
			SET device_status = 'INVALID', revoked_at = NULL
			FROM matched
			WHERE pd.id = matched.push_device_id
			  AND pd.device_status IN ('ACTIVE', 'INVALID')
			RETURNING pd.id
		), terminal AS (
			UPDATE notification_delivery nd
			SET status = 'DEAD', next_attempt_at = :at,
				sent_at = NULL, provider_message_id = NULL
			FROM matched
			WHERE nd.id = matched.id
			  AND nd.status = 'PROCESSING'
			  AND nd.attempt_count = :generation
			RETURNING nd.push_device_id
		), cancelled AS (
			UPDATE notification_delivery nd
			SET status = 'CANCELLED', next_attempt_at = :at,
				sent_at = NULL, provider_message_id = NULL
			FROM terminal
			WHERE nd.push_device_id = terminal.push_device_id
			  AND nd.status IN ('PENDING', 'FAILED')
			RETURNING nd.id
		)
		SELECT count(*) FROM terminal
		""";

	/** 기존 회귀 테스트용 단건 경로도 attempt_count를 올려 새 claim과 동일한 monotonic fence를 유지한다. */
	public static final String CLAIM_SINGLE_LEGACY_PUSH_DELIVERY = """
		UPDATE notification_delivery
		SET status = 'PROCESSING', attempt_count = attempt_count + 1, next_attempt_at = :at,
			sent_at = NULL, provider_message_id = NULL
		WHERE id = :id
		  AND status IN ('PENDING', 'FAILED')
		  AND next_attempt_at <= :at
		RETURNING *
		""";

	/** 기존 단건 API의 종결도 id와 직전 attempt_count를 함께 확인하는 호환 fence를 사용한다. */
	public static final String COMPLETE_LEGACY_PUSH_DELIVERY = """
		UPDATE notification_delivery
		SET status = :status, attempt_count = :attemptCount,
			next_attempt_at = :nextAttemptAt, sent_at = :sentAt,
			provider_message_id = :providerMessageId
		WHERE id = :id
		  AND status = 'PROCESSING'
		  AND attempt_count = :attemptCount - 1
		""";

	public static final String INSERT_PUSH_DEVICE = """
		INSERT INTO push_device
			(user_id, platform, token_ciphertext, token_fingerprint, device_status, last_seen_at, revoked_at)
		VALUES (:userId, :platform, :tokenCiphertext, :tokenFingerprint, :status, :lastSeenAt, :revokedAt)
		RETURNING id, user_id, platform, token_ciphertext, token_fingerprint, device_status, last_seen_at, revoked_at
		""";

	public static final String ACQUIRE_TRANSACTION_ADVISORY_LOCK = """
		SELECT pg_advisory_xact_lock(:lockKey)
		""";

	/**
	 * lock 획득 뒤 새 statement snapshot에서 같은 fingerprint의 revoke, delivery cancel,
	 * ACTIVE upsert를 한 SQL 문장 안에서 끝낸다. 같은 user/platform의 token 갱신은 기존
	 * push_device를 갱신해 미발송 delivery를 유지하고, 다른 소유자의 같은 token만 revoke한다.
	 */
	public static final String REGISTER_OR_TRANSFER_PUSH_DEVICE = """
		WITH current_active AS MATERIALIZED (
			SELECT pd.*
			FROM push_device pd
			WHERE pd.token_fingerprint = :tokenFingerprint
			  AND pd.device_status = 'ACTIVE'
			FOR UPDATE
		),
		owner_platform_active AS MATERIALIZED (
			SELECT pd.*
			FROM push_device pd
			WHERE pd.user_id = :userId
			  AND pd.platform = :platform
			  AND pd.device_status = 'ACTIVE'
			FOR UPDATE
		),
		revoked AS (
			UPDATE push_device
			SET device_status = 'REVOKED', revoked_at = :lastSeenAt
			WHERE id IN (
				SELECT id FROM current_active WHERE user_id <> :userId
			)
			RETURNING id
		),
		cancelled AS (
			UPDATE notification_delivery AS nd
			SET status = 'CANCELLED'
			FROM revoked
			WHERE nd.push_device_id = revoked.id
			  AND nd.status IN ('PENDING', 'FAILED')
			RETURNING nd.id
		),
		revocation_complete AS (
			SELECT count(*) AS revoked_count
			FROM revoked
		),
		refreshed AS (
			UPDATE push_device
			SET platform = :platform,
				token_ciphertext = :tokenCiphertext,
				token_fingerprint = :tokenFingerprint,
				device_status = 'ACTIVE',
				last_seen_at = :lastSeenAt,
				revoked_at = NULL
			FROM revocation_complete
			WHERE id IN (
				SELECT id FROM current_active WHERE user_id = :userId
				UNION
				SELECT id FROM owner_platform_active
			)
			RETURNING push_device.*
		),
		inserted AS (
			INSERT INTO push_device
				(user_id, platform, token_ciphertext, token_fingerprint, device_status, last_seen_at, revoked_at)
			SELECT :userId, :platform, :tokenCiphertext, :tokenFingerprint, 'ACTIVE', :lastSeenAt, NULL
			FROM revocation_complete
			WHERE NOT EXISTS (SELECT 1 FROM refreshed)
			RETURNING id, user_id, platform, token_ciphertext, token_fingerprint, device_status, last_seen_at, revoked_at
		)
		SELECT id, user_id, platform, token_ciphertext, token_fingerprint, device_status, last_seen_at, revoked_at
		FROM refreshed
		UNION ALL
		SELECT id, user_id, platform, token_ciphertext, token_fingerprint, device_status, last_seen_at, revoked_at
		FROM inserted
		""";

	public static final String REVOKE_OWNED_PUSH_DEVICE = """
		WITH current_active AS MATERIALIZED (
			SELECT pd.id
			FROM push_device pd
			WHERE pd.token_fingerprint = :tokenFingerprint
			  AND pd.device_status = 'ACTIVE'
			  AND pd.user_id = :userId
			  AND pd.platform = :platform
			FOR UPDATE
		),
		revoked AS (
			UPDATE push_device
			SET device_status = 'REVOKED', revoked_at = :revokedAt
			WHERE id IN (SELECT id FROM current_active)
			RETURNING id
		),
		cancelled AS (
			UPDATE notification_delivery AS nd
			SET status = 'CANCELLED'
			FROM revoked
			WHERE nd.push_device_id = revoked.id
			  AND nd.status IN ('PENDING', 'FAILED')
			RETURNING nd.id
		)
		SELECT count(*) FROM revoked
		""";

	public static final String FIND_ACTIVE_PUSH_DEVICE_IDS = """
		SELECT id
		FROM push_device
		WHERE user_id = :userId AND device_status = 'ACTIVE'
		ORDER BY id
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
