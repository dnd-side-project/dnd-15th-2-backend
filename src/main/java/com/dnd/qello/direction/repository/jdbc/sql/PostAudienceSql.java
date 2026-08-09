package com.dnd.qello.direction.repository.jdbc.sql;

/** JdbcPostAudienceRepository가 쓰는 SQL 상수. UPSERT는 post_id 기준 ON CONFLICT 갱신이다. */
public final class PostAudienceSql {

	private PostAudienceSql() {
	}

	public static final String UPSERT = """
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
}
