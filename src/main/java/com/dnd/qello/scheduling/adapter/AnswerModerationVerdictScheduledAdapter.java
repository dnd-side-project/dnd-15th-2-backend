package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.filtering.moderation.AnswerModerationVerdictWorker;
import com.dnd.qello.scheduling.WorkerInstanceIdentity;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

// AnswerModerationExecutionScheduledAdapter와 같은 property 하나로만 켜진다
// (부분 활성화 금지, GitHub #204).
@Component
@ConditionalOnProperty(prefix = "qello.worker.scheduling.answer-moderation", name = "enabled", havingValue = "true")
public class AnswerModerationVerdictScheduledAdapter {
	private final AnswerModerationVerdictWorker worker;
	private final WorkerSchedulingProperties.ClaimSettings settings;
	private final WorkerInstanceIdentity identity;
	private final Clock clock;
	private final WorkerMetrics metrics;

	public AnswerModerationVerdictScheduledAdapter(AnswerModerationVerdictWorker worker,
			WorkerSchedulingProperties properties, WorkerInstanceIdentity identity, Clock clock,
			WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.answerModeration().verdict();
		this.identity = identity;
		this.clock = clock;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.answer-moderation.verdict.fixed-delay}")
	void runOnce() {
		try {
			Instant at = clock.instant();
			var result = worker.processBatch(new AnswerModerationVerdictWorker.BatchCommand(
					settings.batchSize(), identity.owner(), at, at.plus(settings.leaseDuration())));
			metrics.recordClaimed(WorkerName.ANSWER_MODERATION_VERDICT, result.claimed());
			result.outcomes()
					.forEach(outcome -> metrics.recordOutcome(WorkerName.ANSWER_MODERATION_VERDICT, map(outcome), 1));
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.ANSWER_MODERATION_VERDICT, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}

	private Outcome map(AnswerModerationVerdictWorker.Outcome outcome) {
		return switch (outcome) {
			case RESOLVED -> Outcome.PROCESSED;
			case STALE_LEASE -> Outcome.STALE_LEASE;
			case FAILED -> Outcome.FAILED;
		};
	}
}
