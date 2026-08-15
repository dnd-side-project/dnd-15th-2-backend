package com.dnd.qello.filtering.moderation;

import java.time.Duration;
import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.notification.domain.OutboxBackoffStrategy;

// FilterJob 자동 실행 실패 후 다음 행동(재시도 예약 vs 소진)을 판정하는 순수 도메인
// 클래스(#108). OutboxRetryPolicy(#119)를 그대로 쓰지 않는다 — 그 record는
// event.attemptCount() 기준으로 dead를 판정하는데, snapshot 단위 retry gate로 인해
// pipeline 호출 없이 미뤄진 재클레임까지 outbox attemptCount에 섞여 "logical
// attempt budget"과 어긋난다. 대신 job.logicalAttemptCount()/createdAt() 기준으로
// 직접 판정한다(INV-RTY-001, INV-RTY-002, INV-RTY-006).
//
// deadline 전에는 fastBackoff, deadline 이후(safety-completion)에는 slowBackoff를
// 선택한다. Retry-After 힌트가 있으면 계산된 backoff의 최소 하한으로 쓴다.
public final class AnswerModerationRetryPolicy {

	private final OutboxBackoffStrategy fastBackoff;
	private final OutboxBackoffStrategy slowBackoff;
	private final int maxAttempts;
	private final Duration maxRetryLifetime;

	public AnswerModerationRetryPolicy(OutboxBackoffStrategy fastBackoff, OutboxBackoffStrategy slowBackoff,
		int maxAttempts, Duration maxRetryLifetime) {
		if (fastBackoff == null || slowBackoff == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "backoffStrategy");
		}
		if (maxAttempts < 1) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "maxAttempts", "maxAttempts는 1 이상이어야 합니다");
		}
		if (maxRetryLifetime == null || maxRetryLifetime.isZero() || maxRetryLifetime.isNegative()) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "maxRetryLifetime", "maxRetryLifetime은 양수여야 합니다");
		}
		this.fastBackoff = fastBackoff;
		this.slowBackoff = slowBackoff;
		this.maxAttempts = maxAttempts;
		this.maxRetryLifetime = maxRetryLifetime;
	}

	/**
	 * job은 이미 {@link FilterJob#recordAutomatedAttempt(Instant)}로 이번 시도가 반영된
	 * 상태여야 한다 — 이 메서드는 "그 시도 이후 무엇을 할지"만 판정하고 FilterJob을
	 * 직접 전이시키지 않는다.
	 */
	public Decision decide(FilterJob job, Duration retryAfterHint, Instant at) {
		requireArgs(job, at);
		if (isExhausted(job, at)) {
			return Decision.exhaustedDecision();
		}
		OutboxBackoffStrategy cadence = at.isBefore(job.deadlineAt()) ? fastBackoff : slowBackoff;
		Duration computed = cadence.delayForAttempt(job.logicalAttemptCount());
		Duration delay = honorRetryAfter(computed, retryAfterHint);
		return Decision.retryAt(at.plus(delay));
	}

	private boolean isExhausted(FilterJob job, Instant at) {
		boolean attemptsExhausted = job.logicalAttemptCount() >= maxAttempts;
		boolean lifetimeExhausted = !at.isBefore(job.createdAt().plus(maxRetryLifetime));
		return attemptsExhausted || lifetimeExhausted;
	}

	private static Duration honorRetryAfter(Duration computed, Duration retryAfterHint) {
		if (retryAfterHint == null || retryAfterHint.isZero() || retryAfterHint.isNegative()) {
			return computed;
		}
		return computed.compareTo(retryAfterHint) >= 0 ? computed : retryAfterHint;
	}

	private static void requireArgs(FilterJob job, Instant at) {
		if (job == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "job");
		}
		if (at == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "at");
		}
	}

	public record Decision(boolean exhausted, Instant nextAttemptAt) {
		public static Decision exhaustedDecision() {
			return new Decision(true, null);
		}

		public static Decision retryAt(Instant nextAttemptAt) {
			return new Decision(false, nextAttemptAt);
		}
	}
}
