/**
 * Created at: 2026-08-27T14:45:22+09:00
 * Extended at: 2026-08-27T15:03:15+09:00
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-009, UNIT-010, UNIT-011, UNIT-013, UNIT-017
 */
package com.dnd.qello.scheduling.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker;
import com.dnd.qello.direction.sweep.SkipConfirmationSweepWorker;
import com.dnd.qello.direction.sweep.SweepBatchResult;
import com.dnd.qello.notification.fanout.NotificationFanOutWorker;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.fanout.ReportResolutionFanOutWorker;
import com.dnd.qello.scheduling.WorkerInstanceIdentity;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties.OutboxRetrySettings;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties.OutboxSettings;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties.SweepSettings;
import com.dnd.qello.scheduling.observability.WorkerMetrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class CoreWorkerScheduledAdapterTest {

	private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");
	private static final WorkerInstanceIdentity IDENTITY = new WorkerInstanceIdentity("worker-test-182");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Mock
	private DirectionMatchingWorker directionMatchingWorker;

	@Mock
	private RecipientNotificationFanOutWorker recipientNotificationFanOutWorker;

	@Mock
	private NotificationFanOutWorker notificationFanOutWorker;

	@Mock
	private ReportResolutionFanOutWorker reportResolutionFanOutWorker;

	@Mock
	private RecipientExpirationSweepWorker recipientExpirationSweepWorker;

	@Mock
	private SkipConfirmationSweepWorker skipConfirmationSweepWorker;

	@Test
	@DisplayName("UNIT-009: matching adapter는 설정된 limit·owner·lease·retry로 한 batch를 실행한다")
	void matchingAdapterBuildsTheConfiguredCommand() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(directionMatchingWorker.processBatch(any())).thenReturn(new DirectionMatchingWorker.BatchResult(2,
			List.of(DirectionMatchingWorker.Outcome.PROCESSED, DirectionMatchingWorker.Outcome.STALE_LEASE)));
		DirectionMatchingScheduledAdapter adapter = new DirectionMatchingScheduledAdapter(directionMatchingWorker,
			properties(), IDENTITY, CLOCK, new WorkerMetrics(registry));

		adapter.runOnce();

		ArgumentCaptor<DirectionMatchingWorker.BatchCommand> command = ArgumentCaptor.forClass(
			DirectionMatchingWorker.BatchCommand.class);
		verify(directionMatchingWorker).processBatch(command.capture());
		assertThat(command.getValue().limit()).isEqualTo(7);
		assertThat(command.getValue().leaseOwner()).isEqualTo("worker-test-182");
		assertThat(command.getValue().at()).isEqualTo(NOW);
		assertThat(command.getValue().leaseExpiresAt()).isEqualTo(Instant.parse("2026-08-27T05:00:30Z"));
		assertThat(command.getValue().retryPolicy().maxAttempts()).isEqualTo(3);
		assertThat(counter(registry, WorkerMetrics.CLAIMED_TOTAL, "direction_matching").count()).isEqualTo(2.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "direction_matching", "PROCESSED").count()).isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "direction_matching", "STALE_LEASE").count()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("UNIT-010: recipient fan-out adapter는 worker 전용 command와 모든 outcome metric을 기록한다")
	void recipientFanOutAdapterBuildsCommandAndRecordsAllOutcomes() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(recipientNotificationFanOutWorker.processBatch(any())).thenReturn(
			new RecipientNotificationFanOutWorker.BatchResult(3, List.of(
				RecipientNotificationFanOutWorker.Outcome.PROCESSED,
				RecipientNotificationFanOutWorker.Outcome.RETRYABLE,
				RecipientNotificationFanOutWorker.Outcome.DEAD,
				RecipientNotificationFanOutWorker.Outcome.STALE_LEASE,
				RecipientNotificationFanOutWorker.Outcome.FAILURE_RECORDING_FAILED)));
		RecipientNotificationFanOutScheduledAdapter adapter = new RecipientNotificationFanOutScheduledAdapter(
			recipientNotificationFanOutWorker, properties(), IDENTITY, CLOCK, new WorkerMetrics(registry));

		adapter.runOnce();

		ArgumentCaptor<RecipientNotificationFanOutWorker.BatchCommand> command = ArgumentCaptor.forClass(
			RecipientNotificationFanOutWorker.BatchCommand.class);
		verify(recipientNotificationFanOutWorker).processBatch(command.capture());
		assertThat(command.getValue().limit()).isEqualTo(8);
		assertThat(command.getValue().leaseOwner()).isEqualTo("worker-test-182");
		assertThat(command.getValue().at()).isEqualTo(NOW);
		assertThat(command.getValue().leaseExpiresAt()).isEqualTo(Instant.parse("2026-08-27T05:00:31Z"));
		assertThat(command.getValue().retryPolicy().maxAttempts()).isEqualTo(4);
		assertWorkerOutcomes(registry, "recipient_notification_fan_out", 3);
	}

	@Test
	@DisplayName("UNIT-010: notification fan-out adapter는 worker 전용 command와 모든 outcome metric을 기록한다")
	void notificationFanOutAdapterBuildsCommandAndRecordsAllOutcomes() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(notificationFanOutWorker.processBatch(any())).thenReturn(new NotificationFanOutWorker.BatchResult(4, List.of(
			NotificationFanOutWorker.Outcome.PROCESSED, NotificationFanOutWorker.Outcome.RETRYABLE,
			NotificationFanOutWorker.Outcome.DEAD, NotificationFanOutWorker.Outcome.STALE_LEASE,
			NotificationFanOutWorker.Outcome.FAILURE_RECORDING_FAILED)));
		NotificationFanOutScheduledAdapter adapter = new NotificationFanOutScheduledAdapter(notificationFanOutWorker,
			properties(), IDENTITY, CLOCK, new WorkerMetrics(registry));

		adapter.runOnce();

		ArgumentCaptor<NotificationFanOutWorker.BatchCommand> command = ArgumentCaptor.forClass(
			NotificationFanOutWorker.BatchCommand.class);
		verify(notificationFanOutWorker).processBatch(command.capture());
		assertThat(command.getValue().limit()).isEqualTo(9);
		assertThat(command.getValue().leaseOwner()).isEqualTo("worker-test-182");
		assertThat(command.getValue().at()).isEqualTo(NOW);
		assertThat(command.getValue().leaseExpiresAt()).isEqualTo(Instant.parse("2026-08-27T05:00:32Z"));
		assertThat(command.getValue().retryPolicy().maxAttempts()).isEqualTo(5);
		assertWorkerOutcomes(registry, "notification_fan_out", 4);
	}

	@Test
	@DisplayName("UNIT-010: report fan-out adapter는 worker 전용 command와 모든 outcome metric을 기록한다")
	void reportFanOutAdapterBuildsCommandAndRecordsAllOutcomes() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(reportResolutionFanOutWorker.processBatch(any())).thenReturn(
			new ReportResolutionFanOutWorker.BatchResult(5, List.of(
				ReportResolutionFanOutWorker.Outcome.PROCESSED, ReportResolutionFanOutWorker.Outcome.RETRYABLE,
				ReportResolutionFanOutWorker.Outcome.DEAD, ReportResolutionFanOutWorker.Outcome.STALE_LEASE,
				ReportResolutionFanOutWorker.Outcome.FAILURE_RECORDING_FAILED)));
		ReportResolutionFanOutScheduledAdapter adapter = new ReportResolutionFanOutScheduledAdapter(
			reportResolutionFanOutWorker, properties(), IDENTITY, CLOCK, new WorkerMetrics(registry));

		adapter.runOnce();

		ArgumentCaptor<ReportResolutionFanOutWorker.BatchCommand> command = ArgumentCaptor.forClass(
			ReportResolutionFanOutWorker.BatchCommand.class);
		verify(reportResolutionFanOutWorker).processBatch(command.capture());
		assertThat(command.getValue().limit()).isEqualTo(10);
		assertThat(command.getValue().leaseOwner()).isEqualTo("worker-test-182");
		assertThat(command.getValue().at()).isEqualTo(NOW);
		assertThat(command.getValue().leaseExpiresAt()).isEqualTo(Instant.parse("2026-08-27T05:00:33Z"));
		assertThat(command.getValue().retryPolicy().maxAttempts()).isEqualTo(6);
		assertWorkerOutcomes(registry, "report_resolution_fan_out", 5);
	}

	@Test
	@DisplayName("UNIT-011: expiration sweep adapter는 scan 결과 네 종류를 정확히 기록한다")
	void expirationSweepRecordsAllResultCounters() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(recipientExpirationSweepWorker.processBatch(any())).thenReturn(new SweepBatchResult(7, 3, 2, 2));
		RecipientExpirationSweepScheduledAdapter adapter = new RecipientExpirationSweepScheduledAdapter(
			recipientExpirationSweepWorker, properties(), CLOCK, new WorkerMetrics(registry));

		adapter.runOnce();

		ArgumentCaptor<RecipientExpirationSweepWorker.BatchCommand> command = ArgumentCaptor.forClass(
			RecipientExpirationSweepWorker.BatchCommand.class);
		verify(recipientExpirationSweepWorker).processBatch(command.capture());
		assertThat(command.getValue().limit()).isEqualTo(7);
		assertThat(command.getValue().at()).isEqualTo(NOW);
		assertThat(counter(registry, WorkerMetrics.SCANNED_TOTAL, "recipient_expiration_sweep").count()).isEqualTo(7.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "recipient_expiration_sweep", "RELEASED").count())
			.isEqualTo(3.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "recipient_expiration_sweep", "INELIGIBLE").count())
			.isEqualTo(2.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "recipient_expiration_sweep", "FAILED").count())
			.isEqualTo(2.0);
	}

	@Test
	@DisplayName("UNIT-011: skip confirmation sweep adapter는 scan 결과 네 종류를 정확히 기록한다")
	void skipConfirmationSweepRecordsAllResultCounters() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(skipConfirmationSweepWorker.processBatch(any())).thenReturn(new SweepBatchResult(11, 5, 4, 2));
		SkipConfirmationSweepScheduledAdapter adapter = new SkipConfirmationSweepScheduledAdapter(
			skipConfirmationSweepWorker, properties(), CLOCK, new WorkerMetrics(registry));

		adapter.runOnce();

		ArgumentCaptor<SkipConfirmationSweepWorker.BatchCommand> command = ArgumentCaptor.forClass(
			SkipConfirmationSweepWorker.BatchCommand.class);
		verify(skipConfirmationSweepWorker).processBatch(command.capture());
		assertThat(command.getValue().limit()).isEqualTo(11);
		assertThat(command.getValue().at()).isEqualTo(NOW);
		assertThat(counter(registry, WorkerMetrics.SCANNED_TOTAL, "skip_confirmation_sweep").count()).isEqualTo(11.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "skip_confirmation_sweep", "RELEASED").count())
			.isEqualTo(5.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "skip_confirmation_sweep", "INELIGIBLE").count())
			.isEqualTo(4.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "skip_confirmation_sweep", "FAILED").count())
			.isEqualTo(2.0);
	}

	@Test
	@DisplayName("UNIT-013: 네 outbox adapter는 각 worker의 fixed-delay 설정만 사용한다")
	void outboxAdaptersUseTheirOwnFixedDelaySchedulingProperty() throws ReflectiveOperationException {
		assertFixedDelay(DirectionMatchingScheduledAdapter.class, "${qello.worker.scheduling.direction-matching.fixed-delay}");
		assertFixedDelay(RecipientNotificationFanOutScheduledAdapter.class,
			"${qello.worker.scheduling.recipient-notification-fan-out.fixed-delay}");
		assertFixedDelay(NotificationFanOutScheduledAdapter.class,
			"${qello.worker.scheduling.notification-fan-out.fixed-delay}");
		assertFixedDelay(ReportResolutionFanOutScheduledAdapter.class,
			"${qello.worker.scheduling.report-resolution-fan-out.fixed-delay}");
	}

	@Test
	@DisplayName("UNIT-013: 두 sweep adapter는 각 worker의 fixed-delay 설정만 사용한다")
	void sweepAdaptersUseTheirOwnFixedDelaySchedulingProperty() throws ReflectiveOperationException {
		assertFixedDelay(RecipientExpirationSweepScheduledAdapter.class,
			"${qello.worker.scheduling.recipient-expiration-sweep.fixed-delay}");
		assertFixedDelay(SkipConfirmationSweepScheduledAdapter.class,
			"${qello.worker.scheduling.skip-confirmation-sweep.fixed-delay}");
	}

	@Test
	@DisplayName("UNIT-017: batch 실패 후 다음 trigger는 BATCH_FAILED를 남기고 다시 실행할 수 있다")
	void matchingAdapterRecordsFailureAndAllowsTheNextInvocation() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(directionMatchingWorker.processBatch(any()))
			.thenThrow(new IllegalStateException("temporary worker failure"))
			.thenReturn(new DirectionMatchingWorker.BatchResult(1, List.of(DirectionMatchingWorker.Outcome.PROCESSED)));
		DirectionMatchingScheduledAdapter adapter = new DirectionMatchingScheduledAdapter(directionMatchingWorker,
			properties(), IDENTITY, CLOCK, new WorkerMetrics(registry));

		assertThatThrownBy(adapter::runOnce).isInstanceOf(IllegalStateException.class)
			.hasMessage("temporary worker failure");
		adapter.runOnce();

		verify(directionMatchingWorker, times(2)).processBatch(any());
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "direction_matching", "BATCH_FAILED").count())
			.isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "direction_matching", "PROCESSED").count()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("UNIT-017: expiration sweep batch 실패 후 다음 trigger는 BATCH_FAILED를 남기고 다시 실행할 수 있다")
	void expirationSweepRecordsFailureAndAllowsTheNextInvocation() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(recipientExpirationSweepWorker.processBatch(any()))
			.thenThrow(new IllegalStateException("temporary sweep failure"))
			.thenReturn(new SweepBatchResult(1, 1, 0, 0));
		RecipientExpirationSweepScheduledAdapter adapter = new RecipientExpirationSweepScheduledAdapter(
			recipientExpirationSweepWorker, properties(), CLOCK, new WorkerMetrics(registry));

		assertThatThrownBy(adapter::runOnce).isInstanceOf(IllegalStateException.class)
			.hasMessage("temporary sweep failure");
		adapter.runOnce();

		verify(recipientExpirationSweepWorker, times(2)).processBatch(any());
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "recipient_expiration_sweep", "BATCH_FAILED").count())
			.isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.SCANNED_TOTAL, "recipient_expiration_sweep").count()).isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "recipient_expiration_sweep", "RELEASED").count())
			.isEqualTo(1.0);
	}

	private WorkerSchedulingProperties properties() {
		return new WorkerSchedulingProperties(true, 1,
			outbox(7, 30, 3), outbox(8, 31, 4), outbox(9, 32, 5), outbox(10, 33, 6),
			sweep(7), sweep(11), null);
	}

	private OutboxSettings outbox(int batchSize, int leaseSeconds, int maxAttempts) {
		return new OutboxSettings(true, Duration.ofSeconds(1), batchSize, Duration.ofSeconds(leaseSeconds),
			new OutboxRetrySettings(maxAttempts, Duration.ofSeconds(1), Duration.ofSeconds(30)));
	}

	private SweepSettings sweep(int batchSize) {
		return new SweepSettings(true, Duration.ofSeconds(1), batchSize);
	}

	private void assertWorkerOutcomes(SimpleMeterRegistry registry, String worker, int claimed) {
		assertThat(counter(registry, WorkerMetrics.CLAIMED_TOTAL, worker).count()).isEqualTo((double) claimed);
		for (String outcome : List.of("PROCESSED", "RETRYABLE", "DEAD", "STALE_LEASE", "FAILURE_RECORDING_FAILED")) {
			assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, worker, outcome).count()).isEqualTo(1.0);
		}
	}

	private Counter counter(SimpleMeterRegistry registry, String name, String worker) {
		return registry.get(name).tag("worker", worker).counter();
	}

	private Counter counter(SimpleMeterRegistry registry, String name, String worker, String outcome) {
		return registry.get(name).tags("worker", worker, "outcome", outcome).counter();
	}

	private void assertFixedDelay(Class<?> adapterClass, String expectedFixedDelay) throws ReflectiveOperationException {
		Method method = adapterClass.getDeclaredMethod("runOnce");
		Scheduled scheduled = method.getAnnotation(Scheduled.class);
		assertThat(scheduled).isNotNull();
		assertThat(scheduled.fixedDelayString()).isEqualTo(expectedFixedDelay);
		assertThat(scheduled.fixedRateString()).isEmpty();
	}
}
