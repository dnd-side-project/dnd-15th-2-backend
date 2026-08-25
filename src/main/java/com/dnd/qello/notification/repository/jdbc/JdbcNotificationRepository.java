package com.dnd.qello.notification.repository.jdbc;

import com.dnd.qello.notification.domain.*;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.notification.repository.jdbc.sql.NotificationSql;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.EnumSet;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
public class JdbcNotificationRepository implements OutboxEventRepository, NotificationRepository {

    private static final Set<String> PUSH_PLATFORM_NAMES = EnumSet.allOf(PushPlatform.class).stream()
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

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
    public List<OutboxEvent> claimDue(Set<OutboxEventType> eventTypes, int limit, String leaseOwner,
                                      Instant at, Instant leaseExpiresAt) {
        if (limit <= 0) {
            throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "limit",
                    "limit은 양수여야 합니다.");
        }
        if (eventTypes == null || eventTypes.isEmpty() || eventTypes.stream().anyMatch(Objects::isNull)) {
            throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "eventTypes",
                    "eventTypes는 하나 이상 필요하며 유효한 값만 포함해야 합니다.");
        }
        validateLeaseRequest(leaseOwner, at, leaseExpiresAt);
        Set<String> eventTypeNames = EnumSet.copyOf(eventTypes).stream()
                .map(OutboxEventType::name).collect(Collectors.toUnmodifiableSet());
        return jdbc.query(NotificationSql.CLAIM_DUE_OUTBOX_EVENTS,
                new MapSqlParameterSource().addValue("limit", limit).addValue("at", timestamp(at))
                        .addValue("leaseOwner", leaseOwner).addValue("leaseExpiresAt", timestamp(leaseExpiresAt))
                        .addValue("eventTypes", eventTypeNames),
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
            throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "nextAttemptAt",
                    "nextAttemptAt은 필수입니다.");
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
    public Notification saveIfAbsent(Notification notification) {
        return jdbc.queryForObject(NotificationSql.INSERT_NOTIFICATION_IF_ABSENT,
                notificationParams(notification), (rs, row) -> mapNotification(rs));
    }

    @Override
    public Optional<Notification> findById(long id) {
        return jdbc.query("SELECT * FROM notification WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, row) -> mapNotification(rs)).stream().findFirst();
    }

    @Override
    public boolean update(Notification notification) {
        // status를 조건에 두어 동시 요청 중 하나만 UNREAD -> READ 전이를 반영하게 한다.
        // 조건이 없으면 나중에 커밋된 요청의 이른 read_at이 먼저 커밋된 요청의 늦은
        // read_at을 덮어쓰거나, 동시에 REVOKED로 전이된 행을 READ로 되돌릴 수 있다.
        return jdbc.update("""
                UPDATE notification SET status = :status, read_at = :readAt
                WHERE id = :id AND status = 'UNREAD'
                """, new MapSqlParameterSource().addValue("status", notification.status().name())
                .addValue("readAt", timestamp(notification.readAt())).addValue("id", notification.id())) == 1;
    }

    @Override
    public int revokeByAnswerId(long answerId) {
        return jdbc.update(NotificationSql.REVOKE_NOTIFICATIONS_BY_ANSWER_ID,
                new MapSqlParameterSource("answerId", answerId));
    }

    @Override
    public int cancelDeliveriesByAnswerId(long answerId) {
        return jdbc.update(NotificationSql.CANCEL_DELIVERIES_BY_ANSWER_ID,
                new MapSqlParameterSource("answerId", answerId));
    }

    @Override
    public NotificationDelivery saveDelivery(NotificationDelivery delivery) {
        Long id = jdbc.queryForObject(NotificationSql.INSERT_NOTIFICATION_DELIVERY, deliveryParams(delivery),
                Long.class);
        return findDeliveryById(id).orElseThrow();
    }

    @Override
    public NotificationDelivery saveDeliveryIfAbsent(NotificationDelivery delivery) {
        return jdbc.queryForObject(NotificationSql.INSERT_NOTIFICATION_DELIVERY_IF_ABSENT,
                deliveryParams(delivery), (rs, row) -> mapDelivery(rs));
    }

    @Override
    public Optional<NotificationDelivery> claimDelivery(long id, Instant at) {
        if (id <= 0) {
            throw new NotificationException(NotificationErrorCode.INVALID_ID, "deliveryId",
                    "deliveryId는 양수여야 합니다.");
        }
        if (at == null) {
            throw new NotificationException(NotificationErrorCode.REQUIRED_VALUE_MISSING, "at",
                    "처리 시각은 필수입니다.");
        }
        return jdbc.query(NotificationSql.CLAIM_SINGLE_LEGACY_PUSH_DELIVERY,
                new MapSqlParameterSource().addValue("id", id).addValue("at", timestamp(at)),
                (rs, row) -> mapDelivery(rs)).stream().findFirst();
    }

    @Override
    public boolean updateDelivery(NotificationDelivery delivery) {
        if (delivery == null || delivery.id() == null || delivery.id() <= 0) {
            throw new NotificationException(NotificationErrorCode.INVALID_ID, "deliveryId",
                    "deliveryId는 양수여야 합니다.");
        }
        if (delivery.status() != DeliveryStatus.SENT && delivery.status() != DeliveryStatus.FAILED
                && delivery.status() != DeliveryStatus.DEAD && delivery.status() != DeliveryStatus.CANCELLED) {
            throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_STATUS, "status",
                    "PROCESSING에서 종결 상태로만 전이할 수 있습니다.");
        }
        return jdbc.update(NotificationSql.COMPLETE_LEGACY_PUSH_DELIVERY,
                deliveryParams(delivery).addValue("id", delivery.id())) == 1;
    }

    @Override
    public PushDevice saveDevice(PushDevice device) {
        return jdbc.queryForObject(NotificationSql.INSERT_PUSH_DEVICE, pushDeviceParams(
                device.userId(), device.platform().name(), device.tokenCiphertext(), device.tokenFingerprint(),
                device.status(), device.lastSeenAt(), device.revokedAt()), (rs, row) -> mapPushDevice(rs));
    }

    @Override
    @Transactional
    public PushDevice registerOrTransferDevice(
            long userId, String platform, byte[] tokenCiphertext, String tokenFingerprint, Instant at) {
        validatePushDeviceWrite(userId, platform, tokenCiphertext, tokenFingerprint, at);
        acquirePushDeviceWriteLocks(userId, platform, tokenFingerprint);
        return jdbc.queryForObject(NotificationSql.REGISTER_OR_TRANSFER_PUSH_DEVICE, pushDeviceParams(
                userId, platform, tokenCiphertext, tokenFingerprint, PushDeviceStatus.ACTIVE, at, null),
                (rs, row) -> mapPushDevice(rs));
    }

    @Override
    @Transactional
    public int revokeOwnedDevice(long userId, String platform, String tokenFingerprint, Instant at) {
        validatePushDeviceLookup(userId, platform, tokenFingerprint, at);
        acquirePushDeviceWriteLocks(userId, platform, tokenFingerprint);
        Number revokedCount = jdbc.queryForObject(NotificationSql.REVOKE_OWNED_PUSH_DEVICE,
                new MapSqlParameterSource().addValue("userId", userId).addValue("platform", platform)
                        .addValue("tokenFingerprint", tokenFingerprint).addValue("revokedAt", timestamp(at)),
                Number.class);
        return revokedCount == null ? 0 : revokedCount.intValue();
    }

	@Override
	public List<Long> findActiveDeviceIdsByUserId(long userId) {
		return jdbc.queryForList(NotificationSql.FIND_ACTIVE_PUSH_DEVICE_IDS,
				new MapSqlParameterSource("userId", userId), Long.class);
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
                .addValue("answerId", n.answerId()).addValue("reportId", n.reportId())
                .addValue("status", n.status().name())
                .addValue("createdAt", timestamp(n.createdAt())).addValue("readAt", timestamp(n.readAt()));
    }

    private static MapSqlParameterSource deliveryParams(NotificationDelivery d) {
        return new MapSqlParameterSource().addValue("notificationId", d.notificationId())
                .addValue("pushDeviceId", d.pushDeviceId()).addValue("status", d.status().name())
                .addValue("attemptCount", d.attemptCount()).addValue("nextAttemptAt", timestamp(d.nextAttemptAt()))
                .addValue("createdAt", timestamp(d.createdAt())).addValue("sentAt", timestamp(d.sentAt()))
                .addValue("providerMessageId", d.providerMessageId());
    }

    private static MapSqlParameterSource pushDeviceParams(
            long userId, String platform, byte[] tokenCiphertext, String tokenFingerprint,
            PushDeviceStatus status, Instant lastSeenAt, Instant revokedAt) {
        return new MapSqlParameterSource().addValue("userId", userId).addValue("platform", platform)
                .addValue("tokenCiphertext", tokenCiphertext.clone()).addValue("tokenFingerprint", tokenFingerprint)
                .addValue("status", status.name()).addValue("lastSeenAt", timestamp(lastSeenAt))
                .addValue("revokedAt", timestamp(revokedAt));
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
                nullableLong(rs, "direction_post_id"), nullableLong(rs, "answer_id"), nullableLong(rs, "report_id"),
                NotificationStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "read_at"));
    }

    private static NotificationDelivery mapDelivery(ResultSet rs) throws SQLException {
        return new NotificationDelivery(rs.getLong("id"), rs.getLong("notification_id"), rs.getLong("push_device_id"),
                DeliveryStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"), instant(rs, "next_attempt_at"),
                instant(rs, "created_at"), instant(rs, "sent_at"), rs.getString("provider_message_id"));
    }

    private static PushDevice mapPushDevice(ResultSet rs) throws SQLException {
        return new PushDevice(rs.getLong("id"), rs.getLong("user_id"),
                PushPlatform.valueOf(rs.getString("platform")), rs.getBytes("token_ciphertext"),
                rs.getString("token_fingerprint"), PushDeviceStatus.valueOf(rs.getString("device_status")),
                instant(rs, "last_seen_at"), instant(rs, "revoked_at"));
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
            throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "lease",
                    "lease owner와 현재 시각 이후의 만료 시각이 필요합니다.");
        }
    }

    private static void validateLeaseIdentity(String owner, long generation, Instant at) {
        if (owner == null || owner.isBlank() || owner.length() > 100 || generation < 0 || at == null) {
            throw new NotificationException(NotificationErrorCode.INVALID_VALUE_RANGE, "lease",
                    "lease owner, 유효한 generation과 현재 시각이 필요합니다.");
        }
    }

    private static void validatePushDeviceWrite(
            long userId, String platform, byte[] tokenCiphertext, String tokenFingerprint, Instant at) {
        validatePushDeviceLookup(userId, platform, tokenFingerprint, at);
        if (tokenCiphertext == null || tokenCiphertext.length == 0) {
            throw new NotificationException(NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "token",
                    "암호화된 push token 값이 필요합니다.");
        }
    }

    private static void validatePushDeviceLookup(long userId, String platform, String tokenFingerprint, Instant at) {
        if (userId <= 0) {
            throw new NotificationException(NotificationErrorCode.INVALID_ID, "userId", "userId는 양수여야 합니다.");
        }
        if (platform == null || !PUSH_PLATFORM_NAMES.contains(platform)) {
            throw new NotificationException(NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "platform",
                    "platform 값이 올바르지 않습니다.");
        }
        if (tokenFingerprint == null || tokenFingerprint.isBlank()) {
            throw new NotificationException(NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "token",
                    "push token 지문 값이 필요합니다.");
        }
        if (at == null) {
            throw new NotificationException(NotificationErrorCode.INVALID_PUSH_DEVICE_REQUEST, "at",
                    "처리 시각은 필수입니다.");
        }
    }

    private static long userPlatformLockKey(long userId, String platform) {
        return AdvisoryLockKeys.of("push-device:user-platform", userId + ":" + platform);
    }

    private void acquirePushDeviceWriteLocks(long userId, String platform, String tokenFingerprint) {
        acquireTransactionAdvisoryLock(userPlatformLockKey(userId, platform));
        acquireTransactionAdvisoryLock(fingerprintLockKey(tokenFingerprint));
    }

    private void acquireTransactionAdvisoryLock(long lockKey) {
        jdbc.query(NotificationSql.ACQUIRE_TRANSACTION_ADVISORY_LOCK,
                new MapSqlParameterSource("lockKey", lockKey), (ResultSet rs) -> null);
    }

    private static long fingerprintLockKey(String tokenFingerprint) {
        return AdvisoryLockKeys.of("push-device:fingerprint", tokenFingerprint);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
