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
	public static final String TIME_BOUND_RECIPIENT_STATUSES = "('AVAILABLE','DISCOVERED','OPENED','SKIP_PENDING')";

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
		              AND (
		                pr.status = 'ANSWERED'
		                OR (pr.status IN """ + TIME_BOUND_RECIPIENT_STATUSES + """
		 AND dp.expires_at > :at)
		              )
		        )
		      )
		)
		""";
}
