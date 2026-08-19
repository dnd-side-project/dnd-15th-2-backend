package com.dnd.qello.feed.repository.jdbc.sql;

/**
 * 뷰어가 본인이 신고한 답변을 목록·카운트에서 다시 보지 않게 하는 SQL 조각(#155
 * 신고자 한정 숨김). 신고의 종결 여부와 무관하게 유지한다 — status 조건을 걸지
 * 않는다.
 *
 * viewer 식별자 표현식이 호출부마다 다르다(bind 파라미터 또는 컬럼 참조)라서
 * 문자열 상수 대신 인자를 받는 메서드로 둔다.
 */
public final class ContentSuppressionSql {

	private ContentSuppressionSql() {
	}

	public static String notReportedByViewer(String viewerIdExpression, String answerIdExpression) {
		return "AND NOT EXISTS (SELECT 1 FROM report r WHERE r.reporter_id = " + viewerIdExpression
			+ " AND r.answer_id = " + answerIdExpression + ")";
	}
}
