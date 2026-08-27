/**
 * Created at: 2026-08-27T14:22:03+09:00
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-001 through UNIT-004
 */
package com.dnd.qello.scheduling.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class WorkerSchedulingPropertiesTest {

	private static final String PREFIX = "qello.worker.scheduling";
	private static final String[] ENABLED_DIRECTION_MATCHING = {
		PREFIX + ".enabled=true",
		PREFIX + ".pool-size=3",
		PREFIX + ".direction-matching.enabled=true",
		PREFIX + ".direction-matching.fixed-delay=PT0.05S",
		PREFIX + ".direction-matching.batch-size=7",
		PREFIX + ".direction-matching.lease-duration=PT30S",
		PREFIX + ".direction-matching.retry.max-attempts=3",
		PREFIX + ".direction-matching.retry.base-delay=PT1S",
		PREFIX + ".direction-matching.retry.max-delay=PT30S"
	};

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
		.withUserConfiguration(WorkerSchedulingPropertiesConfiguration.class);

	@Test
	@DisplayName("UNIT-001: global OFF는 worker 수치가 없어도 안전하게 binding된다")
	void disabledSchedulingDoesNotRequireOperationalValues() {
		runner.withPropertyValues(PREFIX + ".enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(WorkerSchedulingProperties.class).enabled()).isFalse();
			});
	}

	@Test
	@DisplayName("UNIT-002: global ON은 양수 pool size를 요구한다")
	void enabledSchedulingRejectsMissingPoolSize() {
		runner.withPropertyValues(PREFIX + ".enabled=true")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-003: enabled Outbox worker는 delay·batch·lease·retry 전부를 요구한다")
	void enabledOutboxWorkerRejectsMissingLeaseAndRetry() {
		runner.withPropertyValues(
			PREFIX + ".enabled=true",
			PREFIX + ".pool-size=1",
			PREFIX + ".direction-matching.enabled=true",
			PREFIX + ".direction-matching.fixed-delay=PT1S",
			PREFIX + ".direction-matching.batch-size=1")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("UNIT-004: 일곱 worker의 서로 다른 설정은 이름에 맞는 worker에 binding된다")
	void bindsDistinctSettingsWithoutExchangingWorkers() {
		runner.withPropertyValues(
			ENABLED_DIRECTION_MATCHING)
			.withPropertyValues(
				PREFIX + ".recipient-notification-fan-out.enabled=true",
				PREFIX + ".recipient-notification-fan-out.fixed-delay=PT0.06S",
				PREFIX + ".recipient-notification-fan-out.batch-size=8",
				PREFIX + ".recipient-notification-fan-out.lease-duration=PT31S",
				PREFIX + ".recipient-notification-fan-out.retry.max-attempts=4",
				PREFIX + ".recipient-notification-fan-out.retry.base-delay=PT2S",
				PREFIX + ".recipient-notification-fan-out.retry.max-delay=PT31S",
				PREFIX + ".notification-fan-out.enabled=true",
				PREFIX + ".notification-fan-out.fixed-delay=PT0.07S",
				PREFIX + ".notification-fan-out.batch-size=9",
				PREFIX + ".notification-fan-out.lease-duration=PT32S",
				PREFIX + ".notification-fan-out.retry.max-attempts=5",
				PREFIX + ".notification-fan-out.retry.base-delay=PT3S",
				PREFIX + ".notification-fan-out.retry.max-delay=PT32S",
				PREFIX + ".report-resolution-fan-out.enabled=true",
				PREFIX + ".report-resolution-fan-out.fixed-delay=PT0.08S",
				PREFIX + ".report-resolution-fan-out.batch-size=10",
				PREFIX + ".report-resolution-fan-out.lease-duration=PT33S",
				PREFIX + ".report-resolution-fan-out.retry.max-attempts=6",
				PREFIX + ".report-resolution-fan-out.retry.base-delay=PT4S",
				PREFIX + ".report-resolution-fan-out.retry.max-delay=PT33S",
				PREFIX + ".recipient-expiration-sweep.enabled=true",
				PREFIX + ".recipient-expiration-sweep.fixed-delay=PT0.09S",
				PREFIX + ".recipient-expiration-sweep.batch-size=11",
				PREFIX + ".skip-confirmation-sweep.enabled=true",
				PREFIX + ".skip-confirmation-sweep.fixed-delay=PT0.1S",
				PREFIX + ".skip-confirmation-sweep.batch-size=12",
				PREFIX + ".push-delivery-dispatch.enabled=true",
				PREFIX + ".push-delivery-dispatch.fixed-delay=PT0.11S",
				PREFIX + ".push-delivery-dispatch.batch-size=13",
				PREFIX + ".push-delivery-dispatch.lease-duration=PT34S",
				PREFIX + ".push-delivery-dispatch.retry.max-attempts=7",
				PREFIX + ".push-delivery-dispatch.retry.base-backoff=PT5S",
				PREFIX + ".push-delivery-dispatch.retry.backoff-cap=PT34S")
			.run(context -> {
				assertThat(context).hasNotFailed();
				WorkerSchedulingProperties properties = context.getBean(WorkerSchedulingProperties.class);

				assertThat(properties.poolSize()).isEqualTo(3);
				assertThat(properties.directionMatching().fixedDelay()).isEqualTo(Duration.ofMillis(50));
				assertThat(properties.directionMatching().batchSize()).isEqualTo(7);
				assertThat(properties.directionMatching().leaseDuration()).isEqualTo(Duration.ofSeconds(30));
				assertThat(properties.directionMatching().retry().maxAttempts()).isEqualTo(3);
				assertThat(properties.directionMatching().retry().baseDelay()).isEqualTo(Duration.ofSeconds(1));
				assertThat(properties.directionMatching().retry().maxDelay()).isEqualTo(Duration.ofSeconds(30));

				assertThat(properties.recipientNotificationFanOut().fixedDelay()).isEqualTo(Duration.ofMillis(60));
				assertThat(properties.recipientNotificationFanOut().batchSize()).isEqualTo(8);
				assertThat(properties.recipientNotificationFanOut().leaseDuration()).isEqualTo(Duration.ofSeconds(31));
				assertThat(properties.recipientNotificationFanOut().retry().maxAttempts()).isEqualTo(4);
				assertThat(properties.recipientNotificationFanOut().retry().baseDelay()).isEqualTo(Duration.ofSeconds(2));
				assertThat(properties.recipientNotificationFanOut().retry().maxDelay()).isEqualTo(Duration.ofSeconds(31));

				assertThat(properties.notificationFanOut().fixedDelay()).isEqualTo(Duration.ofMillis(70));
				assertThat(properties.notificationFanOut().batchSize()).isEqualTo(9);
				assertThat(properties.notificationFanOut().leaseDuration()).isEqualTo(Duration.ofSeconds(32));
				assertThat(properties.notificationFanOut().retry().maxAttempts()).isEqualTo(5);
				assertThat(properties.notificationFanOut().retry().baseDelay()).isEqualTo(Duration.ofSeconds(3));
				assertThat(properties.notificationFanOut().retry().maxDelay()).isEqualTo(Duration.ofSeconds(32));

				assertThat(properties.reportResolutionFanOut().fixedDelay()).isEqualTo(Duration.ofMillis(80));
				assertThat(properties.reportResolutionFanOut().batchSize()).isEqualTo(10);
				assertThat(properties.reportResolutionFanOut().leaseDuration()).isEqualTo(Duration.ofSeconds(33));
				assertThat(properties.reportResolutionFanOut().retry().maxAttempts()).isEqualTo(6);
				assertThat(properties.reportResolutionFanOut().retry().baseDelay()).isEqualTo(Duration.ofSeconds(4));
				assertThat(properties.reportResolutionFanOut().retry().maxDelay()).isEqualTo(Duration.ofSeconds(33));

				assertThat(properties.recipientExpirationSweep().fixedDelay()).isEqualTo(Duration.ofMillis(90));
				assertThat(properties.recipientExpirationSweep().batchSize()).isEqualTo(11);
				assertThat(properties.skipConfirmationSweep().fixedDelay()).isEqualTo(Duration.ofMillis(100));
				assertThat(properties.skipConfirmationSweep().batchSize()).isEqualTo(12);

				assertThat(properties.pushDeliveryDispatch().fixedDelay()).isEqualTo(Duration.ofMillis(110));
				assertThat(properties.pushDeliveryDispatch().batchSize()).isEqualTo(13);
				assertThat(properties.pushDeliveryDispatch().leaseDuration()).isEqualTo(Duration.ofSeconds(34));
				assertThat(properties.pushDeliveryDispatch().retry().maxAttempts()).isEqualTo(7);
				assertThat(properties.pushDeliveryDispatch().retry().baseBackoff()).isEqualTo(Duration.ofSeconds(5));
				assertThat(properties.pushDeliveryDispatch().retry().backoffCap()).isEqualTo(Duration.ofSeconds(34));
			});
	}

	@Test
	@DisplayName("UNIT-003: enabled worker는 0·음수 수치와 역전된 retry 범위를 거절한다")
	void enabledWorkersRejectInvalidOperationalValues() {
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ZERO, 1, Duration.ofSeconds(1), retry())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("fixedDelay");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(-1), 1, Duration.ofSeconds(1), retry())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("fixedDelay");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(1), 0, Duration.ofSeconds(1), retry())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("batchSize");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(1), -1, Duration.ofSeconds(1), retry())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("batchSize");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(1), 1, Duration.ZERO, retry())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("leaseDuration");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(1), 1, Duration.ofSeconds(-1), retry())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("leaseDuration");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(1), 1, Duration.ofSeconds(1),
				new WorkerSchedulingProperties.OutboxRetrySettings(0, Duration.ofSeconds(1), Duration.ofSeconds(2)))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxAttempts");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(1), 1, Duration.ofSeconds(1),
				new WorkerSchedulingProperties.OutboxRetrySettings(-1, Duration.ofSeconds(1), Duration.ofSeconds(2)))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxAttempts");
		assertThatThrownBy(() -> propertiesWith(
			new WorkerSchedulingProperties.OutboxSettings(true, Duration.ofSeconds(1), 1, Duration.ofSeconds(1),
				new WorkerSchedulingProperties.OutboxRetrySettings(1, Duration.ofSeconds(2), Duration.ofSeconds(1)))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxDelay");
	}

	private WorkerSchedulingProperties propertiesWith(WorkerSchedulingProperties.OutboxSettings directionMatching) {
		return new WorkerSchedulingProperties(true, 1, directionMatching, null, null, null, null, null, null);
	}

	private WorkerSchedulingProperties.OutboxRetrySettings retry() {
		return new WorkerSchedulingProperties.OutboxRetrySettings(1, Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(WorkerSchedulingProperties.class)
	static class WorkerSchedulingPropertiesConfiguration {
	}
}
