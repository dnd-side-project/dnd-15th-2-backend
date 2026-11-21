/**
 * Created at: 2026-08-27T14:32:15+09:00
 * Extended at: 2026-08-27T15:28:15+09:00
 * Extended at: 2026-11-21T12:06:21.291Z
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-006 through UNIT-008, INT-003
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-025 (added 2026-10-09T18:14:46+09:00)
 * Source scenario: TEST-PLAN-GH-366-DEV-WORKER-FCM-UNIT-004, UNIT-006
 */
package com.dnd.qello.scheduling;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
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
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.dnd.qello.account.sweep.AccountWithdrawalSweepWorker;
import com.dnd.qello.direction.matching.DirectionMatchingWorker;
import com.dnd.qello.direction.sweep.RecipientExpirationSweepWorker;
import com.dnd.qello.direction.sweep.SkipConfirmationSweepWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationDeadlineWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationExecutionWorker;
import com.dnd.qello.filtering.moderation.AnswerModerationVerdictWorker;
import com.dnd.qello.notification.config.PushConfiguration;
import com.dnd.qello.notification.fanout.NotificationFanOutWorker;
import com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorker;
import com.dnd.qello.notification.fanout.ReportResolutionFanOutWorker;
import com.dnd.qello.notification.push.PushDeliveryRetryPolicy;
import com.dnd.qello.notification.push.PushProvider;
import com.dnd.qello.notification.push.fcm.FcmHttpV1PushProvider;
import com.dnd.qello.notification.push.security.AesGcmPushTokenProtector;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.notification.service.PushDispatchGroupPlanner;
import com.dnd.qello.scheduling.adapter.AccountWithdrawalSweepScheduledAdapter;
import com.dnd.qello.scheduling.adapter.AnswerModerationDeadlineScheduledAdapter;
import com.dnd.qello.scheduling.adapter.AnswerModerationExecutionScheduledAdapter;
import com.dnd.qello.scheduling.adapter.AnswerModerationVerdictScheduledAdapter;
import com.dnd.qello.scheduling.adapter.DirectionMatchingScheduledAdapter;
import com.dnd.qello.scheduling.adapter.NotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.PushDeliveryDispatchScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientExpirationSweepScheduledAdapter;
import com.dnd.qello.scheduling.adapter.RecipientNotificationFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.ReportResolutionFanOutScheduledAdapter;
import com.dnd.qello.scheduling.adapter.SkipConfirmationSweepScheduledAdapter;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.observability.WorkerMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WorkerSchedulingConfigurationTest {

	private static final Instant DEV_WORKER_AT = Instant.parse("2026-11-21T12:00:00Z");
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
	@DisplayName("GH-366 UNIT-004: 실제 dev 설정에서 global OFF는 scheduling과 모든 adapter를 제거한다")
	void devGlobalOffRollsBackSchedulingWithoutChangingProvider() {
		devConfigDataRunner()
				.withUserConfiguration(PushConfiguration.class)
				.withPropertyValues("qello.worker.scheduling.enabled=false")
				.withPropertyValues(devRollbackCredentials())
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getEnvironment().getProperty("qello.worker.scheduling.enabled"))
							.isEqualTo("false");
					assertThat(context.getEnvironment().getProperty(
							"qello.worker.scheduling.push-delivery-dispatch.enabled")).isEqualTo("true");
					assertThat(context.getEnvironment().getProperty("qello.notification.push.provider"))
							.isEqualTo("fcm");
					assertThat(context).doesNotHaveBean(ThreadPoolTaskScheduler.class);
					assertThat(context).doesNotHaveBean(WorkerInstanceIdentity.class);
					assertThat(context).doesNotHaveBean(WorkerMetrics.class);
					assertThat(context).doesNotHaveBean(DirectionMatchingScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(RecipientNotificationFanOutScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(NotificationFanOutScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(ReportResolutionFanOutScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(PushDeliveryDispatchScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(PushDeliveryDispatchWorker.class);
					assertThat(context).doesNotHaveBean(PushDispatchGroupPlanner.class);
					assertThat(context).doesNotHaveBean(PushDeliveryRetryPolicy.class);
					assertThat(context).hasSingleBean(PushProvider.class);
					assertThat(context.getBean(PushProvider.class)).isInstanceOf(FcmHttpV1PushProvider.class);
					assertThat(context).hasSingleBean(AesGcmPushTokenProtector.class);
					assertExcludedDevAdapters(context);
				});
	}

	private static String[] devRollbackCredentials() {
		return new String[]{
				"qello.notification.push.token-protection.current-key-id=test-only-rollback",
				"qello.notification.push.token-protection.current-encryption-key-base64="
						+ Base64.getEncoder().encodeToString(new byte[32]),
				"qello.notification.push.token-protection.fingerprint-key-base64="
						+ Base64.getEncoder().encodeToString(
								"11111111111111111111111111111111".getBytes(StandardCharsets.UTF_8)),
				"qello.notification.push.fcm.project-id=test-only-rollback-project",
				"qello.notification.push.fcm.credential-json={\"type\":\"authorized_user\","
						+ "\"client_id\":\"test-client\",\"client_secret\":\"test-secret\","
						+ "\"refresh_token\":\"test-refresh\"}"
		};
	}

	@Test
	@DisplayName("GH-366 UNIT-006: 실제 dev scheduler는 다섯 worker에 바인딩된 batch와 lease·retry를 전달한다")
	void devSchedulerInvokesEachWorkerWithBoundCommandsAndShutsDown() {
		DevWorkers workers = new DevWorkers();
		AtomicReference<ThreadPoolTaskScheduler> schedulerReference = new AtomicReference<>();

		devConfigDataRunner()
				.withBean(Clock.class, () -> Clock.fixed(DEV_WORKER_AT, ZoneOffset.UTC))
				.withBean(DirectionMatchingWorker.class, () -> workers.matching)
				.withBean(RecipientNotificationFanOutWorker.class, () -> workers.recipient)
				.withBean(NotificationFanOutWorker.class, () -> workers.notification)
				.withBean(ReportResolutionFanOutWorker.class, () -> workers.report)
				.withBean(PushDeliveryDispatchWorker.class, () -> workers.push)
				.run(context -> {
					assertThat(context).hasNotFailed();
					ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
					schedulerReference.set(scheduler);
					WorkerSchedulingProperties properties = context.getBean(WorkerSchedulingProperties.class);
					assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
							.isEqualTo(properties.poolSize());
					assertThat(context).hasSingleBean(DirectionMatchingScheduledAdapter.class);
					assertThat(context).hasSingleBean(RecipientNotificationFanOutScheduledAdapter.class);
					assertThat(context).hasSingleBean(NotificationFanOutScheduledAdapter.class);
					assertThat(context).hasSingleBean(ReportResolutionFanOutScheduledAdapter.class);
					assertThat(context).hasSingleBean(PushDeliveryDispatchScheduledAdapter.class);
					assertExcludedDevAdapters(context);
					assertDevWorkerCommands(workers, properties, context.getBean(WorkerInstanceIdentity.class).owner());
				});

		assertThat(schedulerReference).hasValueSatisfying(
				scheduler -> assertThat(scheduler.getScheduledThreadPoolExecutor().isShutdown()).isTrue());
	}

	private ApplicationContextRunner devConfigDataRunner() {
		return runner.withUserConfiguration(ScheduledAdapterScanConfiguration.class)
				.withInitializer(context -> {
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
				})
				.withPropertyValues("spring.profiles.active=dev",
						"spring.config.location=classpath:/application.yml")
				.withInitializer(new ConfigDataApplicationContextInitializer());
	}

	private static void assertExcludedDevAdapters(AssertableApplicationContext context) {
		assertThat(context).doesNotHaveBean(RecipientExpirationSweepScheduledAdapter.class);
		assertThat(context).doesNotHaveBean(SkipConfirmationSweepScheduledAdapter.class);
		assertThat(context).doesNotHaveBean(AccountWithdrawalSweepScheduledAdapter.class);
		assertThat(context).doesNotHaveBean(AnswerModerationExecutionScheduledAdapter.class);
		assertThat(context).doesNotHaveBean(AnswerModerationDeadlineScheduledAdapter.class);
		assertThat(context).doesNotHaveBean(AnswerModerationVerdictScheduledAdapter.class);
	}

	private static void assertDevWorkerCommands(DevWorkers workers, WorkerSchedulingProperties properties,
			String owner) throws InterruptedException {
		var matching = properties.directionMatching();
		assertThat(workers.matchingCall.await()).usingRecursiveComparison()
				.ignoringFields("retryPolicy.backoffStrategy.jitterSource")
				.isEqualTo(new DirectionMatchingWorker.BatchCommand(matching.batchSize(), owner, DEV_WORKER_AT,
						DEV_WORKER_AT.plus(matching.leaseDuration()), matching.retry().toPolicy()));
		var recipient = properties.recipientNotificationFanOut();
		assertThat(workers.recipientCall.await()).usingRecursiveComparison()
				.ignoringFields("retryPolicy.backoffStrategy.jitterSource")
				.isEqualTo(
						new RecipientNotificationFanOutWorker.BatchCommand(recipient.batchSize(), owner, DEV_WORKER_AT,
								DEV_WORKER_AT.plus(recipient.leaseDuration()), recipient.retry().toPolicy()));
		var notification = properties.notificationFanOut();
		assertThat(workers.notificationCall.await()).usingRecursiveComparison()
				.ignoringFields("retryPolicy.backoffStrategy.jitterSource")
				.isEqualTo(new NotificationFanOutWorker.BatchCommand(notification.batchSize(), owner, DEV_WORKER_AT,
						DEV_WORKER_AT.plus(notification.leaseDuration()), notification.retry().toPolicy()));
		var report = properties.reportResolutionFanOut();
		assertThat(workers.reportCall.await()).usingRecursiveComparison()
				.ignoringFields("retryPolicy.backoffStrategy.jitterSource")
				.isEqualTo(new ReportResolutionFanOutWorker.BatchCommand(report.batchSize(), owner, DEV_WORKER_AT,
						DEV_WORKER_AT.plus(report.leaseDuration()), report.retry().toPolicy()));
		var push = properties.pushDeliveryDispatch();
		assertThat(workers.pushCall.await()).isEqualTo(new PushDeliveryDispatchWorker.BatchCommand(
				push.batchSize(), DEV_WORKER_AT, DEV_WORKER_AT.plus(push.leaseDuration())));
	}

	private static final class ObservedCommand<T> {
		private final CountDownLatch invoked = new CountDownLatch(1);
		private final AtomicReference<T> command = new AtomicReference<>();

		private <R> R record(T value, R result) {
			command.compareAndSet(null, value);
			invoked.countDown();
			return result;
		}

		private T await() throws InterruptedException {
			assertThat(invoked.await(5, TimeUnit.SECONDS)).as("worker 최초 호출").isTrue();
			return command.get();
		}
	}

	private static final class DevWorkers {
		private final DirectionMatchingWorker matching = mock(DirectionMatchingWorker.class);
		private final RecipientNotificationFanOutWorker recipient = mock(RecipientNotificationFanOutWorker.class);
		private final NotificationFanOutWorker notification = mock(NotificationFanOutWorker.class);
		private final ReportResolutionFanOutWorker report = mock(ReportResolutionFanOutWorker.class);
		private final PushDeliveryDispatchWorker push = mock(PushDeliveryDispatchWorker.class);
		private final ObservedCommand<DirectionMatchingWorker.BatchCommand> matchingCall = new ObservedCommand<>();
		private final ObservedCommand<RecipientNotificationFanOutWorker.BatchCommand> recipientCall = new ObservedCommand<>();
		private final ObservedCommand<NotificationFanOutWorker.BatchCommand> notificationCall = new ObservedCommand<>();
		private final ObservedCommand<ReportResolutionFanOutWorker.BatchCommand> reportCall = new ObservedCommand<>();
		private final ObservedCommand<PushDeliveryDispatchWorker.BatchCommand> pushCall = new ObservedCommand<>();

		private DevWorkers() {
			when(matching.processBatch(any())).thenAnswer(invocation -> matchingCall.record(
					invocation.getArgument(0), new DirectionMatchingWorker.BatchResult(0, List.of())));
			when(recipient.processBatch(any())).thenAnswer(invocation -> recipientCall.record(
					invocation.getArgument(0), new RecipientNotificationFanOutWorker.BatchResult(0, List.of())));
			when(notification.processBatch(any())).thenAnswer(invocation -> notificationCall.record(
					invocation.getArgument(0), new NotificationFanOutWorker.BatchResult(0, List.of())));
			when(report.processBatch(any())).thenAnswer(invocation -> reportCall.record(
					invocation.getArgument(0), new ReportResolutionFanOutWorker.BatchResult(0, List.of())));
			when(push.dispatchBatch(any())).thenAnswer(invocation -> pushCall.record(
					invocation.getArgument(0), new PushDeliveryDispatchWorker.BatchResult(0, List.of())));
		}
	}

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

		assertThat(schedulerReference).hasValueSatisfying(
				scheduler -> assertThat(scheduler.getScheduledThreadPoolExecutor().isShutdown()).isTrue());
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
						"qello.worker.scheduling.push-delivery-dispatch.enabled=false",
						"qello.worker.scheduling.answer-moderation.enabled=false")
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
					assertThat(context).doesNotHaveBean(AnswerModerationExecutionScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(AnswerModerationDeadlineScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(AnswerModerationVerdictScheduledAdapter.class);
				});
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-025: 탈퇴 완료 sweep은 자기 enabled가 꺼져 있으면 adapter bean이 없다")
	void accountWithdrawalSweepAdapterIsAbsentWhenDisabled() {
		adapterRunner(mock(DirectionMatchingWorker.class))
				.withBean(AccountWithdrawalSweepWorker.class, () -> mock(AccountWithdrawalSweepWorker.class))
				.withPropertyValues(
						"qello.worker.scheduling.enabled=true",
						"qello.worker.scheduling.pool-size=1",
						"qello.worker.scheduling.account-withdrawal-sweep.enabled=false")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
					assertThat(context).doesNotHaveBean(AccountWithdrawalSweepScheduledAdapter.class);
				});
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-025: global ON이고 탈퇴 완료 sweep이 ON이면 adapter 하나만 등록한다")
	void accountWithdrawalSweepAdapterIsRegisteredWhenEnabled() {
		AccountWithdrawalSweepWorker worker = mock(AccountWithdrawalSweepWorker.class);
		when(worker.processBatch(any())).thenReturn(new AccountWithdrawalSweepWorker.BatchResult(0, 0, 0, 0));

		adapterRunner(mock(DirectionMatchingWorker.class))
				.withBean(AccountWithdrawalSweepWorker.class, () -> worker)
				.withPropertyValues(
						"qello.worker.scheduling.enabled=true",
						"qello.worker.scheduling.pool-size=1",
						"qello.worker.scheduling.account-withdrawal-sweep.enabled=true",
						"qello.worker.scheduling.account-withdrawal-sweep.fixed-delay=PT1H",
						"qello.worker.scheduling.account-withdrawal-sweep.batch-size=10")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(AccountWithdrawalSweepScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(RecipientExpirationSweepScheduledAdapter.class);
					assertThat(context).doesNotHaveBean(SkipConfirmationSweepScheduledAdapter.class);
				});
	}

	@Test
	@DisplayName("INT-005: answer-moderation의 하나뿐인 enabled가 세 adapter를 함께 켠다")
	void answerModerationSingleGateRegistersAllThreeAdaptersTogether() {
		adapterRunner(mock(DirectionMatchingWorker.class))
				.withBean(AnswerModerationExecutionWorker.class, () -> mock(AnswerModerationExecutionWorker.class))
				.withBean(AnswerModerationDeadlineWorker.class, () -> mock(AnswerModerationDeadlineWorker.class))
				.withBean(AnswerModerationVerdictWorker.class, () -> mock(AnswerModerationVerdictWorker.class))
				.withPropertyValues(
						"qello.worker.scheduling.enabled=true",
						"qello.worker.scheduling.pool-size=1",
						"qello.worker.scheduling.answer-moderation.enabled=true",
						"qello.worker.scheduling.answer-moderation.execution.fixed-delay=PT5S",
						"qello.worker.scheduling.answer-moderation.execution.batch-size=10",
						"qello.worker.scheduling.answer-moderation.execution.lease-duration=PT30S",
						"qello.worker.scheduling.answer-moderation.deadline.fixed-delay=PT30S",
						"qello.worker.scheduling.answer-moderation.deadline.batch-size=50",
						"qello.worker.scheduling.answer-moderation.verdict.fixed-delay=PT5S",
						"qello.worker.scheduling.answer-moderation.verdict.batch-size=20",
						"qello.worker.scheduling.answer-moderation.verdict.lease-duration=PT30S")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(AnswerModerationExecutionScheduledAdapter.class);
					assertThat(context).hasSingleBean(AnswerModerationDeadlineScheduledAdapter.class);
					assertThat(context).hasSingleBean(AnswerModerationVerdictScheduledAdapter.class);
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
