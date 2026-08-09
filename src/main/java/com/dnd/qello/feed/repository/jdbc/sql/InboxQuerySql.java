package com.dnd.qello.feed.repository.jdbc.sql;

/**
 * JdbcInboxQueryRepository가 쓰는 SQL 상수.
 * SELECT_CARD는 수신함 카드 하나를 채우는 SELECT다 — 수신자 기준 방향·거리와
 * 답변 수·공감 수·미읽음 답변 수 집계를 한 쿼리에서 함께 가져온다.
 */
public final class InboxQuerySql {

	private InboxQuerySql() {
	}

	/**
	 * distance_m과 distance_band는 근거리 하한을 기준으로 상호 배타적으로만 노출한다.
	 * 하한 미만이면 정확 거리를 아예 ResultSet에 싣지 않는다 — mapper가 실수로 노출할
	 * 경로를 구조적으로 없앤다.
	 */
	public static final String SELECT_CARD = """
		SELECT pr.id AS post_recipient_id,
		       pr.post_id,
		       pr.status,
		       pr.inbound_bearing_deg,
		       CASE WHEN pr.distance_m < :nearFloor THEN NULL ELSE pr.distance_m END AS distance_m,
		       CASE WHEN pr.distance_m < :nearFloor THEN pr.distance_band ELSE NULL END AS distance_band,
		       pr.matched_at,
		       pr.opened_at,
		       pr.skip_requested_at,
		       aq.question_text,
		       dp.body_text,
		       dp.coarse_region_code AS sender_region_code,
		       dp.expires_at,
		       COALESCE((SELECT array_agg(ma.media_id ORDER BY ma.display_order)
		                 FROM media_attachment ma WHERE ma.post_id = dp.id), '{}'::bigint[]) AS media_ids,
		       (SELECT count(*) FROM answer a
		          JOIN post_recipient pra ON pra.id = a.post_recipient_id
		         WHERE pra.post_id = dp.id AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL
		           AND NOT EXISTS (SELECT 1 FROM user_block ub
		                           WHERE ub.blocker_id = :recipientId
		                             AND ub.blocked_id = a.author_id
		                             AND ub.released_at IS NULL)) AS answer_count,
		       (SELECT count(*) FROM post_reaction prx WHERE prx.post_id = dp.id) AS reaction_count,
		       (SELECT count(*) FROM answer a
		          JOIN post_recipient pru ON pru.id = a.post_recipient_id
		         WHERE pru.post_id = dp.id AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL
		           AND a.published_at > COALESCE(pr.answers_read_at, '-infinity'::timestamptz)
		           AND a.author_id <> :recipientId
		           AND NOT EXISTS (SELECT 1 FROM user_block ub
		                           WHERE ub.blocker_id = :recipientId
		                             AND ub.blocked_id = a.author_id
		                             AND ub.released_at IS NULL)) AS unread_answer_count
		FROM post_recipient pr
		JOIN direction_post dp ON dp.id = pr.post_id
		JOIN approved_question aq ON aq.id = dp.approved_question_id
		""";
}
