package com.dnd.qello.filtering.domain;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// FilterReleaseRetryGate 전이 임계값. 실제 운영 수치(#108 이슈 본문에서 "미결정"으로
// 명시)는 이 record가 아니라 호출자가 주입한다 — 여기서는 형태와 정합성만 강제한다.
public record RetryGateConfig(int degradeThreshold, int minLimit, int rampStep, int recoveryStreak, int healthyLimit) {

	public RetryGateConfig {
		if (degradeThreshold < 1) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "degradeThreshold", "degradeThreshold는 1 이상이어야 합니다");
		}
		if (minLimit < 1) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "minLimit", "minLimit은 1 이상이어야 합니다");
		}
		if (rampStep < 1) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "rampStep", "rampStep은 1 이상이어야 합니다");
		}
		if (recoveryStreak < 1) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "recoveryStreak", "recoveryStreak는 1 이상이어야 합니다");
		}
		if (healthyLimit <= minLimit) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "healthyLimit", "healthyLimit은 minLimit보다 커야 합니다");
		}
	}
}
