/**
 * Created at: 2026-08-27T14:32:15+09:00
 * Source scenario: TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-014 through UNIT-016
 */
package com.dnd.qello.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.scheduling.observability.WorkerMetrics.Outcome;
import com.dnd.qello.scheduling.observability.WorkerMetrics.WorkerName;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WorkerMetricsTest {
	private static final String CLAIMED_TOTAL = "qello.worker.claimed.total";
	private static final String SCANNED_TOTAL = "qello.worker.scanned.total";
	private static final String OUTCOME_TOTAL = "qello.worker.outcome.total";

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final WorkerMetrics metrics = new WorkerMetrics(registry);

	@Test
	@DisplayName("UNIT-014: claimed·scanned·outcome는 worker와 outcome allowlist tag로 입력 count만큼 기록한다")
	void recordsAllWorkerCounterKindsWithExactTagsAndCounts() {
		metrics.recordClaimed(WorkerName.DIRECTION_MATCHING, 3);
		metrics.recordScanned(WorkerName.RECIPIENT_EXPIRATION_SWEEP, 5);
		metrics.recordOutcome(WorkerName.DIRECTION_MATCHING, Outcome.PROCESSED, 2);

		assertThat(WorkerMetrics.CLAIMED_TOTAL).isEqualTo(CLAIMED_TOTAL);
		assertThat(WorkerMetrics.SCANNED_TOTAL).isEqualTo(SCANNED_TOTAL);
		assertThat(WorkerMetrics.OUTCOME_TOTAL).isEqualTo(OUTCOME_TOTAL);
		assertThat(counter(CLAIMED_TOTAL, "worker", "direction_matching").count()).isEqualTo(3.0);
		assertThat(counter(SCANNED_TOTAL, "worker", "recipient_expiration_sweep").count()).isEqualTo(5.0);
		assertThat(counter(OUTCOME_TOTAL, "worker", "direction_matching", "outcome", "PROCESSED").count())
			.isEqualTo(2.0);
		assertThat(registry.getMeters()).hasSize(3);
		for (Meter meter : registry.getMeters()) {
			for (Tag tag : meter.getId().getTags()) {
				assertThat(Set.of("worker", "outcome")).contains(tag.getKey());
			}
		}
	}

	@Test
	@DisplayName("UNIT-015: 음수 count는 counter를 반대로 움직이기 전에 거절한다")
	void rejectsNegativeCountsBeforeRecordingMetrics() {
		assertThatThrownBy(() -> metrics.recordClaimed(WorkerName.DIRECTION_MATCHING, -1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> metrics.recordScanned(WorkerName.DIRECTION_MATCHING, -1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> metrics.recordOutcome(WorkerName.DIRECTION_MATCHING, Outcome.FAILED, -1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(registry.getMeters()).isEmpty();
	}

	@Test
	@DisplayName("UNIT-016: registry 계측 실패는 어느 worker metric API에서도 호출자에게 전파되지 않는다")
	void instrumentationFailuresNeverPropagateToWorkers() {
		WorkerMetrics failingMetrics = new WorkerMetrics(new ThrowingMeterRegistry());

		assertThatCode(() -> failingMetrics.recordClaimed(WorkerName.DIRECTION_MATCHING, 1))
			.doesNotThrowAnyException();
		assertThatCode(() -> failingMetrics.recordScanned(WorkerName.RECIPIENT_EXPIRATION_SWEEP, 1))
			.doesNotThrowAnyException();
		assertThatCode(() -> failingMetrics.recordOutcome(WorkerName.PUSH_DELIVERY_DISPATCH, Outcome.SENT, 1))
			.doesNotThrowAnyException();
	}

	private Counter counter(String name, String... tags) {
		return registry.get(name).tags(tags).counter();
	}

	private static final class ThrowingMeterRegistry extends SimpleMeterRegistry {
		@Override
		public Counter counter(String name, Iterable<Tag> tags) {
			throw new IllegalStateException("registry unavailable");
		}
	}
}
