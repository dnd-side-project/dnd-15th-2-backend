package com.dnd.qello.filtering.moderation;

import java.time.Duration;

// moderation 공급자 호출이 429(rate limit)로 실패했을 때만 던진다. 유효한 Retry-After
// 헤더가 있으면 그 값을, 없거나 파싱할 수 없으면 null을 담는다 — 호출자
// (AnswerModerationExecutionWorker, #108)가 다음 재시도 지연의 최소 하한으로 쓴다.
// 다른 실패(timeout, 5xx, malformed response)는 여전히
// FilteringException(MODERATION_PROVIDER_UNAVAILABLE)로 던져진다 — 이 예외는 429
// 전용이며 판정을 대체하지 않는다.
public final class ModerationRateLimitedException extends RuntimeException {

	private final Duration retryAfter;

	public ModerationRateLimitedException(Duration retryAfter) {
		super("moderation 공급자가 rate limit(429)을 반환했습니다");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return retryAfter;
	}
}
