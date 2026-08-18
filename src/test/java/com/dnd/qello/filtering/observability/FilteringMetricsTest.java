/**
 * Created at: 2026-08-18T21:15:00+09:00
 * Source scenario: TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE-UNIT-001 ~ UNIT-003, UNIT-010 ~ UNIT-012
 */
package com.dnd.qello.filtering.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class FilteringMetricsTest {

	private final MeterRegistry registry = new SimpleMeterRegistry();
	private final FilteringMetrics metrics = new FilteringMetrics(registry);

	@Test
	@DisplayName("UNIT-001: 허용목록 밖 tag 키는 거절한다")
	void rejectsTagKeyOutsideAllowlist() {
		assertThatThrownBy(() -> FilteringMetricTags.tag("user_id", "42"))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);
		assertThatThrownBy(() -> FilteringMetricTags.tag("raw_content", "답변 원문"))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);
	}

	@Test
	@DisplayName("UNIT-002: 허용목록 안 tag는 통과시킨다")
	void acceptsAllowedTag() {
		assertThatCode(() -> FilteringMetricTags.of("path", "ANSWER", "verdict", "BLOCK"))
			.doesNotThrowAnyException();
		assertThat(FilteringMetricTags.allowedKeys()).contains("path", "language", "release", "model", "verdict");
		assertThat(FilteringMetricTags.allowedKeys()).doesNotContain("user_id", "target_id", "content");
	}

	@Test
	@DisplayName("UNIT-003: 빈 값·과도하게 긴 값·짝이 맞지 않는 쌍은 거절한다")
	void rejectsUnusableTagValues() {
		assertThatThrownBy(() -> FilteringMetricTags.tag("path", "  "))
			.isInstanceOf(FilteringException.class);
		assertThatThrownBy(() -> FilteringMetricTags.tag("path", "X".repeat(61)))
			.isInstanceOf(FilteringException.class);
		assertThatThrownBy(() -> FilteringMetricTags.of("path"))
			.isInstanceOf(FilteringException.class);
	}

	@Test
	@DisplayName("UNIT-015: 허용된 키라도 이메일·원문 조각처럼 자유 텍스트인 값은 거절한다")
	void rejectsFreeTextValuesUnderAllowedKeys() {
		// 키만 제한하면 허용된 키에 개인 식별자나 답변 조각을 실을 수 있다.
		assertThatThrownBy(() -> FilteringMetricTags.tag("path", "user@example.com"))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_TEXT);
		assertThatThrownBy(() -> FilteringMetricTags.tag("outcome", "오늘 날씨가 좋네요"))
			.isInstanceOf(FilteringException.class);
		assertThatThrownBy(() -> FilteringMetricTags.tag("reason_code", "nickname:김도연"))
			.isInstanceOf(FilteringException.class);
		assertThatThrownBy(() -> FilteringMetricTags.tag("release", "release-7"))
			.isInstanceOf(FilteringException.class);
		assertThatThrownBy(() -> FilteringMetricTags.tag("model", "Answer body text"))
			.isInstanceOf(FilteringException.class);
	}

	@Test
	@DisplayName("UNIT-016: 거절 메시지에 값 자체를 담지 않는다")
	void rejectionMessageNeverEchoesTheValue() {
		// 거절된 값이 곧 원문이나 식별자일 수 있다. 메시지로 흘리면 막으려던
		// 유출이 그대로 일어난다.
		assertThatThrownBy(() -> FilteringMetricTags.tag("path", "user@example.com"))
			.hasMessageNotContaining("user@example.com");
	}

	@Test
	@DisplayName("UNIT-010: 판정 분포는 release와 실제 model을 분리해 기록한다")
	void recordsVerdictByReleaseAndActualModel() {
		metrics.recordVerdict("7", "omni-moderation-2026-08-01", "BLOCK");

		assertThat(registry.get(FilteringMetrics.VERDICT_TOTAL)
			.tag("release", "7").tag("model", "omni-moderation-2026-08-01").tag("verdict", "BLOCK")
			.counter().count()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("UNIT-011: logical attempt와 실제 공급자 호출은 각각 독립적으로 증가한다")
	void countsLogicalAttemptAndProviderCallSeparately() {
		metrics.countLogicalAttempt("ANSWER");
		metrics.countProviderCall("ANSWER", "SUCCESS");
		metrics.countProviderCall("ANSWER", "TIMEOUT");

		assertThat(registry.get(FilteringMetrics.LOGICAL_ATTEMPT_TOTAL).tag("path", "ANSWER").counter().count())
			.isEqualTo(1.0);
		assertThat(registry.get(FilteringMetrics.PROVIDER_CALL_TOTAL)
			.tag("path", "ANSWER").tag("outcome", "TIMEOUT").counter().count()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("UNIT-012: 허용목록을 위반한 계측 호출은 예외를 던지지 않고 지표만 남기지 않는다")
	void instrumentationFailureNeverPropagates() {
		// 계측 때문에 판정이 실패하면 안 된다. 허용목록 위반은 테스트가 잡고,
		// 운영에서는 지표 한 건을 잃는 편이 낫다.
		assertThatCode(() -> metrics.recordPipeline("ANSWER", "KO", "x".repeat(61), Duration.ofMillis(10)))
			.doesNotThrowAnyException();
		assertThat(registry.find(FilteringMetrics.PIPELINE_OUTCOME).counter()).isNull();
	}

	@Test
	@DisplayName("UNIT-013: 기록된 모든 meter의 tag 키가 허용목록 안에 있다")
	void everyRecordedMeterUsesAllowedTagsOnly() {
		metrics.recordPipeline("ANSWER", "KO", "SUCCESS", Duration.ofMillis(12));
		metrics.recordVerdict("7", "model-a", "ALLOW");
		metrics.countLogicalAttempt("NICKNAME");
		metrics.countProviderCall("NICKNAME", "SUCCESS");
		metrics.recordQueueDwell("KO", "HIGH", Duration.ofMinutes(3));
		metrics.countDeadlineElapsed("KO");
		metrics.countManualDecision("BLOCK");
		metrics.countAppealDecision("OVERTURN_HIDDEN", "NONE");
		metrics.countSlackDelivery("SUCCESS");

		assertThat(registry.getMeters()).isNotEmpty();
		for (Meter meter : registry.getMeters()) {
			for (Tag tag : meter.getId().getTags()) {
				assertThat(FilteringMetricTags.allowedKeys())
					.as("meter %s의 tag 키", meter.getId().getName())
					.contains(tag.getKey());
			}
		}
	}
}
