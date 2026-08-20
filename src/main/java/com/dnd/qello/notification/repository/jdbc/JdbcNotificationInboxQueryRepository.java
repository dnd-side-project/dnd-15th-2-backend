package com.dnd.qello.notification.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.notification.repository.NotificationInboxQueryRepository;
import com.dnd.qello.notification.repository.jdbc.sql.NotificationInboxQuerySql;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationListing;
import com.dnd.qello.notification.view.NotificationTargetDecision;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JdbcNotificationInboxQueryRepository implements NotificationInboxQueryRepository {

	private final NamedParameterJdbcTemplate jdbc;

	@Override
	public NotificationListing list(long recipientId, NotificationListing.Cursor cursor, int limit, Instant at) {
		String sql = NotificationInboxQuerySql.SELECT_ROW
			+ "WHERE " + NotificationInboxQuerySql.LIST_STATUS_FILTER
			+ (cursor != null ? " AND " + NotificationInboxQuerySql.LIST_CURSOR_FILTER : "")
			+ "\n" + NotificationInboxQuerySql.LIST_ORDER_AND_LIMIT;
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("recipientId", recipientId)
			.addValue("at", Timestamp.from(at))
			.addValue("limit", limit);
		if (cursor != null) {
			params.addValue("cursorCreatedAt", Timestamp.from(cursor.createdAt()))
				.addValue("cursorNotificationId", cursor.notificationId());
		}
		List<NotificationCard> items = jdbc.query(sql, params, (rs, rowNum) -> NotificationRowMappers.card(rs));
		NotificationListing.Cursor nextCursor = items.size() == limit
			? new NotificationListing.Cursor(items.getLast().createdAt(), items.getLast().notificationId())
			: null;
		return new NotificationListing(items, nextCursor);
	}

	@Override
	public Optional<NotificationCard> findCard(long recipientId, long notificationId, Instant at) {
		return findOne(recipientId, notificationId, at, NotificationRowMappers::card);
	}

	@Override
	public Optional<NotificationTargetDecision> findTargetDecision(long recipientId, long notificationId, Instant at) {
		return findOne(recipientId, notificationId, at, NotificationRowMappers::targetDecision);
	}

	private <T> Optional<T> findOne(
		long recipientId, long notificationId, Instant at, ResultSetMapper<T> mapper) {
		String sql = NotificationInboxQuerySql.SELECT_ROW + "WHERE sub.notification_id = :notificationId\n";
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("recipientId", recipientId)
			.addValue("at", Timestamp.from(at))
			.addValue("notificationId", notificationId);
		return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty());
	}

	@FunctionalInterface
	private interface ResultSetMapper<T> {
		T map(ResultSet rs) throws SQLException;
	}

	@Override
	public long countUnread(long recipientId) {
		Long count = jdbc.queryForObject(NotificationInboxQuerySql.COUNT_UNREAD,
			new MapSqlParameterSource("recipientId", recipientId), Long.class);
		return count == null ? 0L : count;
	}

	@Override
	public boolean existsUnseen(long recipientId, Instant seenAt) {
		Boolean exists = jdbc.queryForObject(NotificationInboxQuerySql.EXISTS_UNSEEN,
			new MapSqlParameterSource()
				.addValue("recipientId", recipientId)
				.addValue("seenAt", seenAt == null ? null : Timestamp.from(seenAt)),
			Boolean.class);
		return Boolean.TRUE.equals(exists);
	}
}
