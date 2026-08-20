package com.dnd.qello.notification.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.view.NotificationCard;
import com.dnd.qello.notification.view.NotificationTargetDecision;
import com.dnd.qello.notification.view.NotificationTargetKind;
import com.dnd.qello.notification.view.NotificationTargetState;

/** JdbcNotificationInboxQueryRepository가 공유하는 ResultSet 변환 헬퍼. */
final class NotificationRowMappers {

	private NotificationRowMappers() {
	}

	static NotificationCard card(ResultSet rs) throws SQLException {
		NotificationTargetKind kind = NotificationTargetKind.valueOf(rs.getString("target_kind"));
		return new NotificationCard(
			rs.getLong("notification_id"),
			NotificationType.valueOf(rs.getString("notification_type")),
			instant(rs, "created_at"),
			instant(rs, "read_at"),
			NotificationStatus.valueOf(rs.getString("status")) == NotificationStatus.UNREAD,
			kind,
			nullableLong(rs, "target_id"),
			nullableTargetState(rs, "target_state"),
			instant(rs, "expires_at"));
	}

	static NotificationTargetDecision targetDecision(ResultSet rs) throws SQLException {
		NotificationTargetKind kind = NotificationTargetKind.valueOf(rs.getString("target_kind"));
		return new NotificationTargetDecision(
			kind, nullableLong(rs, "target_id"), nullableTargetState(rs, "target_state"));
	}

	static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static NotificationTargetState nullableTargetState(ResultSet rs, String column) throws SQLException {
		String value = rs.getString(column);
		return value == null ? null : NotificationTargetState.valueOf(value);
	}
}
