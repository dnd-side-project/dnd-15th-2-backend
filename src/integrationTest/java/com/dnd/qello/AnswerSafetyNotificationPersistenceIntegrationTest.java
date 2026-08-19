/**
 * Created at: 2026-08-03T21:20:00+09:00
 * Source scenario: TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION-INT-001 through INT-010,
 * TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-007 through INT-010
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.answer.repository.MediaAttachmentRepository;
import com.dnd.qello.answer.domain.MediaAttachment;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.notification.domain.Notification;
import com.dnd.qello.notification.domain.NotificationStatus;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.repository.SafetyRepository;

@SpringBootTest
@ActiveProfiles("test")
class AnswerSafetyNotificationPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-F40";
	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private AnswerNotificationService answerNotificationService;
	@Autowired
	private SafetyRepository safetyRepository;
	@Autowired
	private OutboxEventRepository outboxRepository;
	@Autowired
	private MediaAttachmentRepository mediaAttachmentRepository;
	@Autowired
	private NotificationRepository notificationRepository;

	private long authorId;
	private long recipientId;
	private long postRecipientId;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM moderation_review");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'F40 Test', 'REGION')", REGION);
		authorId = account("author");
		recipientId = account("recipient");
		long questionId = question();
		long postId = post(questionId);
		postRecipientId = jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at,
				 inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, authorId, REGION, Timestamp.from(NOW));
	}

	@Test
	@DisplayName("Flyway schema가 Answer/Safety/Notification 테이블과 핵심 제약을 제공한다")
	void reproducesAnswerSafetyNotificationSchema() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('answer', 'user_block', 'report', 'moderation_review', 'push_device', 'notification_preference', 'outbox_event', 'notification', 'notification_delivery')", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN ('uq_open_report_answer', 'uq_active_push_token', 'outbox_event_dispatch_idx', 'notification_delivery_dispatch_idx')", Integer.class)).isEqualTo(4);
	}

	@Test
	@DisplayName("Answer 저장은 recipient-author composite FK와 idempotency unique를 지킨다")
	void persistsAnswerAndRejectsWrongAuthor() {
		Answer saved = answerRepository.save(answer("answer-1", authorId));
		assertThat(answerRepository.findById(saved.getId())).hasValueSatisfying(found -> {
			assertThat(found.getId()).isEqualTo(saved.getId());
			assertThat(found.getAuthorId()).isEqualTo(authorId);
			assertThat(found.getPostRecipientId()).isEqualTo(postRecipientId);
			assertThat(found.getIdempotencyKey()).isEqualTo("answer-1");
		});
		assertThatThrownBy(() -> answerRepository.save(answer("answer-2", recipientId)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("답변 공개와 ANSWER_PUBLISHED outbox 기록은 같은 transaction에서 함께 저장된다")
	void publishesAnswerAndWritesOutboxAtomically() {
		Answer saved = answerNotificationService.submit(answer("answer-publish", authorId));
		Answer published = answerNotificationService.publish(saved.getId(), NOW.plusSeconds(5));

		assertThat(published.getStatus().name()).isEqualTo("PUBLISHED");
		assertThat(outboxRepository.findByDedupKey("answer-published:" + saved.getId())).isPresent();
	}

	@Test
	@DisplayName("같은 transaction에서 outbox 중복이 발생하면 앞선 Answer 저장도 rollback된다")
	void rollsBackAnswerWhenOutboxInsertFails() {
		long before = countAnswers();
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			answerRepository.save(answer("rollback-answer", authorId));
			OutboxEvent event = OutboxEvent.pending(OutboxAggregateType.ANSWER, 99L, OutboxEventType.ANSWER_PUBLISHED,
				"rollback-key", "{\"answerId\":99}", NOW);
			outboxRepository.save(event);
			outboxRepository.save(event);
		})).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(countAnswers()).isEqualTo(before);
	}

	@Test
	@DisplayName("open report와 block은 DB unique/check 제약 및 idempotent command를 적용한다")
	void persistsSafetyAndDeduplicatesOpenReport() {
		Report report = safetyRepository.saveReport(
			Report.forUser(recipientId, authorId, "HATE_OR_HARASSMENT", "detail", NOW));
		assertThat(safetyRepository.findOpenReport(recipientId, authorId, null, null)).contains(report);
		assertThatThrownBy(() -> safetyRepository.saveReport(
			Report.forUser(recipientId, authorId, "HATE_OR_HARASSMENT", "again", NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> safetyRepository.block(com.dnd.qello.safety.domain.UserBlock.create(authorId, authorId, NOW)))
			.isInstanceOf(com.dnd.qello.safety.error.SafetyException.class)
			.hasFieldOrPropertyWithValue(
				"errorCode", com.dnd.qello.safety.error.SafetyErrorCode.SELF_BLOCK_NOT_ALLOWED);
	}

	@Test
	@DisplayName("Outbox claim은 한 번만 성공하고 processed 전환은 멱등 상태를 보존한다")
	void claimsOutboxOnlyOnce() {
		OutboxEvent event = outboxRepository.save(OutboxEvent.pending(OutboxAggregateType.REPORT, 1L,
			OutboxEventType.REPORT_RESOLVED, "claim-key", "{\"reportId\":1}", NOW));
		assertThat(outboxRepository.claim(event.id(), NOW.plusSeconds(1))).isPresent();
		assertThat(outboxRepository.claim(event.id(), NOW.plusSeconds(2))).isEmpty();
		assertThat(outboxRepository.findEventById(event.id()).orElseThrow().status().name()).isEqualTo("PROCESSING");
	}

	@Test
	@DisplayName("알림 dedup과 기기 전달 unique가 recipient 단위로 중복을 막는다")
	void persistsNotificationAndDeliveryDedup() {
		Answer answer = answerRepository.save(answer("notification-answer", authorId));
		OutboxEvent event = outboxRepository.save(OutboxEvent.pending(OutboxAggregateType.ANSWER, answer.getId(),
			OutboxEventType.ANSWER_PUBLISHED, "notification-event", "{\"answerId\":" + answer.getId() + "}", NOW));
		Notification notification = notificationRepository.save(new Notification(null, recipientId, event.id(),
			NotificationType.ANSWER_RECEIVED, "answer:" + answer.getId(), null, answer.getId(), null,
			NotificationStatus.UNREAD, NOW, null));
		assertThat(notificationRepository.update(notification.markRead(NOW.plusSeconds(1)))).isTrue();
		long deviceId = device(recipientId);
		assertThat(notificationRepository.saveDelivery(com.dnd.qello.notification.domain.NotificationDelivery.pending(notification.id(),
			deviceId, NOW))).isNotNull();
		assertThatThrownBy(() -> notificationRepository.saveDelivery(com.dnd.qello.notification.domain.NotificationDelivery.pending(notification.id(),
			deviceId, NOW))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 PENDING outbox를 동시에 claim해도 한 worker만 성공한다")
	void claimsOutboxOnlyOnceUnderConcurrency() throws Exception {
		OutboxEvent event = outboxRepository.save(OutboxEvent.pending(OutboxAggregateType.ANSWER, 1L,
			OutboxEventType.ANSWER_PUBLISHED, "concurrent-claim", "{\"answerId\":1}", NOW));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = List.of(
				executor.submit(() -> claimInTransaction(event.id(), ready, start)),
				executor.submit(() -> claimInTransaction(event.id(), ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(results.get(0).get(10, TimeUnit.SECONDS) ^ results.get(1).get(10, TimeUnit.SECONDS)).isTrue();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("같은 media_id는 Answer에 한 번만 첨부된다")
	void mediaAttachmentIsOwnedByOneContent() {
		Answer answer = answerRepository.save(answer("media-answer", authorId));
		long mediaId = jdbc.queryForObject("""
			INSERT INTO media_asset (owner_id, status, storage_key, mime_type, byte_size, checksum,
				exif_stripped, moderation_status)
			VALUES (?, 'READY', 'f40-media', 'image/jpeg', 10, 'checksum', TRUE, 'PASSED')
			RETURNING id
			""", Long.class, authorId);
		MediaAttachment attachment = new MediaAttachment(mediaId, authorId, null, answer.getId(), 0);
		assertThat(mediaAttachmentRepository.save(attachment)).isEqualTo(attachment);
		assertThatThrownBy(() -> mediaAttachmentRepository.save(attachment))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("한 질문글에 답변은 1회이며 거절된 답변은 자리를 비켜 준다")
	void allowsOneLiveAnswerPerRecipient() {
		Answer first = answerRepository.save(answer("one-per-recipient-1", authorId));

		assertThatThrownBy(() -> answerRepository.save(answer("one-per-recipient-2", authorId)))
			.isInstanceOf(DataIntegrityViolationException.class);

		jdbc.update("UPDATE answer SET status = 'REJECTED' WHERE id = ?", first.getId());

		Answer rewritten = answerRepository.save(answer("one-per-recipient-3", authorId));

		assertThat(rewritten.getId()).isNotEqualTo(first.getId());
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM answer WHERE post_recipient_id = ? AND status NOT IN ('REJECTED', 'DELETED')",
			Integer.class, postRecipientId)).isEqualTo(1);
	}

	@Test
	@DisplayName("삭제된 답변은 더 이상 자리를 비켜주지 않는다 — 삭제 후 재작성은 불가하다")
	void deletedAnswerNoLongerFreesTheSlot() {
		Answer first = answerRepository.save(answer("deleted-frees-slot-1", authorId));
		answerRepository.save(first.delete(NOW.plusSeconds(10)));

		assertThatThrownBy(() -> answerRepository.save(answer("deleted-frees-slot-2", authorId)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("edit_count와 edited_at의 동치 위반, edit_count 안전 상한(10) 초과는 DB CHECK가 즉시 거절한다")
	void rejectsEditCountEditedAtViolationsAtTheDatabaseLevel() {
		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at,
				 published_at, edited_at, edit_count)
			VALUES (?, ?, 'PUBLISHED', 'edit-check-equivalence', '답변', ?, 45, 'NEAR', 5000, 'PASSED', ?, ?, ?, 0)
			""", postRecipientId, authorId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plusSeconds(60))))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at,
				 published_at, edited_at, edit_count)
			VALUES (?, ?, 'PUBLISHED', 'edit-check-equivalence-reverse', '답변', ?, 45, 'NEAR', 5000, 'PASSED', ?, ?, NULL, 1)
			""", postRecipientId, authorId, REGION, Timestamp.from(NOW), Timestamp.from(NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbc.update("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at,
				 published_at, edited_at, edit_count)
			VALUES (?, ?, 'PUBLISHED', 'edit-check-ceiling', '답변', ?, 45, 'NEAR', 5000, 'PASSED', ?, ?, ?, 11)
			""", postRecipientId, authorId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plusSeconds(60))))
			.isInstanceOf(DataIntegrityViolationException.class);

		int insertedRows = jdbc.update("""
			INSERT INTO answer
				(post_recipient_id, author_id, status, idempotency_key, body_text, coarse_region_code,
				 bearing_from_sender_deg, distance_band, distance_m, moderation_status, submitted_at,
				 published_at, edited_at, edit_count)
			VALUES (?, ?, 'PUBLISHED', 'edit-check-valid-control', '답변', ?, 45, 'NEAR', 5000, 'PASSED', ?, ?, NULL, 0)
			""", postRecipientId, authorId, REGION, Timestamp.from(NOW), Timestamp.from(NOW));

		assertThat(insertedRows).isEqualTo(1);
	}

	private boolean claimInTransaction(long eventId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		return transactionTemplate.execute(status -> outboxRepository.claim(eventId, NOW.plusSeconds(1)).isPresent());
	}

	private Answer answer(String key, long author) {
		return Answer.submit(postRecipientId, author, key, "답변", REGION, BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long question() {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'F40 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), authorId);
	}

	private long post(long questionId) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', 'f40-post', '글', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, recipientId, questionId, REGION, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}

	private long device(long userId) {
		return notificationRepository.saveDevice(new com.dnd.qello.notification.domain.PushDevice(null, userId,
			com.dnd.qello.notification.domain.PushPlatform.ANDROID, new byte[] {1, 2}, "fingerprint-" + userId,
			com.dnd.qello.notification.domain.PushDeviceStatus.ACTIVE, NOW, null)).id();
	}

	private long countAnswers() {
		return jdbc.queryForObject("SELECT count(*) FROM answer WHERE idempotency_key LIKE 'rollback-%'", Long.class);
	}
}
