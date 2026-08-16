package com.dnd.qello.filtering.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthStatus;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.SnapshotHealthRepository;

// ADR-0002: SELECT ... FOR UPDATE와 조건부 존재 보장(INSERT ... ON CONFLICT DO
// NOTHING)이 계약인 연산이라 JPA가 아닌 JDBC로 구현한다(JdbcFilterReleaseRetryGateRepository,
// #108과 동일 패턴).
@Repository
public class JdbcSnapshotHealthRepository implements SnapshotHealthRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcSnapshotHealthRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public SnapshotHealth findOrCreateForUpdate(String modelSnapshot, Instant now) {
		jdbc.update("""
			INSERT INTO snapshot_health
				(model_snapshot, status, target_only_failure_count, first_target_only_failure_at,
				 last_target_only_failure_at, official_announcement, confirmed_at,
				 confirmed_by_operator_user_id, updated_at)
			VALUES (:modelSnapshot, 'HEALTHY', 0, NULL, NULL, FALSE, NULL, NULL, :now)
			ON CONFLICT (model_snapshot) DO NOTHING
			""", new MapSqlParameterSource().addValue("modelSnapshot", modelSnapshot).addValue("now", timestamp(now)));
		return jdbc.query(
			"SELECT * FROM snapshot_health WHERE model_snapshot = :modelSnapshot FOR UPDATE",
			new MapSqlParameterSource("modelSnapshot", modelSnapshot),
			rs -> {
				if (!rs.next()) {
					throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "modelSnapshot",
						"snapshot health 행을 잠그지 못했습니다");
				}
				return map(rs);
			});
	}

	@Override
	public SnapshotHealth save(SnapshotHealth health) {
		jdbc.update("""
			UPDATE snapshot_health
			SET status = :status, target_only_failure_count = :targetOnlyFailureCount,
			    first_target_only_failure_at = :firstTargetOnlyFailureAt,
			    last_target_only_failure_at = :lastTargetOnlyFailureAt,
			    official_announcement = :officialAnnouncement, confirmed_at = :confirmedAt,
			    confirmed_by_operator_user_id = :confirmedByOperatorUserId, updated_at = :updatedAt
			WHERE model_snapshot = :modelSnapshot
			""", params(health));
		return health;
	}

	private static MapSqlParameterSource params(SnapshotHealth health) {
		return new MapSqlParameterSource()
			.addValue("modelSnapshot", health.modelSnapshot())
			.addValue("status", health.status().name())
			.addValue("targetOnlyFailureCount", health.targetOnlyFailureCount())
			.addValue("firstTargetOnlyFailureAt", timestamp(health.firstTargetOnlyFailureAt()))
			.addValue("lastTargetOnlyFailureAt", timestamp(health.lastTargetOnlyFailureAt()))
			.addValue("officialAnnouncement", health.officialAnnouncement())
			.addValue("confirmedAt", timestamp(health.confirmedAt()))
			.addValue("confirmedByOperatorUserId", health.confirmedByOperatorUserId())
			.addValue("updatedAt", timestamp(health.updatedAt()));
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static SnapshotHealth map(ResultSet rs) throws SQLException {
		Timestamp firstFailure = rs.getTimestamp("first_target_only_failure_at");
		Timestamp lastFailure = rs.getTimestamp("last_target_only_failure_at");
		Timestamp confirmedAt = rs.getTimestamp("confirmed_at");
		// wasNull()은 직전 getX 호출을 기준으로 하므로, confirmed_by_operator_user_id를
		// 읽은 직후 바로 확인해야 한다.
		long confirmedByOperatorUserId = rs.getLong("confirmed_by_operator_user_id");
		Long confirmedByOperatorUserIdOrNull = rs.wasNull() ? null : confirmedByOperatorUserId;
		return SnapshotHealth.restore(
			rs.getString("model_snapshot"),
			SnapshotHealthStatus.valueOf(rs.getString("status")),
			rs.getInt("target_only_failure_count"),
			firstFailure == null ? null : firstFailure.toInstant(),
			lastFailure == null ? null : lastFailure.toInstant(),
			rs.getBoolean("official_announcement"),
			confirmedAt == null ? null : confirmedAt.toInstant(),
			confirmedByOperatorUserIdOrNull,
			rs.getTimestamp("updated_at").toInstant());
	}
}
