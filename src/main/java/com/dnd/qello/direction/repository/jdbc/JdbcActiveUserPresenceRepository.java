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

@Repository
public class JdbcActiveUserPresenceRepository implements ActiveUserPresenceRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcActiveUserPresenceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public ActiveUserPresence save(ActiveUserPresence presence) {
		String sql = """
			INSERT INTO active_user_presence
				(user_id, position, coarse_cell_id, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
			VALUES (:userId,
				CASE WHEN :latitude IS NULL OR :longitude IS NULL THEN NULL
				     ELSE ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography END,
				:coarseCellId, :regionCode, :accuracy, :receiveAllowed, :locationAt, :expiresAt)
			ON CONFLICT (user_id) DO UPDATE SET
				position = EXCLUDED.position,
				coarse_cell_id = EXCLUDED.coarse_cell_id,
				coarse_region_code = EXCLUDED.coarse_region_code,
				accuracy_m = EXCLUDED.accuracy_m,
				receive_allowed = EXCLUDED.receive_allowed,
				location_at = EXCLUDED.location_at,
				expires_at = EXCLUDED.expires_at
			""";
		jdbc.update(sql, parameters(presence));
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
		// inbound_bearing_deg는 후보(수신자) 위치를 원점으로 계산한 역방위다. ST_Azimuth의
		// 두 인자 순서를 뒤집으면(origin, p.position 대신 p.position, origin) 구면 역방위를
		// 얻는다 — bearing_deg에 +180을 더하는 평면 근사와 다르다. post_recipient가 표시하는
		// 방향은 언제나 보는 사람 기준이라 이 값을 매칭 시점 스냅샷으로 함께 가져온다.
		String sql = """
			WITH origin AS (
				SELECT ST_SetSRID(ST_MakePoint(:originLongitude, :originLatitude), 4326)::geography AS point
			), candidates AS (
				SELECT p.user_id, p.coarse_region_code,
				       ST_Distance(p.position, origin.point) AS distance_m,
				       DEGREES(ST_Azimuth(origin.point, p.position)) AS bearing_deg,
				       DEGREES(ST_Azimuth(p.position, origin.point)) AS inbound_bearing_deg
				FROM active_user_presence p CROSS JOIN origin
				WHERE p.user_id <> :excludedUserId
				  AND p.position IS NOT NULL
				  AND p.receive_allowed = TRUE
				  AND p.expires_at > :at
				  AND (:regionCode IS NULL OR p.coarse_region_code = :regionCode)
				  AND ST_DWithin(p.position, origin.point, :maxDistanceMeters)
			)
			SELECT user_id, distance_m, bearing_deg, inbound_bearing_deg, coarse_region_code
			FROM candidates
			WHERE distance_m >= :minDistanceMeters
			  AND ((:sectorStartDegrees < :sectorEndDegrees AND bearing_deg >= :sectorStartDegrees AND bearing_deg < :sectorEndDegrees)
			    OR (:sectorStartDegrees >= :sectorEndDegrees AND (bearing_deg >= :sectorStartDegrees OR bearing_deg < :sectorEndDegrees)))
			ORDER BY distance_m, user_id
			""";
		MapSqlParameterSource p = new MapSqlParameterSource()
			.addValue("excludedUserId", excludedUserId).addValue("originLatitude", originLatitude)
			.addValue("originLongitude", originLongitude).addValue("minDistanceMeters", minDistanceMeters)
			.addValue("maxDistanceMeters", maxDistanceMeters).addValue("sectorStartDegrees", sectorStartDegrees)
			.addValue("sectorEndDegrees", sectorEndDegrees).addValue("at", Timestamp.from(at)).addValue("regionCode", regionCode);
		return jdbc.query(sql, p, (rs, rowNum) -> new DirectionCandidate(rs.getLong("user_id"),
			rs.getBigDecimal("distance_m"), rs.getBigDecimal("bearing_deg"), rs.getString("coarse_region_code"),
			rs.getBigDecimal("inbound_bearing_deg")));
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
