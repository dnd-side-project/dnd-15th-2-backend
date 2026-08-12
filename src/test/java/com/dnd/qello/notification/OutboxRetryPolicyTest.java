/**
 * Created at: 2026-08-13T01:45:09+09:00
 * Source scenario: TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-UNIT-001 through UNIT-005
 */
package com.dnd.qello.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxFailureKind;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.domain.OutboxRetryPolicy;
import com.dnd.qello.notification.domain.OutboxStatus;
import com.dnd.qello.notification.error.NotificationErrorCode;
import com.dnd.qello.notification.error.NotificationException;

class OutboxRetryPolicyTest {

	private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

	@Test
	@DisplayName("retryable 실패는 최대 횟수 미만에서 attempt별 backoff 후 FAILED가 된다")
	void retryableFailureSchedulesNextAttempt() {
		OutboxEvent processing = processingEvent("retryable", 2);
		OutboxRetryPolicy policy = new OutboxRetryPolicy(3,
			attempt -> Duration.ofSeconds(attempt * 10L));

		OutboxRetryDecision decision = policy.decide(processing, OutboxFailureKind.RETRYABLE, NOW);

		assertThat(decision.dead()).isFalse();
		assertThat(decision.status()).isEqualTo(OutboxStatus.FAILED);
		assertThat(decision.nextAttemptAt()).isEqualTo(NOW.plusSeconds(20));
	}

	@Test
	@DisplayName("retryable 실패는 현재 attempt가 최대 횟수에 도달하면 DEAD가 된다")
	void retryableFailureAtMaximumBecomesDead() {
		OutboxRetryPolicy policy = new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(10));

		OutboxRetryDecision decision = policy.decide(processingEvent("max-attempt", 3),
			OutboxFailureKind.RETRYABLE, NOW);

		assertThat(decision.dead()).isTrue();
		assertThat(decision.status()).isEqualTo(OutboxStatus.DEAD);
		assertThat(decision.nextAttemptAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("permanent 실패는 남은 시도 횟수와 무관하게 DEAD가 된다")
	void permanentFailureBecomesDeadImmediately() {
		OutboxRetryPolicy policy = new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(10));

		OutboxRetryDecision decision = policy.decide(processingEvent("permanent", 1),
			OutboxFailureKind.PERMANENT, NOW);

		assertThat(decision.dead()).isTrue();
		assertThat(decision.status()).isEqualTo(OutboxStatus.DEAD);
		assertThat(decision.nextAttemptAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("PROCESSING이 아닌 이벤트는 실패 전이 정책으로 판정할 수 없다")
	void rejectsNonProcessingEvent() {
		OutboxRetryPolicy policy = new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(10));
		OutboxEvent pending = OutboxEvent.pending(OutboxAggregateType.ANSWER, 119L,
			OutboxEventType.ANSWER_PUBLISHED, "pending", "{\"answerId\":119}", NOW);

		assertThatThrownBy(() -> policy.decide(pending, OutboxFailureKind.RETRYABLE, NOW))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_NOTIFICATION_STATUS);
	}

	@Test
	@DisplayName("잘못된 retry 정책 값과 overflow backoff를 거절한다")
	void rejectsInvalidPolicyValues() {
		assertThatThrownBy(() -> new OutboxRetryPolicy(0, attempt -> Duration.ofSeconds(1)))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new OutboxRetryPolicy(3, attempt -> Duration.ZERO)
			.decide(processingEvent("zero-backoff", 1), OutboxFailureKind.RETRYABLE, NOW))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new OutboxRetryPolicy(3, attempt -> null)
			.decide(processingEvent("null-backoff", 1), OutboxFailureKind.RETRYABLE, NOW))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))
			.decide(processingEvent("null-kind", 1), null, NOW))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))
			.decide(processingEvent("null-at", 1), OutboxFailureKind.RETRYABLE, null))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> new OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))
			.decide(processingEvent("overflow", 1), OutboxFailureKind.RETRYABLE, Instant.MAX))
			.isInstanceOf(NotificationException.class)
			.hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.INVALID_VALUE_RANGE);
	}

	private OutboxEvent processingEvent(String dedupKey, int attemptCount) {
		OutboxEvent pending = OutboxEvent.pending(OutboxAggregateType.ANSWER, 119L,
			OutboxEventType.ANSWER_PUBLISHED, dedupKey, "{\"answerId\":119}", NOW);
		return new OutboxEvent(119L, OutboxAggregateType.ANSWER, 119L,
			OutboxEventType.ANSWER_PUBLISHED, dedupKey, pending.payload(), OutboxStatus.PROCESSING,
			attemptCount, NOW, NOW, null, null, "worker-119", NOW.plusSeconds(60), attemptCount);
	}
}
