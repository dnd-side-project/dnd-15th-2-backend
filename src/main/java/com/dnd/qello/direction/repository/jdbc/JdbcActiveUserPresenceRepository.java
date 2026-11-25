package com.dnd.qello.direction.repository.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;
import com.dnd.qello.direction.geo.CoordinateGeometry;
import com.dnd.qello.direction.geo.RankedDirectionCandidate;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.jdbc.sql.ActiveUserPresenceSql;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Repository
public class JdbcActiveUserPresenceRepository implements ActiveUserPresenceRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final CoordinateProtector protector;

	@Override
	public ActiveUserPresence save(ActiveUserPresence presence) {
		jdbc.update(ActiveUserPresenceSql.UPSERT, parameters(presence));
		return presence;
	}

	@Override
	public boolean saveIfNewer(ActiveUserPresence presence) {
		return jdbc.update(ActiveUserPresenceSql.UPSERT_IF_NEWER, parameters(presence)) == 1;
	}

	@Override
	public boolean deleteByUserId(long userId) {
		return jdbc.update(ActiveUserPresenceSql.DELETE_BY_USER_ID, new MapSqlParameterSource("userId", userId)) == 1;
	}

	@Override
	public Optional<ActiveUserPresence> findByUserId(long userId) {
		return jdbc.query("""
				SELECT user_id, position_ciphertext,
				       coarse_cell_id, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at
				FROM active_user_presence WHERE user_id = :userId
				""", new MapSqlParameterSource("userId", userId),
				rs -> rs.next() ? Optional.of(mapPresence(rs)) : Optional.empty());
	}

	@Override
	public List<DirectionCandidate> findCandidates(long excludedUserId, double originLatitude, double originLongitude,
			long minDistanceMeters, long maxDistanceMeters, double sectorStartDegrees, double sectorEndDegrees,
			Instant at, String regionCode) {
		Coordinate origin = new Coordinate(BigDecimal.valueOf(originLatitude), BigDecimal.valueOf(originLongitude));
		List<RankedDirectionCandidate> candidates = jdbc.query(ActiveUserPresenceSql.FIND_CANDIDATES_SQL,
				queryParameters(excludedUserId, at, regionCode), rs -> {
					List<RankedDirectionCandidate> result = new ArrayList<>();
					while (rs.next()) {
						candidate(rs, origin).filter(value -> withinRange(value.candidate(),
								minDistanceMeters, maxDistanceMeters))
								.filter(value -> CoordinateGeometry.containsBearing(
										value.candidate().bearingDegrees().doubleValue(),
										sectorStartDegrees, sectorEndDegrees))
								.ifPresent(result::add);
					}
					return result;
				});
		return candidates.stream().sorted(RankedDirectionCandidate.FAIRNESS_ORDER)
				.map(RankedDirectionCandidate::candidate).toList();
	}

	@Override
	public List<DirectionSegmentCandidateCount> findCandidateCountsBySegment(long schemeId, long excludedUserId,
			double originLatitude, double originLongitude, long minDistanceMeters, long maxDistanceMeters,
			Instant at, String regionCode) {
		Coordinate origin = new Coordinate(BigDecimal.valueOf(originLatitude), BigDecimal.valueOf(originLongitude));
		return jdbc.query(ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL,
				queryParameters(excludedUserId, at, regionCode).addValue("schemeId", schemeId),
				rs -> {
					return countSegments(rs, origin, minDistanceMeters, maxDistanceMeters);
				});
	}

	private List<DirectionSegmentCandidateCount> countSegments(ResultSet rs, Coordinate origin, long min, long max)
			throws SQLException {
		List<DirectionCandidate> candidates = new ArrayList<>();
		List<SegmentBounds> segments = new ArrayList<>();
		while (rs.next()) {
			if (rs.getString("segment_key") != null) {
				segments.add(new SegmentBounds(rs.getString("segment_key"),
						rs.getDouble("start_deg"), rs.getDouble("end_deg")));
			} else {
				candidate(rs, origin).map(RankedDirectionCandidate::candidate)
						.filter(value -> withinRange(value, min, max)).ifPresent(candidates::add);
			}
		}
		return segments.stream().map(segment -> new DirectionSegmentCandidateCount(segment.key(),
				candidates.stream().filter(value -> CoordinateGeometry.containsBearing(
						value.bearingDegrees().doubleValue(), segment.start(), segment.end())).count()))
				.toList();
	}

	private Optional<RankedDirectionCandidate> candidate(ResultSet rs, Coordinate origin) throws SQLException {
		long userId = rs.getLong("user_id");
		Coordinate position = protector.decrypt(rs.getBytes("position_ciphertext"),
				CoordinatePurpose.ACTIVE_USER_PRESENCE, userId);
		long recent = rs.getLong("recent_received_count");
		Timestamp last = rs.getTimestamp("last_received_at");
		String region = rs.getString("coarse_region_code");
		return CoordinateGeometry.inverse(origin, position).map(value -> new RankedDirectionCandidate(
				new DirectionCandidate(userId, BigDecimal.valueOf(value.distanceMeters()),
						BigDecimal.valueOf(value.bearingDegrees()), region,
						BigDecimal.valueOf(value.inboundBearingDegrees())),
				recent, last == null ? null : last.toInstant()));
	}

	private static boolean withinRange(DirectionCandidate candidate, long min, long max) {
		double distance = candidate.distanceMeters().doubleValue();
		return distance >= min && distance <= max;
	}

	private static MapSqlParameterSource queryParameters(long userId, Instant at, String region) {
		return new MapSqlParameterSource("excludedUserId", userId)
				.addValue("at", Timestamp.from(at)).addValue("regionCode", region);
	}

	private MapSqlParameterSource parameters(ActiveUserPresence p) {
		byte[] ciphertext = p.getLatitude() == null
				? null
				: protector.protect(
						new Coordinate(p.getLatitude(), p.getLongitude()), CoordinatePurpose.ACTIVE_USER_PRESENCE,
						p.getUserId());
		return new MapSqlParameterSource().addValue("userId", p.getUserId())
				.addValue("positionCiphertext", ciphertext, Types.BINARY)
				.addValue("coarseCellId", p.getCoarseCellId()).addValue("regionCode", p.getCoarseRegionCode())
				.addValue("accuracy", p.getAccuracyMeters()).addValue("receiveAllowed", p.isReceiveAllowed())
				.addValue("locationAt", timestamp(p.getLocationAt()))
				.addValue("expiresAt", timestamp(p.getExpiresAt()));
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private ActiveUserPresence mapPresence(ResultSet rs) throws SQLException {
		long userId = rs.getLong("user_id");
		byte[] ciphertext = rs.getBytes("position_ciphertext");
		Coordinate position = ciphertext == null
				? null
				: protector.decrypt(ciphertext, CoordinatePurpose.ACTIVE_USER_PRESENCE, userId);
		return ActiveUserPresence.create(userId, position == null ? null : position.latitude(),
				position == null ? null : position.longitude(),
				rs.getString("coarse_cell_id"), rs.getString("coarse_region_code"), rs.getBigDecimal("accuracy_m"),
				rs.getBoolean("receive_allowed"), rs.getTimestamp("location_at").toInstant(),
				rs.getTimestamp("expires_at").toInstant());
	}

	private record SegmentBounds(String key, double start, double end) {
	}
}
