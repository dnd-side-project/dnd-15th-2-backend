package com.dnd.qello.direction.repository.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;

@Repository
public class JdbcActiveUserPresenceRepository implements ActiveUserPresenceRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcActiveUserPresenceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public ActiveUserPresence save(ActiveUserPresence presence) {
		jdbc.update(ActiveUserPresenceSql.UPSERT, parameters(presence));
		return presence;
	}

	@Override
	public Optional<ActiveUserPresence> findByUserId(long userId) {
		return jdbc.query("""
			SELECT user_id, ST_Y(position::geometry) AS latitude, ST_X(position::geometry) AS longitude,
			       coarse_cell_id, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at
			FROM active_user_presence WHERE user_id = :userId
			""", new MapSqlParameterSource("userId", userId), rs -> rs.next() ? Optional.of(mapPresence(rs)) : Optional.empty());
	}

	@Override
	public List<DirectionCandidate> findCandidates(long excludedUserId, double originLatitude, double originLongitude,
		long minDistanceMeters, long maxDistanceMeters, double sectorStartDegrees, double sectorEndDegrees,
		Instant at, String regionCode) {
		String sql = ActiveUserPresenceSql.FIND_CANDIDATES_SQL;
		MapSqlParameterSource p = new MapSqlParameterSource()
			.addValue("excludedUserId", excludedUserId).addValue("originLatitude", originLatitude)
			.addValue("originLongitude", originLongitude).addValue("minDistanceMeters", minDistanceMeters)
			.addValue("maxDistanceMeters", maxDistanceMeters).addValue("sectorStartDegrees", sectorStartDegrees)
			.addValue("sectorEndDegrees", sectorEndDegrees).addValue("at", Timestamp.from(at)).addValue("regionCode", regionCode);
		return jdbc.query(sql, p, (rs, rowNum) -> new DirectionCandidate(rs.getLong("user_id"),
			rs.getBigDecimal("distance_m"), rs.getBigDecimal("bearing_deg"), rs.getString("coarse_region_code"),
			rs.getBigDecimal("inbound_bearing_deg")));
	}

	@Override
	public List<DirectionSegmentCandidateCount> findCandidateCountsBySegment(long schemeId, long excludedUserId,
		double originLatitude, double originLongitude, long minDistanceMeters, long maxDistanceMeters,
		Instant at, String regionCode) {
		MapSqlParameterSource p = new MapSqlParameterSource()
			.addValue("schemeId", schemeId).addValue("excludedUserId", excludedUserId)
			.addValue("originLatitude", originLatitude).addValue("originLongitude", originLongitude)
			.addValue("minDistanceMeters", minDistanceMeters).addValue("maxDistanceMeters", maxDistanceMeters)
			.addValue("at", Timestamp.from(at)).addValue("regionCode", regionCode);
		return jdbc.query(ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL, p,
			(rs, rowNum) -> new DirectionSegmentCandidateCount(rs.getString("segment_key"), rs.getLong("candidate_count")));
	}

	private static MapSqlParameterSource parameters(ActiveUserPresence p) {
		return new MapSqlParameterSource().addValue("userId", p.getUserId())
			.addValue("latitude", p.getLatitude()).addValue("longitude", p.getLongitude())
			.addValue("coarseCellId", p.getCoarseCellId()).addValue("regionCode", p.getCoarseRegionCode())
			.addValue("accuracy", p.getAccuracyMeters()).addValue("receiveAllowed", p.isReceiveAllowed())
			.addValue("locationAt", timestamp(p.getLocationAt())).addValue("expiresAt", timestamp(p.getExpiresAt()));
	}

	private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

	private static ActiveUserPresence mapPresence(ResultSet rs) throws SQLException {
		BigDecimal latitude = rs.getBigDecimal("latitude");
		BigDecimal longitude = rs.getBigDecimal("longitude");
		return ActiveUserPresence.create(rs.getLong("user_id"), latitude, longitude,
			rs.getString("coarse_cell_id"), rs.getString("coarse_region_code"), rs.getBigDecimal("accuracy_m"),
			rs.getBoolean("receive_allowed"), rs.getTimestamp("location_at").toInstant(), rs.getTimestamp("expires_at").toInstant());
	}
}
