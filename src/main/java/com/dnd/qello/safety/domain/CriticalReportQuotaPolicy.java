package com.dnd.qello.safety.domain;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 계정당 CRITICAL 신고 일일 쿼터(#157, 설계 문서 §4.1 남용 통제 (a)).
// 실제 운영 수치는 설정 주입 값으로만 존재한다(ReportRateLimitPolicy와 같은 관례).
public record CriticalReportQuotaPolicy(int maxPerDay) {

	public CriticalReportQuotaPolicy {
		if (maxPerDay <= 0) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "maxPerDay", "maxPerDay는 양수여야 합니다");
		}
	}
}
