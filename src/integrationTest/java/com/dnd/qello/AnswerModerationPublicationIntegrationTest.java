/**
 * Created at: 2026-08-17T19:00:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-010 through INT-014,
 * INT-017, INT-018, INT-021
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.service.AnswerSubmissionApplicationService;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.moderation.AnswerModerationEventPayloadsTestSupport;
import com.dnd.qello.filtering.moderation.AnswerModerationVerdictWorker;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import({AnswerPublication125TestClockConfiguration.class, AnswerPublication125FaultInjectionConfiguration.class})
class AnswerModerationPublicationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-17T07:00:00.123456Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AnswerSubmissionApplicationService submissionApplicationService;
	@Autowired
	private AnswerModerationVerdictWorker verdictWorker;
	@Autowired
	private OutboxEventRepository outboxEventRepository;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private AnswerPublication125MutableClock clock;

	private Answer125IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		clock.setInstant(NOW);
		FaultInjectingOutboxEventRepository.reset();
		fixtures = new Answer125IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("publication125-sender");
		recipientId = fixtures.account("publication125-recipient");
		promotedRelease();
	}

	@Test
	@DisplayName("INT-010 / INT-011: ALLOW verdict를 worker가 처리하면 Answer PUBLISHED, recipient ANSWERED, 슬롯 0, published Outbox 1건이 한 transaction으로 commit된다")
	void allowVerdictPublishesAnswerAndReleasesSlotAtomically() {
		long postId = fixtures.post(senderId, "int010", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = submissionApplicationService.submit(recipientId, "int010-key", postRecipientId, "본문", List.of());
		long filterJobId = fixtures.filterJobIdFor(submitted.getId());
		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.ALLOW, 1L);

		AnswerModerationVerdictWorker.BatchResult result = verdictWorker.processBatch(workerCommand("worker-1", 1));

		assertThat(result.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(fixtures.answerStatus(submitted.getId())).isEqualTo("PUBLISHED");
		assertThat(fixtures.status(postRecipientId)).isEqualTo("ANSWERED");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(0);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED' AND aggregate_id = ?",
			Integer.class, submitted.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-012: ANSWER_PUBLISHED outbox 저장이 강제로 실패하면 answer/recipient/count가 전부 원상태로 rollback된다")
	void rollsBackPublishTransactionWhenPublishedOutboxSaveFails() {
		long postId = fixtures.post(senderId, "int012", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = submissionApplicationService.submit(recipientId, "int012-key", postRecipientId, "본문", List.of());
		long filterJobId = fixtures.filterJobIdFor(submitted.getId());
		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.ALLOW, 2L);
		FaultInjectingOutboxEventRepository.failNextSaveOfType(OutboxEventType.ANSWER_PUBLISHED);

		// AnswerModerationVerdictWorker.processVerdictReady는 applyAllow(publish 포함)와
		// completeClaimOrThrow를 한 TransactionTemplate.execute 블록에 함께 묶는다(REQUIRED
		// 전파로 publish()가 같은 물리 transaction에 참여). outbox 저장 실패로 이 블록이 rollback되고,
		// processClaimed가 이벤트별로 예외를 격리하므로(GitHub #125에서 수정) processBatch 자체는
		// 예외 없이 반환하며 이 이벤트의 outcome만 FAILED로 남는다.
		AnswerModerationVerdictWorker.BatchResult result = verdictWorker.processBatch(workerCommand("worker-1", 2));
		assertThat(result.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.FAILED);

		assertThat(fixtures.answerStatus(submitted.getId())).isEqualTo("SUBMITTED");
		assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED'", Integer.class)).isEqualTo(0);
	}

	@Test
	@DisplayName("INT-013: 두 worker가 같은 VERDICT_READY를 동시에 claim해도 한 worker만 처리하고 슬롯·Outbox는 1회만 변경된다")
	void concurrentWorkersClaimVerdictOnlyOnce() throws Exception {
		long postId = fixtures.post(senderId, "int013", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = submissionApplicationService.submit(recipientId, "int013-key", postRecipientId, "본문", List.of());
		long filterJobId = fixtures.filterJobIdFor(submitted.getId());
		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.ALLOW, 3L);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<AnswerModerationVerdictWorker.BatchResult> firstFuture = executor.submit(() -> {
				ready.countDown();
				start.await(5, TimeUnit.SECONDS);
				return verdictWorker.processBatch(workerCommand("worker-a", 1));
			});
			Future<AnswerModerationVerdictWorker.BatchResult> secondFuture = executor.submit(() -> {
				ready.countDown();
				start.await(5, TimeUnit.SECONDS);
				return verdictWorker.processBatch(workerCommand("worker-b", 1));
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			AnswerModerationVerdictWorker.BatchResult first = firstFuture.get(15, TimeUnit.SECONDS);
			AnswerModerationVerdictWorker.BatchResult second = secondFuture.get(15, TimeUnit.SECONDS);

			int totalClaimed = first.claimed() + second.claimed();
			assertThat(totalClaimed).isEqualTo(1);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}

		assertThat(fixtures.status(postRecipientId)).isEqualTo("ANSWERED");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(0);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED'", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-014: 이미 PUBLISHED된 결과에 같은 이벤트를 재처리해도 PUBLISHED/outbox/slot count가 변하지 않는다")
	void replayingProcessedEventDoesNotDuplicateEffects() {
		long postId = fixtures.post(senderId, "int014", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = submissionApplicationService.submit(recipientId, "int014-key", postRecipientId, "본문", List.of());
		long filterJobId = fixtures.filterJobIdFor(submitted.getId());
		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.ALLOW, 4L);
		verdictWorker.processBatch(workerCommand("worker-1", 1));
		assertThat(fixtures.answerStatus(submitted.getId())).isEqualTo("PUBLISHED");

		// 같은 판정을 새 이벤트 id로 재전달(late-retry/재처리를 흉내)한다 — worker는 자체 중복
		// 방지 로직이 없고 AnswerNotificationService.publish()의 멱등성에 위임한다.
		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.ALLOW, 5L);
		AnswerModerationVerdictWorker.BatchResult replay = verdictWorker.processBatch(workerCommand("worker-1", 1));

		assertThat(replay.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(fixtures.answerStatus(submitted.getId())).isEqualTo("PUBLISHED");
		assertThat(fixtures.status(postRecipientId)).isEqualTo("ANSWERED");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(0);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED'", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-017: BLOCK verdict는 answer를 REJECTED로 만들고 recipient/count/ANSWER_PUBLISHED는 건드리지 않으며 새 키 재제출이 가능하다")
	void blockVerdictRejectsAnswerWithoutTouchingSlot() {
		long postId = fixtures.post(senderId, "int017", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = submissionApplicationService.submit(recipientId, "int017-key", postRecipientId, "본문", List.of());
		long filterJobId = fixtures.filterJobIdFor(submitted.getId());
		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.BLOCK, 6L);

		verdictWorker.processBatch(workerCommand("worker-1", 1));

		assertThat(fixtures.answerStatus(submitted.getId())).isEqualTo("REJECTED");
		assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED'", Integer.class)).isEqualTo(0);

		Answer resubmitted = submissionApplicationService.submit(
			recipientId, "int017-key-2", postRecipientId, "다른 본문", List.of());
		assertThat(resubmitted.getId()).isNotEqualTo(submitted.getId());
	}

	@Test
	@DisplayName("INT-018: deadline elapsed만으로는 공개·슬롯 해제가 없고, 이후 도착한 ALLOW는 INT-011과 같은 단일 공개 결과를 만든다")
	void deadlineElapsedAloneDoesNotPublishButLateAllowStillDoes() {
		long postId = fixtures.post(senderId, "int018", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 1);
		Answer submitted = submissionApplicationService.submit(recipientId, "int018-key", postRecipientId, "본문", List.of());
		long filterJobId = fixtures.filterJobIdFor(submitted.getId());
		seedDeadlineElapsed(filterJobId, submitted.getId(), 7L);

		verdictWorker.processBatch(workerCommand("worker-1", 1));

		// deadline elapsed는 답변을 전혀 건드리지 않는다(AnswerModerationVerdictWorker.
		// processDeadlineElapsed) — SAFETY_CHECKING 전이는 publish()/reject()가 호출될 때만
		// 일어나므로 이 시점 상태는 제출 직후의 SUBMITTED 그대로다.
		assertThat(fixtures.answerStatus(submitted.getId())).isEqualTo("SUBMITTED");
		assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");

		seedVerdictReady(filterJobId, submitted.getId(), FilterVerdict.ALLOW, 8L);
		verdictWorker.processBatch(workerCommand("worker-1", 1));

		assertThat(fixtures.answerStatus(submitted.getId())).isEqualTo("PUBLISHED");
		assertThat(fixtures.status(postRecipientId)).isEqualTo("ANSWERED");
		assertThat(fixtures.activeCount(recipientId)).isEqualTo(0);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED'", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-021: 배치의 첫 이벤트 완료 처리가 예외로 실패해도 같은 호출에서 뒤 이벤트가 즉시 처리되고, 실패한 원본 이벤트는 lease 만료 후 재수집돼 중복 공개 없이 정확히 한 번만 공개된다")
	void batchIsolatesFailingEventFromLaterEventsInTheSameClaim() {
		long postA = fixtures.post(senderId, "int021-a", NOW.plusSeconds(3600), "ACTIVE", null);
		long recipientA = fixtures.available(postA, recipientId, NOW.minusSeconds(10), 0);
		long postB = fixtures.post(senderId, "int021-b", NOW.plusSeconds(3600), "ACTIVE", null);
		long recipientB = fixtures.available(postB, recipientId, NOW.minusSeconds(10), 0);
		fixtures.receiveState(recipientId, 2);
		Answer answerA = submissionApplicationService.submit(recipientId, "int021-a", recipientA, "본문 A", List.of());
		Answer answerB = submissionApplicationService.submit(recipientId, "int021-b", recipientB, "본문 B", List.of());
		long jobA = fixtures.filterJobIdFor(answerA.getId());
		long jobB = fixtures.filterJobIdFor(answerB.getId());
		long eventIdA = seedVerdictReady(jobA, answerA.getId(), FilterVerdict.ALLOW, 9L);
		seedVerdictReady(jobB, answerB.getId(), FilterVerdict.ALLOW, 10L);
		FaultInjectingOutboxEventRepository.failCompleteOnce(eventIdA);

		// processClaimed가 이벤트별로 예외를 격리하므로(GitHub #125에서 수정) A의 완료 처리 실패가
		// 예외를 던지지 않고 FAILED outcome으로 끝나며, 같은 stream().map() 호출 안에서 곧바로 B가
		// 시도된다 — B는 이번 첫 호출에서 이미 PUBLISHED여야 한다.
		AnswerModerationVerdictWorker.BatchResult firstBatch = verdictWorker.processBatch(workerCommand("worker-1", 2));
		assertThat(firstBatch.claimed()).isEqualTo(2);
		assertThat(firstBatch.outcomes()).containsExactlyInAnyOrder(
			AnswerModerationVerdictWorker.Outcome.FAILED, AnswerModerationVerdictWorker.Outcome.RESOLVED);
		assertThat(fixtures.answerStatus(answerB.getId())).isEqualTo("PUBLISHED");

		// A는 completeClaimOrThrow가 실패하며 같은 transaction의 publish()도 rollback됐으므로 아직
		// SUBMITTED다. claimDue가 부여한 PROCESSING lease(NOW+60s)는 그 실패한 transaction 밖에서
		// 이미 커밋됐으므로 worker-1이 그대로 들고 있다 — lease가 실제로 만료되는 시점(NOW+120s) 이후로
		// 재시도해야 진짜 reclaim 경로를 태워 A를 공개할 수 있다.
		assertThat(fixtures.answerStatus(answerA.getId())).isEqualTo("SUBMITTED");
		verdictWorker.processBatch(workerCommand("worker-1", 5, NOW.plusSeconds(120)));

		assertThat(fixtures.answerStatus(answerA.getId())).isEqualTo("PUBLISHED");
		assertThat(fixtures.answerStatus(answerB.getId())).isEqualTo("PUBLISHED");
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'ANSWER_PUBLISHED' AND aggregate_id IN (?, ?)",
			Integer.class, answerA.getId(), answerB.getId())).isEqualTo(2);
	}

	private long seedVerdictReady(long filterJobId, long answerId, FilterVerdict verdict, long dedupSuffix) {
		OutboxEvent event = OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, filterJobId,
			OutboxEventType.MODERATION_VERDICT_READY, "filter-job:" + filterJobId + ":VERDICT_READY:" + dedupSuffix,
			AnswerModerationEventPayloadsTestSupport.verdictReadyJson(objectMapper, filterJobId, answerId, verdict), NOW);
		return outboxEventRepository.save(event).id();
	}

	private long seedDeadlineElapsed(long filterJobId, long answerId, long dedupSuffix) {
		OutboxEvent event = OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, filterJobId,
			OutboxEventType.MODERATION_DEADLINE_ELAPSED, "filter-job:" + filterJobId + ":DEADLINE_ELAPSED:" + dedupSuffix,
			AnswerModerationEventPayloadsTestSupport.deadlineElapsedJson(objectMapper, filterJobId, answerId), NOW);
		return outboxEventRepository.save(event).id();
	}

	private AnswerModerationVerdictWorker.BatchCommand workerCommand(String owner, int limit) {
		return new AnswerModerationVerdictWorker.BatchCommand(limit, owner, NOW.plusSeconds(30), NOW.plusSeconds(60));
	}

	private AnswerModerationVerdictWorker.BatchCommand workerCommand(String owner, int limit, Instant at) {
		return new AnswerModerationVerdictWorker.BatchCommand(limit, owner, at, at.plusSeconds(30));
	}

	private void promotedRelease() {
		AnswerModerationReleaseTestFixture.promotedRelease(releaseRegistryService, senderId);
	}
}

@TestConfiguration
class AnswerPublication125TestClockConfiguration {

	@Bean
	@Primary
	AnswerPublication125MutableClock answerPublication125MutableClock() {
		return new AnswerPublication125MutableClock(Instant.parse("2026-08-17T07:00:00.123456Z"), ZoneOffset.UTC);
	}
}

final class AnswerPublication125MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	AnswerPublication125MutableClock(Instant initial, ZoneId zone) {
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
		return new AnswerPublication125MutableClock(current.get(), newZone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}
}

@TestConfiguration
class AnswerPublication125FaultInjectionConfiguration {

	@Bean
	@Primary
	OutboxEventRepository faultInjectingOutboxEventRepository(
		@Qualifier("jdbcNotificationRepository") OutboxEventRepository delegate) {
		return new FaultInjectingOutboxEventRepository(delegate);
	}
}

/**
 * 실제 {@code jdbcNotificationRepository} 빈에 모든 호출을 위임하되, 테스트가 지정한 조건에서만
 * 강제로 예외를 던진다(GitHub #125 INT-012, INT-021). 정적 상태를 쓰는 이유는 테스트 메서드가
 * {@code @TestConfiguration}이 만든 빈 인스턴스에 직접 접근할 방법이 없기 때문이다 — 각 테스트
 * 시작 시 {@link #reset()}으로 초기화한다.
 */
