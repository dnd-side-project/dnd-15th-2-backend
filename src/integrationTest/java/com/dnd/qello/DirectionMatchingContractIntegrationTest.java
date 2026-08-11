/**
 * Created at: 2026-08-11T20:14:20+09:00
 * Source scenario: TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-002 through INT-004
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionPost;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionPostRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;

@SpringBootTest
@ActiveProfiles("test")
class DirectionMatchingContractIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-MATCHING-115";
	private static final Instant NOW = Instant.parse("2026-08-11T11:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private DirectionPostService postService;
	@Autowired
	private DirectionPostRepository postRepository;
	@Autowired
	private DirectionSchemeRepository schemeRepository;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private OutboxEventRepository outboxRepository;

	private long senderId;
	private long questionId;
	private long schemeId;

	@BeforeEach
	void resetFixtures() {
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM direction_segment");
		jdbc.update("DELETE FROM direction_scheme");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Matching Test', 'REGION')", REGION);

		senderId = account("matching-sender");
		questionId = activeQuestion();
		schemeId = scheme();
		presenceRepository.save(ActiveUserPresence.create(senderId, BigDecimal.valueOf(37.5),
			BigDecimal.valueOf(127.0), null, REGION, BigDecimal.ONE, true,
			NOW.minusSeconds(10), NOW.plusSeconds(3600)));
	}

	@Test
	@DisplayName("발송 후 request fingerprint가 direction_post와 복원 도메인에 저장된다")
	void persistsRequestFingerprintAndRestoresIt() {
		DirectionPostService.SendResult result = send("fingerprint-key", "같은 의도");
		DirectionPost restored = postRepository.findById(result.post().getId()).orElseThrow();

		assertThat(restored.getRequestFingerprint()).isNotNull();
		assertThat(restored.getRequestFingerprint()).isEqualTo(result.post().getRequestFingerprint());
		assertThat(jdbc.queryForObject("SELECT request_fingerprint FROM direction_post WHERE id = ?",
			String.class, result.post().getId())).isEqualTo(restored.getRequestFingerprint().value());
	}

	@Test
	@DisplayName("같은 멱등키의 동일 요청은 기존 결과를 반환하고 다른 fingerprint는 충돌한다")
	void returnsSameResultAndRejectsDifferentFingerprint() {
		DirectionPostService.SendResult first = send("duplicate-key", "첫 번째 의도");
		long postCount = jdbc.queryForObject("SELECT count(*) FROM direction_post", Long.class);
		DirectionPostService.SendResult retry = send("duplicate-key", "첫 번째 의도");

		assertThat(retry.post().getId()).isEqualTo(first.post().getId());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post", Long.class)).isEqualTo(postCount);
		assertThatThrownBy(() -> send("duplicate-key", "의도가 달라진 재사용"))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.IDEMPOTENCY_KEY_REUSED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post", Long.class)).isEqualTo(postCount);
	}

	@Test
	@DisplayName("legacy null fingerprint는 동일 요청에서 audience 의도로 lazy backfill된다")
	void backfillsLegacyFingerprintForSameRequest() {
		DirectionPostService.SendResult first = send("legacy-same-key", "legacy 의도");
		jdbc.update("UPDATE direction_post SET request_fingerprint = NULL WHERE id = ?", first.post().getId());

		DirectionPostService.SendResult retry = send("legacy-same-key", "legacy 의도");

		assertThat(retry.post().getId()).isEqualTo(first.post().getId());
		assertThat(retry.post().getRequestFingerprint()).isEqualTo(first.post().getRequestFingerprint());
		assertThat(jdbc.queryForObject("SELECT request_fingerprint FROM direction_post WHERE id = ?",
			String.class, first.post().getId())).isEqualTo(first.post().getRequestFingerprint().value());
	}

	@Test
	@DisplayName("legacy null fingerprint는 다른 요청에서 backfill하지 않고 멱등키 충돌을 반환한다")
	void rejectsDifferentRequestBeforeLegacyBackfill() {
		DirectionPostService.SendResult first = send("legacy-different-key", "원래 legacy 의도");
		jdbc.update("UPDATE direction_post SET request_fingerprint = NULL WHERE id = ?", first.post().getId());

		assertThatThrownBy(() -> send("legacy-different-key", "달라진 legacy 의도"))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.IDEMPOTENCY_KEY_REUSED);
		assertThat(jdbc.queryForObject("SELECT request_fingerprint FROM direction_post WHERE id = ?",
			String.class, first.post().getId())).isNull();
	}

	@Test
	@DisplayName("동일 fingerprint의 동시 멱등 요청은 하나의 logical result를 함께 반환한다")
	void concurrentSameFingerprintRequestsReturnOneLogicalResult() throws Exception {
		DirectionPostService.SendCommand command = command("concurrent-key", "동일한 동시 의도");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<DirectionPostService.SendResult>> futures = List.of(
				executor.submit(() -> sendAfterSignal(command, ready, start)),
				executor.submit(() -> sendAfterSignal(command, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			DirectionPostService.SendResult first = futures.get(0).get(10, TimeUnit.SECONDS);
			DirectionPostService.SendResult second = futures.get(1).get(10, TimeUnit.SECONDS);
			assertThat(second.post().getId()).isEqualTo(first.post().getId());
			assertThat(jdbc.queryForObject("SELECT count(*) FROM direction_post", Long.class)).isEqualTo(1L);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type = ?",
				Long.class, OutboxEventType.RECIPIENT_MATCH_REQUESTED.name())).isEqualTo(1L);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("매칭 outbox는 round별로 유일하고 payload에 정확 좌표를 저장하지 않는다")
	void enforcesMatchingRoundUniquenessAndCoarsePayload() {
		DirectionPostService.SendResult post = send("outbox-key", "매칭 payload");
		String fingerprint = post.post().getRequestFingerprint().value();
		String payload = "{\"postId\":" + post.post().getId()
			+ ",\"matchRound\":1,\"eventType\":\"RECIPIENT_MATCH_REQUESTED\""
			+ ",\"requestFingerprint\":\"" + fingerprint + "\",\"coarseRegionCode\":\"" + REGION + "\"}";

		String dedupKey = "direction-match:" + post.post().getId() + ":1:RECIPIENT_MATCH_REQUESTED";
		OutboxEvent first = outboxRepository.findByDedupKey(dedupKey).orElseThrow();
		assertThat(first.matchRound()).isEqualTo(1);
		assertThat(first.eventType()).isEqualTo(OutboxEventType.RECIPIENT_MATCH_REQUESTED);
		assertThat(jdbc.queryForObject("SELECT payload ->> 'coarseRegionCode' FROM outbox_event WHERE id = ?",
			String.class, first.id())).isEqualTo(REGION);
		String storedPayload = jdbc.queryForObject("SELECT payload::text FROM outbox_event WHERE id = ?",
			String.class, first.id());
		assertThat(storedPayload).doesNotContain("latitude", "longitude", "origin_position", "37.5", "127.0");

		assertThatThrownBy(() -> outboxRepository.save(OutboxEvent.matchingPending(post.post().getId(), 1,
			"direction-match:duplicate-dedup", payload, NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);

		OutboxEvent nextRound = outboxRepository.save(OutboxEvent.matchingPending(post.post().getId(), 2,
			"direction-match:round-2", payload.replace("\"matchRound\":1", "\"matchRound\":2"), NOW));
		assertThat(nextRound.matchRound()).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
			Long.class, post.post().getId(), OutboxEventType.RECIPIENT_MATCH_REQUESTED.name())).isEqualTo(2);
	}

	@Test
	@DisplayName("non-matching Outbox event에는 match round를 저장할 수 없다")
	void rejectsRoundOnNonMatchingEvent() {
		assertThatThrownBy(() -> OutboxEvent.pending(OutboxAggregateType.ANSWER, 1L,
			OutboxEventType.ANSWER_PUBLISHED, "answer-round", "{\"answerId\":1}", 1, NOW))
			.isInstanceOf(com.dnd.qello.notification.error.NotificationException.class);
	}

	private DirectionPostService.SendResult send(String idempotencyKey, String bodyText) {
		return postService.send(command(idempotencyKey, bodyText));
	}

	private DirectionPostService.SendCommand command(String idempotencyKey, String bodyText) {
		return new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0",
			0, 500, REGION, idempotencyKey, bodyText, NOW, NOW.plus(1, ChronoUnit.HOURS));
	}

	private DirectionPostService.SendResult sendAfterSignal(DirectionPostService.SendCommand command,
		CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		return postService.send(command);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long activeQuestion() {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', '매칭 계약 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW), senderId);
	}

	private long scheme() {
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual("TEST-MATCHING-115", 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(),
			"S" + index, "matching-segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index)));
		return scheme.getId();
	}
}
