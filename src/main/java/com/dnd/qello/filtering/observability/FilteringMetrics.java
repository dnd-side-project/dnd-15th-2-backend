package com.dnd.qello.filtering.observability;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

// 필터링 지표의 단일 계측 지점(#113).
//
// 계측은 절대 판정을 바꾸지 않는다. 호출자는 이 클래스가 던지는 예외를
// 판정 실패로 오인하면 안 되므로, 여기서 모든 예외를 흡수한다 — 관측 때문에
// 본 기능이 실패하는 것이 관측이 없는 것보다 나쁘다.
//
// tag는 FilteringMetricTags의 허용목록을 통과한 값만 쓴다. 원문·사용자
// 식별자는 어떤 경로로도 tag가 될 수 없다(INV-CMP-001, INV-CMP-002).
@Component
public class FilteringMetrics {

	public static final String PIPELINE_LATENCY = "qello.filtering.pipeline.latency";
	public static final String PIPELINE_OUTCOME = "qello.filtering.pipeline.outcome";
	public static final String VERDICT_TOTAL = "qello.filtering.verdict.total";
	public static final String LOGICAL_ATTEMPT_TOTAL = "qello.filtering.attempt.logical.total";
	public static final String PROVIDER_CALL_TOTAL = "qello.filtering.attempt.provider.total";
	public static final String QUEUE_DWELL = "qello.filtering.queue.dwell";
	public static final String DEADLINE_ELAPSED_TOTAL = "qello.filtering.deadline.elapsed.total";
	public static final String MANUAL_DECISION_TOTAL = "qello.filtering.manual.decision.total";
	public static final String APPEAL_DECISION_TOTAL = "qello.filtering.appeal.decision.total";
	public static final String SLACK_DELIVERY_TOTAL = "qello.filtering.slack.delivery.total";

	private final MeterRegistry registry;

	public FilteringMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	// 판정 경로 한 번의 소요 시간과 결과 분류. timeout·오류도 outcome으로 구분한다.
	public void recordPipeline(String path, String language, String outcome, Duration elapsed) {
		record(() -> {
			registry.timer(PIPELINE_LATENCY, FilteringMetricTags.of("path", path, "language", language))
				.record(elapsed);
			registry.counter(PIPELINE_OUTCOME,
				FilteringMetricTags.of("path", path, "language", language, "outcome", outcome)).increment();
		});
	}

	// release와 공급자가 실제로 보고한 model을 분리해 센다. 둘이 다를 수 있어
	// regression 추적에 둘 다 필요하다(INV-REL-005).
	public void recordVerdict(String release, String model, String verdict) {
		record(() -> registry.counter(VERDICT_TOTAL,
			FilteringMetricTags.of("release", release, "model", model, "verdict", verdict)).increment());
	}

	// logical attempt와 실제 공급자 호출은 재시도 정책 때문에 어긋날 수 있다.
	// 두 수를 따로 세야 "몇 번 시도했는지"와 "실제로 얼마를 썼는지"를 구분한다.
	public void countLogicalAttempt(String path) {
		record(() -> registry.counter(LOGICAL_ATTEMPT_TOTAL, FilteringMetricTags.of("path", path)).increment());
	}

	public void countProviderCall(String path, String outcome) {
		record(() -> registry.counter(PROVIDER_CALL_TOTAL,
			FilteringMetricTags.of("path", path, "outcome", outcome)).increment());
	}

	public void recordQueueDwell(String language, String band, Duration dwell) {
		record(() -> registry.timer(QUEUE_DWELL, FilteringMetricTags.of("language", language, "band", band))
			.record(dwell));
	}

	public void countDeadlineElapsed(String language) {
		record(() -> registry.counter(DEADLINE_ELAPSED_TOTAL, FilteringMetricTags.of("language", language))
			.increment());
	}

	public void countManualDecision(String verdict) {
		record(() -> registry.counter(MANUAL_DECISION_TOTAL, FilteringMetricTags.of("verdict", verdict)).increment());
	}

	// 이의제기 결과와, 인용됐는데도 복원 콜백이 막힌 경우의 사유를 함께 센다.
	public void countAppealDecision(String decision, String reasonCode) {
		record(() -> registry.counter(APPEAL_DECISION_TOTAL,
			FilteringMetricTags.of("decision", decision, "reason_code", reasonCode)).increment());
	}

	public void countSlackDelivery(String outcome) {
		record(() -> registry.counter(SLACK_DELIVERY_TOTAL, FilteringMetricTags.of("outcome", outcome)).increment());
	}

	// 계측 실패를 삼킨다. 허용목록 위반은 개발 중 테스트가 잡고(FilteringMetricTagsTest,
	// 전체 meter 전수 검사), 운영에서는 지표 한 건을 잃는 편이 판정을 실패시키는
	// 것보다 낫다.
	private void record(Runnable instrumentation) {
		try {
			instrumentation.run();
		} catch (RuntimeException ignored) {
			// 관측 실패가 판정 결과를 바꾸지 않는다.
		}
	}
}
