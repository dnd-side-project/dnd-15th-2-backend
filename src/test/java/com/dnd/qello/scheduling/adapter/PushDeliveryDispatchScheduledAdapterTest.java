/**
 * Created at: 2026-08-27T15:13:23+09:00
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-012, UNIT-013, UNIT-017
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

import com.dnd.qello.notification.service.PushDeliveryDispatchWorker;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker.BatchCommand;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker.BatchResult;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker.DeliveryOutcome;
import com.dnd.qello.notification.service.PushDeliveryDispatchWorker.Outcome;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties.PushRetrySettings;
import com.dnd.qello.scheduling.config.WorkerSchedulingProperties.PushSettings;
import com.dnd.qello.scheduling.observability.WorkerMetrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PushDeliveryDispatchScheduledAdapterTest {

	private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Mock
	private PushDeliveryDispatchWorker worker;

	@Test
	@DisplayName("UNIT-012: push adapter는 batch·at·leaseUntil과 모든 outcome metric을 기록한다")
	void recordsClaimedAndEveryPushOutcome() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(worker.dispatchBatch(any())).thenReturn(new BatchResult(6, List.of(
			new DeliveryOutcome(1L, 1, Outcome.SENT, "sent"),
			new DeliveryOutcome(2L, 1, Outcome.RETRY_SCHEDULED, "retry"),
			new DeliveryOutcome(3L, 1, Outcome.DEAD, "dead"),
			new DeliveryOutcome(4L, 1, Outcome.CANCELLED, "cancelled"),
			new DeliveryOutcome(5L, 1, Outcome.STALE_CLAIM, "stale_claim"),
			new DeliveryOutcome(6L, 1, Outcome.FAILURE_RECORDING_FAILED, "failure_recording_failed"))));
		PushDeliveryDispatchScheduledAdapter adapter = new PushDeliveryDispatchScheduledAdapter(
			worker, properties(), CLOCK, new WorkerMetrics(registry));

		adapter.runOnce();

		ArgumentCaptor<BatchCommand> command = ArgumentCaptor.forClass(BatchCommand.class);
		verify(worker).dispatchBatch(command.capture());
		assertThat(command.getValue().batchSize()).isEqualTo(7);
		assertThat(command.getValue().at()).isEqualTo(NOW);
		assertThat(command.getValue().leaseUntil()).isEqualTo(NOW.plusSeconds(30));
		assertThat(counter(registry, WorkerMetrics.CLAIMED_TOTAL, "push_delivery_dispatch").count()).isEqualTo(6.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "SENT").count()).isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "RETRY_SCHEDULED").count())
			.isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "DEAD").count()).isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "CANCELLED").count())
			.isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "STALE_CLAIM").count())
			.isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "FAILURE_RECORDING_FAILED")
			.count()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("UNIT-013: push adapter는 fixed-delay 설정만 사용한다")
	void usesFixedDelaySchedulingProperty() throws ReflectiveOperationException {
		Method method = PushDeliveryDispatchScheduledAdapter.class.getDeclaredMethod("runOnce");
		Scheduled scheduled = method.getAnnotation(Scheduled.class);
		assertThat(scheduled).isNotNull();
		assertThat(scheduled.fixedDelayString())
			.isEqualTo("${qello.worker.scheduling.push-delivery-dispatch.fixed-delay}");
		assertThat(scheduled.fixedRateString()).isEmpty();
	}

	@Test
	@DisplayName("UNIT-017: batch 실패 후 다음 trigger는 BATCH_FAILED를 남기고 다시 실행할 수 있다")
	void recordsFailureAndAllowsTheNextInvocation() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(worker.dispatchBatch(any()))
			.thenThrow(new IllegalStateException("temporary push dispatch failure"))
			.thenReturn(new BatchResult(1, List.of(new DeliveryOutcome(11L, 1, Outcome.SENT, "sent"))));
		PushDeliveryDispatchScheduledAdapter adapter = new PushDeliveryDispatchScheduledAdapter(
			worker, properties(), CLOCK, new WorkerMetrics(registry));

		assertThatThrownBy(adapter::runOnce).isInstanceOf(IllegalStateException.class)
			.hasMessage("temporary push dispatch failure");
		adapter.runOnce();

		verify(worker, times(2)).dispatchBatch(any());
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "BATCH_FAILED").count())
			.isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.CLAIMED_TOTAL, "push_delivery_dispatch").count()).isEqualTo(1.0);
		assertThat(counter(registry, WorkerMetrics.OUTCOME_TOTAL, "push_delivery_dispatch", "SENT").count()).isEqualTo(1.0);
	}

	private WorkerSchedulingProperties properties() {
		return new WorkerSchedulingProperties(true, 1, null, null, null, null, null, null,
			new PushSettings(true, Duration.ofMillis(50), 7, Duration.ofSeconds(30),
				new PushRetrySettings(3, Duration.ofSeconds(1), Duration.ofSeconds(30))));
	}

	private Counter counter(SimpleMeterRegistry registry, String name, String worker) {
		return registry.get(name).tag("worker", worker).counter();
	}

	private Counter counter(SimpleMeterRegistry registry, String name, String worker, String outcome) {
		return registry.get(name).tags("worker", worker, "outcome", outcome).counter();
	}
}
