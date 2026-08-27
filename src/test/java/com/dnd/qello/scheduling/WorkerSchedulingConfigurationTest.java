/**
 * Created at: 2026-08-27T14:32:15+09:00
 * Extended at: 2026-08-27T15:28:15+09:00
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-006 through UNIT-008, INT-003
 */
package com.dnd.qello.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker;
import com.dnd.qello.direction.sweep.SkipConfirmationSweepWorker;
import com.dnd.qello.notification.fanout.NotificationFanOutWorker;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.fanout.ReportResolutionFanOutWorker;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.scheduling.adapter.DirectionMatchingScheduledAdapter;
import com.dnd.qello.scheduling.adapter.NotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.PushDeliveryDispatchScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientExpirationSweepScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientNotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.ReportResolutionFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.SkipConfirmationSweepScheduledAdapter;
import com.dnd.qello.scheduling.observability.WorkerMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WorkerSchedulingConfigurationTest {

	private static final String[] DIRECTION_MATCHING_ONLY = {
		"qello.worker.scheduling.enabled=true",
		"qello.worker.scheduling.pool-size=3",
		"qello.worker.scheduling.direction-matching.enabled=true",
		"qello.worker.scheduling.direction-matching.fixed-delay=PT0.05S",
		"qello.worker.scheduling.direction-matching.batch-size=7",
		"qello.worker.scheduling.direction-matching.lease-duration=PT30S",
		"qello.worker.scheduling.direction-matching.retry.max-attempts=3",
		"qello.worker.scheduling.direction-matching.retry.base-delay=PT1S",
		"qello.worker.scheduling.direction-matching.retry.max-delay=PT30S"
	};

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
		.withUserConfiguration(WorkerSchedulingConfiguration.class)
		.withBean(MeterRegistry.class, SimpleMeterRegistry::new);

	@Test
	@DisplayName("UNIT-007: global OFF는 scheduling infrastructure를 등록하지 않는다")
	void disabledGlobalGateRegistersNoSchedulingInfrastructure() {
		runner.withPropertyValues("qello.worker.scheduling.enabled=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean(ThreadPoolTaskScheduler.class);
				assertThat(context).doesNotHaveBean(WorkerInstanceIdentity.class);
				assertThat(context).doesNotHaveBean(WorkerMetrics.class);
			});
	}

	@Test
	@DisplayName("UNIT-006: global ON은 설정된 pool과 전용 worker thread prefix를 사용한다")
	void enabledGlobalGateCreatesConfiguredDedicatedScheduler() {
		AtomicReference<ThreadPoolTaskScheduler> schedulerReference = new AtomicReference<>();

		runner.withPropertyValues(
				"qello.worker.scheduling.enabled=true",
				"qello.worker.scheduling.pool-size=3")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(WorkerInstanceIdentity.class);
				assertThat(context).hasSingleBean(WorkerMetrics.class);
				ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
				schedulerReference.set(scheduler);
				assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3);
				assertThat(scheduler.getThreadNamePrefix()).isEqualTo("qello-worker-");
			});

		assertThat(schedulerReference).hasValueSatisfying(scheduler ->
			assertThat(scheduler.getScheduledThreadPoolExecutor().isShutdown()).isTrue());
	}

	@Test
	@DisplayName("UNIT-008: global ON이고 모든 worker가 OFF면 scheduler만 등록하고 adapter는 없다")
	void enabledGlobalGateWithAllWorkersDisabledRegistersNoAdapters() {
		adapterRunner(mock(DirectionMatchingWorker.class))
			.withPropertyValues(
				"qello.worker.scheduling.enabled=true",
				"qello.worker.scheduling.pool-size=3",
				"qello.worker.scheduling.direction-matching.enabled=false",
				"qello.worker.scheduling.recipient-notification-fan-out.enabled=false",
				"qello.worker.scheduling.notification-fan-out.enabled=false",
				"qello.worker.scheduling.report-resolution-fan-out.enabled=false",
				"qello.worker.scheduling.recipient-expiration-sweep.enabled=false",
				"qello.worker.scheduling.skip-confirmation-sweep.enabled=false",
				"qello.worker.scheduling.push-delivery-dispatch.enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
				assertThat(context).hasSingleBean(WorkerInstanceIdentity.class);
				assertThat(context).hasSingleBean(WorkerMetrics.class);
				assertThat(context).doesNotHaveBean(DirectionMatchingScheduledAdapter.class);
				assertThat(context).doesNotHaveBean(RecipientNotificationFanOutScheduledAdapter.class);
				assertThat(context).doesNotHaveBean(NotificationFanOutScheduledAdapter.class);
				assertThat(context).doesNotHaveBean(ReportResolutionFanOutScheduledAdapter.class);
				assertThat(context).doesNotHaveBean(RecipientExpirationSweepScheduledAdapter.class);
				assertThat(context).doesNotHaveBean(SkipConfirmationSweepScheduledAdapter.class);
				assertThat(context).doesNotHaveBean(PushDeliveryDispatchScheduledAdapter.class);
			});
	}

	@Test
	@DisplayName("INT-003: matching만 ON이면 반복 호출되고 같은 adapter는 겹치지 않는다")
	void selectedWorkerRepeatsWithoutOverlappingTheSameAdapter() throws InterruptedException {
		CountDownLatch invoked = new CountDownLatch(2);
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch holdFirst = new CountDownLatch(1);
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger maxConcurrent = new AtomicInteger();
		AtomicBoolean firstCall = new AtomicBoolean(true);
		DirectionMatchingWorker worker = mock(DirectionMatchingWorker.class);
		when(worker.processBatch(any())).thenAnswer(invocation -> {
			int current = inFlight.incrementAndGet();
			maxConcurrent.accumulateAndGet(current, Math::max);
			boolean first = firstCall.compareAndSet(true, false);
			if (first) {
				firstStarted.countDown();
			}
			invoked.countDown();
			try {
				if (first) {
					holdFirst.await(2, TimeUnit.SECONDS);
				}
				return new DirectionMatchingWorker.BatchResult(0, List.of());
			} finally {
				inFlight.decrementAndGet();
			}
		});

		try {
			adapterRunner(worker)
				.withPropertyValues(DIRECTION_MATCHING_ONLY)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(DirectionMatchingScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(RecipientNotificationFanOutScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(NotificationFanOutScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(ReportResolutionFanOutScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(RecipientExpirationSweepScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(SkipConfirmationSweepScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(PushDeliveryDispatchScheduledAdapter.class);

					assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
					invoked.await(200, TimeUnit.MILLISECONDS);
					holdFirst.countDown();

					assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue();
					assertThat(maxConcurrent.get()).isEqualTo(1);
					verify(worker, atLeast(2)).processBatch(any());
				});
		} finally {
			holdFirst.countDown();
		}
	}

	private ApplicationContextRunner adapterRunner(DirectionMatchingWorker directionMatchingWorker) {
		return new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(WorkerSchedulingConfiguration.class, ScheduledAdapterScanConfiguration.class)
			.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
			.withBean(Clock.class, Clock::systemUTC)
			.withBean(DirectionMatchingWorker.class, () -> directionMatchingWorker)
			.withBean(RecipientNotificationFanOutWorker.class, () -> mock(RecipientNotificationFanOutWorker.class))
			.withBean(NotificationFanOutWorker.class, () -> mock(NotificationFanOutWorker.class))
			.withBean(ReportResolutionFanOutWorker.class, () -> mock(ReportResolutionFanOutWorker.class))
			.withBean(RecipientExpirationSweepWorker.class, () -> mock(RecipientExpirationSweepWorker.class))
			.withBean(SkipConfirmationSweepWorker.class, () -> mock(SkipConfirmationSweepWorker.class))
			.withBean(PushDeliveryDispatchWorker.class, () -> mock(PushDeliveryDispatchWorker.class));
	}

	@Configuration
	@ComponentScan(basePackageClasses = DirectionMatchingScheduledAdapter.class)
	static class ScheduledAdapterScanConfiguration {
	}
}
