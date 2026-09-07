package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.filtering.moderation.AnswerModerationDeadlineWorker;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

// AnswerModerationExecutionScheduledAdapter와 같은 property 하나로만 켜진다
// (부분 활성화 금지, GitHub #204). 이 worker는 claim/lease가 없는 스캔이라
// WorkerInstanceIdentity가 필요 없다 — 같은 job에 두 번 신호를 보내지 않는
// 보장은 dedup_key 유일성 제약이 담당한다(AnswerModerationDeadlineWorker 참고).
@Component
@ConditionalOnProperty(prefix = "qello.worker.scheduling.answer-moderation", name = "enabled", havingValue = "true")
public class AnswerModerationDeadlineScheduledAdapter {
	private final AnswerModerationDeadlineWorker worker;
	private final WorkerSchedulingProperties.ScanSettings settings;
	private final Clock clock;
	private final WorkerMetrics metrics;

	public AnswerModerationDeadlineScheduledAdapter(AnswerModerationDeadlineWorker worker,
			WorkerSchedulingProperties properties, Clock clock, WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.answerModeration().deadline();
		this.clock = clock;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.answer-moderation.deadline.fixed-delay}")
	void runOnce() {
		try {
			var result = worker.processBatch(settings.batchSize(), clock.instant());
			metrics.recordScanned(WorkerName.ANSWER_MODERATION_DEADLINE, result.scanned());
			result.outcomes()
					.forEach(outcome -> metrics.recordOutcome(WorkerName.ANSWER_MODERATION_DEADLINE, map(outcome), 1));
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.ANSWER_MODERATION_DEADLINE, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}

	private Outcome map(AnswerModerationDeadlineWorker.Outcome outcome) {
		return switch (outcome) {
			case SIGNALED -> Outcome.PROCESSED;
			case ALREADY_SIGNALED -> Outcome.INELIGIBLE;
			case FAILED -> Outcome.FAILED;
		};
	}
}
