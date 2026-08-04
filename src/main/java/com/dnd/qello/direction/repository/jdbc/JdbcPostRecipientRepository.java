package com.dnd.qello.direction.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.PostRecipientRepository;

@Repository
public class JdbcPostRecipientRepository implements PostRecipientRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcPostRecipientRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public PostRecipient save(PostRecipient recipient) {
		String sql = recipient.getId() == null ? """
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, skip_requested_at, skipped_at, capacity_released_at,
				 expired_at, blocked_at)
			VALUES (:postId, :recipientId, :status, :distanceBand, :bearing, :regionCode,
				:matchedAt, :discoveredAt, :openedAt, :skipRequestedAt, :skippedAt, :capacityReleasedAt,
				:expiredAt, :blockedAt)
			RETURNING id
			""" : """
			UPDATE post_recipient
			SET post_id = :postId, recipient_id = :recipientId, status = :status, distance_band = :distanceBand,
			    matched_bearing_deg = :bearing, matched_region_code = :regionCode, matched_at = :matchedAt,
			    discovered_at = :discoveredAt, opened_at = :openedAt, skip_requested_at = :skipRequestedAt,
			    skipped_at = :skippedAt, capacity_released_at = :capacityReleasedAt, expired_at = :expiredAt,
			    blocked_at = :blockedAt
			WHERE id = :id
			RETURNING id
			""";
		Long id = jdbc.queryForObject(sql, params(recipient), Long.class);
		return PostRecipient.restore(id, recipient.getPostId(), recipient.getRecipientId(), recipient.getStatus(),
			recipient.getDistanceBand(), recipient.getMatchedBearingDegrees(), recipient.getMatchedRegionCode(), recipient.getMatchedAt(),
			recipient.getDiscoveredAt(), recipient.getOpenedAt(), recipient.getSkipRequestedAt(), recipient.getSkippedAt(),
			recipient.getCapacityReleasedAt(), recipient.getExpiredAt(), recipient.getBlockedAt());
	}

	@Override
	public List<PostRecipient> findAllByPostId(long postId) {
		return jdbc.query("SELECT * FROM post_recipient WHERE post_id = :postId ORDER BY id",
			new MapSqlParameterSource("postId", postId), (rs, rowNum) -> map(rs));
	}

	private static MapSqlParameterSource params(PostRecipient r) {
		return new MapSqlParameterSource().addValue("id", r.getId()).addValue("postId", r.getPostId())
			.addValue("recipientId", r.getRecipientId()).addValue("status", r.getStatus().name())
			.addValue("distanceBand", r.getDistanceBand()).addValue("bearing", r.getMatchedBearingDegrees())
			.addValue("regionCode", r.getMatchedRegionCode()).addValue("matchedAt", timestamp(r.getMatchedAt()))
			.addValue("discoveredAt", timestamp(r.getDiscoveredAt())).addValue("openedAt", timestamp(r.getOpenedAt()))
			.addValue("skipRequestedAt", timestamp(r.getSkipRequestedAt()))
			.addValue("skippedAt", timestamp(r.getSkippedAt())).addValue("capacityReleasedAt", timestamp(r.getCapacityReleasedAt()))
			.addValue("expiredAt", timestamp(r.getExpiredAt())).addValue("blockedAt", timestamp(r.getBlockedAt()));
	}

	private static Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }

	private static PostRecipient map(ResultSet rs) throws SQLException {
		return PostRecipient.restore(rs.getLong("id"), rs.getLong("post_id"), rs.getLong("recipient_id"),
			PostRecipientStatus.valueOf(rs.getString("status")), rs.getString("distance_band"), rs.getBigDecimal("matched_bearing_deg"),
			rs.getString("matched_region_code"), rs.getTimestamp("matched_at").toInstant(),
			instant(rs, "discovered_at"), instant(rs, "opened_at"), instant(rs, "skip_requested_at"),
			instant(rs, "skipped_at"), instant(rs, "capacity_released_at"),
			instant(rs, "expired_at"), instant(rs, "blocked_at"));
	}

	private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
		java.sql.Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
