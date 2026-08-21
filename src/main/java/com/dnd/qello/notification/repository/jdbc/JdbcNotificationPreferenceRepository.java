package com.dnd.qello.notification.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.notification.domain.NotificationPreferenceSnapshot;
import com.dnd.qello.notification.domain.NotificationQuietHours;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.jdbc.sql.NotificationPreferenceSql;

@Repository
public class JdbcNotificationPreferenceRepository implements NotificationPreferenceRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcNotificationPreferenceRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public NotificationPreferenceSnapshot findByUserId(long userId) {
		List<PreferenceRow> rows = jdbc.query(NotificationPreferenceSql.FIND_PREFERENCE_SNAPSHOT,
			new MapSqlParameterSource("userId", userId), (rs, rowNum) -> mapPreferenceRow(rs));
		EnumMap<NotificationType, Boolean> typeEnabled = new EnumMap<>(NotificationType.class);
		for (PreferenceRow row : rows) {
			typeEnabled.put(row.notificationType(), row.typeEnabled());
		}
		PreferenceRow first = rows.get(0);
		return new NotificationPreferenceSnapshot(userId, first.pushEnabled(), first.quietHours(), typeEnabled);
	}

	@Override
	public void lockUser(long userId) {
		jdbc.queryForObject(NotificationPreferenceSql.LOCK_USER, new MapSqlParameterSource("userId", userId), Long.class);
	}

	@Override
	public void saveUserSetting(long userId, boolean pushEnabled, NotificationQuietHours quietHours) {
		jdbc.update(NotificationPreferenceSql.UPSERT_USER_SETTING,
			new MapSqlParameterSource()
				.addValue("userId", userId)
				.addValue("pushEnabled", pushEnabled)
				.addValue("quietStart", toSqlTime(quietHours == null ? null : quietHours.start()))
				.addValue("quietEnd", toSqlTime(quietHours == null ? null : quietHours.end()))
				.addValue("quietZoneId", quietHours == null ? null : quietHours.zoneId().getId()));
	}

	@Override
	public void replaceTypePreferences(long userId, Map<NotificationType, Boolean> typeEnabled) {
		for (NotificationType notificationType : NotificationType.values()) {
			jdbc.update(NotificationPreferenceSql.UPSERT_TYPE_PREFERENCE,
				new MapSqlParameterSource()
					.addValue("notificationType", notificationType.name())
					.addValue("userId", userId)
					.addValue("enabled", typeEnabled.get(notificationType)));
		}
	}

	@Override
	public boolean isPushEnabled(long userId, NotificationType notificationType) {
		return Boolean.TRUE.equals(jdbc.queryForObject(NotificationPreferenceSql.FIND_EFFECTIVE_PUSH_ENABLED,
			new MapSqlParameterSource()
				.addValue("userId", userId)
				.addValue("notificationType", notificationType.name()),
			Boolean.class));
	}

	private static PreferenceRow mapPreferenceRow(ResultSet rs) throws SQLException {
		return new PreferenceRow(
			rs.getBoolean("push_enabled"),
			quietHours(rs),
			NotificationType.valueOf(rs.getString("notification_type")),
			rs.getBoolean("type_enabled"));
	}

	private static NotificationQuietHours quietHours(ResultSet rs) throws SQLException {
		LocalTime quietStart = toLocalTime(rs.getTime("quiet_start"));
		LocalTime quietEnd = toLocalTime(rs.getTime("quiet_end"));
		String quietZoneId = rs.getString("quiet_zone_id");
		if (quietStart == null && quietEnd == null && quietZoneId == null) {
			return null;
		}
		return new NotificationQuietHours(quietStart, quietEnd, ZoneId.of(quietZoneId));
	}

	private static Time toSqlTime(LocalTime value) {
		return value == null ? null : Time.valueOf(value);
	}

	private static LocalTime toLocalTime(Time value) {
		return value == null ? null : value.toLocalTime();
	}

	private record PreferenceRow(
		boolean pushEnabled,
		NotificationQuietHours quietHours,
		NotificationType notificationType,
		boolean typeEnabled) {
	}
}
