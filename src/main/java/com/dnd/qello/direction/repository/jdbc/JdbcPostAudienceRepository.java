package com.dnd.qello.direction.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.repository.jdbc.sql.PostAudienceSql;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Repository
public class JdbcPostAudienceRepository implements PostAudienceRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final CoordinateProtector protector;

	@Override
	public PostAudience save(PostAudience audience) {
		byte[] ciphertext = audience.getOriginLatitude() == null
				? null
				: protector.protect(
						new Coordinate(audience.getOriginLatitude(), audience.getOriginLongitude()),
						CoordinatePurpose.POST_AUDIENCE, audience.getPostId());
		jdbc.update(PostAudienceSql.UPSERT, new MapSqlParameterSource().addValue("postId", audience.getPostId())
				.addValue("schemeId", audience.getDirectionSchemeId())
				.addValue("segmentKey", audience.getSelectedSegmentKey())
				.addValue("center", audience.getCenterBearingDegrees())
				.addValue("width", audience.getAngularWidthDegrees())
				.addValue("minDistance", audience.getMinDistanceMeters())
				.addValue("maxDistance", audience.getMaxDistanceMeters())
				.addValue("originPositionCiphertext", ciphertext, Types.BINARY)
				.addValue("originCellId", audience.getOriginCellId())
				.addValue("snapshottedAt", Timestamp.from(audience.getSnapshottedAt())));
		return audience;
	}

	@Override
	public Optional<PostAudience> findByPostId(long postId) {
		return jdbc.query("""
				SELECT post_id, direction_scheme_id, selected_segment_key, center_bearing_deg, angular_width_deg,
				       min_distance_m, max_distance_m, origin_position_ciphertext, origin_cell_id, snapshotted_at
				FROM post_audience WHERE post_id = :postId
				""", new MapSqlParameterSource("postId", postId),
				rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	private PostAudience map(ResultSet rs) throws SQLException {
		long postId = rs.getLong("post_id");
		byte[] ciphertext = rs.getBytes("origin_position_ciphertext");
		Coordinate origin = ciphertext == null
				? null
				: protector.decrypt(ciphertext, CoordinatePurpose.POST_AUDIENCE, postId);
		return PostAudience.restore(postId, rs.getLong("direction_scheme_id"), rs.getString("selected_segment_key"),
				rs.getBigDecimal("center_bearing_deg"), rs.getBigDecimal("angular_width_deg"),
				rs.getLong("min_distance_m"),
				rs.getLong("max_distance_m"), origin == null ? null : origin.latitude(),
				origin == null ? null : origin.longitude(),
				rs.getString("origin_cell_id"), rs.getTimestamp("snapshotted_at").toInstant());
	}
}
