package com.dnd.qello.notification.repository.jdbc.sql;

/**
 * JdbcPushDispatchGroupRepository가 쓰는 긴 SQL 상수.
 */
public final class PushDispatchGroupSql {

	private PushDispatchGroupSql() {
	}

	/**
	 * member가 없고 미발송 또는 lease 만료 delivery가 있는 notification만 잠근다.
	 * recommendation cycle ID는 outbox aggregate → question_assignment.cycle_id에서 읽는다.
	 */
	public static final String LOCK_UNGROUPED = """
		WITH locked AS MATERIALIZED (
			SELECT n.id
			FROM notification n
			WHERE EXISTS (
				SELECT 1
				FROM notification_delivery nd
				WHERE nd.notification_id = n.id
				  AND (
					nd.status IN ('PENDING', 'FAILED')
					OR (nd.status = 'PROCESSING' AND nd.next_attempt_at <= :at)
				  )
			)
			  AND NOT EXISTS (
				SELECT 1
				FROM push_dispatch_group_member m
				WHERE m.notification_id = n.id
			)
			ORDER BY n.created_at, n.id
			LIMIT :limit
			FOR UPDATE OF n SKIP LOCKED
		)
		SELECT
			n.id AS notification_id,
			n.recipient_id,
			n.notification_type,
			n.created_at,
			qa.cycle_id AS recommendation_cycle_id
		FROM locked
		JOIN notification n ON n.id = locked.id
		LEFT JOIN outbox_event oe ON oe.id = n.outbox_event_id
		LEFT JOIN question_assignment qa
			ON qa.id = oe.aggregate_id
			AND n.notification_type = 'QUESTION_RECOMMENDED'
		ORDER BY n.created_at, n.id
		""";

	/** 계획서(2026-08-25 push-bundling-budget)가 지정한 Postgres 측 hash 기반 grouping lock. */
	public static final String ACQUIRE_GROUPING_LOCK = """
		SELECT pg_advisory_xact_lock(hashtextextended(:groupingKey, 0))
		""";

	public static final String CLOSE_EXPIRED_COLLECTING = """
		UPDATE push_dispatch_group
		SET status = 'PENDING',
			next_attempt_at = collect_until
		WHERE recipient_id = :recipientId
		  AND notification_type = :notificationType
		  AND status = 'COLLECTING'
		  AND collect_until < :at
		""";

	public static final String FIND_COLLECTING_FOR_UPDATE = """
		SELECT *
		FROM push_dispatch_group
		WHERE recipient_id = :recipientId
		  AND notification_type = :notificationType
		  AND status = 'COLLECTING'
		  AND collect_until >= :at
		FOR UPDATE
		""";

	private static final String INSERT_GROUP_BODY = """
		INSERT INTO push_dispatch_group (
			recipient_id, notification_type, aggregation_key, status,
			window_started_at, collect_until, policy_expires_at, attempt_count,
			next_attempt_at, budget_local_date, budget_consumed_at, first_attempted_at,
			created_at, completed_at)
		VALUES (
			:recipientId, :notificationType, :aggregationKey, :status,
			:windowStartedAt, :collectUntil, :policyExpiresAt, :attemptCount,
			:nextAttemptAt, :budgetLocalDate, :budgetConsumedAt, :firstAttemptedAt,
			:createdAt, :completedAt)
		""";

	public static final String INSERT_GROUP = INSERT_GROUP_BODY + "RETURNING *\n";

	public static final String UPSERT_GROUP = INSERT_GROUP_BODY + """
		ON CONFLICT (aggregation_key) DO UPDATE
		SET aggregation_key = push_dispatch_group.aggregation_key
		RETURNING *
		""";

	public static final String INSERT_MEMBER = """
		INSERT INTO push_dispatch_group_member (group_id, notification_id, created_at)
		VALUES (:groupId, :notificationId, :at)
		ON CONFLICT (notification_id) DO NOTHING
		""";

