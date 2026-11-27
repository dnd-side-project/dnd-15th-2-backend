/**
 * Created at: 2026-11-24T17:40:10Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-011 through INT-016
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository.DirectionSegmentCandidateCount;
import com.dnd.qello.direction.security.Coordinate;

final class CoordinatePostgisOracle {

	static final String BASELINE_COMMIT = "a910241e140071b3b34e9e05f461efdc7e43e24d";
	static final String BASELINE_SOURCE_SHA256 = "9026c39ce7a7a393b713a5d7835a56b2ad21d746b576fd0319a161c668bf65ae";
	private static final List<String> SNAPSHOT_TABLES = List.of("user_account", "recipient_receive_state",
			"user_block", "direction_scheme", "direction_segment");
	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate named;

	CoordinatePostgisOracle(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.named = new NamedParameterJdbcTemplate(jdbc);
	}

	void reset() {
		jdbc.execute("CREATE SCHEMA IF NOT EXISTS coordinate_oracle");
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS coordinate_oracle.active_user_presence (
					user_id bigint PRIMARY KEY, position geography(Point, 4326), coarse_region_code varchar,
					receive_allowed boolean, location_at timestamptz, expires_at timestamptz)
				""");
		jdbc.execute("TRUNCATE coordinate_oracle.active_user_presence");
	}

	void seed(ActiveUserPresence presence) {
		named.update("""
				INSERT INTO coordinate_oracle.active_user_presence
					(user_id, position, coarse_region_code, receive_allowed, location_at, expires_at)
				VALUES (:id, CASE WHEN :lat IS NULL THEN NULL ELSE
					ST_SetSRID(ST_MakePoint(:lon, :lat),4326)::geography END, :region, :allowed, :at, :expires)
				ON CONFLICT (user_id) DO UPDATE SET position = EXCLUDED.position,
					coarse_region_code = EXCLUDED.coarse_region_code, receive_allowed = EXCLUDED.receive_allowed,
					location_at = EXCLUDED.location_at, expires_at = EXCLUDED.expires_at
				""", new MapSqlParameterSource("id", presence.getUserId())
				.addValue("lat", presence.getLatitude(), Types.NUMERIC)
				.addValue("lon", presence.getLongitude(), Types.NUMERIC)
				.addValue("region", presence.getCoarseRegionCode()).addValue("allowed", presence.isReceiveAllowed())
				.addValue("at", Timestamp.from(presence.getLocationAt()))
				.addValue("expires", Timestamp.from(presence.getExpiresAt())));
	}

	void snapshot() {
		for (String table : SNAPSHOT_TABLES) {
			jdbc.execute("CREATE TABLE IF NOT EXISTS coordinate_oracle." + table
					+ " AS SELECT * FROM public." + table + " WITH NO DATA");
			jdbc.execute("TRUNCATE coordinate_oracle." + table);
			jdbc.execute("INSERT INTO coordinate_oracle." + table + " SELECT * FROM public." + table);
		}
	}

	List<DirectionCandidate> candidates(MapSqlParameterSource parameters) {
		return named.query(isolated(FIND_CANDIDATES_SQL), parameters,
				(rs, row) -> new DirectionCandidate(rs.getLong("user_id"), rs.getBigDecimal("distance_m"),
						rs.getBigDecimal("bearing_deg"), rs.getString("coarse_region_code"),
						rs.getBigDecimal("inbound_bearing_deg")));
	}

	List<DirectionSegmentCandidateCount> counts(MapSqlParameterSource parameters) {
		return named.query(isolated(FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL), parameters,
				(rs, row) -> new DirectionSegmentCandidateCount(rs.getString("segment_key"),
						rs.getLong("candidate_count")));
	}

	Coordinate project(Coordinate origin, double distance, double bearing) {
		return jdbc.queryForObject("""
				SELECT ST_Y(point::geometry) AS latitude, ST_X(point::geometry) AS longitude FROM
				(SELECT ST_Project(ST_SetSRID(ST_MakePoint(?, ?),4326)::geography, ?, radians(?)) AS point) p
				""", (rs, row) -> new Coordinate(rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude")),
				origin.longitude(), origin.latitude(), distance, bearing);
	}

	List<NumericResult> measurements(Coordinate origin) {
		return named.query("""
				WITH origin AS (SELECT ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography AS point)
				SELECT p.user_id, ST_Distance(origin.point,p.position) AS distance_m,
					degrees(ST_Azimuth(origin.point,p.position)) AS bearing_deg,
					degrees(ST_Azimuth(p.position,origin.point)) AS inbound_bearing_deg
				FROM coordinate_oracle.active_user_presence p CROSS JOIN origin WHERE p.position IS NOT NULL
				""", new MapSqlParameterSource("lat", origin.latitude()).addValue("lon", origin.longitude()),
				(rs, row) -> new NumericResult(rs.getLong("user_id"), rs.getBigDecimal("distance_m"),
						rs.getBigDecimal("bearing_deg"), rs.getBigDecimal("inbound_bearing_deg")));
	}

	private static String isolated(String sql) {
		String result = sql.replace("active_user_presence p", "coordinate_oracle.active_user_presence p");
		for (String table : SNAPSHOT_TABLES) {
			result = result.replace(table + " ", "coordinate_oracle." + table + " ");
		}
		return result;
	}

	record NumericResult(long userId, BigDecimal distance, BigDecimal bearing, BigDecimal inbound) {
	}

	Map<Long, Fairness> fairness() {
		return jdbc.query("""
				SELECT p.user_id, coalesce(r.recent_received_count, 0) AS received, r.last_received_at
				FROM coordinate_oracle.active_user_presence p
				LEFT JOIN coordinate_oracle.recipient_receive_state r ON r.user_id = p.user_id
				""", (rs, row) -> Map.entry(rs.getLong("user_id"),
				new Fairness(rs.getLong("received"), rs.getTimestamp("last_received_at"))))
				.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	record Fairness(long recentReceivedCount, Timestamp lastReceivedAt) {
	}

	public static final String FIND_CANDIDATES_SQL = """
			WITH origin AS (
				SELECT ST_SetSRID(ST_MakePoint(:originLongitude, :originLatitude), 4326)::geography AS point
			), candidates AS (
				SELECT p.user_id, p.coarse_region_code,
				       ST_Distance(p.position, origin.point) AS distance_m,
				       DEGREES(ST_Azimuth(origin.point, p.position)) AS bearing_deg,
				       DEGREES(ST_Azimuth(p.position, origin.point)) AS inbound_bearing_deg,
				       COALESCE(rrs.recent_received_count, 0) AS recent_received_count,
				       rrs.last_received_at
				FROM active_user_presence p
				JOIN user_account ua ON ua.id = p.user_id
				LEFT JOIN recipient_receive_state rrs ON rrs.user_id = p.user_id
				CROSS JOIN origin
				WHERE p.user_id <> :excludedUserId
				  AND ua.status = 'ACTIVE'
				  AND p.position IS NOT NULL
				  AND p.receive_allowed = TRUE
				  AND p.location_at <= :at
				  AND p.expires_at > :at
				  AND (CAST(:regionCode AS VARCHAR) IS NULL OR p.coarse_region_code = CAST(:regionCode AS VARCHAR))
				  AND ST_DWithin(p.position, origin.point, :maxDistanceMeters)
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = :excludedUserId
				                    AND ub.blocked_id = p.user_id
				                    AND ub.released_at IS NULL)
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = p.user_id
				                    AND ub.blocked_id = :excludedUserId
				                    AND ub.released_at IS NULL)
			)
			SELECT user_id, distance_m, bearing_deg, inbound_bearing_deg, coarse_region_code
			FROM candidates
			WHERE distance_m >= :minDistanceMeters
			  AND ((:sectorStartDegrees < :sectorEndDegrees AND bearing_deg >= :sectorStartDegrees AND bearing_deg < :sectorEndDegrees)
			    OR (:sectorStartDegrees >= :sectorEndDegrees AND (bearing_deg >= :sectorStartDegrees OR bearing_deg < :sectorEndDegrees)))
			ORDER BY recent_received_count, last_received_at NULLS FIRST, distance_m, user_id
			""";

	/**
	 * 활성 scheme의 모든 segment를 기준으로 후보를 한 번에 집계한다. segment를 기준으로 LEFT JOIN하기 때문에 후보가
	 * 없는 방향도 결과에서 사라지지 않는다.
	 */
	public static final String FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL = """
			WITH origin AS (
				SELECT ST_SetSRID(ST_MakePoint(:originLongitude, :originLatitude), 4326)::geography AS point
			), candidate_bearings AS (
				SELECT p.user_id,
				       ST_Distance(p.position, origin.point) AS distance_m,
				       DEGREES(ST_Azimuth(origin.point, p.position)) AS bearing_deg
				FROM active_user_presence p
				JOIN user_account ua ON ua.id = p.user_id
				CROSS JOIN origin
				WHERE p.user_id <> :excludedUserId
				  AND ua.status = 'ACTIVE'
				  AND p.position IS NOT NULL
				  AND p.receive_allowed = TRUE
				  AND p.location_at <= :at
				  AND p.expires_at > :at
				  AND (CAST(:regionCode AS VARCHAR) IS NULL OR p.coarse_region_code = CAST(:regionCode AS VARCHAR))
				  AND ST_DWithin(p.position, origin.point, :maxDistanceMeters)
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = :excludedUserId
				                    AND ub.blocked_id = p.user_id
				                    AND ub.released_at IS NULL)
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = p.user_id
				                    AND ub.blocked_id = :excludedUserId
				                    AND ub.released_at IS NULL)
			), eligible_candidates AS (
				SELECT user_id, distance_m, bearing_deg
				FROM candidate_bearings
				WHERE distance_m >= :minDistanceMeters
				  AND distance_m <= :maxDistanceMeters
			)
			SELECT ds.segment_key, COUNT(ec.user_id) AS candidate_count
			FROM direction_scheme scheme
			JOIN direction_segment ds ON ds.scheme_id = scheme.id
			CROSS JOIN LATERAL (
				SELECT MOD(ds.center_bearing_deg - ds.angular_width_deg / 2 + 360, 360) AS start_deg,
				       MOD(ds.center_bearing_deg + ds.angular_width_deg / 2 + 360, 360) AS end_deg
			) bounds
			LEFT JOIN eligible_candidates ec
				ON ((bounds.start_deg < bounds.end_deg
						AND ec.bearing_deg >= bounds.start_deg AND ec.bearing_deg < bounds.end_deg)
					OR (bounds.start_deg >= bounds.end_deg
						AND (ec.bearing_deg >= bounds.start_deg OR ec.bearing_deg < bounds.end_deg)))
			WHERE scheme.id = :schemeId
			  AND scheme.status = 'ACTIVE'
			GROUP BY ds.segment_key, ds.sort_order
			ORDER BY ds.sort_order
			""";
}
