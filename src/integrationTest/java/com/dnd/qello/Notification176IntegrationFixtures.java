/**
 * Created at: 2026-08-20T16:00:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-INT-001 through
 * INT-018 (shared fixture, not itself a test class)
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

/**
 * TEST-PLAN-GH-176 통합 테스트가 공유하는 fixture다. E4(알림함 통합)만 소유하고
 * 고친다 — E5(fan-out 재배치)는 기존 테스트의 자체 fixture를 그대로 쓴다.
 */
final class Notification176IntegrationFixtures {

	private static final String REGION = "TEST-GH176";

	private final JdbcTemplate jdbc;
	private final NotificationRepository notifications;
	private final OutboxEventRepository outboxEvents;
	private final Instant now;
	private long dedupSequence;

	Notification176IntegrationFixtures(
		JdbcTemplate jdbc, NotificationRepository notifications, OutboxEventRepository outboxEvents, Instant now) {
		this.jdbc = jdbc;
		this.notifications = notifications;
		this.outboxEvents = outboxEvents;
		this.now = now;
	}

	void reset() {
		jdbc.update("""
			DELETE FROM notification_delivery WHERE notification_id IN (
				SELECT id FROM notification WHERE recipient_id IN (
					SELECT id FROM user_account WHERE coarse_region_code = ?))
			""", REGION);
		jdbc.update("DELETE FROM notification_seen_state WHERE user_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM notification WHERE recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT' AND dedup_key LIKE 'gh176-%'");
		jdbc.update("""
			DELETE FROM answer WHERE post_recipient_id IN (
				SELECT pr.id FROM post_recipient pr JOIN direction_post dp ON dp.id = pr.post_id
				WHERE dp.coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM post_recipient pr
			WHERE pr.recipient_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR pr.post_id IN (SELECT id FROM direction_post WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("DELETE FROM direction_post WHERE coarse_region_code = ?", REGION);
		jdbc.update("""
			DELETE FROM approved_question WHERE approved_by IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION);
		jdbc.update("""
			DELETE FROM user_block WHERE blocker_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			   OR blocked_id IN (SELECT id FROM user_account WHERE coarse_region_code = ?)
			""", REGION, REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'GH176 Notification Test', 'REGION')
			""", REGION);
	}

	long account(String nickname) {
		return account(nickname, "USER", "ACTIVE");
	}

	long account(String nickname, String role, String status) {
		return jdbc.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES (?, ?, ?, ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, role, role.equals("OPERATOR") ? null : "KR", status, REGION, nickname);
	}

	/** ACTIVE 상태, 삭제되지 않은 살아 있는 질문글. */
	long activePost(long senderId, String key, Instant expiresAt) {
		return post(senderId, key, "ACTIVE", expiresAt, null);
	}

	long expiredPost(long senderId, String key, Instant expiresAt) {
		return post(senderId, key, "EXPIRED", expiresAt, null);
	}

	long deletedPost(long senderId, String key) {
		return post(senderId, key, "DELETED", now.plusSeconds(3600), now);
	}

	private long post(long senderId, String key, String status, Instant expiresAt, Instant deletedAt) {
		long questionId = jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'GH176 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(now), Timestamp.from(now), senderId);
		// ck_direction_post_expiry가 expires_at > submitted_at을 강제한다 — 이미 만료된
		// 질문글을 만들 때도 submitted_at은 항상 expiresAt보다 앞서야 하므로 now가 아니라
		// 고정 오프셋으로 둔다.
		Instant submittedAt = now.minusSeconds(7200);
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at, deleted_at)
			VALUES (?, ?, ?, ?, 'GH176 본문', ?, 'PASSED', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, status, "gh176-" + key, REGION,
			Timestamp.from(submittedAt), Timestamp.from(submittedAt), Timestamp.from(expiresAt),
			deletedAt == null ? null : Timestamp.from(deletedAt));
	}

	/** answer를 붙이기 위한 최소 post_recipient. 답변 상태(ANSWERED)라 capacity_released_at을 함께 채운다. */
	long answeredRecipient(long postId, long recipientId) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, discovered_at, opened_at, capacity_released_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'ANSWERED', 'NEAR', 10, ?, ?, ?, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION,
			Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
	}

	long publishedAnswer(long postRecipientId, long authorId, String key) {
		return answer(postRecipientId, authorId, key, "PUBLISHED", now, null);
	}

	long hiddenAnswer(long postRecipientId, long authorId, String key) {
		return answer(postRecipientId, authorId, key, "HIDDEN", now, null);
	}

	long unpublishedAnswer(long postRecipientId, long authorId, String key) {
		return answer(postRecipientId, authorId, key, "SUBMITTED", null, null);
	}

	long deletedAnswer(long postRecipientId, long authorId, String key) {
		return answer(postRecipientId, authorId, key, "DELETED", now, now);
	}

	private long answer(
		long postRecipientId, long authorId, String key, String status, Instant publishedAt, Instant deletedAt) {
		return jdbc.queryForObject("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at, published_at, deleted_at)
			VALUES (?, ?, ?, ?, 'GH176 답변', ?, 45.0, 'NEAR', 5000, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, postRecipientId, authorId, status, "gh176-" + key, REGION,
			Timestamp.from(now), publishedAt == null ? null : Timestamp.from(publishedAt),
			deletedAt == null ? null : Timestamp.from(deletedAt));
	}

	/** 편도 활성 차단. blocker가 blocked를 차단한다. */
	void activeBlock(long blockerId, long blockedId) {
		jdbc.update("""
			INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at)
			VALUES (?, ?, ?, NULL)
			""", blockerId, blockedId, Timestamp.from(now));
	}

	void releasedBlock(long blockerId, long blockedId) {
		jdbc.update("""
			INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at)
			VALUES (?, ?, ?, ?)
			""", blockerId, blockedId, Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now));
	}

	/** DIRECTION_POST 대상 알림. */
	Notification directionPostNotification(long recipientId, long directionPostId, Instant createdAt) {
		return notifications.saveIfAbsent(new Notification(
			null, recipientId, sourceEventId(), NotificationType.DIRECTION_POST_RECEIVED, nextDedupKey(),
			directionPostId, null, null, NotificationStatus.UNREAD, createdAt, null));
	}

	/** ANSWER 대상 알림. N1이 소비자를 만들지 않는 타입(ANSWER_RECEIVED 등)의 목록·판정 동작만 확인하는 용도다. */
	Notification answerNotification(long recipientId, long answerId, Instant createdAt) {
		return notifications.saveIfAbsent(new Notification(
			null, recipientId, sourceEventId(), NotificationType.ANSWER_RECEIVED, nextDedupKey(),
			null, answerId, null, NotificationStatus.UNREAD, createdAt, null));
	}

	/** 대상 없는 알림(NONE). #177 이전에는 ANSWER_REACTED 등 5종이 전부 이 모양이다. */
	Notification targetlessNotification(long recipientId, NotificationType type, Instant createdAt) {
		return notifications.saveIfAbsent(new Notification(
			null, recipientId, sourceEventId(), type, nextDedupKey(),
			null, null, null, NotificationStatus.UNREAD, createdAt, null));
	}

	Notification withStatus(Notification notification, NotificationStatus status, Instant readAt) {
		Notification updated = new Notification(
			notification.id(), notification.recipientId(), notification.outboxEventId(), notification.notificationType(),
			notification.dedupKey(), notification.directionPostId(), notification.answerId(), notification.reportId(), status,
			notification.createdAt(), readAt);
		notifications.update(updated);
		return updated;
	}

	private long sourceEventId() {
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(
			OutboxAggregateType.POST_RECIPIENT, 1L, OutboxEventType.RECIPIENTS_CONFIRMED,
			"gh176-outbox-" + (dedupSequence++), "{}", now));
		return event.id();
	}

	private String nextDedupKey() {
		return "gh176-notification-" + (dedupSequence++);
	}
}
