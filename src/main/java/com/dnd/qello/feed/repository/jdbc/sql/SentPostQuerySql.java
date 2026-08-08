package com.dnd.qello.feed.repository.jdbc.sql;

/**
 * JdbcSentPostQueryRepository가 쓰는 SQL 상수.
 * SELECT_CARD는 보낸 질문글 카드 하나를 채우는 SELECT다 — 답변 수·공감 수·
 * 미읽음 답변 수 집계를 한 쿼리에서 함께 가져온다.
 */
public final class SentPostQuerySql {

	private SentPostQuerySql() {
	}

	public static final String SELECT_CARD = """
		SELECT dp.id AS post_id,
		       aq.question_text,
		       dp.body_text,
		       dp.coarse_region_code,
		       dp.submitted_at,
		       dp.expires_at,
		       dp.answers_read_at,
		       COALESCE((SELECT array_agg(ma.media_id ORDER BY ma.display_order)
		                 FROM media_attachment ma WHERE ma.post_id = dp.id), '{}'::bigint[]) AS media_ids,
		       (SELECT count(*) FROM answer a
		          JOIN post_recipient pra ON pra.id = a.post_recipient_id
		         WHERE pra.post_id = dp.id AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL
		           AND NOT EXISTS (SELECT 1 FROM user_block ub
		                           WHERE ub.blocker_id = dp.sender_id
		                             AND ub.blocked_id = a.author_id
		                             AND ub.released_at IS NULL)) AS answer_count,
		       (SELECT count(*) FROM post_reaction prx WHERE prx.post_id = dp.id) AS reaction_count,
		       (SELECT count(*) FROM answer a
		          JOIN post_recipient pru ON pru.id = a.post_recipient_id
		         WHERE pru.post_id = dp.id AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL
		           AND a.published_at > COALESCE(dp.answers_read_at, '-infinity'::timestamptz)
		           AND NOT EXISTS (SELECT 1 FROM user_block ub
		                           WHERE ub.blocker_id = dp.sender_id
		                             AND ub.blocked_id = a.author_id
		                             AND ub.released_at IS NULL)) AS unread_answer_count
		FROM direction_post dp
		JOIN approved_question aq ON aq.id = dp.approved_question_id
		""";
}
