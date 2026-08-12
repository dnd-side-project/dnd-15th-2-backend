package com.dnd.qello.direction.repository.jdbc.sql;

/**
 * JdbcActiveUserPresenceRepository가 쓰는 SQL 상수.
 * UPSERT는 사용자별 최신 위치를 user_id 기준 ON CONFLICT로 갱신하고,
 * FIND_CANDIDATES_SQL은 PostGIS로 원점 기준 반경·방위 섹터 안에 있고 수신
 * 가능 상태인 사용자를 최근 수신 이력·거리 순으로 공정하게 조회한다.
 */
public final class ActiveUserPresenceSql {

	private ActiveUserPresenceSql() {
	}

	public static final String UPSERT = """
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

	// inbound_bearing_deg는 후보(수신자) 위치를 원점으로 계산한 역방위다. ST_Azimuth의
	// 두 인자 순서를 뒤집으면(origin, p.position 대신 p.position, origin) 구면 역방위를
	// 얻는다 — bearing_deg에 +180을 더하는 평면 근사와 다르다. post_recipient가 표시하는
	// 방향은 언제나 보는 사람 기준이라 이 값을 매칭 시점 스냅샷으로 함께 가져온다.
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
			  AND (:regionCode IS NULL OR p.coarse_region_code = :regionCode)
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
	 * 활성 scheme의 모든 segment를 기준으로 후보를 한 번에 집계한다.
	 * segment를 기준으로 LEFT JOIN하기 때문에 후보가 없는 방향도 결과에서 사라지지 않는다.
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
			  AND (:regionCode IS NULL OR p.coarse_region_code = :regionCode)
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
