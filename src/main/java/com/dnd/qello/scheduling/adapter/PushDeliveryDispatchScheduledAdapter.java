package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

@Component
@ConditionalOnProperty(
	prefix = "qello.worker.scheduling",
	name = {"enabled", "push-delivery-dispatch.enabled"},
	havingValue = "true")
public class PushDeliveryDispatchScheduledAdapter {
	private final PushDeliveryDispatchWorker worker;
	private final WorkerSchedulingProperties.PushSettings settings;
	private final Clock clock;
	private final WorkerMetrics metrics;

	public PushDeliveryDispatchScheduledAdapter(PushDeliveryDispatchWorker worker,
		WorkerSchedulingProperties properties, Clock clock, WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.pushDeliveryDispatch();
		this.clock = clock;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.push-delivery-dispatch.fixed-delay}")
	void runOnce() {
		try {
			Instant at = clock.instant();
			var result = worker.dispatchBatch(new PushDeliveryDispatchWorker.BatchCommand(
				settings.batchSize(), at, at.plus(settings.leaseDuration())));
			metrics.recordClaimed(WorkerName.PUSH_DELIVERY_DISPATCH, result.claimed());
			result.outcomes().forEach(item -> metrics.recordOutcome(
				WorkerName.PUSH_DELIVERY_DISPATCH, map(item.outcome()), 1));
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.PUSH_DELIVERY_DISPATCH, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}

	private Outcome map(PushDeliveryDispatchWorker.Outcome outcome) {
		return switch (outcome) {
			case SENT -> Outcome.SENT;
			case RETRY_SCHEDULED -> Outcome.RETRY_SCHEDULED;
			case DEAD -> Outcome.DEAD;
			case CANCELLED -> Outcome.CANCELLED;
			case STALE_CLAIM -> Outcome.STALE_CLAIM;
			case FAILURE_RECORDING_FAILED -> Outcome.FAILURE_RECORDING_FAILED;
		};
	}
}
