package com.dnd.qello.notification.repository.jdbc.sql;

/**
 * JdbcNotificationInboxQueryRepository가 쓰는 SQL 상수.
 * SELECT_ROW는 알림 한 줄과 그 줄의 대상 생존 상태를 한 쿼리에서 함께 판정한다 —
 * 목록과 진입 판정(target)이 같은 SELECT를 공유해야 우선순위(§7.2)가 두 경로에서
 * 갈라지지 않는다.
 */
public final class NotificationInboxQuerySql {

	private NotificationInboxQuerySql() {
	}

	/**
	 * 우선순위는 CASE 절의 평가 순서 그 자체다 — GONE > BLOCKED > HIDDEN > EXPIRED >
	 * AVAILABLE. 삭제된 대상에 차단 이유를 노출하면 상대의 차단 사실이 새어 나가므로
	 * GONE을 항상 먼저 본다.
	 * target_kind가 NONE이면(둘 다 참조가 없으면) target_state는 NULL이다 — N1이 다루는
	 * DIRECTION_POST_RECEIVED 외 5종은 아직 발행 소비자가 없어(#177) 대상 없는 알림으로
	 * 남는다.
	 */
	public static final String SELECT_ROW = """
		SELECT sub.notification_id,
		       sub.notification_type,
		       sub.created_at,
		       sub.read_at,
		       sub.status,
		       sub.target_kind,
		       sub.target_id,
		       sub.target_state,
		       CASE WHEN sub.target_kind = 'DIRECTION_POST' AND sub.target_state = 'AVAILABLE'
		            THEN sub.post_expires_at ELSE NULL END AS expires_at
		FROM (
		  SELECT n.id AS notification_id,
		         n.notification_type,
		         n.created_at,
		         n.read_at,
		         n.status,
		         CASE WHEN n.direction_post_id IS NOT NULL THEN 'DIRECTION_POST'
		              WHEN n.answer_id IS NOT NULL THEN 'ANSWER'
		              ELSE 'NONE' END AS target_kind,
		         COALESCE(n.direction_post_id, n.answer_id) AS target_id,
		         CASE
		           WHEN n.direction_post_id IS NOT NULL THEN
		             CASE
		               WHEN dp.id IS NULL OR dp.deleted_at IS NOT NULL THEN 'GONE'
		               WHEN EXISTS (
		                 SELECT 1 FROM user_block ub
		                  WHERE ((ub.blocker_id = :recipientId AND ub.blocked_id = dp.sender_id)
		                      OR (ub.blocker_id = dp.sender_id AND ub.blocked_id = :recipientId))
		                    AND ub.released_at IS NULL
		               ) THEN 'BLOCKED'
		               WHEN dp.status <> 'ACTIVE' OR dp.expires_at <= :at THEN 'EXPIRED'
		               ELSE 'AVAILABLE'
		             END
		           WHEN n.answer_id IS NOT NULL THEN
		             CASE
		               WHEN a.id IS NULL OR a.status = 'DELETED' THEN 'GONE'
		               WHEN EXISTS (
		                 SELECT 1 FROM user_block ub
		                  WHERE ((ub.blocker_id = :recipientId AND ub.blocked_id = a.author_id)
		                      OR (ub.blocker_id = a.author_id AND ub.blocked_id = :recipientId))
		                    AND ub.released_at IS NULL
		               ) THEN 'BLOCKED'
		               WHEN a.status = 'HIDDEN' OR a.published_at IS NULL THEN 'HIDDEN'
		               ELSE 'AVAILABLE'
		             END
		           ELSE NULL
		         END AS target_state,
		         dp.expires_at AS post_expires_at
		  FROM notification n
		  LEFT JOIN direction_post dp ON dp.id = n.direction_post_id
		  LEFT JOIN answer a ON a.id = n.answer_id
		  WHERE n.recipient_id = :recipientId
		) sub
		""";

	/** 목록에서 REVOKED·DISMISSED를 제외한다 — notification_recipient_feed_idx의 부분 조건과 같은 술어라야 인덱스가 잡힌다. */
	public static final String LIST_STATUS_FILTER = "sub.status IN ('UNREAD', 'READ')";

	/** (created_at, id) 튜플 비교라야 같은 시각 다건에서 줄이 흘리거나 중복되지 않는다. */
	public static final String LIST_CURSOR_FILTER =
		"(sub.created_at, sub.notification_id) < (:cursorCreatedAt, :cursorNotificationId)";

	public static final String LIST_ORDER_AND_LIMIT = "ORDER BY sub.created_at DESC, sub.notification_id DESC LIMIT :limit";

	public static final String COUNT_UNREAD = """
		SELECT count(*) FROM notification WHERE recipient_id = :recipientId AND status = 'UNREAD'
		""";

	/** seenAt이 NULL이면(한 번도 열람하지 않았으면) UNREAD 존재만으로 판정한다. */
	public static final String EXISTS_UNSEEN = """
		SELECT EXISTS (
		  SELECT 1 FROM notification
		   WHERE recipient_id = :recipientId
		     AND status = 'UNREAD'
		     AND (:seenAt::timestamptz IS NULL OR created_at > :seenAt)
		)
		""";
}
