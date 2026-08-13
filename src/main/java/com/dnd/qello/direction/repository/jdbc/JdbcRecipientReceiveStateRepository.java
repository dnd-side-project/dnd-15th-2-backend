package com.dnd.qello.direction.repository.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.direction.domain.RecipientReceiveState;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository.LockCandidate;
import com.dnd.qello.direction.repository.jdbc.sql.RecipientReceiveStateSql;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcRecipientReceiveStateRepository implements RecipientReceiveStateRepository {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public RecipientReceiveState save(RecipientReceiveState state) {
		jdbc.update(RecipientReceiveStateSql.SAVE, new MapSqlParameterSource().addValue("userId", state.getUserId())
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
		int updated = jdbc.update(RecipientReceiveStateSql.RESERVE, new MapSqlParameterSource().addValue("userId", userId)
			.addValue("receivedAt", timestamp(receivedAt)).addValue("activeLimit", activeLimit));
		return updated == 1;
	}

	@Override
	public boolean release(long userId, Instant releasedAt) {
		int updated = jdbc.update(RecipientReceiveStateSql.RELEASE, new MapSqlParameterSource().addValue("userId", userId)
			.addValue("releasedAt", timestamp(releasedAt)));
		return updated == 1;
	}

	@Override
	public void ensureForUsers(List<Long> userIds, Instant at) {
		RecipientReceiveStateRepository.super.ensureForUsers(userIds, at);
		for (Long userId : userIds) {
			jdbc.update(RecipientReceiveStateSql.ENSURE, new MapSqlParameterSource()
				.addValue("userId", userId).addValue("at", timestamp(at)));
		}
	}

	@Override
	public List<Long> lockAvailableUserIds(List<LockCandidate> candidates, int limit, int activeLimit) {
		if (candidates == null || candidates.isEmpty() || limit <= 0 || activeLimit <= 0) return List.of();
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("lockLimit", limit).addValue("activeLimit", activeLimit);
		StringJoiner values = new StringJoiner(", ");
		for (int index = 0; index < candidates.size(); index++) {
			LockCandidate candidate = candidates.get(index);
			if (candidate == null) {
				throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING,
					"candidate", "candidate는 필수입니다");
			}
			String userIdParameter = "candidateUserId" + index;
			String distanceParameter = "candidateDistance" + index;
			values.add("(:" + userIdParameter + ", :" + distanceParameter + ")");
			parameters.addValue(userIdParameter, candidate.userId());
			parameters.addValue(distanceParameter, candidate.distanceMeters());
		}
		return jdbc.query(RecipientReceiveStateSql.lockAvailable(values.toString()), parameters,
			(rs, rowNum) -> rs.getLong("user_id"));
	}

	private static RecipientReceiveState map(ResultSet rs) throws SQLException {
		return RecipientReceiveState.restore(rs.getLong("user_id"), rs.getInt("active_unhandled_count"),
			rs.getInt("recent_received_count"), rs.getTimestamp("recent_window_started_at").toInstant(),
			rs.getTimestamp("last_received_at") == null ? null : rs.getTimestamp("last_received_at").toInstant(),
			rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
	}

	private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
}
