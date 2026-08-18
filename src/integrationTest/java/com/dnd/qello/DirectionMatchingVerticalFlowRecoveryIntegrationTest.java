/**
 * Created at: 2026-08-18T16:10:00+09:00
 * Source scenario: TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW-INT-006 through INT-008
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;

/**
 * GH-127 Gap C. 단계별 임대 만료·재시도·{@code DEAD} 전환 자체는
 * {@code OutboxLeaseIntegrationTest}·{@code DirectionMatchingWorkerConcurrencyIntegrationTest}가
 * 이미 소유한다. 이 클래스는 그 전이가 일어난 뒤 체인 전체가 어떤 최종 상태로 남는지,
 * 그리고 그 상태 위에서 나머지 체인이 계속 정상 작동하는지만 다룬다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(DirectionFlow127TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionMatchingVerticalFlowRecoveryIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-FLOW-127-RECOVERY";
	private static final Instant NOW = Instant.parse("2026-08-18T09:00:00Z");
	private static final double SENDER_LAT = 37.5000;
	private static final double SENDER_LON = 127.0000;

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private DirectionPresenceService presenceService;
	@Autowired
	private DirectionPostApplicationService postApplicationService;
	@Autowired
	private DirectionMatchingWorker matchingWorker;
	@Autowired
	private RecipientNotificationFanOutWorker fanOutWorker;
	@Autowired
	private OutboxEventRepository outboxEventRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private DirectionFlow127MutableClock clock;

	@BeforeEach
	void reset() {
		clock.setInstant(NOW);
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM push_device");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Flow 127 Recovery', 'REGION')", REGION);
	}

	@Test
	@DisplayName("INT-006: 검열 미통과 상태로 매칭이 DEAD 종료되면 질문글은 MATCHING에 머물고 슬롯은 하나도 점유되지 않는다")
	void deadMatchingLeavesPostStuckWithoutLeakingSlots() {
		long senderId = account("int006-sender");
		long questionId = activeQuestion(senderId);
		long candidateId = account("int006-candidate");
		updatePresence(senderId, SENDER_LAT, SENDER_LON);
		updatePresence(candidateId, SENDER_LAT + 0.0010, SENDER_LON);

		long schemeId = octantSchemeId();
		var submitResult = postApplicationService.submit(senderId, "int006-submit",
			new DirectionPostApplicationService.SubmitCommand(questionId, schemeId, "N", "검열 미통과 본문", List.of()));
		long postId = submitResult.post().getId();
		// 의도적으로 moderation_status를 PASSED로 전이시키지 않는다 — 검열 대기(PENDING)
		// 상태에서 매칭이 재시도 소진 후 DEAD로 끝나는 경로를 관측한다.
		assertThat(jdbc.queryForObject("SELECT moderation_status FROM direction_post WHERE id = ?", String.class, postId))
			.isEqualTo("PENDING");

		// maxAttempts=1이므로 claim이 attempt_count를 1로 올린 첫 실패에서 즉시 DEAD로 끝난다.
		OutboxRetryPolicy singleAttemptPolicy = new OutboxRetryPolicy(1, attempt -> Duration.ofSeconds(1));
		DirectionMatchingWorker.BatchResult result = matchingWorker.processBatch(
			new DirectionMatchingWorker.BatchCommand(10, "int006-matching-worker", NOW.plusSeconds(30),
				NOW.plusSeconds(90), singleAttemptPolicy));

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.DEAD);
		assertThat(jdbc.queryForObject(
			"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?", String.class, postId))
			.isEqualTo("DEAD");
		// 관측된 최종 상태: 질문글은 MATCHING에 머물고 자동 복구·발신자 알림 경로가 없다.
		// 이 이슈의 범위는 이 상태를 관측하는 것까지이며, 복구 정책은 별도 Issue로 보고한다.
		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
			.isEqualTo("MATCHING");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId)).isZero();
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM recipient_receive_state WHERE user_id = ?", Long.class, candidateId)).isZero();
	}

	@Test
	@DisplayName("INT-007: 알림 대상 하나가 차단 상태여도 fan-out은 전부 PROCESSED로 끝나고 억제된 수신자의 수신 자격은 유지된다")
	void blockedRecipientKeepsEligibilityWhileNotificationIsSuppressed() {
		long senderId = account("int007-sender");
		long questionId = activeQuestion(senderId);
		List<Long> candidateIds = List.of(account("int007-candidate-0"), account("int007-candidate-1"), account("int007-candidate-2"));
		updatePresence(senderId, SENDER_LAT, SENDER_LON);
		for (int index = 0; index < candidateIds.size(); index++) {
			updatePresence(candidateIds.get(index), SENDER_LAT + 0.0010 + index * 0.0001, SENDER_LON);
		}

		long schemeId = octantSchemeId();
		var submitResult = postApplicationService.submit(senderId, "int007-submit",
			new DirectionPostApplicationService.SubmitCommand(questionId, schemeId, "N", "부분 실패 검증 본문", List.of()));
		long postId = submitResult.post().getId();
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);

		OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1));
		DirectionMatchingWorker.BatchResult matchResult = matchingWorker.processBatch(
			new DirectionMatchingWorker.BatchCommand(10, "int007-matching-worker", NOW.plusSeconds(30),
				NOW.plusSeconds(90), retryPolicy));
		assertThat(matchResult.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);

		long blockedRecipientId = candidateIds.get(0);
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at, released_at) VALUES (?, ?, ?, NULL)",
			blockedRecipientId, senderId, Timestamp.from(NOW.plusSeconds(31)));
		for (long candidateId : candidateIds) {
			device(candidateId, "int007-device-" + candidateId);
		}

		RecipientNotificationFanOutWorker.BatchResult fanOutResult = fanOutWorker.processBatch(
			new RecipientNotificationFanOutWorker.BatchCommand(10, "int007-fanout-worker", NOW.plusSeconds(32),
				NOW.plusSeconds(92), retryPolicy));

		assertThat(fanOutResult.outcomes()).hasSize(3)
			.allMatch(outcome -> outcome == RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isEqualTo(2L);
		long blockedPostRecipientId = jdbc.queryForObject(
			"SELECT id FROM post_recipient WHERE post_id = ? AND recipient_id = ?", Long.class, postId, blockedRecipientId);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM notification WHERE recipient_id = ?", Long.class, blockedRecipientId)).isZero();
		// 억제는 알림 생성만 건너뛴다 — 수신 자격(post_recipient 상태·슬롯)은 훼손되지 않는다.
		assertThat(jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, blockedPostRecipientId))
			.isEqualTo("AVAILABLE");
		assertThat(jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, blockedRecipientId))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("INT-008: 완료 전 임대가 만료된 매칭 이벤트를 재claim해도 체인은 중복 수신자 없이 정상 완료된다")
	void reclaimingExpiredMatchingLeaseCompletesChainWithoutDuplicateRecipients() {
		long senderId = account("int008-sender");
		long questionId = activeQuestion(senderId);
		List<Long> candidateIds = List.of(account("int008-candidate-0"), account("int008-candidate-1"));
		updatePresence(senderId, SENDER_LAT, SENDER_LON);
		for (int index = 0; index < candidateIds.size(); index++) {
			updatePresence(candidateIds.get(index), SENDER_LAT + 0.0010 + index * 0.0001, SENDER_LON);
		}

		long schemeId = octantSchemeId();
		var submitResult = postApplicationService.submit(senderId, "int008-submit",
			new DirectionPostApplicationService.SubmitCommand(questionId, schemeId, "N", "재claim 검증 본문", List.of()));
		long postId = submitResult.post().getId();
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);

		// 크래시 시뮬레이션: outbox 행을 직접 claim만 하고(짧은 lease) 어떤 도메인 transaction도
		// 실행하지 않는다 — DirectionMatchingWorker.processBatch를 호출하지 않았으므로
		// persistRecipients도, completeClaimOrThrow도 실행되지 않는다.
		outboxEventRepository.claimDue(Set.of(OutboxEventType.RECIPIENT_MATCH_REQUESTED), 10, "int008-crashed-worker",
			NOW.plusSeconds(30), NOW.plusSeconds(31));

		OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1));
		DirectionMatchingWorker.BatchResult recovered = matchingWorker.processBatch(
			new DirectionMatchingWorker.BatchCommand(10, "int008-recovery-worker", NOW.plusSeconds(35),
				NOW.plusSeconds(95), retryPolicy));

		assertThat(recovered.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
			.isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
			.isEqualTo(2L);
		for (long candidateId : candidateIds) {
			assertThat(jdbc.queryForObject(
				"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, candidateId))
				.isEqualTo(1);
		}

		for (long candidateId : candidateIds) {
			device(candidateId, "int008-device-" + candidateId);
		}
		RecipientNotificationFanOutWorker.BatchResult fanOutResult = fanOutWorker.processBatch(
			new RecipientNotificationFanOutWorker.BatchCommand(10, "int008-fanout-worker", NOW.plusSeconds(36),
				NOW.plusSeconds(96), retryPolicy));
		assertThat(fanOutResult.outcomes()).hasSize(2)
			.allMatch(outcome -> outcome == RecipientNotificationFanOutWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isEqualTo(2L);
	}

	private void updatePresence(long userId, double latitude, double longitude) {
		presenceService.update(userId, new DirectionPresenceService.UpdateCommand(
			BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude), BigDecimal.ONE, true, NOW));
	}

	private long octantSchemeId() {
		return jdbc.queryForObject("SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'", Long.class);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long activeQuestion(long approverId) {
		return jdbc.queryForObject("""
			INSERT INTO approved_question (source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'flow-127-recovery question', 'TEXT', ?, ?, ?, ?) RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
			Timestamp.from(NOW.minusSeconds(120)), approverId);
	}

	private void device(long userId, String fingerprint) {
		notificationRepository.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID,
			new byte[] {1, 2, 3, 4}, fingerprint, PushDeviceStatus.ACTIVE, NOW, null));
	}
}
