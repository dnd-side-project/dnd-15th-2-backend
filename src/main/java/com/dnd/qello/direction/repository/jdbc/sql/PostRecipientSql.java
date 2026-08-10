package com.dnd.qello.direction.repository.jdbc.sql;

/**
 * JdbcPostRecipientRepository가 쓰는 SQL 상수.
 * INSERT/UPDATE는 id 유무에 따른 upsert 분기의 각 절반이다 — 분기 자체는
 * 리포지토리에 남아 있다.
 */
public final class PostRecipientSql {

	private PostRecipientSql() {
	}

	public static final String INSERT = """
		INSERT INTO post_recipient
			(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
			 matched_at, discovered_at, opened_at, skip_requested_at, skipped_at, capacity_released_at,
			 expired_at, blocked_at, inbound_bearing_deg, distance_m, answers_read_at)
		VALUES (:postId, :recipientId, :status, :distanceBand, :bearing, :regionCode,
			:matchedAt, :discoveredAt, :openedAt, :skipRequestedAt, :skippedAt, :capacityReleasedAt,
			:expiredAt, :blockedAt, :inboundBearing, :distanceM, :answersReadAt)
		RETURNING id
		""";

	public static final String UPDATE = """
		UPDATE post_recipient
		SET post_id = :postId, recipient_id = :recipientId, status = :status, distance_band = :distanceBand,
		    matched_bearing_deg = :bearing, matched_region_code = :regionCode, matched_at = :matchedAt,
		    discovered_at = :discoveredAt, opened_at = :openedAt, skip_requested_at = :skipRequestedAt,
		    skipped_at = :skippedAt, capacity_released_at = :capacityReleasedAt, expired_at = :expiredAt,
		    blocked_at = :blockedAt, inbound_bearing_deg = :inboundBearing, distance_m = :distanceM,
		    answers_read_at = :answersReadAt
		WHERE id = :id
		RETURNING id
		""";

	/**
	 * 만료 sweep 후보. `SKIP_PENDING`은 제외한다 — 되돌리기 유예 동안은 넘김 확정
	 * sweep의 전용 레인이며, 두 sweep의 대상 상태 집합이 겹치지 않아야 동시 실행에서
	 * 경쟁이 생기지 않는다(direction.domain.PostRecipient.expire 참고).
	 */
	public static final String FIND_EXPIRABLE = """
		SELECT pr.* FROM post_recipient pr
		JOIN direction_post dp ON dp.id = pr.post_id
		WHERE pr.status IN ('AVAILABLE', 'DISCOVERED', 'OPENED')
		  AND dp.expires_at <= :at
		""";

	/** 되돌리기 유예가 지난 SKIP_PENDING만 확정 대상이다. deadline = 기준 시각 - 유예 시간. */
	public static final String FIND_CONFIRMABLE_SKIPS = """
		SELECT * FROM post_recipient
		WHERE status = 'SKIP_PENDING' AND skip_requested_at <= :deadline
		""";

	/**
	 * 차단자(blockerId) 자신의 수신 항목 중 차단당한 발신자(blockedSenderId)가 보낸
	 * 질문글에 대한 것만 대상이다. 방향은 feed 조회의 `ub.blocker_id = 뷰어` 필터와
	 * 같다 — 차단당한 사람의 수신 항목은 건드리지 않는다.
	 */
	public static final String FIND_BLOCKABLE = """
		SELECT pr.* FROM post_recipient pr
		JOIN direction_post dp ON dp.id = pr.post_id
		WHERE pr.recipient_id = :blockerId
		  AND dp.sender_id = :blockedSenderId
		  AND pr.status IN ('AVAILABLE', 'DISCOVERED', 'OPENED', 'SKIP_PENDING')
		""";

	/**
	 * 세 조건부 전이(TRANSITION_TO_EXPIRED/SKIPPED/BLOCKED)는 TRANSITION_TO_ANSWERED와
	 * 같은 낙관적 패턴이다 — `WHERE id = :id AND status = :previousStatus`가 다른
	 * transaction이 먼저 전이시킨 행에는 매치되지 않아 0행을 반환하고, 호출자는 그
	 * 빈 Optional을 "이미 처리됨"의 정상 신호로 받는다(예외가 아니다).
	 */
	public static final String TRANSITION_TO_EXPIRED = """
		UPDATE post_recipient
		SET status = :status, expired_at = :expiredAt, capacity_released_at = :capacityReleasedAt
		WHERE id = :id AND status = :previousStatus
		RETURNING *
		""";

	public static final String TRANSITION_TO_SKIPPED = """
		UPDATE post_recipient
		SET status = :status, skipped_at = :skippedAt, capacity_released_at = :capacityReleasedAt
		WHERE id = :id AND status = :previousStatus
		RETURNING *
		""";

	/** skip_requested_at을 NULL로 비운다 — BLOCKED는 SKIP_PENDING이 아니므로 남겨두면 생성자 불변식을 위반한다. */
	public static final String TRANSITION_TO_BLOCKED = """
		UPDATE post_recipient
		SET status = :status, blocked_at = :blockedAt, capacity_released_at = :capacityReleasedAt,
		    skip_requested_at = NULL
		WHERE id = :id AND status = :previousStatus
		RETURNING *
		""";
}