final class FaultInjectingOutboxEventRepository implements OutboxEventRepository {

	private static final AtomicReference<OutboxEventType> failNextSaveOfType = new AtomicReference<>();
	private static final AtomicLong failCompleteOnceForEventId = new AtomicLong(-1);

	private final OutboxEventRepository delegate;

	FaultInjectingOutboxEventRepository(OutboxEventRepository delegate) {
		this.delegate = delegate;
	}

	static void reset() {
		failNextSaveOfType.set(null);
		failCompleteOnceForEventId.set(-1);
	}

	static void failNextSaveOfType(OutboxEventType type) {
		failNextSaveOfType.set(type);
	}

	static void failCompleteOnce(long eventId) {
		failCompleteOnceForEventId.set(eventId);
	}

	@Override
	public OutboxEvent save(OutboxEvent event) {
		if (event.eventType() == failNextSaveOfType.get()) {
			failNextSaveOfType.set(null);
			throw new RuntimeException("INT-012 강제 실패: " + event.eventType());
		}
		return delegate.save(event);
	}

	@Override
	public Optional<OutboxEvent> findEventById(long id) {
		return delegate.findEventById(id);
	}

	@Override
	public Optional<OutboxEvent> findByDedupKey(String dedupKey) {
		return delegate.findByDedupKey(dedupKey);
	}

