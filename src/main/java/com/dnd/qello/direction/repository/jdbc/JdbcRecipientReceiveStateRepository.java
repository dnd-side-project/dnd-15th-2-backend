package com.dnd.qello.direction.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.RecipientReceiveState;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcRecipientReceiveStateRepository implements RecipientReceiveStateRepository {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public RecipientReceiveState save(RecipientReceiveState state) {
		jdbc.update("""
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
			VALUES (:userId, :activeCount, :recentCount, :windowStartedAt, :lastReceivedAt, COALESCE(:updatedAt, clock_timestamp()))
			ON CONFLICT (user_id) DO UPDATE SET
				active_unhandled_count = EXCLUDED.active_unhandled_count,
				recent_received_count = EXCLUDED.recent_received_count,
				recent_window_started_at = EXCLUDED.recent_window_started_at,
				last_received_at = EXCLUDED.last_received_at,
				updated_at = EXCLUDED.updated_at
			""", new MapSqlParameterSource().addValue("userId", state.getUserId())
			.addValue("activeCount", state.getActiveUnhandledCount()).addValue("recentCount", state.getRecentReceivedCount())
			.addValue("windowStartedAt", timestamp(state.getRecentWindowStartedAt())).addValue("lastReceivedAt", timestamp(state.getLastReceivedAt()))
			.addValue("updatedAt", timestamp(state.getUpdatedAt())));
		return state;
	}

	@Override
	public Optional<RecipientReceiveState> findByUserId(long userId) {
		return jdbc.query("SELECT * FROM recipient_receive_state WHERE user_id = :userId",
			new MapSqlParameterSource("userId", userId), rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
	}

	@Override
	public boolean reserve(long userId, Instant receivedAt, int activeLimit) {
		int updated = jdbc.update("""
			UPDATE recipient_receive_state
			SET active_unhandled_count = active_unhandled_count + 1,
			    recent_received_count = recent_received_count + 1,
			    last_received_at = :receivedAt,
			    updated_at = clock_timestamp()
			WHERE user_id = :userId AND active_unhandled_count < :activeLimit
			""", new MapSqlParameterSource().addValue("userId", userId)
			.addValue("receivedAt", timestamp(receivedAt)).addValue("activeLimit", activeLimit));
		return updated == 1;
	}

	/**
	 * 활성 미처리 카운터를 1 줄인다. 이미 0이면 아무것도 하지 않고 false를 반환한다 —
	 * 답변 발행이 재시도되거나 두 경로가 동시에 슬롯을 반환하려 할 때 카운터가
	 * 음수로 내려가지 않도록 막는 안전장치다. 반환값(성공한 행 수)으로 실제 반영
	 * 여부를 판단하며, 예외를 던지지 않는다.
	 */
	@Override
	public boolean release(long userId, Instant releasedAt) {
		int updated = jdbc.update("""
			UPDATE recipient_receive_state
			SET active_unhandled_count = active_unhandled_count - 1,
			    updated_at = :releasedAt
			WHERE user_id = :userId AND active_unhandled_count > 0
			""", new MapSqlParameterSource().addValue("userId", userId)
			.addValue("releasedAt", timestamp(releasedAt)));
		return updated == 1;
	}

	private static RecipientReceiveState map(ResultSet rs) throws SQLException {
		return RecipientReceiveState.restore(rs.getLong("user_id"), rs.getInt("active_unhandled_count"),
			rs.getInt("recent_received_count"), rs.getTimestamp("recent_window_started_at").toInstant(),
			rs.getTimestamp("last_received_at") == null ? null : rs.getTimestamp("last_received_at").toInstant(),
			rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
	}

	private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
}
