package com.dnd.qello.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationEvent;
import com.dnd.qello.notification.domain.NotificationRetryPolicy;
import com.dnd.qello.notification.domain.OutboxFailureKind;
import com.dnd.qello.notification.domain.OutboxRetryDecision;

/**
 * Created at: 2026-08-17T16:20:00+09:00
 * Source scenario: TEST-PLAN-GH-111-SLACK-MANUAL-REVIEW-NOTIFICATION-UNIT-006 through UNIT-006c
 */
class NotificationRetryPolicyTest {

	private static final Instant CREATED_AT = Instant.parse("2026-08-17T00:00:00Z");
	private static final String ADMIN_LINK_PATH = "/admin/filtering/manual-review-cases/7";
	private static final NotificationRetryPolicy POLICY =
		new NotificationRetryPolicy(3, attempt -> Duration.ofSeconds(30));

	@Test
	@DisplayName("최대 시도 횟수 미만의 재시도 가능 실패는 backoff만큼 미룬 재시도로 판정한다")
	void decidesRetryWhenAttemptsRemain() {
		NotificationEvent processing = processingEventWithAttempts(2);

		OutboxRetryDecision decision = POLICY.decide(processing, OutboxFailureKind.RETRYABLE, CREATED_AT);

		assertThat(decision.dead()).isFalse();
		assertThat(decision.nextAttemptAt()).isEqualTo(CREATED_AT.plusSeconds(30));
	}

	@Test
	@DisplayName("최대 시도 횟수에 도달한 재시도 가능 실패는 dead로 판정한다")
	void decidesDeadWhenMaxAttemptsReached() {
		NotificationEvent processing = processingEventWithAttempts(3);

		OutboxRetryDecision decision = POLICY.decide(processing, OutboxFailureKind.RETRYABLE, CREATED_AT);

		assertThat(decision.dead()).isTrue();
	}

	@Test
	@DisplayName("PERMANENT 실패는 남은 시도 횟수와 무관하게 즉시 dead로 판정한다")
	void decidesDeadImmediatelyOnPermanentFailure() {
		NotificationEvent processing = processingEventWithAttempts(0);

		OutboxRetryDecision decision = POLICY.decide(processing, OutboxFailureKind.PERMANENT, CREATED_AT);

		assertThat(decision.dead()).isTrue();
		assertThat(decision.nextAttemptAt()).isEqualTo(CREATED_AT);
	}

	private static NotificationEvent processingEventWithAttempts(int attemptCount) {
		return new NotificationEvent(1L, 7L, ADMIN_LINK_PATH,
			com.dnd.qello.notification.domain.NotificationEventStatus.PROCESSING, attemptCount, CREATED_AT,
			CREATED_AT, null, "worker-a", CREATED_AT.plusSeconds(60), 1);
	}
}
