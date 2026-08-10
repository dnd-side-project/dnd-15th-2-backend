package com.dnd.qello.feed.repository.jdbc.sql;

/**
 * JdbcPostAnswerQueryRepository가 쓰는 SQL 상수.
 * CAN_VIEW_ANSWERS_SQL은 뷰어가 그 질문글의 답변을 볼 수 있는지(질문자이거나,
 * 시점 기준 자격을 유지한 수신자인지)를 EXISTS로 판정한다.
 */
public final class PostAnswerQuerySql {

	private PostAnswerQuerySql() {
	}

	/**
	 * 넘김 되돌리기가 가능한 SKIP_PENDING은 아직 자격을 유지한다. ANSWERED는 만료
	 * 시각과 무관하게 항상 자격을 유지하므로 이 목록과 별도로 취급한다.
	 */
	public static final String CAN_VIEW_ANSWERS_SQL = """
		SELECT EXISTS (
		    SELECT 1
		    FROM direction_post dp
		    WHERE dp.id = :postId
		      AND dp.deleted_at IS NULL
		      AND (
		        dp.sender_id = :viewerId
		        OR EXISTS (
		            SELECT 1 FROM post_recipient pr
		            WHERE pr.post_id = dp.id
		              AND pr.recipient_id = :viewerId
		              AND\s""" + FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY + """
		        )
		      )
		)
		""";

	/**
	 * 답변 목록 projection. 현재 조회자의 post_recipient 거리만 거리 표시의 근거로
	 * 사용하고 answer.distance_m·answer.distance_band는 읽지 않는다.
	 */
	public static final String SELECT_ANSWERS = """
		SELECT a.id AS answer_id,
		       ua.nickname AS author_nickname,
		       a.coarse_region_code AS author_region_code,
		       a.body_text,
		       a.bearing_from_sender_deg,
		       CASE WHEN (CASE WHEN dp.sender_id = :viewerId THEN 0 ELSE viewer_pr.distance_m END) < :nearFloor
		            THEN NULL
		            ELSE (CASE WHEN dp.sender_id = :viewerId THEN 0 ELSE viewer_pr.distance_m END)
		       END AS distance_m,
		       CASE WHEN (CASE WHEN dp.sender_id = :viewerId THEN 0 ELSE viewer_pr.distance_m END) < :nearFloor
		            THEN :nearDistanceLabel
		            ELSE NULL
		       END AS distance_band,
		       a.published_at,
		       a.edited_at,
		       COALESCE((SELECT array_agg(ma.media_id ORDER BY ma.display_order)
		                 FROM media_attachment ma WHERE ma.answer_id = a.id), '{}'::bigint[]) AS media_ids,
		       EXISTS (SELECT 1 FROM answer_reaction ar
		               WHERE ar.answer_id = a.id AND ar.reactor_id = :viewerId) AS reacted_by_me,
		       (SELECT count(*) FROM answer_reaction ar2 WHERE ar2.answer_id = a.id) AS reaction_count
		FROM answer a
		JOIN post_recipient answer_pr ON answer_pr.id = a.post_recipient_id
		JOIN direction_post dp ON dp.id = answer_pr.post_id
		LEFT JOIN post_recipient viewer_pr ON viewer_pr.post_id = dp.id AND viewer_pr.recipient_id = :viewerId
		JOIN user_account ua ON ua.id = a.author_id
		WHERE dp.id = :postId
		  AND dp.deleted_at IS NULL
		  AND a.status = 'PUBLISHED'
		  AND a.deleted_at IS NULL
		  AND NOT EXISTS (SELECT 1 FROM user_block ub
		                  WHERE ub.blocker_id = :viewerId
		                    AND ub.blocked_id = a.author_id
		                    AND ub.released_at IS NULL)
		""";

	public static final String CURSOR_FILTER = " AND (a.published_at, a.id) < (:cursorPublishedAt, :cursorId)";
	public static final String ANSWER_ORDER = " ORDER BY a.published_at DESC, a.id DESC";
}
