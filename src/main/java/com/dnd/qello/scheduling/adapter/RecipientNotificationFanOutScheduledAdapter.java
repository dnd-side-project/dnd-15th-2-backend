package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.scheduling.WorkerInstanceIdentity;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

@Component
@ConditionalOnProperty(
	prefix = "qello.worker.scheduling",
	name = {"enabled", "recipient-notification-fan-out.enabled"},
	havingValue = "true")
public class RecipientNotificationFanOutScheduledAdapter {
	private final RecipientNotificationFanOutWorker worker;
	private final WorkerSchedulingProperties.OutboxSettings settings;
	private final WorkerInstanceIdentity identity;
	private final Clock clock;
	private final WorkerMetrics metrics;
	private final OutboxRetryPolicy retryPolicy;

	public RecipientNotificationFanOutScheduledAdapter(RecipientNotificationFanOutWorker worker,
		WorkerSchedulingProperties properties, WorkerInstanceIdentity identity, Clock clock, WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.recipientNotificationFanOut();
		this.identity = identity;
		this.clock = clock;
		this.metrics = metrics;
		this.retryPolicy = settings.retry().toPolicy();
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.recipient-notification-fan-out.fixed-delay}")
	void runOnce() {
		try {
			Instant at = clock.instant();
			var result = worker.processBatch(new RecipientNotificationFanOutWorker.BatchCommand(settings.batchSize(),
				identity.owner(), at, at.plus(settings.leaseDuration()), retryPolicy));
			metrics.recordClaimed(WorkerName.RECIPIENT_NOTIFICATION_FAN_OUT, result.claimed());
			result.outcomes().forEach(outcome -> metrics.recordOutcome(WorkerName.RECIPIENT_NOTIFICATION_FAN_OUT,
				map(outcome), 1));
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.RECIPIENT_NOTIFICATION_FAN_OUT, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}

	private Outcome map(RecipientNotificationFanOutWorker.Outcome outcome) {
		return switch (outcome) {
			case PROCESSED -> Outcome.PROCESSED;
			case RETRYABLE -> Outcome.RETRYABLE;
			case DEAD -> Outcome.DEAD;
			case STALE_LEASE -> Outcome.STALE_LEASE;
			case FAILURE_RECORDING_FAILED -> Outcome.FAILURE_RECORDING_FAILED;
		};
	}
}
