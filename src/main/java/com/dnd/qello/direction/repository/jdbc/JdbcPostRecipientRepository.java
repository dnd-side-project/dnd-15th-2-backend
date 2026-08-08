package com.dnd.qello.direction.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.direction.repository.jdbc.sql.PostRecipientSql;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcPostRecipientRepository implements PostRecipientRepository {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public PostRecipient save(PostRecipient recipient) {
		String sql = recipient.getId() == null ? PostRecipientSql.INSERT : PostRecipientSql.UPDATE;
		Long id = jdbc.queryForObject(sql, params(recipient), Long.class);
		return PostRecipient.restore(id, recipient.getPostId(), recipient.getRecipientId(), recipient.getStatus(),
			recipient.getDistanceBand(), recipient.getMatchedBearingDegrees(), recipient.getMatchedRegionCode(), recipient.getMatchedAt(),
			recipient.getDiscoveredAt(), recipient.getOpenedAt(), recipient.getSkipRequestedAt(), recipient.getSkippedAt(),
			recipient.getCapacityReleasedAt(), recipient.getExpiredAt(), recipient.getBlockedAt(),
			recipient.getInboundBearingDegrees(), recipient.getDistanceM(), recipient.getAnswersReadAt());
	}

	@Override
	public List<PostRecipient> findAllByPostId(long postId) {
		return jdbc.query("SELECT * FROM post_recipient WHERE post_id = :postId ORDER BY id",
			new MapSqlParameterSource("postId", postId), (rs, rowNum) -> map(rs));
	}

	@Override
	public Optional<PostRecipient> findById(long id) {
		return one("SELECT * FROM post_recipient WHERE id = :id", new MapSqlParameterSource("id", id));
	}

	@Override
	public Optional<PostRecipient> findByIdAndRecipientId(long id, long recipientId) {
		return one("SELECT * FROM post_recipient WHERE id = :id AND recipient_id = :recipientId",
			new MapSqlParameterSource().addValue("id", id).addValue("recipientId", recipientId));
	}

	/**
	 * 질문글+수신자 조합으로 찾는다. postRecipientId를 모르는 호출자용이다 — 예를 들어
	 * PostReactionService가 "이 사용자가 이 질문글의 수신 자격이 있는가"를 확인할 때
	 * postId만 갖고 있으므로 이 finder를 쓴다.
	 */
	@Override
	public Optional<PostRecipient> findByPostIdAndRecipientId(long postId, long recipientId) {
		return one("SELECT * FROM post_recipient WHERE post_id = :postId AND recipient_id = :recipientId",
			new MapSqlParameterSource().addValue("postId", postId).addValue("recipientId", recipientId));
	}

	@Override
	public Optional<PostRecipient> transitionToAnswered(PostRecipient answered, PostRecipientStatus previousStatus) {
		return jdbc.query("""
			UPDATE post_recipient
			SET status = :status, discovered_at = :discoveredAt, opened_at = :openedAt,
			    capacity_released_at = :capacityReleasedAt
			WHERE id = :id AND status = :previousStatus
			RETURNING *
			""", new MapSqlParameterSource().addValue("id", answered.getId())
			.addValue("status", answered.getStatus().name())
			.addValue("discoveredAt", timestamp(answered.getDiscoveredAt()))
			.addValue("openedAt", timestamp(answered.getOpenedAt()))
			.addValue("capacityReleasedAt", timestamp(answered.getCapacityReleasedAt()))
			.addValue("previousStatus", previousStatus.name()),
			rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	@Override
	public PostRecipient advanceAnswersReadAt(long id, Instant at) {
		return jdbc.query("""
			UPDATE post_recipient
			SET answers_read_at = GREATEST(answers_read_at, :at)
			WHERE id = :id
			RETURNING *
			""", new MapSqlParameterSource().addValue("id", id).addValue("at", timestamp(at)),
			rs -> { rs.next(); return map(rs); });
	}

	private Optional<PostRecipient> one(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	private static MapSqlParameterSource params(PostRecipient r) {
		return new MapSqlParameterSource().addValue("id", r.getId()).addValue("postId", r.getPostId())
			.addValue("recipientId", r.getRecipientId()).addValue("status", r.getStatus().name())
			.addValue("distanceBand", r.getDistanceBand()).addValue("bearing", r.getMatchedBearingDegrees())
			.addValue("regionCode", r.getMatchedRegionCode()).addValue("matchedAt", timestamp(r.getMatchedAt()))
			.addValue("discoveredAt", timestamp(r.getDiscoveredAt())).addValue("openedAt", timestamp(r.getOpenedAt()))
			.addValue("skipRequestedAt", timestamp(r.getSkipRequestedAt()))
			.addValue("skippedAt", timestamp(r.getSkippedAt())).addValue("capacityReleasedAt", timestamp(r.getCapacityReleasedAt()))
			.addValue("expiredAt", timestamp(r.getExpiredAt())).addValue("blockedAt", timestamp(r.getBlockedAt()))
			.addValue("inboundBearing", r.getInboundBearingDegrees()).addValue("distanceM", r.getDistanceM())
			.addValue("answersReadAt", timestamp(r.getAnswersReadAt()));
	}

	private static Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }

	private static PostRecipient map(ResultSet rs) throws SQLException {
		return PostRecipient.restore(rs.getLong("id"), rs.getLong("post_id"), rs.getLong("recipient_id"),
			PostRecipientStatus.valueOf(rs.getString("status")), rs.getString("distance_band"), rs.getBigDecimal("matched_bearing_deg"),
			rs.getString("matched_region_code"), rs.getTimestamp("matched_at").toInstant(),
			instant(rs, "discovered_at"), instant(rs, "opened_at"), instant(rs, "skip_requested_at"),
			instant(rs, "skipped_at"), instant(rs, "capacity_released_at"),
			instant(rs, "expired_at"), instant(rs, "blocked_at"),
			rs.getBigDecimal("inbound_bearing_deg"), rs.getLong("distance_m"), instant(rs, "answers_read_at"));
	}

	private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
		java.sql.Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}
}
