/**
 * Created at: 2026-08-18T15:40:00+09:00
 * Source scenario: TEST-PLAN-DIRECTION-MATCHING-VERTICAL-FLOW-INT-001 through INT-005
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.service.AnswerSubmissionApplicationService;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker;
import com.dnd.qello.direction.sweep.SweepBatchResult;
import com.dnd.qello.direction.sweep.SkipConfirmationSweepWorker;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxDetail;
import com.dnd.qello.feed.view.InboxListing;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.AnswerModerationEventPayloadsTestSupport;
import com.dnd.qello.filtering.moderation.AnswerModerationVerdictWorker;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.PushDevice;
import com.dnd.qello.notification.domain.PushDeviceStatus;
import com.dnd.qello.notification.domain.PushPlatform;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.repository.NotificationRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GH-127 Gap A. presence 갱신부터 만료·넘김확정·답변공개까지, 각 단계가 실제로 만든
 * 행만 다음 단계 입력으로 사용해 관통한다. 콘텐츠 검열 게이트(direction_post 방향
 * 검열, answer moderation verdict)만 필터링 수직(#105~#112)이 소유하는 별도 시스템이므로
 * 예외로 취급한다 — direction_post는 fixture UPDATE로, answer는 기존 GH-125 통합 테스트가
 * 확립한 실제 {@link AnswerModerationVerdictWorker} 호출 패턴으로 통과시킨다.
 *
 * <p>단계 내부 동시성·outbox 임대·PostGIS 쿼리 계약 자체는 이 클래스의 범위가 아니다.
 * {@code DirectionMatchingWorkerIntegrationTest}, {@code OutboxLeaseIntegrationTest},
 * {@code DirectionPostgisPersistenceIntegrationTest}가 소유한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(DirectionFlow127TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionMatchingVerticalFlowIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-FLOW-127";
	private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");
	private static final double SENDER_LAT = 37.5000;
	private static final double SENDER_LON = 127.0000;
	private static final OutboxRetryPolicy RETRY_POLICY = new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1));

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
	private InboxApplicationService inboxApplicationService;
	@Autowired
	private AnswerSubmissionApplicationService answerApplicationService;
	@Autowired
	private RecipientExpirationSweepWorker expirationSweepWorker;
	@Autowired
	private SkipConfirmationSweepWorker skipSweepWorker;
	@Autowired
	private OutboxEventRepository outboxEventRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private AnswerModerationVerdictWorker verdictWorker;
	@Autowired
	private ObjectMapper objectMapper;
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
		jdbc.update("DELETE FROM media_attachment WHERE answer_id IN (SELECT id FROM answer WHERE coarse_region_code = ?)", REGION);
		jdbc.update("DELETE FROM answer WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Direction Flow 127', 'REGION')", REGION);
	}

	@Test
	@DisplayName("INT-001: presence 갱신부터 fan-out까지 픽스처 주입 없이 관통하면 확정 3건과 알림 3건이 생성된다")
	void confirmsAndFansOutThroughTheRealChain() {
		Chain chain = runChainToFannedOut("int001");

		assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, chain.postId()))
			.isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ? AND status = 'AVAILABLE'",
			Long.class, chain.postId())).isEqualTo(3L);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT' AND event_type = 'RECIPIENTS_CONFIRMED' AND status = 'PROCESSED'",
			Long.class)).isEqualTo(3L);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Long.class)).isEqualTo(3L);
		for (long candidateId : chain.candidateIds()) {
			assertThat(jdbc.queryForObject(
				"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, candidateId))
				.isEqualTo(1);
		}
	}

	@Test
	@DisplayName("INT-002: 수신함에서 열람한 실제 수신자가 답변을 제출·공개하면 슬롯이 1에서 0으로 정확히 원복된다")
	void answeringRecipientReleasesSlotExactlyOnce() {
		Chain chain = runChainToFannedOut("int002");
		long recipientId = chain.candidateIds().get(0);
		long postRecipientId = chain.postRecipientIdFor(jdbc, chain.postId(), recipientId);
		// 수신함·답변 application service는 Clock 빈을 직접 읽는다. 매칭·fan-out 단계는
		// BatchCommand의 명시적 at(NOW+91s까지)을 쓰므로, 그 이후 단계로 넘어가려면
		// mutable clock을 그 시각 뒤로 옮겨야 matched_at 이전 시각으로 open()이
		// 거절되지 않는다.
		clock.setInstant(NOW.plusSeconds(120));

		InboxListing listing = inboxApplicationService.list(recipientId, InboxCategory.UNANSWERED, null);
		assertThat(listing.cards()).anyMatch(card -> card.postRecipientId() == postRecipientId);
		InboxDetail detail = inboxApplicationService.detail(recipientId, postRecipientId);
		assertThat(detail.card().postRecipientId()).isEqualTo(postRecipientId);

		promotedRelease(chain.senderId());
		Answer submitted = answerApplicationService.submit(recipientId, "int002-answer", postRecipientId, "실제 체인을 통과한 답변", List.of());
		assertThat(submitted.getStatus()).isEqualTo(AnswerStatus.SUBMITTED);
		assertThat(jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, recipientId))
			.isEqualTo(1);

		long filterJobId = jdbc.queryForObject(
			"SELECT id FROM filter_job WHERE target_type = 'ANSWER' AND target_id = ?", Long.class, submitted.getId());
		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.ALLOW, "int002");
		AnswerModerationVerdictWorker.BatchResult verdictResult = verdictWorker.processBatch(
			new AnswerModerationVerdictWorker.BatchCommand(10, "int002-verdict-worker", NOW.plusSeconds(120), NOW.plusSeconds(180)));

		assertThat(verdictResult.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(jdbc.queryForObject("SELECT status FROM answer WHERE id = ?", String.class, submitted.getId()))
			.isEqualTo("PUBLISHED");
		assertThat(jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId))
			.isEqualTo(PostRecipientStatus.ANSWERED.name());
		assertThat(jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, recipientId))
			.isEqualTo(0);
	}

	@Test
	@DisplayName("INT-003: 넘김 요청 후 유예가 지나면 sweep이 슬롯을 정확히 한 번만 해제하고 재실행해도 추가 감소가 없다")
	void skipConfirmationReleasesSlotExactlyOnce() {
		Chain chain = runChainToFannedOut("int003");
		long recipientId = chain.candidateIds().get(1);
		long postRecipientId = chain.postRecipientIdFor(jdbc, chain.postId(), recipientId);
		clock.setInstant(NOW.plusSeconds(120));

		inboxApplicationService.detail(recipientId, postRecipientId);
		inboxApplicationService.skip(recipientId, postRecipientId);
		assertThat(jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId))
			.isEqualTo(PostRecipientStatus.SKIP_PENDING.name());

		Instant afterGrace = NOW.plusSeconds(130);
		SweepBatchResult first = skipSweepWorker.processBatch(
			new SkipConfirmationSweepWorker.BatchCommand(10, afterGrace));
		assertThat(first.released()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId))
			.isEqualTo(PostRecipientStatus.SKIPPED.name());
		assertThat(jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, recipientId))
			.isEqualTo(0);

		SweepBatchResult replay = skipSweepWorker.processBatch(
			new SkipConfirmationSweepWorker.BatchCommand(10, afterGrace.plusSeconds(60)));
		assertThat(replay.released()).isZero();
		assertThat(jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, recipientId))
			.isEqualTo(0);
	}

	@Test
	@DisplayName("INT-004: 질문글 만료 뒤 미응답 항목은 만료 sweep이 각 수신자의 슬롯을 정확히 한 번만 해제한다")
	void expirationSweepReleasesSlotExactlyOnce() {
		Chain chain = runChainToFannedOut("int004");
		long recipientId = chain.candidateIds().get(2);
		long postRecipientId = chain.postRecipientIdFor(jdbc, chain.postId(), recipientId);
		Instant postExpiresAt = jdbc.queryForObject(
			"SELECT expires_at FROM direction_post WHERE id = ?", Timestamp.class, chain.postId()).toInstant();

		SweepBatchResult result = expirationSweepWorker.processBatch(
			new RecipientExpirationSweepWorker.BatchCommand(10, postExpiresAt.plusSeconds(1)));

		// 이 체인의 세 후보 모두 아직 미응답이므로 batch는 셋 다 만료시킨다. 이 시나리오가
		// 단언하는 "정확히 한 번"은 batch 결과 건수가 아니라 recipientId 한 명의 카운터가
		// 1에서 0으로 딱 한 번만 감소한다는 것이다.
		assertThat(result.released()).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT status FROM post_recipient WHERE id = ?", String.class, postRecipientId))
			.isEqualTo(PostRecipientStatus.EXPIRED.name());
		assertThat(jdbc.queryForObject(
			"SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, recipientId))
			.isEqualTo(0);
	}

	@Test
	@DisplayName("INT-005: 흐름이 만든 모든 outbox·notification 행에 정확 좌표가 포함되지 않는다")
	void neitherOutboxNorNotificationPayloadsExposePreciseCoordinates() {
		Chain chain = runChainToFannedOut("int005");

		List<String> outboxPayloads = jdbc.queryForList(
			"SELECT payload FROM outbox_event WHERE aggregate_type IN ('DIRECTION_POST', 'POST_RECIPIENT')",
			String.class);
		assertThat(outboxPayloads).isNotEmpty();
		for (String payload : outboxPayloads) {
			assertThat(payload).doesNotContain(String.valueOf(SENDER_LAT)).doesNotContain(String.valueOf(SENDER_LON));
			for (int index = 0; index < 3; index++) {
				assertThat(payload).doesNotContain(String.valueOf(candidateLatitude(index)));
			}
		}

		long notificationCount = jdbc.queryForObject("SELECT count(*) FROM notification", Long.class);
		assertThat(notificationCount).isEqualTo(3L);
		List<String> notificationColumns = jdbc.queryForList(
			"SELECT string_agg(column_name, ',') FROM information_schema.columns WHERE table_name = 'notification'",
			String.class);
		assertThat(notificationColumns.getFirst().toLowerCase())
			.doesNotContain("latitude").doesNotContain("longitude").doesNotContain("position");
	}

	// -- chain construction -------------------------------------------------

	private Chain runChainToFannedOut(String keyPrefix) {
		long senderId = account(keyPrefix + "-sender");
		long questionId = activeQuestion(senderId);
		List<Long> candidateIds = List.of(
			account(keyPrefix + "-candidate-0"),
			account(keyPrefix + "-candidate-1"),
			account(keyPrefix + "-candidate-2"));

		updatePresence(senderId, SENDER_LAT, SENDER_LON);
		for (int index = 0; index < candidateIds.size(); index++) {
			updatePresence(candidateIds.get(index), candidateLatitude(index), SENDER_LON);
		}

		var preview = postApplicationService.preview(senderId);
		assertThat(preview.schemeCode()).isEqualTo("OCTANT");
		var northSegment = preview.segments().stream().filter(segment -> segment.segmentKey().equals("N")).findFirst()
			.orElseThrow();
		assertThat(northSegment.count()).isEqualTo(3L);

		long schemeId = octantSchemeId();
		var submitResult = postApplicationService.submit(senderId, keyPrefix + "-submit",
			new DirectionPostApplicationService.SubmitCommand(questionId, schemeId, "N", "실제 체인 관통 본문", List.of()));
		long postId = submitResult.post().getId();

		// 콘텐츠 검열 게이트 seam: 방향 검열은 필터링 수직(#105~#112) 소유이며 이 계획의
		// 범위가 아니다. DirectionMatchingWorkerIntegrationTest와 동일한 방식으로
		// PASSED로 직접 전이시킨다.
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);

		DirectionMatchingWorker.BatchResult matchResult = matchingWorker.processBatch(
			new DirectionMatchingWorker.BatchCommand(10, keyPrefix + "-matching-worker", NOW.plusSeconds(30),
				NOW.plusSeconds(90), RETRY_POLICY));
		assertThat(matchResult.outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);

		for (long candidateId : candidateIds) {
			device(candidateId, keyPrefix + "-device-" + candidateId);
		}

		RecipientNotificationFanOutWorker.BatchResult fanOutResult = fanOutWorker.processBatch(
			new RecipientNotificationFanOutWorker.BatchCommand(10, keyPrefix + "-fanout-worker", NOW.plusSeconds(31),
				NOW.plusSeconds(91), RETRY_POLICY));
		assertThat(fanOutResult.outcomes()).allMatch(outcome -> outcome == RecipientNotificationFanOutWorker.Outcome.PROCESSED);

		return new Chain(senderId, postId, candidateIds);
	}

	private double candidateLatitude(int index) {
		return SENDER_LAT + 0.0010 + index * 0.0001;
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
			VALUES ('OPERATOR', 'ACTIVE', 'flow-127 question', 'TEXT', ?, ?, ?, ?) RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
			Timestamp.from(NOW.minusSeconds(120)), approverId);
	}

	private void device(long userId, String fingerprint) {
		notificationRepository.saveDevice(new PushDevice(null, userId, PushPlatform.ANDROID,
			new byte[] {1, 2, 3, 4}, fingerprint, PushDeviceStatus.ACTIVE, NOW, null));
	}

	private void promotedRelease(long operatorUserId) {
		var candidate = releaseRegistryService.createCandidate("flow127-norm", "flow127-ruleset", "flow127-category-map",
			"flow127-model-snapshot");
		releaseRegistryService.markOfflineEvaluated(candidate.id());
		releaseRegistryService.designateShadow(candidate.id());
		releaseRegistryService.designateCanary(candidate.id());
		releaseRegistryService.promote(candidate.id(), operatorUserId);
	}

	private void seedVerdictReady(long filterJobId, long answerId, FilterVerdict verdict, String dedupSuffix) {
		OutboxEvent event = OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, filterJobId,
			OutboxEventType.MODERATION_VERDICT_READY, "flow127-filter-job:" + filterJobId + ":" + dedupSuffix,
			AnswerModerationEventPayloadsTestSupport.verdictReadyJson(objectMapper, filterJobId, answerId, verdict), NOW);
		outboxEventRepository.save(event);
	}

	private record Chain(long senderId, long postId, List<Long> candidateIds) {
		long postRecipientIdFor(JdbcTemplate jdbc, long postId, long recipientId) {
			return jdbc.queryForObject("SELECT id FROM post_recipient WHERE post_id = ? AND recipient_id = ?",
				Long.class, postId, recipientId);
		}
	}
}

@TestConfiguration
class DirectionFlow127TestClockConfiguration {

	@Bean
	@Primary
	DirectionFlow127MutableClock directionFlow127MutableClock() {
		return new DirectionFlow127MutableClock(Instant.parse("2026-08-18T08:00:00Z"), ZoneOffset.UTC);
	}
}

final class DirectionFlow127MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	DirectionFlow127MutableClock(Instant initial, ZoneId zone) {
		this.current = new AtomicReference<>(initial);
		this.zone = zone;
	}

	void setInstant(Instant instant) {
		current.set(instant);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId newZone) {
		return new DirectionFlow127MutableClock(current.get(), newZone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}
}