	/**
	 * due 후보를 잠근 뒤 같은 문장에서 PROCESSING·generation·lease를 반영한다.
	 * COLLECTING은 collect_until, 그 외는 next_attempt_at으로 due를 판정한다.
	 */
	public static final String CLAIM_DUE_GROUPS = """
		WITH due AS MATERIALIZED (
			SELECT id,
				CASE WHEN status = 'COLLECTING' THEN collect_until ELSE next_attempt_at END AS due_at
			FROM push_dispatch_group
			WHERE status IN ('COLLECTING', 'PENDING', 'FAILED', 'PROCESSING')
			  AND next_attempt_at <= :now
			  AND (
				status IN ('PENDING', 'FAILED', 'PROCESSING')
				OR collect_until <= :now
			  )
			ORDER BY due_at, id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
		), claimed AS (
			UPDATE push_dispatch_group AS g
			SET status = 'PROCESSING',
				attempt_count = g.attempt_count + 1,
				next_attempt_at = :leaseUntil
			FROM due
			WHERE g.id = due.id
			RETURNING g.id AS group_id, g.attempt_count AS generation,
				g.next_attempt_at AS lease_until
		)
		SELECT claimed.group_id, claimed.generation, claimed.lease_until
		FROM claimed
		JOIN due ON due.id = claimed.group_id
		ORDER BY due.due_at, due.id
		""";

	public static final String TRANSITION_GROUP = """
		UPDATE push_dispatch_group
		SET status = :status,
			next_attempt_at = :nextAttemptAt,
			completed_at = :completedAt
		WHERE id = :groupId
		  AND attempt_count = :generation
		  AND status = 'PROCESSING'
		""";

	public static final String FIND_GROUP_MEMBER_BY_NOTIFICATION = """
		SELECT group_id, notification_id, created_at
		FROM push_dispatch_group_member
		WHERE notification_id = :notificationId
		""";

	/**
	 * group generation과 수신자 timezone, 설정, 추천 이력, member delivery eligibility를
	 * 한 snapshot으로 읽는다. 본문·닉네임·좌표·거리와 token 평문은 선택하지 않는다.
	 */
	public static final String FIND_GROUP_CONTEXT = """
		SELECT
			g.id,
			g.recipient_id,
			g.notification_type AS group_notification_type,
			g.aggregation_key,
			g.status,
			g.window_started_at,
			g.collect_until,
			g.policy_expires_at,
			g.attempt_count,
			g.next_attempt_at,
			g.budget_local_date,
			g.budget_consumed_at,
			g.first_attempted_at,
			g.created_at,
			g.completed_at,
			recipient.timezone AS budget_zone,
			COALESCE(nus.push_enabled, TRUE) AS push_enabled,
			nus.quiet_start,
			nus.quiet_end,
			nus.quiet_zone_id,
			COALESCE(type_prefs.answer_received, TRUE) AS type_answer_received,
			COALESCE(type_prefs.answer_reacted, TRUE) AS type_answer_reacted,
			COALESCE(type_prefs.direction_post_received, TRUE) AS type_direction_post_received,
			COALESCE(type_prefs.report_resolved, TRUE) AS type_report_resolved,
			COALESCE(type_prefs.question_proposal_reviewed, TRUE) AS type_question_proposal_reviewed,
			COALESCE(type_prefs.question_recommended, TRUE) AS type_question_recommended,
			last_recommendation.first_attempted_at AS last_recommendation_attempt_at,
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
			(n.id IS NOT NULL AND actor_ref.actor_id IS NOT NULL AND EXISTS (
				SELECT 1
				FROM user_block ub
				WHERE ub.released_at IS NULL
				  AND ((ub.blocker_id = n.recipient_id AND ub.blocked_id = actor_ref.actor_id)
					OR (ub.blocker_id = actor_ref.actor_id AND ub.blocked_id = n.recipient_id))
			)) AS blocked_in_either_direction,
			CASE
				WHEN n.id IS NULL THEN NULL
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
		FROM push_dispatch_group g
		JOIN user_account recipient ON recipient.id = g.recipient_id
		LEFT JOIN notification_user_setting nus ON nus.user_id = g.recipient_id
		LEFT JOIN LATERAL (
			SELECT
				bool_or(enabled) FILTER (WHERE notification_type = 'ANSWER_RECEIVED') AS answer_received,
				bool_or(enabled) FILTER (WHERE notification_type = 'ANSWER_REACTED') AS answer_reacted,
				bool_or(enabled) FILTER (WHERE notification_type = 'DIRECTION_POST_RECEIVED') AS direction_post_received,
				bool_or(enabled) FILTER (WHERE notification_type = 'REPORT_RESOLVED') AS report_resolved,
				bool_or(enabled) FILTER (WHERE notification_type = 'QUESTION_PROPOSAL_REVIEWED') AS question_proposal_reviewed,
				bool_or(enabled) FILTER (WHERE notification_type = 'QUESTION_RECOMMENDED') AS question_recommended
			FROM notification_preference
			WHERE user_id = g.recipient_id
		) type_prefs ON TRUE
		LEFT JOIN LATERAL (
			SELECT hist.first_attempted_at
			FROM push_dispatch_group hist
			WHERE hist.recipient_id = g.recipient_id
			  AND hist.notification_type = 'QUESTION_RECOMMENDED'
			  AND hist.first_attempted_at IS NOT NULL
			  AND hist.id <> g.id
			ORDER BY hist.first_attempted_at DESC
			LIMIT 1
		) last_recommendation ON TRUE
		LEFT JOIN push_dispatch_group_member m ON m.group_id = g.id
		LEFT JOIN notification n ON n.id = m.notification_id
		LEFT JOIN notification_delivery nd ON nd.notification_id = n.id
		LEFT JOIN push_device pd ON pd.id = nd.push_device_id
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
		LEFT JOIN notification_preference np
			ON np.user_id = n.recipient_id AND np.notification_type = n.notification_type
		WHERE g.id = :groupId
		  AND g.attempt_count = :generation
		ORDER BY nd.id
		""";

