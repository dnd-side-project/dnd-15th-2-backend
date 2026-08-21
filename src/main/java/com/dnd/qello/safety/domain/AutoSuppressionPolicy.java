package com.dnd.qello.safety.domain;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 자동 전역 숨김을 트리거하는 서로 다른 신고자 수 임계값. 실제 운영 수치는
// 미정이며 설정 주입 값으로만 존재한다(#156, ReportRateLimitPolicy와 같은 관례).
public record AutoSuppressionPolicy(int distinctReporterThreshold) {

	public AutoSuppressionPolicy {
		if (distinctReporterThreshold <= 0) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING,
				"distinctReporterThreshold", "distinctReporterThreshold는 양수여야 합니다");
		}
	}
}
