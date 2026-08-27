package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker;
import com.dnd.qello.direction.sweep.SweepBatchResult;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

@Component
@ConditionalOnProperty(
	prefix = "qello.worker.scheduling",
	name = {"enabled", "recipient-expiration-sweep.enabled"},
	havingValue = "true")
public class RecipientExpirationSweepScheduledAdapter {
	private final RecipientExpirationSweepWorker worker;
	private final WorkerSchedulingProperties.SweepSettings settings;
	private final Clock clock;
	private final WorkerMetrics metrics;

	public RecipientExpirationSweepScheduledAdapter(RecipientExpirationSweepWorker worker,
		WorkerSchedulingProperties properties, Clock clock, WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.recipientExpirationSweep();
		this.clock = clock;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.recipient-expiration-sweep.fixed-delay}")
	void runOnce() {
		try {
			Instant at = clock.instant();
			SweepBatchResult result = worker.processBatch(
				new RecipientExpirationSweepWorker.BatchCommand(settings.batchSize(), at));
			metrics.recordScanned(WorkerName.RECIPIENT_EXPIRATION_SWEEP, result.scanned());
			metrics.recordOutcome(WorkerName.RECIPIENT_EXPIRATION_SWEEP, Outcome.RELEASED, result.released());
			metrics.recordOutcome(WorkerName.RECIPIENT_EXPIRATION_SWEEP, Outcome.INELIGIBLE, result.ineligible());
			metrics.recordOutcome(WorkerName.RECIPIENT_EXPIRATION_SWEEP, Outcome.FAILED, result.failed());
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.RECIPIENT_EXPIRATION_SWEEP, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}
}
