package com.dnd.qello.filtering.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.filtering.domain.FilterReleaseGateState;
import com.dnd.qello.filtering.domain.FilterReleaseRetryGate;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;

// ADR-0002: SELECT ... FOR UPDATE와 조건부 존재 보장(INSERT ... ON CONFLICT DO
// NOTHING)이 계약인 연산이라 JPA가 아닌 JDBC로 구현한다.
@Repository
public class JdbcFilterReleaseRetryGateRepository implements FilterReleaseRetryGateRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcFilterReleaseRetryGateRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public FilterReleaseRetryGate findOrCreateForUpdate(long filterReleaseId, Instant now) {
		jdbc.update("""
			INSERT INTO filter_release_retry_gate
				(filter_release_id, state, current_limit, consecutive_failures, consecutive_successes, updated_at)
			VALUES (:releaseId, 'HEALTHY', NULL, 0, 0, :now)
			ON CONFLICT (filter_release_id) DO NOTHING
			""", new MapSqlParameterSource().addValue("releaseId", filterReleaseId).addValue("now", timestamp(now)));
		return jdbc.query(
			"SELECT * FROM filter_release_retry_gate WHERE filter_release_id = :releaseId FOR UPDATE",
			new MapSqlParameterSource("releaseId", filterReleaseId),
			rs -> {
				if (!rs.next()) {
					throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "filterReleaseId",
						"게이트 행을 잠그지 못했습니다");
				}
				return map(rs);
			});
	}

	@Override
	public FilterReleaseRetryGate save(FilterReleaseRetryGate gate) {
		jdbc.update("""
			UPDATE filter_release_retry_gate
			SET state = :state, current_limit = :currentLimit, consecutive_failures = :consecutiveFailures,
			    consecutive_successes = :consecutiveSuccesses, updated_at = :updatedAt
			WHERE filter_release_id = :releaseId
			""", params(gate));
		return gate;
	}

	private static MapSqlParameterSource params(FilterReleaseRetryGate gate) {
		return new MapSqlParameterSource()
			.addValue("releaseId", gate.filterReleaseId())
			.addValue("state", gate.state().name())
			.addValue("currentLimit", gate.currentLimit())
			.addValue("consecutiveFailures", gate.consecutiveFailures())
			.addValue("consecutiveSuccesses", gate.consecutiveSuccesses())
			.addValue("updatedAt", timestamp(gate.updatedAt()));
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static FilterReleaseRetryGate map(ResultSet rs) throws SQLException {
		// wasNull()은 직전 getX 호출을 기준으로 하므로, current_limit을 읽은 직후 바로
		// 확인해야 한다 — 이후 다른 컬럼을 먼저 읽으면 그 컬럼의 null 여부로 덮어써진다.
		int currentLimit = rs.getInt("current_limit");
		Integer currentLimitOrNull = rs.wasNull() ? null : currentLimit;
		return FilterReleaseRetryGate.restore(rs.getLong("filter_release_id"),
			FilterReleaseGateState.valueOf(rs.getString("state")), currentLimitOrNull,
			rs.getInt("consecutive_failures"), rs.getInt("consecutive_successes"),
			rs.getTimestamp("updated_at").toInstant());
	}
}
