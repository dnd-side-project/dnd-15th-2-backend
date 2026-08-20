/**
 * Created at: 2026-08-19T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS-INT-011 through INT-017
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.notification.domain.NotificationPreference;
import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.fanout.ReportResolutionFanOutWorker;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.safety.domain.ModerationDecision;
import com.dnd.qello.safety.domain.Report;
import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseStatus;
import com.dnd.qello.safety.domain.ReportStatus;
import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;
import com.dnd.qello.safety.repository.ReportCaseRepository;
import com.dnd.qello.safety.repository.SafetyRepository;
import com.dnd.qello.safety.service.SafetyCaseResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class ReportResolutionIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH155-RES";
	private static final String OWNER = "gh155-resolution-worker";
	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private SafetyRepository safetyRepository;
	@Autowired
	private ReportCaseRepository reportCaseRepository;
	@Autowired
	private SafetyCaseResolutionService resolutionService;
	@Autowired
	private NotificationRepository notificationRepository;
	@MockitoSpyBean
	private OutboxEventRepository outboxEventRepository;
	@Autowired
	private ReportResolutionFanOutWorker worker;
	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("TRUNCATE report_case_event");
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM notification_preference");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM report_case");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES (?, 'KR', 'GH155 Resolution Test', 'REGION')", REGION);
	}

	@Test
	@DisplayName("INT-011: 신고자 2명이 병합된 사건을 종결하면 신고 1건당 outbox event가 1개씩(총 2개) 나온다")
	void resolvingMergedCaseEmitsOneOutboxEventPerReport() {
		Fixture fixture = fixture();
		long reporterA = account("reporterA");
		long reporterB = account("reporterB");
		long caseId = openCase(fixture.answerId());
		long reportAId = attachReport(reporterA, fixture.answerId(), caseId);
		long reportBId = attachReport(reporterB, fixture.answerId(), caseId);

		resolutionService.resolveCase(caseId, ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		List<Map<String, Object>> events = jdbc.queryForList("""
			SELECT dedup_key, aggregate_id FROM outbox_event
			WHERE aggregate_type = 'REPORT' AND event_type = 'REPORT_RESOLVED'
			ORDER BY id
			""");
		assertThat(events).hasSize(2);
		assertThat(events).extracting(row -> row.get("dedup_key")).containsExactlyInAnyOrder(
			"report-resolved:" + reportAId, "report-resolved:" + reportBId);
	}

	@Test
	@DisplayName("INT-012: outbox payload에 대상·작성자 식별자가 없다")
	void outboxPayloadOmitsTargetAndAuthorIdentifiers() throws Exception {
		Fixture fixture = fixture();
		long reporterA = account("reporterA");
		long caseId = openCase(fixture.answerId());
		attachReport(reporterA, fixture.answerId(), caseId);

		resolutionService.resolveCase(caseId, ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		String payload = jdbc.queryForObject("""
			SELECT payload FROM outbox_event WHERE aggregate_type = 'REPORT' AND event_type = 'REPORT_RESOLVED'
			""", String.class);
		JsonNode node = objectMapper.readTree(payload);
		assertThat(node.fieldNames()).toIterable().containsExactly("reportId");
		assertThat(node.has("targetUserId")).isFalse();
		assertThat(node.has("answerId")).isFalse();
		assertThat(node.has("authorId")).isFalse();
	}

	@Test
	@DisplayName("INT-013: fan-out worker를 실행하면 신고자별로 알림 1건과 활성 기기 수만큼 전달 행이 생긴다")
	void fanOutCreatesNotificationPerReporterWithDeliveries() {
		Fixture fixture = fixture();
		long reporterA = account("reporterA");
		long reporterB = account("reporterB");
		device(reporterA);
		device(reporterB);
		long caseId = openCase(fixture.answerId());
		attachReport(reporterA, fixture.answerId(), caseId);
		attachReport(reporterB, fixture.answerId(), caseId);
		resolutionService.resolveCase(caseId, ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		ReportResolutionFanOutWorker.BatchResult result = worker.processBatch(command(10));

		assertThat(result.outcomes()).containsExactly(
			ReportResolutionFanOutWorker.Outcome.PROCESSED, ReportResolutionFanOutWorker.Outcome.PROCESSED);
		assertThat(notificationCount(reporterA)).isEqualTo(1);
		assertThat(notificationCount(reporterB)).isEqualTo(1);
		assertThat(deliveryCount(reporterA)).isEqualTo(1);
		assertThat(deliveryCount(reporterB)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-014: worker를 두 번 실행해도 알림과 전달 행이 중복되지 않는다")
	void fanOutIsIdempotentAcrossReruns() {
		Fixture fixture = fixture();
		long reporterA = account("reporterA");
		device(reporterA);
		long caseId = openCase(fixture.answerId());
		attachReport(reporterA, fixture.answerId(), caseId);
		resolutionService.resolveCase(caseId, ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		worker.processBatch(command(10));
		long notificationsAfterFirst = notificationCount(reporterA);
		long deliveriesAfterFirst = deliveryCount(reporterA);

		ReportResolutionFanOutWorker.BatchResult rerun = worker.processBatch(command(10));

		assertThat(rerun.claimed()).isZero();
		assertThat(notificationCount(reporterA)).isEqualTo(notificationsAfterFirst);
		assertThat(deliveryCount(reporterA)).isEqualTo(deliveriesAfterFirst);
	}

	@Test
	@DisplayName("INT-015: push 선호가 꺼져 있어도 인앱 알림 행은 생성되고 push 전달만 게이트된다")
	void inAppNotificationIsCreatedEvenWhenPushPreferenceDisabled() {
		Fixture fixture = fixture();
		long reporterA = account("reporterA");
		device(reporterA);
		notificationRepository.savePreference(
			new NotificationPreference(NotificationType.REPORT_RESOLVED, reporterA, false, null, null));
		long caseId = openCase(fixture.answerId());
		attachReport(reporterA, fixture.answerId(), caseId);
		resolutionService.resolveCase(caseId, ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10));

		worker.processBatch(command(10));

		assertThat(notificationCount(reporterA)).isEqualTo(1);
		assertThat(deliveryCount(reporterA)).isZero();
	}

	@Test
	@DisplayName("INT-016: 사건 종결 중 outbox 저장이 실패하면 사건과 신고 상태가 모두 롤백된다")
	void outboxSaveFailureRollsBackCaseAndReportState() {
		Fixture fixture = fixture();
		long reporterA = account("reporterA");
		long caseId = openCase(fixture.answerId());
		long reportId = attachReport(reporterA, fixture.answerId(), caseId);
		doThrow(new DataIntegrityViolationException("gh155 rollback test"))
			.when(outboxEventRepository).save(any());

		assertThatThrownBy(() -> resolutionService.resolveCase(caseId, ModerationDecision.NO_VIOLATION, NOW.plusSeconds(10)))
			.isInstanceOf(DataIntegrityViolationException.class);

		ReportCase persisted = reportCaseRepository.findById(caseId).orElseThrow();
		assertThat(persisted.status()).isNotEqualTo(ReportCaseStatus.RESOLVED);
		Report persistedReport = safetyRepository.findReportById(reportId).orElseThrow();
		assertThat(persistedReport.status()).isNotEqualTo(ReportStatus.NO_VIOLATION);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'REPORT' AND aggregate_id = ?",
			Integer.class, reportId)).isZero();
	}

	@Test
	@DisplayName("INT-017: 같은 사건을 동시에 두 번 종결하면 정확히 한 번만 성공하고 outbox event는 신고 수만큼만 생긴다")
	void concurrentResolveAttemptsResolveExactlyOnce() throws Exception {
		Fixture fixture = fixture();
		long reporterA = account("reporterA");
		long reporterB = account("reporterB");
		long caseId = openCase(fixture.answerId());
		attachReport(reporterA, fixture.answerId(), caseId);
		attachReport(reporterB, fixture.answerId(), caseId);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = List.of(
				executor.submit(() -> resolveAttempt(caseId, ready, start)),
				executor.submit(() -> resolveAttempt(caseId, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			boolean firstSucceeded = results.get(0).get(10, TimeUnit.SECONDS);
			boolean secondSucceeded = results.get(1).get(10, TimeUnit.SECONDS);
			assertThat(firstSucceeded ^ secondSucceeded).isTrue();
		} finally {
			executor.shutdownNow();
		}

		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'REPORT' AND event_type = 'REPORT_RESOLVED'",
			Integer.class)).isEqualTo(2);
	}

	private boolean resolveAttempt(long caseId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await(5, TimeUnit.SECONDS);
		try {
			resolutionService.resolveCase(caseId, ModerationDecision.ACTIONED, NOW.plusSeconds(10));
			return true;
		} catch (SafetyException e) {
			assertThat(e.getErrorCode()).isEqualTo(SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED);
			return false;
		}
	}

	private long notificationCount(long recipientId) {
		return jdbc.queryForObject(
			"SELECT count(*) FROM notification WHERE recipient_id = ? AND notification_type = 'REPORT_RESOLVED'",
			Long.class, recipientId);
	}

	private long deliveryCount(long recipientId) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM notification_delivery nd
			JOIN notification n ON n.id = nd.notification_id
			WHERE n.recipient_id = ? AND n.notification_type = 'REPORT_RESOLVED'
			""", Long.class, recipientId);
	}

	private long device(long userId) {
		return notificationRepository.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID,
			new byte[] {1, 2}, "fingerprint-" + userId, PushDeviceStatus.ACTIVE, NOW, null)).id();
	}

	private long openCase(long answerId) {
		return reportCaseRepository.save(ReportCase.open(null, null, answerId, NOW)).id();
	}

	private long attachReport(long reporterId, long answerId, long caseId) {
		Report saved = safetyRepository.saveReport(
			Report.forAnswer(reporterId, answerId, "SPAM_OR_ADVERTISING", null, NOW));
		safetyRepository.updateReport(saved.attachToCase(caseId));
		return saved.id();
	}

	private ReportResolutionFanOutWorker.BatchCommand command(int limit) {
		return new ReportResolutionFanOutWorker.BatchCommand(limit, OWNER, NOW.plusSeconds(30),
			NOW.plusSeconds(90), new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(attempt)));
	}

	private Fixture fixture() {
		long sender = account("sender");
		long author = account("author");
		long questionId = question(sender);
		long postId = post(sender, questionId);
		long postRecipientId = recipient(postId, author);
		long answerId = publishedAnswer(postRecipientId, author, "답변 본문");
		return new Fixture(sender, author, postId, answerId);
	}

	private record Fixture(long senderId, long authorId, long postId, long answerId) {
	}

	private long publishedAnswer(long postRecipientId, long authorId, String bodyText) {
		Answer submitted = Answer.submit(postRecipientId, authorId, "gh155-res-" + postRecipientId, bodyText,
			REGION, BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		Answer published = submitted.startSafetyCheck().markSafetyPassed().publish(NOW);
		return answerRepository.save(published).getId();
	}

	private long recipient(long postId, long recipientId) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, recipientId, REGION, Timestamp.from(NOW));
	}

	private long account(String nicknamePrefix) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nicknamePrefix + "-" + System.nanoTime());
	}

	private long question(long approverId) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'GH155 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), approverId);
	}

	private long post(long senderId, long questionId) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '글', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, "gh155-post-" + System.nanoTime(), REGION, Timestamp.from(NOW),
			Timestamp.from(NOW), Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}
}
