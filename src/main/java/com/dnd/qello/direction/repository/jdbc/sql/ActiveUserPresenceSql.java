package com.dnd.qello.direction.repository.jdbc.sql;

public final class ActiveUserPresenceSql {

	private ActiveUserPresenceSql() {
	}

	public static final String UPSERT = """
			INSERT INTO active_user_presence
				(user_id, position_ciphertext, coarse_cell_id, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
			VALUES (:userId, :positionCiphertext,
				:coarseCellId, :regionCode, :accuracy, :receiveAllowed, :locationAt, :expiresAt)
			ON CONFLICT (user_id) DO UPDATE SET
				position_ciphertext = EXCLUDED.position_ciphertext,
				coarse_cell_id = EXCLUDED.coarse_cell_id,
				coarse_region_code = EXCLUDED.coarse_region_code,
				accuracy_m = EXCLUDED.accuracy_m,
				receive_allowed = EXCLUDED.receive_allowed,
				location_at = EXCLUDED.location_at,
				expires_at = EXCLUDED.expires_at
			""";

	public static final String UPSERT_IF_NEWER = UPSERT.stripTrailing() + """

			WHERE active_user_presence.location_at < EXCLUDED.location_at
			""";

	public static final String DELETE_BY_USER_ID = """
			DELETE FROM active_user_presence WHERE user_id = :userId
			""";

	public static final String FIND_CANDIDATES_SQL = """
				SELECT p.user_id, p.position_ciphertext, p.coarse_region_code,
				       COALESCE(rrs.recent_received_count, 0) AS recent_received_count,
				       rrs.last_received_at
				FROM active_user_presence p
				JOIN user_account ua ON ua.id = p.user_id
				LEFT JOIN recipient_receive_state rrs ON rrs.user_id = p.user_id
				WHERE p.user_id <> :excludedUserId
				  AND ua.status = 'ACTIVE'
				  AND p.position_ciphertext IS NOT NULL
				  AND p.receive_allowed = TRUE
				  AND p.location_at <= :at
				  AND p.expires_at > :at
				  AND (CAST(:regionCode AS VARCHAR) IS NULL OR p.coarse_region_code = CAST(:regionCode AS VARCHAR))
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = :excludedUserId
				                    AND ub.blocked_id = p.user_id
				                    AND ub.released_at IS NULL)
				  AND NOT EXISTS (SELECT 1 FROM user_block ub
				                  WHERE ub.blocker_id = p.user_id
				                    AND ub.blocked_id = :excludedUserId
				                    AND ub.released_at IS NULL)
			""";

	// UNION keeps candidate data and scheme bounds in one statement snapshot
	// without multiplying encrypted rows.
	public static final String FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL = "WITH candidates AS ("
			+ FIND_CANDIDATES_SQL + """
					)
					SELECT user_id, position_ciphertext, coarse_region_code, recent_received_count, last_received_at,
					       NULL::varchar AS segment_key, NULL::numeric AS start_deg, NULL::numeric AS end_deg,
					       NULL::integer AS sort_order
					FROM candidates
					UNION ALL
					SELECT NULL::bigint, NULL::bytea, NULL::varchar, NULL::integer, NULL::timestamptz,
					       ds.segment_key,
					       MOD(ds.center_bearing_deg - ds.angular_width_deg / 2 + 360, 360),
					       MOD(ds.center_bearing_deg + ds.angular_width_deg / 2 + 360, 360), ds.sort_order
					FROM direction_scheme scheme
					JOIN direction_segment ds ON ds.scheme_id = scheme.id
					WHERE scheme.id = :schemeId
					  AND scheme.status = 'ACTIVE'
					ORDER BY sort_order NULLS LAST
					""";
}
