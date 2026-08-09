package com.dnd.qello.direction.repository.jdbc.sql;

/**
 * JdbcDirectionSchemeRepository가 쓰는 SQL 상수.
 * SCHEME_*는 direction_scheme, SEGMENT_*는 direction_segment의 upsert 분기
 * 각 절반이다 — 분기 자체는 리포지토리에 남아 있다.
 */
public final class DirectionSchemeSql {

	private DirectionSchemeSql() {
	}

	public static final String SCHEME_INSERT = """
		INSERT INTO direction_scheme (code, version, type, segment_count, start_offset_deg, status)
		VALUES (:code, :version, :type, :segmentCount, :startOffset, :status)
		RETURNING id
		""";

	public static final String SCHEME_UPDATE = """
		UPDATE direction_scheme
	SET code = :code, version = :version, type = :type, segment_count = :segmentCount,
	    start_offset_deg = :startOffset, status = :status
	WHERE id = :id
	RETURNING id
		""";

	public static final String SEGMENT_INSERT = """
		INSERT INTO direction_segment (scheme_id, segment_key, display_name, center_bearing_deg,
			angular_width_deg, sort_order)
		VALUES (:schemeId, :segmentKey, :displayName, :center, :width, :sortOrder)
		RETURNING id
		""";

	public static final String SEGMENT_UPDATE = """
		UPDATE direction_segment
	SET scheme_id = :schemeId, segment_key = :segmentKey, display_name = :displayName,
	    center_bearing_deg = :center, angular_width_deg = :width, sort_order = :sortOrder
	WHERE id = :id
	RETURNING id
		""";
}
