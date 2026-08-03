package com.dnd.qello.notification.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.sql.Time;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.notification.domain.DeliveryStatus;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.NotificationPreference;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@Repository
public class JdbcNotificationRepository implements OutboxEventRepository, NotificationRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public OutboxEvent save(OutboxEvent event) {
		Long id = jdbc.queryForObject("""
			INSERT INTO outbox_event
				(aggregate_type, aggregate_id, event_type, dedup_key, payload, status,
				 attempt_count, next_attempt_at, created_at, processed_at)
			VALUES (:aggregateType, :aggregateId, :eventType, :dedupKey, CAST(:payload AS jsonb),
				:status, :attemptCount, :nextAttemptAt, :createdAt, :processedAt)
			RETURNING id
			""", outboxParams(event), Long.class);
		return findEventById(id).orElseThrow();
	}

	@Override
	public Optional<OutboxEvent> findEventById(long id) {
		return jdbc.query("SELECT * FROM outbox_event WHERE id = :id",
			new MapSqlParameterSource("id", id), (rs, row) -> mapOutbox(rs)).stream().findFirst();
	}

	@Override
	public Optional<OutboxEvent> findByDedupKey(String dedupKey) {
		return jdbc.query("SELECT * FROM outbox_event WHERE dedup_key = :dedupKey",
			new MapSqlParameterSource("dedupKey", dedupKey), (rs, row) -> mapOutbox(rs)).stream().findFirst();
	}

	@Override
	public Optional<OutboxEvent> claim(long id, Instant at) {
		int updated = jdbc.update("""
			UPDATE outbox_event
			SET status = 'PROCESSING', attempt_count = attempt_count + 1, next_attempt_at = :at
			WHERE id = :id AND status IN ('PENDING', 'FAILED') AND next_attempt_at <= :at
			""", new MapSqlParameterSource().addValue("id", id).addValue("at", timestamp(at)));
		return updated == 1 ? findEventById(id) : Optional.empty();
	}

	@Override
	public boolean update(OutboxEvent event) {
		return jdbc.update("""
			UPDATE outbox_event SET status = :status, attempt_count = :attemptCount,
				next_attempt_at = :nextAttemptAt, processed_at = :processedAt
			WHERE id = :id
			""", outboxParams(event).addValue("id", event.id())) == 1;
	}

	@Override
	public Notification save(Notification notification) {
		Long id = jdbc.queryForObject("""
			INSERT INTO notification
				(recipient_id, outbox_event_id, notification_type, dedup_key,
				 direction_post_id, answer_id, status, created_at, read_at)
			VALUES (:recipientId, :outboxEventId, :notificationType, :dedupKey,
				:directionPostId, :answerId, :status, :createdAt, :readAt)
			RETURNING id
			""", notificationParams(notification), Long.class);
		return findById(id).orElseThrow();
	}

	@Override
	public Optional<Notification> findById(long id) {
		return jdbc.query("SELECT * FROM notification WHERE id = :id",
			new MapSqlParameterSource("id", id), (rs, row) -> mapNotification(rs)).stream().findFirst();
	}

	@Override
	public boolean update(Notification notification) {
		return jdbc.update("""
			UPDATE notification SET status = :status, read_at = :readAt WHERE id = :id
			""", new MapSqlParameterSource().addValue("status", notification.status().name())
			.addValue("readAt", timestamp(notification.readAt())).addValue("id", notification.id())) == 1;
	}

	@Override
	public NotificationDelivery saveDelivery(NotificationDelivery delivery) {
		Long id = jdbc.queryForObject("""
			INSERT INTO notification_delivery
				(notification_id, push_device_id, status, attempt_count, next_attempt_at,
				 created_at, sent_at, provider_message_id)
			VALUES (:notificationId, :pushDeviceId, :status, :attemptCount, :nextAttemptAt,
				:createdAt, :sentAt, :providerMessageId)
			RETURNING id
			""", deliveryParams(delivery), Long.class);
		return findDeliveryById(id).orElseThrow();
	}

	@Override
	public Optional<NotificationDelivery> claimDelivery(long id, Instant at) {
		int updated = jdbc.update("""
			UPDATE notification_delivery
			SET status = 'PROCESSING', next_attempt_at = :at
			WHERE id = :id AND status IN ('PENDING', 'FAILED') AND next_attempt_at <= :at
			""", new MapSqlParameterSource().addValue("id", id).addValue("at", timestamp(at)));
		return updated == 1 ? findDeliveryById(id) : Optional.empty();
	}

	@Override
	public boolean updateDelivery(NotificationDelivery delivery) {
		return jdbc.update("""
			UPDATE notification_delivery SET status = :status, attempt_count = :attemptCount,
				next_attempt_at = :nextAttemptAt, sent_at = :sentAt,
				provider_message_id = :providerMessageId WHERE id = :id
			""", deliveryParams(delivery).addValue("id", delivery.id())) == 1;
	}

	@Override
	public PushDevice saveDevice(PushDevice device) {
		Long id = jdbc.queryForObject("""
			INSERT INTO push_device (user_id, platform, token_ciphertext, token_fingerprint,
				device_status, last_seen_at, revoked_at)
			VALUES (:userId, :platform, :tokenCiphertext, :tokenFingerprint, :status, :lastSeenAt, :revokedAt)
			RETURNING id
			""", new MapSqlParameterSource().addValue("userId", device.userId())
			.addValue("platform", device.platform().name()).addValue("tokenCiphertext", device.tokenCiphertext())
			.addValue("tokenFingerprint", device.tokenFingerprint()).addValue("status", device.status().name())
			.addValue("lastSeenAt", timestamp(device.lastSeenAt())).addValue("revokedAt", timestamp(device.revokedAt())), Long.class);
		return new PushDevice(id, device.userId(), device.platform(), device.tokenCiphertext(), device.tokenFingerprint(),
			device.status(), device.lastSeenAt(), device.revokedAt());
	}

	@Override
	public NotificationPreference savePreference(NotificationPreference preference) {
		jdbc.update("""
			INSERT INTO notification_preference
				(notification_type, user_id, enabled, quiet_start, quiet_end)
			VALUES (:notificationType, :userId, :enabled, :quietStart, :quietEnd)
			ON CONFLICT (notification_type, user_id) DO UPDATE SET
				enabled = EXCLUDED.enabled, quiet_start = EXCLUDED.quiet_start,
				quiet_end = EXCLUDED.quiet_end, updated_at = clock_timestamp()
			""", new MapSqlParameterSource().addValue("notificationType", preference.notificationType().name())
			.addValue("userId", preference.userId()).addValue("enabled", preference.enabled())
			.addValue("quietStart", preference.quietStart() == null ? null : Time.valueOf(preference.quietStart()))
			.addValue("quietEnd", preference.quietEnd() == null ? null : Time.valueOf(preference.quietEnd())));
		return preference;
	}

	private Optional<NotificationDelivery> findDeliveryById(long id) {
		return jdbc.query("SELECT * FROM notification_delivery WHERE id = :id",
			new MapSqlParameterSource("id", id), (rs, row) -> mapDelivery(rs)).stream().findFirst();
	}

	private static MapSqlParameterSource outboxParams(OutboxEvent e) {
		return new MapSqlParameterSource().addValue("aggregateType", e.aggregateType().name())
			.addValue("aggregateId", e.aggregateId()).addValue("eventType", e.eventType().name())
			.addValue("dedupKey", e.dedupKey()).addValue("payload", e.payload())
			.addValue("status", e.status().name()).addValue("attemptCount", e.attemptCount())
			.addValue("nextAttemptAt", timestamp(e.nextAttemptAt())).addValue("createdAt", timestamp(e.createdAt()))
			.addValue("processedAt", timestamp(e.processedAt()));
	}

	private static MapSqlParameterSource notificationParams(Notification n) {
		return new MapSqlParameterSource().addValue("recipientId", n.recipientId())
			.addValue("outboxEventId", n.outboxEventId()).addValue("notificationType", n.notificationType().name())
			.addValue("dedupKey", n.dedupKey()).addValue("directionPostId", n.directionPostId())
			.addValue("answerId", n.answerId()).addValue("status", n.status().name())
			.addValue("createdAt", timestamp(n.createdAt())).addValue("readAt", timestamp(n.readAt()));
	}

	private static MapSqlParameterSource deliveryParams(NotificationDelivery d) {
		return new MapSqlParameterSource().addValue("notificationId", d.notificationId())
			.addValue("pushDeviceId", d.pushDeviceId()).addValue("status", d.status().name())
			.addValue("attemptCount", d.attemptCount()).addValue("nextAttemptAt", timestamp(d.nextAttemptAt()))
			.addValue("createdAt", timestamp(d.createdAt())).addValue("sentAt", timestamp(d.sentAt()))
			.addValue("providerMessageId", d.providerMessageId());
	}

	private static OutboxEvent mapOutbox(ResultSet rs) throws SQLException {
		return new OutboxEvent(rs.getLong("id"), OutboxAggregateType.valueOf(rs.getString("aggregate_type")),
			rs.getLong("aggregate_id"), OutboxEventType.valueOf(rs.getString("event_type")),
			rs.getString("dedup_key"), rs.getString("payload"), OutboxStatus.valueOf(rs.getString("status")),
			rs.getInt("attempt_count"), instant(rs, "next_attempt_at"), instant(rs, "created_at"), instant(rs, "processed_at"));
	}

	private static Notification mapNotification(ResultSet rs) throws SQLException {
		return new Notification(rs.getLong("id"), rs.getLong("recipient_id"), rs.getLong("outbox_event_id"),
			NotificationType.valueOf(rs.getString("notification_type")), rs.getString("dedup_key"),
			nullableLong(rs, "direction_post_id"), nullableLong(rs, "answer_id"),
			NotificationStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "read_at"));
	}

	private static NotificationDelivery mapDelivery(ResultSet rs) throws SQLException {
		return new NotificationDelivery(rs.getLong("id"), rs.getLong("notification_id"), rs.getLong("push_device_id"),
			DeliveryStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"), instant(rs, "next_attempt_at"),
			instant(rs, "created_at"), instant(rs, "sent_at"), rs.getString("provider_message_id"));
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
	private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}
}