	/** group generation+PROCESSING fence를 잠그는 CTE 본문. 다른 group 종료 문장이 공유한다. */
	private static final String LOCKED_PROCESSING_GROUP = """
		SELECT id
		FROM push_dispatch_group
		WHERE id = :groupId
		  AND attempt_count = :generation
		  AND status = 'PROCESSING'
		FOR UPDATE
		""";

	/**
	 * group generation+PROCESSING fence 뒤 미발송 member delivery만 취소한다.
	 * delivery attempt_count는 group generation과 다른 카운터라 PROCESSING 조건에 넣지 않는다.
	 */
	public static final String CANCEL_GROUP = """
		WITH locked AS (
		""" + LOCKED_PROCESSING_GROUP + """
		), cancelled_deliveries AS (
			UPDATE notification_delivery nd
			SET status = 'CANCELLED',
				next_attempt_at = :at,
				sent_at = NULL,
				provider_message_id = NULL
			FROM push_dispatch_group_member m
			JOIN locked ON locked.id = m.group_id
			WHERE nd.notification_id = m.notification_id
			  AND nd.status IN ('PENDING', 'FAILED', 'PROCESSING')
			RETURNING nd.id
		), cancelled_group AS (
			UPDATE push_dispatch_group g
			SET status = 'CANCELLED',
				next_attempt_at = :at,
				completed_at = :at
			FROM locked
			WHERE g.id = locked.id
			  AND g.attempt_count = :generation
			  AND g.status = 'PROCESSING'
			RETURNING g.id
		)
		SELECT EXISTS (SELECT 1 FROM cancelled_group) AS cancelled
		""";

	public static final String LOCK_GROUP_FOR_UPDATE = """
		SELECT *
		FROM push_dispatch_group
		WHERE id = :groupId
		FOR UPDATE
		""";

	public static final String UPSERT_DAILY_BUDGET = """
		INSERT INTO push_daily_budget (user_id, budget_date, consumed_total, consumed_general, updated_at)
		VALUES (:userId, :budgetDate, 0, 0, :at)
		ON CONFLICT (user_id, budget_date) DO NOTHING
		""";

	public static final String LOCK_DAILY_BUDGET = """
		SELECT consumed_total, consumed_general
		FROM push_daily_budget
		WHERE user_id = :userId AND budget_date = :budgetDate
		FOR UPDATE
		""";

	public static final String INCREMENT_DAILY_BUDGET = """
		UPDATE push_daily_budget
		SET consumed_total = consumed_total + 1,
			consumed_general = consumed_general + :generalIncrement,
			updated_at = :at
		WHERE user_id = :userId AND budget_date = :budgetDate
		""";

	public static final String STAMP_GROUP_BUDGET = """
		UPDATE push_dispatch_group
		SET budget_local_date = :budgetDate,
			budget_consumed_at = :at,
			first_attempted_at = :at
		WHERE id = :groupId
		  AND attempt_count = :generation
		  AND status = 'PROCESSING'
		  AND budget_consumed_at IS NULL
		""";

	public static final String LOCK_GROUP_PROCESSING = LOCKED_PROCESSING_GROUP;

