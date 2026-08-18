package com.dnd.qello.safety.repository.jdbc.sql;

import com.dnd.qello.feed.repository.jdbc.sql.FeedScopeSql;

/**
 * 신고 대상(답변/질문글/사용자)의 존재·열람 자격·현재 콘텐츠를 한 번에 읽는다.
 * 결과가 없으면 "존재하지 않음"과 "열람 자격 없음"을 구분하지 않는다 — 신고자에게
 * 대상 존재 여부를 노출하지 않기 위해서다(설계 문서 §9.4).
 */
public final class ReportTargetSql {

	private ReportTargetSql() {
	}

	/**
	 * 열람 자격은 답변을 쓴 recipient 자신의 행이 아니라, 조회자 소유의 post_recipient
	 * 행을 독립적으로 찾아 판정한다({@code viewer_pr}) — 답변 소유 행({@code owner_pr})에
	 * 그대로 조인하면 그 답변을 쓴 본인만 조건을 만족하는 버그가 된다.
	 */
	public static final String SELECT_VIEWABLE_ANSWER = """
		SELECT a.author_id,
		       a.body_text,
		       a.edit_count,
		       a.published_at AS content_published_at,
		       COALESCE((SELECT array_agg(asset.storage_key ORDER BY ma.display_order)
		                 FROM media_attachment ma
		                 JOIN media_asset asset ON asset.id = ma.media_id
		                 WHERE ma.answer_id = a.id), '{}'::text[]) AS media_object_keys
		FROM answer a
		JOIN post_recipient owner_pr ON owner_pr.id = a.post_recipient_id
		JOIN direction_post dp ON dp.id = owner_pr.post_id
		WHERE a.id = :answerId
		  AND a.status = 'PUBLISHED'
		  AND a.deleted_at IS NULL
		  AND dp.deleted_at IS NULL
		  AND (dp.sender_id = :viewerId
		       OR EXISTS (SELECT 1 FROM post_recipient viewer_pr
		                  WHERE viewer_pr.post_id = dp.id AND viewer_pr.recipient_id = :viewerId
		                    AND\s""" + FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY.replace("pr.", "viewer_pr.") + """
		))
		  AND NOT EXISTS (SELECT 1 FROM user_block ub
		                  WHERE ub.released_at IS NULL
		                    AND ((ub.blocker_id = :viewerId AND ub.blocked_id = a.author_id)
		                      OR (ub.blocker_id = a.author_id AND ub.blocked_id = :viewerId)))
		""";

	public static final String SELECT_VIEWABLE_POST = """
		SELECT dp.sender_id AS author_id,
		       dp.body_text,
		       0 AS edit_count,
		       dp.published_at AS content_published_at,
		       COALESCE((SELECT array_agg(asset.storage_key ORDER BY ma.display_order)
		                 FROM media_attachment ma
		                 JOIN media_asset asset ON asset.id = ma.media_id
		                 WHERE ma.post_id = dp.id), '{}'::text[]) AS media_object_keys
		FROM direction_post dp
		WHERE dp.id = :postId
		  AND dp.deleted_at IS NULL
		  AND dp.status = 'ACTIVE'
		  AND (dp.sender_id = :viewerId
		       OR EXISTS (SELECT 1 FROM post_recipient pr
		                  WHERE pr.post_id = dp.id AND pr.recipient_id = :viewerId
		                    AND\s""" + FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY + """
		))
		  AND NOT EXISTS (SELECT 1 FROM user_block ub
		                  WHERE ub.released_at IS NULL
		                    AND ((ub.blocker_id = :viewerId AND ub.blocked_id = dp.sender_id)
		                      OR (ub.blocker_id = dp.sender_id AND ub.blocked_id = :viewerId)))
		""";

	/**
	 * 신고자가 이 사용자를 신고할 수 있으려면 방향글로 실제 마주친 이력이 있어야
	 * 한다(ASSUMED — 이슈가 관계 기준을 명시하지 않아 채택한 가정, 설계 문서 §2
	 * Excluded 참고). "마주침"은 서로 발신자·수신자 관계이거나, 같은 질문글의
	 * 수신자로 함께 묶여 있는 경우(#79 이후 수신자 간 답변 상호 열람과 동일한
	 * 범위) 둘 다 인정한다.
	 */
	public static final String SELECT_VIEWABLE_USER = """
		SELECT ua.id AS author_id,
		       ua.nickname AS body_text,
		       0 AS edit_count,
		       NULL::timestamptz AS content_published_at,
		       '{}'::text[] AS media_object_keys
		FROM user_account ua
		WHERE ua.id = :targetUserId
		  AND ua.status = 'ACTIVE'
		  AND (
		      EXISTS (
		          SELECT 1 FROM direction_post dp
		          JOIN post_recipient pr ON pr.post_id = dp.id
		          WHERE (dp.sender_id = :viewerId AND pr.recipient_id = :targetUserId)
		             OR (dp.sender_id = :targetUserId AND pr.recipient_id = :viewerId)
		      )
		      OR EXISTS (
		          SELECT 1 FROM post_recipient viewer_pr
		          JOIN post_recipient target_pr ON target_pr.post_id = viewer_pr.post_id
		          WHERE viewer_pr.recipient_id = :viewerId AND target_pr.recipient_id = :targetUserId
		      )
		  )
		  AND NOT EXISTS (SELECT 1 FROM user_block ub
		                  WHERE ub.released_at IS NULL
		                    AND ((ub.blocker_id = :viewerId AND ub.blocked_id = ua.id)
		                      OR (ub.blocker_id = ua.id AND ub.blocked_id = :viewerId)))
		""";
}
