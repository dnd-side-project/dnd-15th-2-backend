package com.dnd.qello.notification.repository.jdbc.sql;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.dnd.qello.notification.domain.NotificationType;

public final class NotificationPreferenceSql {

	private static final String NOTIFICATION_TYPE_ROWS = IntStream.range(0, NotificationType.values().length)
		.mapToObj(index -> "\t\t\t(%d, '%s')".formatted(index, NotificationType.values()[index].name()))
		.collect(Collectors.joining(",\n"));

	private NotificationPreferenceSql() {
	}

	public static final String FIND_PREFERENCE_SNAPSHOT = """
		WITH notification_types(sort_order, notification_type) AS (
			VALUES
		%s
		)
		SELECT
			COALESCE(nus.push_enabled, TRUE) AS push_enabled,
			nus.quiet_start,
			nus.quiet_end,
			nus.quiet_zone_id,
			nt.notification_type,
			COALESCE(np.enabled, TRUE) AS type_enabled
		FROM notification_types nt
		LEFT JOIN notification_user_setting nus
			ON nus.user_id = :userId
		LEFT JOIN notification_preference np
			ON np.user_id = :userId
			AND np.notification_type = nt.notification_type
		ORDER BY nt.sort_order
		""".formatted(NOTIFICATION_TYPE_ROWS);

	public static final String LOCK_USER = """
		SELECT id
		FROM user_account
		WHERE id = :userId
		FOR UPDATE
		""";

	public static final String UPSERT_USER_SETTING = """
		INSERT INTO notification_user_setting
			(user_id, push_enabled, quiet_start, quiet_end, quiet_zone_id)
		VALUES (:userId, :pushEnabled, :quietStart, :quietEnd, :quietZoneId)
		ON CONFLICT (user_id) DO UPDATE SET
			push_enabled = EXCLUDED.push_enabled,
			quiet_start = EXCLUDED.quiet_start,
			quiet_end = EXCLUDED.quiet_end,
			quiet_zone_id = EXCLUDED.quiet_zone_id,
			updated_at = clock_timestamp()
		""";

	public static final String UPSERT_TYPE_PREFERENCE = """
		INSERT INTO notification_preference
			(notification_type, user_id, enabled)
		VALUES (:notificationType, :userId, :enabled)
		ON CONFLICT (notification_type, user_id) DO UPDATE SET
			enabled = EXCLUDED.enabled,
			updated_at = clock_timestamp()
		""";

	public static final String FIND_EFFECTIVE_PUSH_ENABLED = """
		SELECT
			COALESCE((SELECT push_enabled FROM notification_user_setting WHERE user_id = :userId), TRUE)
			AND
			COALESCE((SELECT enabled FROM notification_preference
				WHERE user_id = :userId AND notification_type = :notificationType), TRUE)
		""";
}
