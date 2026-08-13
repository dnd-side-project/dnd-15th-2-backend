/**
 * Created at: 2026-08-13T17:47:00+09:00
 * Source scenario: TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-010 through INT-012
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.DirectionSchemeRepository;
import com.dnd.qello.direction.service.DirectionPostService;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.OutboxStatus;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectionMatchingWorkerConcurrencyIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-DIRECTION-MATCHING-WORKER-CONCURRENCY";
	private static final Instant NOW = Instant.parse("2026-08-13T08:30:00Z");

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
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) VALUES (?, 'KR', 'Matching Worker Concurrency', 'REGION')", REGION);
	}

	@Test
	@DisplayName("같은 matching Outbox를 두 worker가 claim해도 하나의 logical 결과만 커밋한다")
	void claimsSameEventOnceAcrossWorkers() throws Exception {
		Fixture fixture = fixture("concurrent-claim", 1);
		long postId = submitAndPass(fixture.senderIds().get(0), fixture.questionId(), fixture.schemeId(), "concurrent-claim");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<DirectionMatchingWorker.BatchResult>> futures = List.of(
				executor.submit(() -> processAfterSignal("worker-claim-a", NOW, ready, start)),
				executor.submit(() -> processAfterSignal("worker-claim-b", NOW, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			DirectionMatchingWorker.BatchResult first = futures.get(0).get(10, TimeUnit.SECONDS);
			DirectionMatchingWorker.BatchResult second = futures.get(1).get(10, TimeUnit.SECONDS);

			assertThat(first.claimed() + second.claimed()).isEqualTo(1);
			assertThat(first.outcomes().contains(DirectionMatchingWorker.Outcome.PROCESSED)
				|| second.outcomes().contains(DirectionMatchingWorker.Outcome.PROCESSED)).isTrue();
			assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId)).isEqualTo(1L);
			assertThat(jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class,
				fixture.candidateIds().get(0))).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'", Long.class)).isEqualTo(1L);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("서로 다른 질문글이 공통 수신자의 마지막 slot을 동시에 예약해도 상한을 넘지 않는다")
	void reservesCommonLastSlotOnlyOnce() throws Exception {
		Fixture fixture = fixture("common-last-slot", 1);
		long firstPostId = submitAndPass(fixture.senderIds().get(0), fixture.questionId(), fixture.schemeId(), "common-last-slot-a");
		long secondPostId = submitAndPass(fixture.senderIds().get(1), fixture.questionId(), fixture.schemeId(), "common-last-slot-b");
		long candidateId = fixture.candidateIds().get(0);
		jdbc.update("""
			INSERT INTO recipient_receive_state
				(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at, updated_at)
			VALUES (?, 4, 4, ?, ?, ?)
			""", candidateId, Timestamp.from(NOW.minusSeconds(3600)), Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<DirectionMatchingWorker.BatchResult>> futures = List.of(
				executor.submit(() -> processAfterSignal("worker-slot-a", NOW, ready, start)),
				executor.submit(() -> processAfterSignal("worker-slot-b", NOW, ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			futures.forEach(future -> {
				try {
					future.get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			});

			assertThat(jdbc.queryForObject("SELECT active_unhandled_count FROM recipient_receive_state WHERE user_id = ?", Integer.class, candidateId)).isEqualTo(5);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE recipient_id = ? AND capacity_released_at IS NULL", Long.class, candidateId)).isEqualTo(1L);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id IN (?, ?)", Long.class, firstPostId, secondPostId)).isEqualTo(1L);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'", Long.class)).isEqualTo(1L);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("stale lease worker의 domain write는 rollback되고 reclaim한 worker만 결과를 커밋한다")
	void rollsBackStaleWorkerAfterLeaseReclaim() throws Exception {
		Fixture fixture = fixture("stale-lease", 1);
		long postId = submitAndPass(fixture.senderIds().get(0), fixture.questionId(), fixture.schemeId(), "stale-lease");
		CountDownLatch postLocked = new CountDownLatch(1);
		CountDownLatch releasePost = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(3);
		try {
			Future<?> lockHolder = executor.submit(() -> holdPostLock(postId, postLocked, releasePost));
			assertThat(postLocked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<DirectionMatchingWorker.BatchResult> staleWorker = executor.submit(() -> worker.processBatch(command("worker-stale", NOW, NOW.plusSeconds(1))));
			awaitLease(postId, 1, "worker-stale");
			Future<DirectionMatchingWorker.BatchResult> reclaimingWorker = executor.submit(() -> worker.processBatch(command("worker-reclaim", NOW.plusSeconds(2), NOW.plusSeconds(62))));
			awaitLease(postId, 2, "worker-reclaim");
			releasePost.countDown();

			assertThat(staleWorker.get(10, TimeUnit.SECONDS).outcomes()).containsExactly(DirectionMatchingWorker.Outcome.STALE_LEASE);
			assertThat(reclaimingWorker.get(10, TimeUnit.SECONDS).outcomes()).containsExactly(DirectionMatchingWorker.Outcome.PROCESSED);
			lockHolder.get(10, TimeUnit.SECONDS);

			assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, postId)).isEqualTo("ACTIVE");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM post_recipient WHERE post_id = ?", Long.class, postId)).isEqualTo(1L);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_type = 'POST_RECIPIENT'", Long.class)).isEqualTo(1L);
			assertThat(jdbc.queryForObject("SELECT status FROM outbox_event WHERE aggregate_id = ? AND aggregate_type = 'DIRECTION_POST' AND event_type = 'RECIPIENT_MATCH_REQUESTED'", String.class, postId))
				.isEqualTo(OutboxStatus.PROCESSED.name());
		} finally {
			releasePost.countDown();
			executor.shutdownNow();
		}
	}

	private DirectionMatchingWorker.BatchResult processAfterSignal(String owner, Instant at,
		CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker start barrier timed out");
		return worker.processBatch(command(owner, at, at.plusSeconds(60)));
	}

	private DirectionMatchingWorker.BatchCommand command(String owner, Instant at, Instant leaseExpiresAt) {
		return new DirectionMatchingWorker.BatchCommand(10, owner, at, leaseExpiresAt,
			new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1)));
	}

	private void holdPostLock(long postId, CountDownLatch locked, CountDownLatch release) {
		jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
			boolean previousAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM direction_post WHERE id = ? FOR UPDATE")) {
				statement.setLong(1, postId);
				try (ResultSet ignored = statement.executeQuery()) {
					ignored.next();
				}
				locked.countDown();
				if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("post lock release timed out");
				connection.commit();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				connection.rollback();
				throw new AssertionError(exception);
			} catch (RuntimeException exception) {
				connection.rollback();
				throw exception;
			} finally {
				connection.setAutoCommit(previousAutoCommit);
			}
			return null;
		});
	}

	private void awaitLease(long postId, long generation, String owner) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			List<String> owners = jdbc.queryForList("SELECT lease_owner FROM outbox_event WHERE aggregate_id = ? AND event_type = 'RECIPIENT_MATCH_REQUESTED' AND lease_generation = ?",
				String.class, postId, generation);
			if (owners.contains(owner)) return;
			Thread.sleep(10);
		}
		throw new AssertionError("lease was not acquired: generation=" + generation + ", owner=" + owner);
	}

	private Fixture fixture(String key, int candidateCount) {
		long senderOne = account("worker-concurrent-sender-a-" + key);
		long senderTwo = account("worker-concurrent-sender-b-" + key);
		long questionId = activeQuestion(senderOne);
		long schemeId = eightSegmentScheme(key);
		presence(senderOne, 37.5000, 127.0000, NOW.minusSeconds(120), NOW.plusSeconds(3600));
		presence(senderTwo, 37.5000, 127.0000, NOW.minusSeconds(120), NOW.plusSeconds(3600));
		List<Long> candidates = IntStream.range(0, candidateCount)
			.mapToObj(index -> account("worker-concurrent-candidate-" + key + "-" + index)).toList();
		IntStream.range(0, candidateCount).forEach(index -> presence(candidates.get(index), 37.5010 + index * 0.0001,
			127.0000, NOW.minusSeconds(10), NOW.plusSeconds(3600)));
		return new Fixture(List.of(senderOne, senderTwo), questionId, schemeId, candidates);
	}

	private long submitAndPass(long senderId, long questionId, long schemeId, String key) {
		long postId = postService.send(new DirectionPostService.SendCommand(senderId, questionId, schemeId, "S0",
			0, 5_000, REGION, key, "concurrency body", NOW.minusSeconds(60), NOW.plusSeconds(3600))).post().getId();
		jdbc.update("UPDATE direction_post SET moderation_status = 'PASSED' WHERE id = ?", postId);
		return postId;
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
			VALUES ('OPERATOR', 'ACTIVE', 'concurrency question', 'TEXT', ?, ?, ?, ?) RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.plusSeconds(7200)),
			Timestamp.from(NOW.minusSeconds(120)), approverId);
	}

	private long eightSegmentScheme(String key) {
		DirectionScheme scheme = schemeRepository.save(DirectionScheme.createEqual("TEST-WORKER-" + key, 1, 8, BigDecimal.ZERO));
		IntStream.range(0, 8).forEach(index -> schemeRepository.saveSegment(DirectionSegment.create(scheme.getId(), "S" + index,
			"concurrency-segment-" + index, BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index)));
		return scheme.getId();
	}

	private void presence(long userId, double latitude, double longitude, Instant locationAt, Instant expiresAt) {
		presenceRepository.save(ActiveUserPresence.create(userId, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
			null, REGION, BigDecimal.ONE, true, locationAt, expiresAt));
	}

	private record Fixture(List<Long> senderIds, long questionId, long schemeId, List<Long> candidateIds) {
	}
}
