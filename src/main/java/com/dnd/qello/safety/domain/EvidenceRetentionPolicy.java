package com.dnd.qello.safety.domain;

import java.time.Duration;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 증거 스냅샷 보존 기간(#157, 설계 문서 §12 purge_after 기본값). 실제 운영
// 수치는 설정 주입 값으로만 존재한다(ReportRateLimitPolicy와 같은 관례).
// legal_hold인 스냅샷은 이 기간과 무관하게 정리 배치에서 제외된다.
public record EvidenceRetentionPolicy(Duration retentionPeriod) {

	public EvidenceRetentionPolicy {
		if (retentionPeriod == null || retentionPeriod.isZero() || retentionPeriod.isNegative()) {
			throw new SafetyException(
				SafetyErrorCode.REQUIRED_VALUE_MISSING, "retentionPeriod", "retentionPeriod는 양수여야 합니다");
		}
	}
}