	@Override
	public Optional<OutboxEvent> claim(long id, Instant at) {
		return delegate.claim(id, at);
	}

	@Override
	public Optional<OutboxEvent> claim(long id, String leaseOwner, Instant at, Instant leaseExpiresAt) {
		return delegate.claim(id, leaseOwner, at, leaseExpiresAt);
	}

	@Override
	public List<OutboxEvent> claimDue(Set<OutboxEventType> eventTypes, int limit, String leaseOwner,
		Instant at, Instant leaseExpiresAt) {
		return delegate.claimDue(eventTypes, limit, leaseOwner, at, leaseExpiresAt);
	}

	@Override
	public boolean complete(long id, String leaseOwner, long leaseGeneration, Instant processedAt) {
		if (failCompleteOnceForEventId.compareAndSet(id, -1)) {
			throw new RuntimeException("INT-021 강제 실패: complete(" + id + ")");
		}
		return delegate.complete(id, leaseOwner, leaseGeneration, processedAt);
	}

	@Override
	public boolean fail(long id, String leaseOwner, long leaseGeneration, Instant at,
		Instant nextAttemptAt, boolean dead) {
		return delegate.fail(id, leaseOwner, leaseGeneration, at, nextAttemptAt, dead);
	}

	@Override
	public boolean update(OutboxEvent event) {
		return delegate.update(event);
	}
}