	public static final String CANCEL_MEMBER_DELIVERIES = """
		WITH locked AS (
		""" + LOCKED_PROCESSING_GROUP + """
		)
		UPDATE notification_delivery nd
		SET status = 'CANCELLED',
			next_attempt_at = :at,
			sent_at = NULL,
			provider_message_id = NULL
		FROM push_dispatch_group_member m
		JOIN locked ON locked.id = m.group_id
		WHERE nd.notification_id = m.notification_id
		  AND nd.id IN (:deliveryIds)
		  AND nd.status IN ('PENDING', 'FAILED', 'PROCESSING')
		""";

	public static final String CLAIM_DEVICES = """
		WITH locked AS (
		""" + LOCKED_PROCESSING_GROUP + """
		), due AS MATERIALIZED (
			SELECT nd.id, nd.push_device_id, nd.next_attempt_at
			FROM notification_delivery nd
			JOIN push_dispatch_group_member m ON m.notification_id = nd.notification_id
			JOIN locked g ON g.id = m.group_id
			WHERE nd.id IN (:deliveryIds)
			  AND nd.next_attempt_at <= :now
			  AND (
				nd.status IN ('PENDING', 'FAILED')
				OR nd.status = 'PROCESSING'
			  )
			ORDER BY nd.push_device_id, nd.id
			FOR UPDATE OF nd SKIP LOCKED
		), claimed AS (
			UPDATE notification_delivery AS nd
			SET status = 'PROCESSING',
				attempt_count = nd.attempt_count + 1,
				next_attempt_at = :leaseUntil,
				sent_at = NULL,
				provider_message_id = NULL
			FROM due
			WHERE nd.id = due.id
			RETURNING nd.id AS delivery_id, nd.push_device_id AS device_id,
				nd.attempt_count AS generation, nd.next_attempt_at AS lease_until
		)
		SELECT claimed.device_id, claimed.delivery_id, claimed.generation, claimed.lease_until
		FROM claimed
		JOIN due ON due.id = claimed.delivery_id
		ORDER BY claimed.device_id, due.id
		""";

	public static final String COMPLETE_DEVICE_DELIVERY = """
		UPDATE notification_delivery nd
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
		WHERE nd.id = :deliveryId
		  AND nd.status = 'PROCESSING'
		  AND nd.attempt_count = :generation
		  AND nd.push_device_id = :deviceId
		  AND EXISTS (
			SELECT 1
			FROM push_dispatch_group g
			JOIN push_dispatch_group_member m ON m.group_id = g.id
			WHERE g.id = :groupId
			  AND g.attempt_count = :groupGeneration
			  AND g.status = 'PROCESSING'
			  AND m.notification_id = nd.notification_id
		  )
		""";

	public static final String FINALIZE_GROUP = """
		WITH locked AS (
		""" + LOCKED_PROCESSING_GROUP + """
		), retry AS (
			SELECT min(nd.next_attempt_at) AS next_at
			FROM notification_delivery nd
			JOIN push_dispatch_group_member m ON m.notification_id = nd.notification_id
			JOIN locked g ON g.id = m.group_id
			WHERE nd.status IN ('PENDING', 'FAILED', 'PROCESSING')
		), updated AS (
			UPDATE push_dispatch_group g
			SET status = CASE WHEN retry.next_at IS NULL THEN 'COMPLETED' ELSE 'FAILED' END,
				next_attempt_at = COALESCE(retry.next_at, CAST(:at AS TIMESTAMPTZ)),
				completed_at = CASE WHEN retry.next_at IS NULL THEN CAST(:at AS TIMESTAMPTZ) ELSE NULL END
			FROM locked, retry
			WHERE g.id = locked.id
			  AND g.attempt_count = :generation
			  AND g.status = 'PROCESSING'
			RETURNING g.status
		)
		SELECT status FROM updated
		""";

	public static final String INVALIDATE_DEVICE_IF_ACTIVE = """
		UPDATE push_device
		SET device_status = 'INVALID',
			revoked_at = NULL
		WHERE id = :deviceId
		  AND device_status IN ('ACTIVE', 'INVALID')
		""";

	public static final String CANCEL_DEVICE_PENDING_OR_FAILED = """
		UPDATE notification_delivery
		SET status = 'CANCELLED',
			next_attempt_at = :at,
			sent_at = NULL,
			provider_message_id = NULL
		WHERE push_device_id = :deviceId
		  AND status IN ('PENDING', 'FAILED')
		""";
}
