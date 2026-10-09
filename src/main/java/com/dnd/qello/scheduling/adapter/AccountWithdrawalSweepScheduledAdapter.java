package com.dnd.qello.scheduling.adapter;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dnd.qello.account.sweep.AccountWithdrawalSweepWorker;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;
import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

@Component
@ConditionalOnProperty(prefix = "qello.worker.scheduling", name = {"enabled",
		"account-withdrawal-sweep.enabled"}, havingValue = "true")
public class AccountWithdrawalSweepScheduledAdapter {
	private final AccountWithdrawalSweepWorker worker;
	private final WorkerSchedulingProperties.SweepSettings settings;
	private final Clock clock;
	private final WorkerMetrics metrics;

	public AccountWithdrawalSweepScheduledAdapter(AccountWithdrawalSweepWorker worker,
			WorkerSchedulingProperties properties, Clock clock, WorkerMetrics metrics) {
		this.worker = worker;
		this.settings = properties.accountWithdrawalSweep();
		this.clock = clock;
		this.metrics = metrics;
	}

	@Scheduled(fixedDelayString = "${qello.worker.scheduling.account-withdrawal-sweep.fixed-delay}")
	void runOnce() {
		try {
			Instant at = clock.instant();
			AccountWithdrawalSweepWorker.BatchResult result = worker.processBatch(
					new AccountWithdrawalSweepWorker.BatchCommand(settings.batchSize(), at));
			metrics.recordScanned(WorkerName.ACCOUNT_WITHDRAWAL_SWEEP, result.scanned());
			metrics.recordOutcome(WorkerName.ACCOUNT_WITHDRAWAL_SWEEP, Outcome.PROCESSED, result.completed());
			metrics.recordOutcome(WorkerName.ACCOUNT_WITHDRAWAL_SWEEP, Outcome.INELIGIBLE, result.ineligible());
			metrics.recordOutcome(WorkerName.ACCOUNT_WITHDRAWAL_SWEEP, Outcome.FAILED, result.failed());
		} catch (RuntimeException failure) {
			metrics.recordOutcome(WorkerName.ACCOUNT_WITHDRAWAL_SWEEP, Outcome.BATCH_FAILED, 1);
			throw failure;
		}
	}
}
