/**
 * Created at: 2026-08-17T16:30:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-010 through UNIT-014
 * (UNIT-010 stable idempotency key/single execution-requested event is already covered by
 * AnswerModerationJobIntakeServiceTest emitsHistoryAndExecutionRequestedEvent, created for #107)
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

class AnswerModerationVerdictWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final long ANSWER_ID = 42L;
	private static final long FILTER_JOB_ID = 900L;

	private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
	private final AnswerNotificationService answerNotificationService = mock(AnswerNotificationService.class);

	@Test
	@DisplayName("UNIT-011: ALLOW verdict는 publish만 호출하고 reject 경로를 실행하지 않는다")
	void appliesAllowVerdictByPublishingOnly() {
		OutboxEvent event = claimedVerdictEvent(FilterVerdict.ALLOW, 1L);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(event));
		when(outboxEventRepository.complete(eq(1L), eq("worker-1"), eq(event.leaseGeneration()), any())).thenReturn(true);
		when(answerNotificationService.publish(ANSWER_ID, NOW)).thenReturn(safetyChecking().markSafetyPassed().publish(NOW));
		AnswerModerationVerdictWorker worker = worker();

		AnswerModerationVerdictWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		verify(answerNotificationService).publish(ANSWER_ID, NOW);
		verify(answerNotificationService, never()).reject(anyLong(), any());
	}

	@Test
	@DisplayName("UNIT-012: BLOCK verdict는 reject만 호출하고 recipient/slot/ANSWER_PUBLISHED에 영향을 주지 않는다")
	void appliesBlockVerdictByRejectingOnly() {
		OutboxEvent event = claimedVerdictEvent(FilterVerdict.BLOCK, 2L);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(event));
		when(outboxEventRepository.complete(eq(2L), eq("worker-1"), eq(event.leaseGeneration()), any())).thenReturn(true);
		when(answerNotificationService.reject(ANSWER_ID, NOW)).thenReturn(safetyChecking().rejectSafety());
		AnswerModerationVerdictWorker worker = worker();

		AnswerModerationVerdictWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		verify(answerNotificationService).reject(ANSWER_ID, NOW);
		verify(answerNotificationService, never()).publish(anyLong(), any());
	}

	@Test
	@DisplayName("UNIT-013: deadline elapsed는 answer를 건드리지 않고 이벤트만 소비하며, 이후 도착한 ALLOW는 정상 공개 경로로 처리한다")
	void treatsDeadlineElapsedAsFailClosedAndStillAppliesLateAllow() {
		OutboxEvent deadlineEvent = claimedDeadlineEvent(3L);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(deadlineEvent));
		when(outboxEventRepository.complete(eq(3L), eq("worker-1"), eq(deadlineEvent.leaseGeneration()), any()))
			.thenReturn(true);
		AnswerModerationVerdictWorker worker = worker();

		AnswerModerationVerdictWorker.BatchResult first = worker.processBatch(command());

		assertThat(first.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		verify(answerNotificationService, never()).publish(anyLong(), any());
		verify(answerNotificationService, never()).reject(anyLong(), any());

		OutboxEvent lateAllow = claimedVerdictEvent(FilterVerdict.ALLOW, 4L);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(lateAllow));
		when(outboxEventRepository.complete(eq(4L), eq("worker-1"), eq(lateAllow.leaseGeneration()), any())).thenReturn(true);
		when(answerNotificationService.publish(ANSWER_ID, NOW)).thenReturn(safetyChecking().markSafetyPassed().publish(NOW));

		AnswerModerationVerdictWorker.BatchResult second = worker.processBatch(command());

		assertThat(second.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		verify(answerNotificationService).publish(ANSWER_ID, NOW);
	}

	@Test
	@DisplayName("UNIT-014: 이미 PUBLISHED인 답변에 다시 도착한 ALLOW는 AnswerNotificationService의 멱등 반환에 위임하고 worker가 별도 판단하지 않는다")
	void delegatesTerminalIdempotencyToNotificationService() {
		OutboxEvent event = claimedVerdictEvent(FilterVerdict.ALLOW, 5L);
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(event));
		when(outboxEventRepository.complete(eq(5L), eq("worker-1"), eq(event.leaseGeneration()), any())).thenReturn(true);
		Answer alreadyPublished = safetyChecking().markSafetyPassed().publish(NOW.minusSeconds(60));
		when(answerNotificationService.publish(ANSWER_ID, NOW)).thenReturn(alreadyPublished);
		AnswerModerationVerdictWorker worker = worker();

		AnswerModerationVerdictWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		verify(answerNotificationService, org.mockito.Mockito.times(1)).publish(ANSWER_ID, NOW);
		// targetVersion 불일치 재처리: FilterTarget.targetVersion은 현재 항상 0으로 고정되고
		// (filtering/domain/FilterTarget.java) Answer 도메인에는 낙관적 버전 필드가 없다(답변 편집은
		// GH-125 명시적 제외 범위). 즉 이 코드 경로에서 "stale targetVersion"을 만들 방법이 없어
		// 검증할 대상이 없다 — 편집 기능이 생기면 이 worker와 Answer에 버전 비교를 추가하고 이
		// 테스트를 확장해야 한다. 이 테스트는 UNIT-014의 나머지 절반(terminal 결과의 멱등 위임)만
		// 검증한다.
	}

	@Test
	@DisplayName("ANSWER가 아닌 target의 VERDICT_READY는 answer 처리 없이 스킵 완료한다")
	void skipsVerdictForNonAnswerTarget() {
		AnswerModerationEventPayloads.VerdictReady payload = new AnswerModerationEventPayloads.VerdictReady(
			FILTER_JOB_ID, FilterTargetType.NICKNAME, 1L, 0L, FilterVerdict.ALLOW);
		OutboxEvent event = withId(6L, OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, FILTER_JOB_ID,
			OutboxEventType.MODERATION_VERDICT_READY, "filter-job:" + FILTER_JOB_ID + ":VERDICT_READY",
			AnswerModerationEventPayloads.toJson(MAPPER, payload), NOW).claimed("worker-1", NOW, NOW.plusSeconds(30)));
		when(outboxEventRepository.claimDue(any(), eq(10), eq("worker-1"), eq(NOW), eq(NOW.plusSeconds(30))))
			.thenReturn(List.of(event));
		when(outboxEventRepository.complete(eq(6L), eq("worker-1"), eq(event.leaseGeneration()), any())).thenReturn(true);
		AnswerModerationVerdictWorker worker = worker();

		AnswerModerationVerdictWorker.BatchResult result = worker.processBatch(command());

		assertThat(result.outcomes()).containsExactly(AnswerModerationVerdictWorker.Outcome.RESOLVED);
		verify(answerNotificationService, never()).publish(anyLong(), any());
		verify(answerNotificationService, never()).reject(anyLong(), any());
	}

	private AnswerModerationVerdictWorker.BatchCommand command() {
		return new AnswerModerationVerdictWorker.BatchCommand(10, "worker-1", NOW, NOW.plusSeconds(30));
	}

	private OutboxEvent claimedVerdictEvent(FilterVerdict verdict, long eventId) {
		AnswerModerationEventPayloads.VerdictReady payload = new AnswerModerationEventPayloads.VerdictReady(
			FILTER_JOB_ID, FilterTargetType.ANSWER, ANSWER_ID, 0L, verdict);
		String json = AnswerModerationEventPayloads.toJson(MAPPER, payload);
		return withId(eventId, OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, FILTER_JOB_ID,
			OutboxEventType.MODERATION_VERDICT_READY, "filter-job:" + FILTER_JOB_ID + ":VERDICT_READY", json, NOW)
			.claimed("worker-1", NOW, NOW.plusSeconds(30)));
	}

	private OutboxEvent claimedDeadlineEvent(long eventId) {
		AnswerModerationEventPayloads.DeadlineElapsed payload = new AnswerModerationEventPayloads.DeadlineElapsed(
			FILTER_JOB_ID, FilterTargetType.ANSWER, ANSWER_ID, 0L);
		String json = AnswerModerationEventPayloads.toJson(MAPPER, payload);
		return withId(eventId, OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, FILTER_JOB_ID,
			OutboxEventType.MODERATION_DEADLINE_ELAPSED, "filter-job:" + FILTER_JOB_ID + ":DEADLINE_ELAPSED", json, NOW)
			.claimed("worker-1", NOW, NOW.plusSeconds(30)));
	}

	private static OutboxEvent withId(long id, OutboxEvent event) {
		return new OutboxEvent(id, event.aggregateType(), event.aggregateId(), event.eventType(), event.dedupKey(),
			event.payload(), event.status(), event.attemptCount(), event.nextAttemptAt(), event.createdAt(),
			event.processedAt(), event.matchRound(), event.leaseOwner(), event.leaseExpiresAt(),
			event.leaseGeneration());
	}

	private static Answer safetyChecking() {
		Answer submitted = Answer.submit(7L, 11L, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		Answer withId = Answer.restore(ANSWER_ID, submitted.getPostRecipientId(), submitted.getAuthorId(),
			AnswerStatus.SUBMITTED, submitted.getIdempotencyKey(), submitted.getBodyText(),
			submitted.getCoarseRegionCode(), submitted.getBearingFromSenderDegrees(), submitted.getDistanceBand(),
			AnswerModerationStatus.PENDING, submitted.getSubmittedAt(), null, null, submitted.getDistanceM(), null, 0);
		return withId.startSafetyCheck();
	}

	private AnswerModerationVerdictWorker worker() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return new AnswerModerationVerdictWorker(outboxEventRepository, answerNotificationService, MAPPER,
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
