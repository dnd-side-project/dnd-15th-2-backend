package com.dnd.qello.notification.repository.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.notification.repository.NotificationSeenStateRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcNotificationSeenStateRepository implements NotificationSeenStateRepository {

	private static final String FIND_SEEN_AT = """
		SELECT seen_at FROM notification_seen_state WHERE user_id = :userId
		""";

	/**
	 * GREATEST 단일 문장으로 읽고-비교하고-쓰는 세 단계를 없앤다 — 그렇지 않으면 동시
	 * 호출에서 경합이 생긴다. INSERT ... ON CONFLICT는 (user_id) 하나뿐인 PK 행을
	 * 원자적으로 잠그고 갱신한다.
	 */
	private static final String ADVANCE_SEEN_AT = """
		INSERT INTO notification_seen_state (user_id, seen_at)
		VALUES (:userId, :at)
		ON CONFLICT (user_id) DO UPDATE
		    SET seen_at = GREATEST(EXCLUDED.seen_at, notification_seen_state.seen_at)
		RETURNING seen_at
		""";

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public Optional<Instant> findSeenAt(long userId) {
		return jdbc.query(FIND_SEEN_AT, new MapSqlParameterSource("userId", userId),
			rs -> rs.next() ? Optional.of(rs.getTimestamp("seen_at").toInstant()) : Optional.empty());
	}

	@Override
	public Instant advance(long userId, Instant at) {
		Timestamp result = jdbc.queryForObject(ADVANCE_SEEN_AT,
			new MapSqlParameterSource().addValue("userId", userId).addValue("at", Timestamp.from(at)),
			Timestamp.class);
		return result.toInstant();
	}
}
