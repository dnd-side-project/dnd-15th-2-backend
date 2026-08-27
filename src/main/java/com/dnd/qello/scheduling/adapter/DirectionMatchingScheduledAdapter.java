package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.notification.domain.ExponentialJitterBackoffStrategy;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.scheduling.WorkerInstanceIdentity;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

@Component
@ConditionalOnProperty(
	prefix = "qello.worker.scheduling",
	name = {"enabled", "direction-matching.enabled"},
	havingValue = "true")
public class DirectionMatchingScheduledAdapter {
	private final DirectionMatchingWorker worker;
	private final WorkerSchedulingProperties.OutboxSettings settings;
	private final WorkerInstanceIdentity identity;
	private final Clock clock;
	private final WorkerMetrics metrics;
	private final OutboxRetryPolicy retryPolicy;

	public DirectionMatchingScheduledAdapter(DirectionMatchingWorker worker, WorkerSchedulingProperties properties,
		WorkerInstanceIdentity identity, Clock clock, WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.directionMatching();
		this.identity = identity;
		this.clock = clock;
		this.metrics = metrics;
		var retry = settings.retry();
		this.retryPolicy = new OutboxRetryPolicy(retry.maxAttempts(), ExponentialJitterBackoffStrategy.withRandomJitter(
			retry.baseDelay(), retry.maxDelay()));
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.direction-matching.fixed-delay}")
	void runOnce() {
		try {
			Instant at = clock.instant();
			var result = worker.processBatch(new DirectionMatchingWorker.BatchCommand(settings.batchSize(), identity.owner(), at,
				at.plus(settings.leaseDuration()), retryPolicy));
			metrics.recordClaimed(WorkerName.DIRECTION_MATCHING, result.claimed());
			result.outcomes().forEach(outcome -> metrics.recordOutcome(WorkerName.DIRECTION_MATCHING, map(outcome), 1));
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.DIRECTION_MATCHING, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}

	private Outcome map(DirectionMatchingWorker.Outcome outcome) {
		return switch (outcome) {
			case PROCESSED -> Outcome.PROCESSED;
			case RETRYABLE -> Outcome.RETRYABLE;
			case DEAD -> Outcome.DEAD;
			case STALE_LEASE -> Outcome.STALE_LEASE;
		};
	}
}
