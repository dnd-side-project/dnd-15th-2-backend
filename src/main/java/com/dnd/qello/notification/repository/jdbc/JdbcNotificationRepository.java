package com.dnd.qello.notification.repository.jdbc;

import com.dnd.qello.notification.domain.*;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.repository.jdbc.sql.NotificationSql;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcNotificationRepository implements OutboxEventRepository, NotificationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        Long id = jdbc.queryForObject(NotificationSql.INSERT_OUTBOX_EVENT, outboxParams(event), Long.class);
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
        // 기존 호출자는 worker identity와 lease duration을 전달하지 않는다. 새 worker는
        // 명시적 overload를 사용하고, 이 경로는 기존 API 호환을 위한 짧은 fallback이다.
        return claim(id, "legacy-compat", at, at.plus(Duration.ofMinutes(5)));
    }

    @Override
    public Optional<OutboxEvent> claim(long id, String leaseOwner, Instant at, Instant leaseExpiresAt) {
        validateLeaseRequest(leaseOwner, at, leaseExpiresAt);
        return jdbc.query(NotificationSql.CLAIM_OUTBOX_EVENT,
                new MapSqlParameterSource().addValue("id", id).addValue("at", timestamp(at))
                .addValue("leaseOwner", leaseOwner).addValue("leaseExpiresAt", timestamp(leaseExpiresAt)),
                (rs, row) -> mapOutbox(rs)).stream().findFirst();
    }

    @Override
    public List<OutboxEvent> claimDue(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        validateLeaseRequest(leaseOwner, at, leaseExpiresAt);
        return jdbc.query(NotificationSql.CLAIM_DUE_OUTBOX_EVENTS,
                new MapSqlParameterSource().addValue("limit", limit).addValue("at", timestamp(at))
                        .addValue("leaseOwner", leaseOwner).addValue("leaseExpiresAt", timestamp(leaseExpiresAt)),
                (rs, row) -> mapOutbox(rs));
    }

    @Override
    public boolean complete(long id, String leaseOwner, long leaseGeneration, Instant processedAt) {
        validateLeaseIdentity(leaseOwner, leaseGeneration, processedAt);
        return jdbc.update(NotificationSql.COMPLETE_OUTBOX_EVENT,
                new MapSqlParameterSource().addValue("id", id)
                .addValue("leaseOwner", leaseOwner).addValue("leaseGeneration", leaseGeneration)
                .addValue("processedAt", timestamp(processedAt))) == 1;
    }

    @Override
    public boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at,
                        Instant nextAttemptAt, boolean dead) {
        validateLeaseIdentity(leaseOwner, leaseGeneration, at);
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt must not be null");
        }
        return jdbc.update(NotificationSql.FAIL_OUTBOX_EVENT,
                new MapSqlParameterSource().addValue("id", id)
                .addValue("nextStatus", dead ? OutboxStatus.DEAD.name() : OutboxStatus.FAILED.name())
                .addValue("nextAttemptAt", timestamp(nextAttemptAt)).addValue("leaseOwner", leaseOwner)
                .addValue("leaseGeneration", leaseGeneration).addValue("at", timestamp(at))) == 1;
    }

    @Override
    public boolean update(OutboxEvent event) {
        // Terminal updates must carry the fencing tuple explicitly through complete/fail.
        // This compatibility method therefore refuses an unsafe unguarded terminal update.
        if (event.status() != OutboxStatus.PROCESSING) {
            return false;
        }
        return jdbc.update(NotificationSql.UPDATE_PROCESSING_OUTBOX_EVENT,
                outboxParams(event).addValue("id", event.id()).addValue("at", timestamp(Instant.now()))) == 1;
    }

    @Override
    public Notification save(Notification notification) {
        Long id = jdbc.queryForObject(NotificationSql.INSERT_NOTIFICATION, notificationParams(notification), Long.class);
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
        Long id = jdbc.queryForObject(NotificationSql.INSERT_NOTIFICATION_DELIVERY, deliveryParams(delivery),
                Long.class);
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
        jdbc.update(NotificationSql.UPSERT_NOTIFICATION_PREFERENCE,
                new MapSqlParameterSource().addValue("notificationType", preference.notificationType().name())
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
                .addValue("processedAt", timestamp(e.processedAt())).addValue("matchRound", e.matchRound())
                .addValue("leaseOwner", e.leaseOwner()).addValue("leaseExpiresAt", timestamp(e.leaseExpiresAt()))
                .addValue("leaseGeneration", e.leaseGeneration());
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
                rs.getInt("attempt_count"), instant(rs, "next_attempt_at"), instant(rs, "created_at"), instant(rs, "processed_at"),
                nullableInteger(rs, "match_round"), rs.getString("lease_owner"), instant(rs, "lease_expires_at"),
                rs.getLong("lease_generation"));
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

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static void validateLeaseRequest(String owner, Instant at, Instant leaseExpiresAt) {
        if (owner == null || owner.isBlank() || owner.length() > 100 || at == null || leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(at)) {
            throw new IllegalArgumentException("lease owner and a future expiry are required");
        }
    }

    private static void validateLeaseIdentity(String owner, long generation, Instant at) {
        if (owner == null || owner.isBlank() || owner.length() > 100 || generation < 0 || at == null) {
            throw new IllegalArgumentException("lease owner, generation and time are required");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
