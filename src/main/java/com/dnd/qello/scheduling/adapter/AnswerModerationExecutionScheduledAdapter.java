package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.scheduling.WorkerInstanceIdentity;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

// execution·deadline·verdict 세 adapter가 모두 이 하나의 property로만 활성화된다
// (GitHub #204 완료 조건: 부분 활성화 금지). 세 worker 중 하나만 켜는 별도
// 조건을 두지 않는다 — WorkerSchedulingProperties.AnswerModerationSettings도
// 개별 enabled 없이 이 하나의 flag만 갖는 이유와 같다.
@Component
@ConditionalOnProperty(prefix = "qello.worker.scheduling.answer-moderation", name = "enabled", havingValue = "true")
public class AnswerModerationExecutionScheduledAdapter {
	private final AnswerModerationExecutionWorker worker;
	private final WorkerSchedulingProperties.ClaimSettings settings;
	private final WorkerInstanceIdentity identity;
	private final Clock clock;
	private final WorkerMetrics metrics;

	public AnswerModerationExecutionScheduledAdapter(AnswerModerationExecutionWorker worker,
			WorkerSchedulingProperties properties, WorkerInstanceIdentity identity, Clock clock,
			WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.answerModeration().execution();
		this.identity = identity;
		this.clock = clock;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.answer-moderation.execution.fixed-delay}")
	void runOnce() {
		try {
			Instant at = clock.instant();
			var result = worker.processBatch(new AnswerModerationExecutionWorker.BatchCommand(
					settings.batchSize(), identity.owner(), at, at.plus(settings.leaseDuration())));
			metrics.recordClaimed(WorkerName.ANSWER_MODERATION_EXECUTION, result.claimed());
			result.outcomes()
					.forEach(outcome -> metrics.recordOutcome(WorkerName.ANSWER_MODERATION_EXECUTION, map(outcome), 1));
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.ANSWER_MODERATION_EXECUTION, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}

	private Outcome map(AnswerModerationExecutionWorker.Outcome outcome) {
		return switch (outcome) {
			case RESOLVED -> Outcome.PROCESSED;
			case SKIPPED_NOT_ELIGIBLE -> Outcome.INELIGIBLE;
			case RETRY_SCHEDULED -> Outcome.RETRY_SCHEDULED;
			case RETRY_EXHAUSTED -> Outcome.DEAD;
			case RETRY_DEFERRED_BY_GATE -> Outcome.RETRYABLE;
			case JOB_NOT_FOUND -> Outcome.FAILED;
			case STALE_LEASE -> Outcome.STALE_LEASE;
		};
	}
}
