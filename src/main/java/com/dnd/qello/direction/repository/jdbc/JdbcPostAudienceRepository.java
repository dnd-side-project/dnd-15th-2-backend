package com.dnd.qello.direction.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.repository.PostAudienceRepository;

@Repository
public class JdbcPostAudienceRepository implements PostAudienceRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcPostAudienceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public PostAudience save(PostAudience audience) {
		String sql = """
			INSERT INTO post_audience
				(post_id, direction_scheme_id, selected_segment_key, center_bearing_deg, angular_width_deg,
				 min_distance_m, max_distance_m, origin_position, origin_cell_id, snapshotted_at)
			VALUES (:postId, :schemeId, :segmentKey, :center, :width, :minDistance, :maxDistance,
				CASE WHEN :latitude IS NULL OR :longitude IS NULL THEN NULL
				     ELSE ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography END,
				:originCellId, :snapshottedAt)
			ON CONFLICT (post_id) DO UPDATE SET
				direction_scheme_id = EXCLUDED.direction_scheme_id,
				selected_segment_key = EXCLUDED.selected_segment_key,
				center_bearing_deg = EXCLUDED.center_bearing_deg,
				angular_width_deg = EXCLUDED.angular_width_deg,
				min_distance_m = EXCLUDED.min_distance_m,
				max_distance_m = EXCLUDED.max_distance_m,
				origin_position = EXCLUDED.origin_position,
				origin_cell_id = EXCLUDED.origin_cell_id,
				snapshotted_at = EXCLUDED.snapshotted_at
			""";
		jdbc.update(sql, new MapSqlParameterSource().addValue("postId", audience.getPostId())
			.addValue("schemeId", audience.getDirectionSchemeId()).addValue("segmentKey", audience.getSelectedSegmentKey())
			.addValue("center", audience.getCenterBearingDegrees()).addValue("width", audience.getAngularWidthDegrees())
			.addValue("minDistance", audience.getMinDistanceMeters()).addValue("maxDistance", audience.getMaxDistanceMeters())
			.addValue("latitude", audience.getOriginLatitude()).addValue("longitude", audience.getOriginLongitude())
			.addValue("originCellId", audience.getOriginCellId()).addValue("snapshottedAt", Timestamp.from(audience.getSnapshottedAt())));
		return audience;
	}

	@Override
	public Optional<PostAudience> findByPostId(long postId) {
		return jdbc.query("""
			SELECT post_id, direction_scheme_id, selected_segment_key, center_bearing_deg, angular_width_deg,
			       min_distance_m, max_distance_m, ST_Y(origin_position::geometry) AS latitude,
			       ST_X(origin_position::geometry) AS longitude, origin_cell_id, snapshotted_at
			FROM post_audience WHERE post_id = :postId
			""", new MapSqlParameterSource("postId", postId), rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	private static PostAudience map(ResultSet rs) throws SQLException {
		return PostAudience.create(rs.getLong("post_id"), rs.getLong("direction_scheme_id"), rs.getString("selected_segment_key"),
			rs.getBigDecimal("center_bearing_deg"), rs.getBigDecimal("angular_width_deg"), rs.getLong("min_distance_m"),
			rs.getLong("max_distance_m"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
			rs.getString("origin_cell_id"), rs.getTimestamp("snapshotted_at").toInstant());
	}
}
