/**
 * Created at: 2026-08-13T17:45:00+09:00
 * Source scenario: TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-001 through INT-009, INT-013, INT-015
 * Source scenario: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-014
 * Source scenario: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION (release fixture and DIRECTION_POST outbox filter, added 2026-10-07T22:14:59+09:00)
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-006 (added 2026-10-09T18:47:28+09:00)
 * Source scenario: TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-001 (base와 중복된 컨텍스트 폐기 선언 제거, added 2026-10-10T15:19:46+09:00)
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-017, INT-023, INT-024
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dnd.qello.direction.config.DirectionSchemeProperties;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.repository.RecipientReceiveStateRepository;
import com.dnd.qello.direction.security.AesGcmCoordinateProtector;
import com.dnd.qello.direction.security.CoordinateKeyRing;
import com.dnd.qello.direction.security.CoordinateProtectionException;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;
import com.dnd.qello.direction.service.DirectionPostModerationVerdictApplier;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.repository.OutboxEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class DirectionMatchingWorkerIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-MATCHING-WORKER";
	private static final String OTHER_REGION = "TEST-DIRECTION-MATCHING-WORKER-OTHER";
	private static final Instant NOW = Instant.parse("2026-08-13T08:30:00Z");

	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private DirectionSchemeRepository schemeRepository;
	@Autowired
	private DirectionPostService postService;
	@Autowired
	private DirectionMatchingWorker worker;
	@Autowired
	private OutboxEventRepository outboxRepository;
	@Autowired
	private RecipientReceiveStateRepository receiveStateRepository;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private Clock clock;
	@Autowired
	private DirectionSchemeProperties schemeProperties;
	@Autowired
	private DirectionPostModerationVerdictApplier verdictApplier;
	@Autowired
	private CoordinateProtector protector;

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM user_block");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM direction_segment");
		jdbc.update("DELETE FROM direction_scheme");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", OTHER_REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", OTHER_REGION);
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Matching Worker', 'REGION')",
				REGION);
		jdbc.update(
				"INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Matching Worker Other', 'REGION')",
				OTHER_REGION);
		// #137: 본문 있는 질문글 제출은 승격된 release가 없으면 moderation job 접수에서 거절된다.
		AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, 1L);
	}

	@Test
	@DisplayName("위치 PUT·preview·submit·worker 실제 경로는 두 bytea 좌표를 저장하고 응답·payload·로그에 좌표를 노출하지 않는다")
	void protectsCoordinatesAcrossHttpSubmissionAndMatching(CapturedOutput output) throws Exception {
		Fixture fixture = fixtureWithCandidates(1);
		Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
		jdbc.update("UPDATE direction_scheme SET code = ? WHERE id = ?", schemeProperties.schemeCode(),
				fixture.schemeId());
		jdbc.update("UPDATE approved_question SET active_from = ?, active_until = ? WHERE id = ?",
				Timestamp.from(at.minusSeconds(120)), Timestamp.from(at.plusSeconds(7200)), fixture.questionId());
		presenceRepository.save(ActiveUserPresence.create(fixture.candidateIds().getFirst(),
				new BigDecimal("37.51334567"), new BigDecimal("127.09876543"), null, REGION,
				BigDecimal.ONE, true, at.minusSeconds(1), at.plusSeconds(7200)));

		long postId = submitThroughHttp(fixture, at);
		Instant matchingAt = jdbc.queryForObject("""
				SELECT next_attempt_at FROM outbox_event
				WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?
					AND event_type = 'RECIPIENT_MATCH_REQUESTED'
				""", Timestamp.class, postId).toInstant();
		verdictApplier.applyVerdict(postId, FilterVerdict.ALLOW, matchingAt);
		var result = worker.processBatch(new DirectionMatchingWorker.BatchCommand(10, "http-coordinate-388",
				matchingAt, matchingAt.plusSeconds(60),
				new com.dnd.qello.notification.domain.OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))));

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForList("SELECT recipient_id FROM post_recipient WHERE post_id = ?",
				Long.class, postId)).containsExactly(fixture.candidateIds().getFirst());
		assertStoredCoordinate("active_user_presence", "position_ciphertext", "user_id", fixture.senderId(),
				CoordinatePurpose.ACTIVE_USER_PRESENCE);
		assertStoredCoordinate("post_audience", "origin_position_ciphertext", "post_id", postId,
				CoordinatePurpose.POST_AUDIENCE);
		String observed = result + output.getAll()
				+ jdbc.queryForList("SELECT payload::text FROM outbox_event", String.class);
		assertThat(observed.contains("37.51234567") || observed.contains("127.09876543"))
				.as("successful flow must not expose exact coordinates").isFalse();
	}

	@Test
	@DisplayName("PASSED 질문글은 실행 시점 후보를 확정하고 slot과 confirmed Outbox를 함께 생성한다")
	void confirmsRecipientsAtomically() {
		Fixture fixture = fixtureWithCandidates(3);
		long postId = submitAndPass(fixture, "worker-normal");

		DirectionMatchingWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId)).isEqualTo(OutboxStatus.PROCESSED.name());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isEqualTo(3L);
		assertThat(jdbc.queryForObject(
				"SELECT sum(active_unhandled_count) FROM recipient_receive_state WHERE user_id IN (?, ?, ?)",
				Long.class, fixture.candidateIds().toArray())).isEqualTo(3L);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT' AND event_type = ?",
				Long.class, OutboxEventType.RECIPIENTS_CONFIRMED.name())).isEqualTo(3L);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isZero();
	}

	@Test
	@DisplayName("GLOBAL worker는 질문글 표시 지역과 다른 후보도 방향·거리 조건이면 확정한다")
	void matchesCandidateOutsidePostRegionInGlobalScope() {
		Fixture fixture = fixtureWithCandidates(1);
		long candidateId = fixture.candidateIds().getFirst();
		jdbc.update("UPDATE user_account SET coarse_region_code = ? WHERE id = ?", OTHER_REGION, candidateId);
		jdbc.update("UPDATE active_user_presence SET coarse_region_code = ? WHERE user_id = ?", OTHER_REGION,
				candidateId);
		long postId = submitAndPass(fixture, "worker-global-region");

		assertThat(worker.processBatch(command()).outcomes())
				.containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForList("SELECT recipient_id FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.containsExactly(candidateId);
		assertThat(jdbc.queryForObject("SELECT matched_region_code FROM post_recipient WHERE post_id = ?", String.class,
				postId))
				.isEqualTo(OTHER_REGION);
	}

	@Test
	@DisplayName("moderation이 PENDING이면 수신자와 슬롯을 만들지 않고 retryable 상태로 남긴다")
	void holdsUntilModerationPasses() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submit(fixture, "worker-pending");

		DirectionMatchingWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.RETRYABLE);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_receive_state", Long.class)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId))
				.isEqualTo(OutboxStatus.FAILED.name());
	}

	@Test
	@DisplayName("deadline 경계의 질문글은 EXPIRED가 되고 recipient와 slot을 만들지 않는다")
	void expiresAtDeadlineWithoutRecipients() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submitAndPass(fixture, "worker-expired");
		jdbc.update("UPDATE direction_post SET expires_at = ? WHERE id = ?", Timestamp.from(NOW), postId);

		DirectionMatchingWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("EXPIRED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_receive_state", Long.class)).isZero();
	}

	@Test
	@DisplayName("후보가 없어도 질문글은 ACTIVE로 완료되고 후속 Outbox는 생성하지 않는다")
	void completesWithZeroCandidates() {
		Fixture fixture = fixtureWithCandidates(0);
		long postId = submitAndPass(fixture, "worker-empty");

		worker.processBatch(command());

		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'",
				Long.class)).isZero();
	}

	@Test
	@DisplayName("실행 시점에 비활성·만료·차단된 후보는 제외하고 정상 후보만 확정한다")
	void reevaluatesCandidateEligibilityAtExecutionTime() {
		Fixture fixture = fixtureWithCandidates(4);
		long postId = submitAndPass(fixture, "worker-current-state");
		long expiredPresence = fixture.candidateIds().get(0);
		long inactive = fixture.candidateIds().get(1);
		long blocked = fixture.candidateIds().get(2);
		jdbc.update("UPDATE active_user_presence SET expires_at = ? WHERE user_id = ?",
				Timestamp.from(NOW.minusSeconds(1)), expiredPresence);
		jdbc.update("UPDATE user_account SET status = 'BLOCKED' WHERE id = ?", inactive);
		long senderId = fixture.senderId();
		jdbc.update("INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)", senderId, blocked,
				Timestamp.from(NOW));

		worker.processBatch(command());

		assertThat(jdbc.queryForList("SELECT recipient_id FROM post_recipient WHERE post_id = ? ORDER BY recipient_id",
				Long.class, postId))
				.containsExactly(fixture.candidateIds().get(3));
	}

	@ParameterizedTest
	@ValueSource(strings = {"WITHDRAWAL_PENDING", "BLOCKED"})
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-INT-006: 발신자가 탈퇴 유예 중이거나 차단되면 PASSED 질문글도 수신자·슬롯 없이 매칭 이벤트를 완료하고 질문글은 MATCHING으로 남는다")
	void completesMatchingWithoutRecipientsWhenSenderIsNotActive(String senderStatus) {
		Fixture fixture = fixtureWithCandidates(2);
		long postId = submitAndPass(fixture, "worker-sender-" + senderStatus.toLowerCase());
		jdbc.update("""
				UPDATE user_account
				SET status = ?, withdrawal_requested_at = CASE WHEN ? = 'WITHDRAWAL_PENDING' THEN ?::timestamptz END
				WHERE id = ?
				""", senderStatus, senderStatus, Timestamp.from(NOW.minusSeconds(30)), fixture.senderId());

		DirectionMatchingWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_receive_state", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'",
				Long.class)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId)).isEqualTo(OutboxStatus.PROCESSED.name());
		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("MATCHING");
	}

	@Test
	@DisplayName("confirmed Outbox payload에는 정확 위치와 거리·방위를 저장하지 않는다")
	void keepsConfirmedPayloadCoarse() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submitAndPass(fixture, "worker-privacy");
		worker.processBatch(command());

		String payload = jdbc.queryForObject(
				"SELECT payload::text FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT' AND aggregate_id IN (SELECT id FROM post_recipient WHERE post_id = ?)",
				String.class, postId);
		assertThat(payload).doesNotContain("latitude", "longitude", "distance", "bearing", "37.501", "127.000");
	}

	@Test
	@DisplayName("wrap-around 방향과 거리 범위는 실행 시점 경계 규칙을 그대로 적용한다")
	void appliesWrapAroundDirectionAndDistanceBoundaries() {
		long senderId = account("worker-wrap-sender");
		long questionId = activeQuestion(senderId);
		long schemeId = eightSegmentScheme();
		presence(senderId, 37.5000, 127.0000, NOW.plusSeconds(3600), NOW.minusSeconds(120));
		List<Long> inside = List.of(
				account("worker-wrap-350"), account("worker-wrap-0"), account("worker-wrap-19"),
				account("worker-wrap-250"));
		presenceAtBearing(inside.get(0), 120, 350, NOW.plusSeconds(3600));
		presenceAtBearing(inside.get(1), 250, 0, NOW.plusSeconds(3600));
		presenceAtBearing(inside.get(2), 499, 19, NOW.plusSeconds(3600));
		presenceAtBearing(inside.get(3), 250, 25, NOW.plusSeconds(3600));
		long belowMin = account("worker-wrap-below-min");
		long outsideSector = account("worker-wrap-outside-sector");
		long aboveMax = account("worker-wrap-above-max");
		presenceAtBearing(belowMin, 99, 0, NOW.plusSeconds(3600));
		presenceAtBearing(outsideSector, 250, 20, NOW.plusSeconds(3600));
		presenceAtBearing(aboveMax, 501, 0, NOW.plusSeconds(3600));

		long postId = submitAndPass(new Fixture(senderId, questionId, schemeId, List.of()), "worker-wrap");
		jdbc.update(
				"UPDATE post_audience SET center_bearing_deg = 5, angular_width_deg = 30, min_distance_m = 100, max_distance_m = 500 WHERE post_id = ?",
				postId);

		worker.processBatch(command());

		assertThat(jdbc.queryForList("SELECT recipient_id FROM post_recipient WHERE post_id = ? ORDER BY recipient_id",
				Long.class, postId))
				.containsExactlyInAnyOrderElementsOf(inside.subList(0, 3));
		assertThat(jdbc.queryForList("SELECT recipient_id FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.doesNotContain(outsideSector, belowMin, aboveMax);
	}

	@Test
	@DisplayName("공정성 순서와 질문글별 최대 수신자 상한을 적용한다")
	void appliesFairnessOrderAndPostLimit() {
		Fixture fixture = fixtureWithCandidates(12);
		fixture.candidateIds()
				.forEach((id) -> jdbc.update("DELETE FROM recipient_receive_state WHERE user_id = ?", id));
		jdbc.update(
				"INSERT INTO recipient_receive_state (user_id, active_unhandled_count, recent_received_count, recent_window_started_at, updated_at) VALUES (?, 0, 0, ?, ?)",
				fixture.candidateIds().get(0), Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW));
		jdbc.update(
				"INSERT INTO recipient_receive_state (user_id, active_unhandled_count, recent_received_count, recent_window_started_at, updated_at) VALUES (?, 0, 0, ?, ?)",
				fixture.candidateIds().get(1), Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW));
		IntStream.range(2, 10).forEach(index -> jdbc.update(
				"INSERT INTO recipient_receive_state (user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at) VALUES (?, 0, ?, ?, ?, ?)",
				fixture.candidateIds().get(index), index - 1, Timestamp.from(NOW.minusSeconds(3600)),
				Timestamp.from(NOW.minusSeconds(index)), Timestamp.from(NOW)));
		IntStream.range(10, 12).forEach(index -> jdbc.update(
				"INSERT INTO recipient_receive_state (user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at) VALUES (?, 0, 20, ?, ?, ?)",
				fixture.candidateIds().get(index), Timestamp.from(NOW.minusSeconds(3600)),
				Timestamp.from(NOW.minusSeconds(index)), Timestamp.from(NOW)));
		long postId = submitAndPass(fixture, "worker-fairness-limit");

		worker.processBatch(command());

		assertThat(jdbc.queryForList("SELECT recipient_id FROM post_recipient WHERE post_id = ? ORDER BY recipient_id",
				Long.class, postId))
				.containsExactlyInAnyOrderElementsOf(fixture.candidateIds().subList(0, 10));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isEqualTo(10L);
		assertThat(jdbc.queryForObject(
				"SELECT sum(active_unhandled_count) FROM recipient_receive_state WHERE user_id IN (?, ?)", Long.class,
				fixture.candidateIds().get(10), fixture.candidateIds().get(11))).isZero();
	}

	@Test
	@DisplayName("receive state 잠금은 active count가 아니라 최근 수신량·시각·거리·ID 순서를 적용한다")
	void locksReceiveStatesInApprovedFairnessOrder() {
		Fixture fixture = fixtureWithCandidates(3);
		List<Long> candidates = fixture.candidateIds();
		receiveStateRepository.ensureForUsers(candidates, NOW);
		jdbc.update(
				"UPDATE recipient_receive_state SET active_unhandled_count = 4, recent_received_count = 0 WHERE user_id = ?",
				candidates.get(0));
		jdbc.update(
				"UPDATE recipient_receive_state SET active_unhandled_count = 0, recent_received_count = 0 WHERE user_id = ?",
				candidates.get(1));
		jdbc.update(
				"UPDATE recipient_receive_state SET active_unhandled_count = 0, recent_received_count = 0 WHERE user_id = ?",
				candidates.get(2));

		List<Long> locked = receiveStateRepository.lockAvailableUserIds(List.of(
				new RecipientReceiveStateRepository.LockCandidate(candidates.get(0), BigDecimal.valueOf(100)),
				new RecipientReceiveStateRepository.LockCandidate(candidates.get(1), BigDecimal.valueOf(200)),
				new RecipientReceiveStateRepository.LockCandidate(candidates.get(2), BigDecimal.valueOf(50))), 2, 5);

		assertThat(locked).containsExactly(candidates.get(2), candidates.get(0));
	}

	@Test
	@DisplayName("누락된 receive state를 초기화하고 재실행에서는 recipient와 count를 중복 생성하지 않는다")
	void initializesMissingStateAndIsIdempotentOnReplay() {
		Fixture fixture = fixtureWithCandidates(1);
		long candidateId = fixture.candidateIds().get(0);
		long postId = submitAndPass(fixture, "worker-replay");

		worker.processBatch(command());
		assertThat(jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?",
				Integer.class, candidateId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT recent_received_count FROM recipient_receive_state WHERE user_id = ?",
				Integer.class, candidateId)).isEqualTo(1);

		jdbc.update("UPDATE direction_post SET status = 'MATCHING', published_at = NULL WHERE id = ?", postId);
		jdbc.update(
				"""
						UPDATE outbox_event
						SET status = 'PENDING', attempt_count = 0, next_attempt_at = ?, processed_at = NULL,
							lease_owner = NULL, lease_expires_at = NULL
						WHERE aggregate_id = ? AND aggregate_type = 'DIRECTION_POST' AND event_type = 'RECIPIENT_MATCH_REQUESTED'
						""",
				Timestamp.from(NOW), postId);
		worker.processBatch(command());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?",
				Integer.class, candidateId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT' AND event_type = 'RECIPIENTS_CONFIRMED'",
				Long.class)).isEqualTo(1L);
	}

	@Test
	@DisplayName("REVIEW_HELD moderation은 PASSED 전까지 매칭을 보류한다")
	void holdsReviewHeldModeration() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submit(fixture, "worker-review-held");
		jdbc.update("UPDATE direction_post SET moderation_status = 'REVIEW_HELD' WHERE id = ?", postId);

		DirectionMatchingWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.RETRYABLE);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient", Long.class)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId))
				.isEqualTo(OutboxStatus.FAILED.name());
	}

	@Test
	@DisplayName("REJECTED moderation은 수신자 없이 원본 작업을 terminal 처리한다")
	void consumesRejectedModerationWithoutRecipients() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submit(fixture, "worker-rejected");
		jdbc.update("UPDATE direction_post SET moderation_status = 'REJECTED' WHERE id = ?", postId);

		DirectionMatchingWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient", Long.class)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId))
				.isEqualTo(OutboxStatus.PROCESSED.name());
	}

	@Test
	@DisplayName("현재 시각보다 이른 deadline은 EXPIRED로 닫히고 매칭하지 않는다")
	void expiresBeforeWorkerNow() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submitAndPass(fixture, "worker-expired-before-now");
		jdbc.update("UPDATE direction_post SET expires_at = ? WHERE id = ?", Timestamp.from(NOW.minusSeconds(1)),
				postId);

		worker.processBatch(command());

		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("EXPIRED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient", Long.class)).isZero();
	}

	@Test
	@DisplayName("정상 deadline 이후의 PASSED 질문글은 ACTIVE로 전환된다")
	void matchesBeforeDeadline() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submitAndPass(fixture, "worker-deadline-after");

		worker.processBatch(command());

		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId))
				.isEqualTo(1L);
	}

	@Test
	@DisplayName("confirmed Outbox 삽입 실패는 recipient·slot·post·source를 함께 rollback한다")
	void rollsBackMatchingTransactionWhenConfirmedOutboxFails() {
		Fixture fixture = fixtureWithCandidates(1);
		long postId = submitAndPass(fixture, "worker-confirmed-rollback");
		jdbc.execute("""
				CREATE OR REPLACE FUNCTION test_gh120_fail_confirmed_outbox()
				RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN
					IF NEW.event_type = 'RECIPIENTS_CONFIRMED' THEN
						RAISE EXCEPTION 'TEST-PLAN-GH-120 confirmed outbox rollback';
					END IF;
					RETURN NEW;
				END;
				$$
				""");
		jdbc.execute(
				"CREATE TRIGGER test_gh120_fail_confirmed_outbox BEFORE INSERT ON outbox_event FOR EACH ROW EXECUTE FUNCTION test_gh120_fail_confirmed_outbox()");
		try {
			assertThat(worker.processBatch(command()).outcomes())
					.containsExactly(DirectionMatchingWorker.Outcome.RETRYABLE);
		} finally {
			jdbc.execute("DROP TRIGGER IF EXISTS test_gh120_fail_confirmed_outbox ON outbox_event");
			jdbc.execute("DROP FUNCTION IF EXISTS test_gh120_fail_confirmed_outbox()");
		}

		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId))
				.isEqualTo("MATCHING");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_receive_state", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'",
				Long.class)).isZero();
		assertThat(jdbc.queryForObject(
				"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
				String.class, postId))
				.isEqualTo(OutboxStatus.FAILED.name());
	}

	@Test
	@DisplayName("같은 batch의 정상 event는 손상된 event의 DEAD 처리와 독립적으로 완료된다")
	void isolatesPermanentEventFailureWithinBatch() {
		Fixture fixture = fixtureWithCandidates(1);
		long goodPostId = submitAndPass(fixture, "worker-batch-good");
		long badPostId = 999_999_991L;
		outboxRepository.save(
				OutboxEvent.matchingPending(badPostId, 1, "direction-match:batch-bad", "{\"postId\":999999991}", NOW));

		DirectionMatchingWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactlyInAnyOrder(DirectionMatchingWorker.Outcome.PROCESSED,
				DirectionMatchingWorker.Outcome.DEAD);
		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, goodPostId))
				.isEqualTo("ACTIVE");
		assertThat(
				jdbc.queryForObject(
						"SELECT status FROM outbox_event WHERE aggregate_type = 'DIRECTION_POST' AND aggregate_id = ?",
						String.class, badPostId))
				.isEqualTo(OutboxStatus.DEAD.name());
	}

	private Fixture fixtureWithCandidates(int count) {
		long senderId = account("worker-sender");
		long questionId = activeQuestion(senderId);
		long schemeId = eightSegmentScheme();
		presence(senderId, 37.5000, 127.0000, NOW.plusSeconds(3600), NOW.minusSeconds(120));
		List<Long> candidates = IntStream.range(0, count).mapToObj(index -> account("worker-candidate-" + index))
				.toList();
		IntStream.range(0, count).forEach(
				index -> presence(candidates.get(index), 37.5010 + index * 0.0001, 127.0000, NOW.plusSeconds(3600)));
		return new Fixture(senderId, questionId, schemeId, candidates);
	}

	private long submit(Fixture fixture, String key) {
		return postService
				.send(new DirectionPostService.SendCommand(fixture.senderId(), fixture.questionId(), fixture.schemeId(),
						"S0",
						0, 5_000, REGION, key, "worker body", NOW.minusSeconds(60), NOW.plusSeconds(3600)))
				.post().getId();
	}

	private long submitAndPass(Fixture fixture, String key) {
		long postId = submit(fixture, key);
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);
		return postId;
	}

	private DirectionMatchingWorker.BatchCommand command() {
		return new DirectionMatchingWorker.BatchCommand(10, "matching-worker", NOW, NOW.plusSeconds(60),
				new com.dnd.qello.notification.domain.OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1)));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
				""", Long.class, REGION, nickname);
	}

	private long activeQuestion(long approverId) {
		return jdbc.queryForObject(
				"""
						INSERT INTO approved_question (source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
						VALUES ('OPERATOR', 'ACTIVE', 'worker question', 'TEXT', ?, ?, ?, ?) RETURNING id
						""",
				Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
				Timestamp.from(NOW.minusSeconds(120)), approverId);
	}

	private long eightSegmentScheme() {
		DirectionScheme scheme = schemeRepository
				.save(DirectionScheme.createEqual("TEST-WORKER", 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8)
				.forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(), "S" + index,
						"worker-segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45),
						index)));
		return scheme.getId();
	}

	private void presence(long userId, double latitude, double longitude, Instant expiresAt) {
		presence(userId, latitude, longitude, expiresAt, NOW.minusSeconds(10));
	}

	private void presence(long userId, double latitude, double longitude, Instant expiresAt, Instant updatedAt) {
		presenceRepository
				.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
						null, REGION, BigDecimal.ONE, true, updatedAt, expiresAt));
	}

	private void presenceAtBearing(long userId, double distanceMeters, double bearingDegrees, Instant expiresAt) {
		double[] point = jdbc.queryForObject("""
				SELECT ST_Y(projected::geometry), ST_X(projected::geometry)
				FROM (SELECT ST_Project(ST_SetSRID(ST_MakePoint(127.0000, 37.5000), 4326)::geography,
					?, radians(?)) AS projected) fixture
				""", (rs, rowNum) -> new double[]{rs.getDouble(1), rs.getDouble(2)}, distanceMeters, bearingDegrees);
		presence(userId, point[0], point[1], expiresAt);
	}

	private long submitThroughHttp(Fixture fixture, Instant at) throws Exception {
		String presenceResponse = mockMvc.perform(authenticated(put("/api/v1/direction/presence"), fixture.senderId())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"latitude":37.51234567,"longitude":127.09876543,"accuracyMeters":1,
						 "receiveAllowed":true,"observedAt":"%s"}
						""".formatted(at)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(true))
				.andReturn().getResponse().getContentAsString();
		String previewResponse = mockMvc.perform(authenticated(get("/api/v1/direction/preview"), fixture.senderId()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String submissionResponse = mockMvc.perform(authenticated(post("/api/v1/direction/posts"), fixture.senderId())
				.header("Idempotency-Key", "http-coordinate-388").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"approvedQuestionId":%d,"schemeId":%d,"segmentKey":"S0",
						 "bodyText":"Coordinate flow","mediaIds":[]}
						""".formatted(fixture.questionId(), fixture.schemeId())))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.data.submissionStatus").value("SUBMITTED"))
				.andReturn().getResponse().getContentAsString();
		assertThat((presenceResponse + previewResponse + submissionResponse)
				.contains("37.51234567")
				|| (presenceResponse + previewResponse + submissionResponse).contains("127.09876543"))
				.as("HTTP responses must not expose exact coordinates").isFalse();
		return jdbc.queryForObject("SELECT id FROM direction_post WHERE sender_id = ? AND idempotency_key = ?",
				Long.class, fixture.senderId(), "http-coordinate-388");
	}

	private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, long userId) {
		return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
				.jwt(token -> token.subject(Long.toString(userId))));
	}

	private void assertStoredCoordinate(String table, String column, String idColumn, long id,
			CoordinatePurpose purpose) {
		assertThat(jdbc.queryForObject("SELECT pg_typeof(" + column + ")::text FROM " + table
				+ " WHERE " + idColumn + " = ?", String.class, id)).isEqualTo("bytea");
		byte[] envelope = jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE " + idColumn + " = ?",
				byte[].class, id);
		var coordinate = protector.decrypt(envelope, purpose, id);
		assertThat(coordinate.latitude()).isEqualByComparingTo("37.51234567");
		assertThat(coordinate.longitude()).isEqualByComparingTo("127.09876543");
		var wrongKey = new AesGcmCoordinateProtector(new CoordinateKeyRing("coordinate-fixture-current",
				Map.of("coordinate-fixture-current", new byte[32])));
		assertThatThrownBy(() -> wrongKey.decrypt(envelope, purpose, id))
				.isInstanceOf(CoordinateProtectionException.class);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'
					AND table_name = ? AND column_name = ?
				""", Long.class, table, purpose == CoordinatePurpose.POST_AUDIENCE ? "origin_position" : "position"))
				.isZero();
	}

	private record Fixture(long senderId, long questionId, long schemeId, List<Long> candidateIds) {
	}
}
