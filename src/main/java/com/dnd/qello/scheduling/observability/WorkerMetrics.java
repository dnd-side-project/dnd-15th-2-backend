package com.dnd.qello.scheduling.observability;

import io.micrometer.core.instrument.MeterRegistry;

public class WorkerMetrics {

	public static final String CLAIMED_TOTAL = "qello.worker.claimed.total";
	public static final String SCANNED_TOTAL = "qello.worker.scanned.total";
	public static final String OUTCOME_TOTAL = "qello.worker.outcome.total";

	private final MeterRegistry registry;

	public WorkerMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void recordClaimed(WorkerName worker, int count) {
		validateCount(count);
		record(() -> registry.counter(CLAIMED_TOTAL, "worker", worker.tag).increment(count));
	}

	public void recordScanned(WorkerName worker, int count) {
		validateCount(count);
		record(() -> registry.counter(SCANNED_TOTAL, "worker", worker.tag).increment(count));
	}

	public void recordOutcome(WorkerName worker, Outcome outcome, int count) {
		validateCount(count);
		record(() -> registry.counter(OUTCOME_TOTAL, "worker", worker.tag, "outcome", outcome.name()).increment(count));
	}

	private void validateCount(int count) {
		if (count < 0) {
			throw new IllegalArgumentException("count는 음수일 수 없습니다");
		}
	}

	private void record(Runnable instrumentation) {
		try {
			instrumentation.run();
		} catch (RuntimeException ignored) {
			// 관측 실패가 worker 실행 결과를 바꾸지 않는다.
		}
	}

	public enum WorkerName {
		DIRECTION_MATCHING("direction_matching"),
		RECIPIENT_NOTIFICATION_FAN_OUT("recipient_notification_fan_out"),
		NOTIFICATION_FAN_OUT("notification_fan_out"),
		REPORT_RESOLUTION_FAN_OUT("report_resolution_fan_out"),
		RECIPIENT_EXPIRATION_SWEEP("recipient_expiration_sweep"),
		SKIP_CONFIRMATION_SWEEP("skip_confirmation_sweep"),
		PUSH_DELIVERY_DISPATCH("push_delivery_dispatch");

		private final String tag;

		WorkerName(String tag) {
			this.tag = tag;
		}
	}

	public enum Outcome {
		PROCESSED, RETRYABLE, RETRY_SCHEDULED, DEAD, STALE_LEASE, STALE_CLAIM,
		FAILURE_RECORDING_FAILED, RELEASED, INELIGIBLE, FAILED, SENT, CANCELLED,
		BATCH_FAILED
	}
}
