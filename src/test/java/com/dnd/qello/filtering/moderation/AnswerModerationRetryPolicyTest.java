/**
 * Created at: 2026-08-15T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY-UNIT-002 through UNIT-006
 */
package com.dnd.qello.filtering.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;

class AnswerModerationRetryPolicyTest {

	private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
	private static final FilterTarget TARGET = FilterTarget.of(FilterTargetType.ANSWER, 1L);
	private static final OutboxBackoffStrategy FAST = attempt -> Duration.ofSeconds(1);
	private static final OutboxBackoffStrategy SLOW = attempt -> Duration.ofSeconds(60);

	@Test
	@DisplayName("logicalAttemptCount가 maxAttempts에 도달하면 exhaust로 판정한다")
	void exhaustsWhenMaxAttemptsReached() {
		AnswerModerationRetryPolicy policy = new AnswerModerationRetryPolicy(FAST, SLOW, 2, Duration.ofHours(1));
		FilterJob job = jobWithAttempts(2, NOW.plusSeconds(600));

		AnswerModerationRetryPolicy.Decision decision = policy.decide(job, null, NOW.plusSeconds(1));

		assertThat(decision.exhausted()).isTrue();
		assertThat(decision.nextAttemptAt()).isNull();
	}

	@Test
	@DisplayName("createdAt 기준 maxRetryLifetime을 넘으면 attemptCount와 무관하게 exhaust로 판정한다")
	void exhaustsWhenLifetimeExceededRegardlessOfAttemptCount() {
		AnswerModerationRetryPolicy policy = new AnswerModerationRetryPolicy(FAST, SLOW, 100, Duration.ofMinutes(30));
		FilterJob job = jobWithAttempts(1, NOW.plusSeconds(600));

		AnswerModerationRetryPolicy.Decision decision = policy.decide(job, null, NOW.plus(Duration.ofMinutes(31)));

		assertThat(decision.exhausted()).isTrue();
	}

	@Test
	@DisplayName("deadline 이전에는 fast backoff, deadline 이후에는 slow backoff를 선택한다")
	void selectsCadenceByDeadline() {
		AnswerModerationRetryPolicy policy = new AnswerModerationRetryPolicy(FAST, SLOW, 100, Duration.ofHours(1));
		Instant deadline = NOW.plusSeconds(600);
		FilterJob job = jobWithAttempts(1, deadline);

		AnswerModerationRetryPolicy.Decision beforeDeadline = policy.decide(job, null, deadline.minusSeconds(1));
		AnswerModerationRetryPolicy.Decision afterDeadline = policy.decide(job, null, deadline.plusSeconds(1));

		assertThat(beforeDeadline.nextAttemptAt()).isEqualTo(deadline.minusSeconds(1).plusSeconds(1));
		assertThat(afterDeadline.nextAttemptAt()).isEqualTo(deadline.plusSeconds(1).plusSeconds(60));
	}

	@Test
	@DisplayName("Retry-After 힌트가 계산된 backoff보다 크면 하한으로 사용한다")
	void usesRetryAfterAsFloorWhenLarger() {
		AnswerModerationRetryPolicy policy = new AnswerModerationRetryPolicy(FAST, SLOW, 100, Duration.ofHours(1));
		FilterJob job = jobWithAttempts(1, NOW.plusSeconds(600));

		AnswerModerationRetryPolicy.Decision decision = policy.decide(job, Duration.ofSeconds(45), NOW);

		assertThat(decision.nextAttemptAt()).isEqualTo(NOW.plusSeconds(45));
	}

	@Test
	@DisplayName("Retry-After 힌트가 계산된 backoff보다 작으면 계산된 backoff를 그대로 쓴다")
	void ignoresRetryAfterWhenSmallerThanComputedBackoff() {
		AnswerModerationRetryPolicy policy = new AnswerModerationRetryPolicy(FAST, SLOW, 100, Duration.ofHours(1));
		FilterJob job = jobWithAttempts(1, NOW.plusSeconds(600));

		AnswerModerationRetryPolicy.Decision decision = policy.decide(job, Duration.ofMillis(1), NOW);

		assertThat(decision.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	@DisplayName("Retry-After 힌트가 없거나 0 이하이면 순수 capped backoff만 적용한다")
	void ignoresAbsentOrNonPositiveRetryAfter() {
		AnswerModerationRetryPolicy policy = new AnswerModerationRetryPolicy(FAST, SLOW, 100, Duration.ofHours(1));
		FilterJob job = jobWithAttempts(1, NOW.plusSeconds(600));

		AnswerModerationRetryPolicy.Decision withNullHint = policy.decide(job, null, NOW);
		AnswerModerationRetryPolicy.Decision withZeroHint = policy.decide(job, Duration.ZERO, NOW);

		assertThat(withNullHint.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(withZeroHint.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	@DisplayName("생성자 인자가 유효하지 않으면 생성 시점에 거절된다")
	void rejectsInvalidConstructionArguments() {
		assertThatThrownBy(() -> new AnswerModerationRetryPolicy(FAST, SLOW, 0, Duration.ofHours(1)))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
		assertThatThrownBy(() -> new AnswerModerationRetryPolicy(FAST, SLOW, 1, Duration.ZERO))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}

	private static FilterJob jobWithAttempts(int attempts, Instant deadlineAt) {
		FilterJob job = FilterJob.create(TARGET, 5L, "idem-key", deadlineAt, NOW);
		for (int i = 0; i < attempts; i++) {
			job = job.recordAutomatedAttempt(1, NOW.plusSeconds(i + 1));
		}
		return job;
	}
}
