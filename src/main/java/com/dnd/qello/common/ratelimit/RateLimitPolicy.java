package com.dnd.qello.common.ratelimit;

import java.time.Duration;

// window 동안 키 하나에 허용하는 요청 수. 값은 각 기능의 설정에서 주입한다.
public record RateLimitPolicy(int maxRequests, Duration window) {

	public RateLimitPolicy {
		if (maxRequests <= 0) {
			throw new IllegalArgumentException("maxRequests는 양수여야 합니다");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("window는 양수여야 합니다");
		}
	}
}
