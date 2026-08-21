package com.dnd.qello.safety.domain;

import java.time.Duration;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 큐별 SLA 기간. 실제 운영 수치는 미정이며 설정 주입 값으로만 존재한다(#156,
// ReportRateLimitPolicy와 같은 관례).
public record SlaPolicy(Duration standard, Duration urgent) {

	public SlaPolicy {
		requirePositive(standard, "standard");
		requirePositive(urgent, "urgent");
	}

	public Duration of(ReportCaseQueue queue) {
		return switch (queue) {
			case STANDARD -> standard;
			case URGENT -> urgent;
		};
	}

	private static void requirePositive(Duration duration, String field) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, field, field + "는 양수여야 합니다");
		}
	}
}
