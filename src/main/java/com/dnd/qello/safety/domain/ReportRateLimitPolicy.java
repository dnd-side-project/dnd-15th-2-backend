package com.dnd.qello.safety.domain;

import java.time.Duration;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 신고자당 rate limit. 실제 운영 수치는 미정이며 설정 주입 값으로만 존재한다
// (설계 문서 §12, AGENTS.md §4.3의 UNKNOWN 표기에 해당).
public record ReportRateLimitPolicy(int maxRequestsPerWindow, Duration window) {

	public ReportRateLimitPolicy {
		if (maxRequestsPerWindow <= 0) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING,
				"maxRequestsPerWindow", "maxRequestsPerWindow는 양수여야 합니다");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "window", "window는 양수여야 합니다");
		}
	}
}
