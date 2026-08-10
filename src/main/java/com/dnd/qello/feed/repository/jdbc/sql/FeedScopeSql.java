package com.dnd.qello.feed.repository.jdbc.sql;

/**
 * 수신자 기반 feed 조회에서 공유하는 열람 자격 SQL 조각.
 * 상태·만료 판정은 상세 조회와 답변 열람이 같은 규칙을 사용해야 하므로
 * 각 쿼리 SQL 클래스에 따로 두지 않는다.
 */
public final class FeedScopeSql {

	private FeedScopeSql() {
	}

	/** 만료 시각의 영향을 받는, 아직 답변하지 않은 수신 상태. */
	public static final String TIME_BOUND_RECIPIENT_STATUSES =
		"('AVAILABLE','DISCOVERED','OPENED','SKIP_PENDING')";

	/**
	 * 수신함에서 질문글 자체를 노출할 수 있는 공통 스코프.
	 * 목록과 상세가 질문글 상태·삭제·활성 발신자 차단 조건을 따로 가지면 한 경로에서만
	 * 차단된 글이 다시 노출될 수 있으므로 fragment로 공유한다.
	 */
	public static final String ACTIVE_POST_VISIBILITY = """
		  AND dp.status = 'ACTIVE'
		  AND dp.deleted_at IS NULL
		  AND NOT EXISTS (SELECT 1 FROM user_block ub
		                  WHERE ub.blocker_id = :recipientId
		                    AND ub.blocked_id = dp.sender_id
		                    AND ub.released_at IS NULL)
		""";

	/**
	 * 수신자가 질문글을 열람할 수 있는 상태·시간 조건.
	 * ANSWERED는 만료 후에도 유지하고, 그 외 미종결 상태는 명시된 at 이전에만 유지한다.
	 */
	public static final String RECIPIENT_VIEW_ELIGIBILITY =
		"(pr.status = 'ANSWERED' OR (pr.status IN " + TIME_BOUND_RECIPIENT_STATUSES
			+ " AND dp.expires_at > :at))";
}
