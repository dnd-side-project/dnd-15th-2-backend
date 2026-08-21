/**
 * Created at: 2026-08-14T17:46:34+09:00
 * Source scenario: TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT-INT-020 through INT-023,
 * TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-013
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.repository.PostRecipientRepository;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationDelivery;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.repository.NotificationPreferenceRepository;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@SpringBootTest
@ActiveProfiles("test")
class NotificationFanOutPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH123-PERSIST";
	private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private NotificationRepository notifications;
	@Autowired
	private NotificationPreferenceRepository preferences;
	@Autowired
	private OutboxEventRepository outboxEvents;
	@Autowired
	private PostRecipientRepository postRecipients;
	@Autowired
	private TransactionTemplate transactions;

	private long senderId;
	private long recipientId;
	private long postId;
	private long postRecipientId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM notification_user_setting");
		jdbc.update("DELETE FROM notification_preference");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH123 Persistence', 'REGION')
			""", REGION);

		senderId = account("gh123-sender");
		recipientId = account("gh123-recipient");
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'GH123 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(1)), Timestamp.from(NOW), senderId);
		postId = jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', 'gh123-persistence-post', '질문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
		postRecipientId = jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg,
				 matched_region_code, matched_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION, Timestamp.from(NOW));
	}

	@Test
	@DisplayName("알림 설정 행이 없으면 활성이고 저장된 enabled 값은 그대로 조회한다")
	void readsAbsentPreferenceAsEnabled() {
		long disabledUserId = account("gh123-disabled");
		preferences.replaceTypePreferences(senderId, typePreferences(NotificationType.DIRECTION_POST_RECEIVED, true));
		preferences.replaceTypePreferences(disabledUserId,
			typePreferences(NotificationType.DIRECTION_POST_RECEIVED, false));

		assertThat(preferences.isPushEnabled(recipientId,
			NotificationType.DIRECTION_POST_RECEIVED)).isTrue();
		assertThat(preferences.isPushEnabled(senderId,
			NotificationType.DIRECTION_POST_RECEIVED)).isTrue();
		assertThat(preferences.isPushEnabled(disabledUserId,
			NotificationType.DIRECTION_POST_RECEIVED)).isFalse();
	}

	@Test
	@DisplayName("V26 이후 전용 preference repository는 enabled를 저장하고 다시 읽을 수 있다")
	void savesPreferenceEnabledRoundTripAfterV25() {
		preferences.replaceTypePreferences(recipientId, typePreferences(NotificationType.ANSWER_RECEIVED, false));

		assertThat(preferences.isPushEnabled(recipientId, NotificationType.ANSWER_RECEIVED)).isFalse();
		assertThat(jdbc.queryForObject("""
			SELECT enabled
			FROM notification_preference
			WHERE notification_type = 'ANSWER_RECEIVED' AND user_id = ?
			""", Boolean.class, recipientId)).isFalse();
	}

	@Test
	@DisplayName("ACTIVE PushDevice ID만 안정적인 순서로 조회하고 token 값은 반환하지 않는다")
	void findsOnlyActivePushDeviceIds() {
		PushDevice firstActive = notifications.saveDevice(device(PushDeviceStatus.ACTIVE, "active-1", null));
		notifications.saveDevice(device(PushDeviceStatus.INVALID, "invalid", null));
		PushDevice secondActive = notifications.saveDevice(device(PushDeviceStatus.ACTIVE, "active-2", null));
		notifications.saveDevice(device(PushDeviceStatus.REVOKED, "revoked", NOW.plusSeconds(1)));
		notifications.saveDevice(new PushDevice(null, senderId, PushPlatform.ANDROID, new byte[] {9, 9},
			"other-user-device", PushDeviceStatus.ACTIVE, NOW, null));

		List<Long> activeDeviceIds = notifications.findActiveDeviceIdsByUserId(recipientId);

		assertThat(activeDeviceIds).containsExactly(firstActive.id(), secondActive.id());
	}

	@Test
	@DisplayName("Notification insert-if-absent는 최초 행을 유지하고 strict save 중복 예외를 보존한다")
	void insertsNotificationIfAbsentWithoutChangingStrictSave() {
		OutboxEvent firstSource = sourceEvent("notification-source-1");
		OutboxEvent secondSource = sourceEvent("notification-source-2");
		Notification firstAttempt = notification(firstSource.id(), NOW);

		Notification first = notifications.saveIfAbsent(firstAttempt);
		Notification replay = notifications.saveIfAbsent(firstAttempt);
		Notification differentSourceReplay = notifications.saveIfAbsent(
			notification(secondSource.id(), NOW.plusSeconds(10)));

		assertThat(replay.id()).isEqualTo(first.id());
		assertThat(differentSourceReplay.id()).isEqualTo(first.id());
		assertThat(differentSourceReplay.outboxEventId()).isEqualTo(firstSource.id());
		assertThat(differentSourceReplay.createdAt()).isEqualTo(NOW);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification WHERE recipient_id = ? AND dedup_key = ?
			""", Long.class, recipientId, notificationDedupKey())).isEqualTo(1L);
		assertThatThrownBy(() -> notifications.save(notification(secondSource.id(), NOW.plusSeconds(20))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("Delivery insert-if-absent는 최초 행을 유지하고 strict saveDelivery 중복 예외를 보존한다")
	void insertsDeliveryIfAbsentWithoutChangingStrictSaveDelivery() {
		Notification notification = notifications.saveIfAbsent(notification(sourceEvent("delivery-source").id(), NOW));
		long deviceId = notifications.saveDevice(device(PushDeviceStatus.ACTIVE, "delivery", null)).id();
		NotificationDelivery firstAttempt = NotificationDelivery.pending(notification.id(), deviceId, NOW);

		NotificationDelivery first = notifications.saveDeliveryIfAbsent(firstAttempt);
		NotificationDelivery replay = notifications.saveDeliveryIfAbsent(
			NotificationDelivery.pending(notification.id(), deviceId, NOW.plusSeconds(10)));

		assertThat(replay.id()).isEqualTo(first.id());
		assertThat(replay.createdAt()).isEqualTo(NOW);
		assertThat(replay.nextAttemptAt()).isEqualTo(NOW);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery
			WHERE notification_id = ? AND push_device_id = ?
			""", Long.class, notification.id(), deviceId)).isEqualTo(1L);
		assertThatThrownBy(() -> notifications.saveDelivery(firstAttempt))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("PostRecipient fan-out 조회는 aggregate 행을 FOR UPDATE 경계로 반환한다")
	void findsPostRecipientForUpdate() {
		PostRecipient locked = transactions.execute(status ->
			postRecipients.findByIdForUpdate(postRecipientId).orElseThrow());

		assertThat(locked).isNotNull();
		assertThat(locked.getId()).isEqualTo(postRecipientId);
		assertThat(locked.getPostId()).isEqualTo(postId);
		assertThat(locked.getRecipientId()).isEqualTo(recipientId);
		assertThat(postRecipients.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private PushDevice device(PushDeviceStatus status, String fingerprintSuffix, Instant revokedAt) {
		return new PushDevice(null, recipientId, PushPlatform.ANDROID, new byte[] {1, 2, 3},
			"gh123-" + fingerprintSuffix, status, NOW, revokedAt);
	}

	private OutboxEvent sourceEvent(String dedupKey) {
		return outboxEvents.save(OutboxEvent.pending(OutboxAggregateType.POST_RECIPIENT, postRecipientId,
			OutboxEventType.RECIPIENTS_CONFIRMED, dedupKey, "{}", NOW));
	}

	private Notification notification(long outboxEventId, Instant createdAt) {
		return new Notification(null, recipientId, outboxEventId, NotificationType.DIRECTION_POST_RECEIVED,
			notificationDedupKey(), postId, null, null, NotificationStatus.UNREAD, createdAt, null);
	}

	private String notificationDedupKey() {
		return "direction-post-received:" + postRecipientId;
	}

	private Map<NotificationType, Boolean> typePreferences(NotificationType targetType, boolean enabled) {
		EnumMap<NotificationType, Boolean> preferencesByType = new EnumMap<>(NotificationType.class);
		for (NotificationType notificationType : NotificationType.values()) {
			preferencesByType.put(notificationType, true);
		}
		preferencesByType.put(targetType, enabled);
		return preferencesByType;
	}
}
