package com.dnd.qello.feed.repository.jdbc.sql;

/**
 * JdbcInboxQueryRepository가 쓰는 SQL 상수.
 * SELECT_CARD는 수신함 카드 하나를 채우는 SELECT다 — 수신자 기준 방향·거리와
 * 답변 수·공감 수·미읽음 답변 수 집계와 뷰어 본인의 공감 여부를 한 쿼리에서 함께
 * 가져온다. reacted_by_me는 목록과 상세가 같은 SELECT를 공유하므로 한 곳만 고치면
 * 두 경로에 함께 반영된다.
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
		       CASE WHEN pr.distance_m < :nearFloor THEN :nearDistanceLabel ELSE NULL END AS distance_band,
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
		                             AND ub.released_at IS NULL)
		           """ + ContentSuppressionSql.notReportedByViewer(":recipientId", "a.id") + """
		           ) AS answer_count,
		       EXISTS (SELECT 1 FROM post_reaction prm
		                WHERE prm.post_id = dp.id AND prm.reactor_id = :recipientId) AS reacted_by_me,
		       (SELECT count(*) FROM post_reaction prx WHERE prx.post_id = dp.id) AS reaction_count,
		       (SELECT count(*) FROM answer a
		          JOIN post_recipient pru ON pru.id = a.post_recipient_id
		         WHERE pru.post_id = dp.id AND a.status = 'PUBLISHED' AND a.deleted_at IS NULL
		           AND a.published_at > COALESCE(pr.answers_read_at, '-infinity'::timestamptz)
		           AND a.author_id <> :recipientId
		           AND NOT EXISTS (SELECT 1 FROM user_block ub
		                           WHERE ub.blocker_id = :recipientId
		                             AND ub.blocked_id = a.author_id
		                             AND ub.released_at IS NULL)
		           """ + ContentSuppressionSql.notReportedByViewer(":recipientId", "a.id") + """
		           ) AS unread_answer_count
		FROM post_recipient pr
		JOIN direction_post dp ON dp.id = pr.post_id
		JOIN approved_question aq ON aq.id = dp.approved_question_id
		""";

	/**
	 * 방향 구간 조인. 구간 시작각(center - width/2)을 원점으로 옮긴 오프셋이 폭 미만인지로
	 * 원형 포함을 판정한다 — direction.domain.DirectionSegment.contains의 반열림 규칙
	 * (bearing >= start && bearing < end)과 같다. +720은 시작각이 음수인 구간(북 구간처럼
	 * center - width/2 < 0)에서 MOD 피연산자를 양수로 올리는 보정이다 — PostgreSQL의 MOD는
	 * 피연산자 부호를 따라가므로 이 보정이 없으면 음수 오프셋이 나와 비교가 깨진다.
	 * ds.code = :schemeCode로 스킴을 고정한다 — uq_direction_scheme_active가 (code) WHERE
	 * status = 'ACTIVE'라 code별로만 유일하므로, code를 고정하지 않으면 다른 code의 스킴이
	 * 동시에 ACTIVE일 때 한 pr 행이 두 스킴의 구간에 각각 잡혀 집계가 중복된다.
	 * NUMERIC을 double precision으로 캐스팅하지 않는다 — 부동소수 반올림이 22.5도 같은
	 * 경계각을 인접 구간으로 밀어낼 수 있고, 대상 행 수 × 8구간 규모에서는 속도 차이가
	 * 무의미하다.
	 */
	public static final String SEGMENT_JOIN = """
		JOIN direction_scheme ds ON ds.status = 'ACTIVE' AND ds.code = :schemeCode
		JOIN direction_segment seg ON seg.scheme_id = ds.id
		 AND MOD(pr.inbound_bearing_deg - (seg.center_bearing_deg - seg.angular_width_deg / 2) + 720, 360)
		     < seg.angular_width_deg
		""";

	/**
	 * findInbox와 countByDirection이 공유하는 스코프 조건(카테고리 상태 필터 제외).
	 * 두 쿼리가 각자 WHERE절을 들면 한쪽만 고쳐질 때 "칩 count == 그 칩으로 필터한
	 * 목록 건수" 불변식이 조용히 깨진다 — 그래서 이 조건을 한 상수로 공유한다.
	 */
	public static final String SCOPE_FILTER = FeedScopeSql.ACTIVE_POST_VISIBILITY + """
		  AND dp.expires_at > :at
		""";

	/**
	 * 상세 조회에서만 사용하는 질문글·수신자 열람 스코프.
	 * 목록의 만료 전 스코프를 그대로 재사용하지 않고, ANSWERED의 만료 후 열람 예외를
	 * FeedScopeSql의 공통 상태·시간 정책으로 보존한다.
	 */
	public static final String DETAIL_SCOPE_FILTER = FeedScopeSql.ACTIVE_POST_VISIBILITY + """
		  AND\s""" + FeedScopeSql.RECIPIENT_VIEW_ELIGIBILITY + """
		""";

	/**
	 * 방향 칩 집계의 SELECT/FROM/JOIN. 카테고리별 상태 필터, SCOPE_FILTER, GROUP BY는
	 * 리포지토리가 조립한다. 방향 필터를 받지 않는다 — 칩 집계는 항상 category 스코프
	 * 전체를 본다.
	 */
	public static final String SELECT_CHIP_AGGREGATE = """
		SELECT seg.segment_key, seg.display_name, seg.sort_order, count(*) AS chip_count
		FROM post_recipient pr
		JOIN direction_post dp ON dp.id = pr.post_id
		""" + SEGMENT_JOIN;
}
