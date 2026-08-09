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
}
