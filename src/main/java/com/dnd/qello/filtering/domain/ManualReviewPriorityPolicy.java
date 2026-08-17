package com.dnd.qello.filtering.domain;

import java.time.Duration;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// ManualReviewCase의 band 평가와 aging 승격 임계값(#110). 실제 운영 수치(이슈
// 본문에서 "미결정"으로 명시)는 이 record가 아니라 호출자가 주입한다 — 여기서는
// 형태와 정합성만 강제한다.
public record ManualReviewPriorityPolicy(
	int highBandReportSignalThreshold, Duration agingThreshold, String policyVersion
) {

	private static final int POLICY_VERSION_MAX_LENGTH = 50;

	public ManualReviewPriorityPolicy {
		if (highBandReportSignalThreshold < 1) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "highBandReportSignalThreshold",
				"highBandReportSignalThreshold는 1 이상이어야 합니다");
		}
		if (agingThreshold == null || agingThreshold.isNegative() || agingThreshold.isZero()) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "agingThreshold", "agingThreshold는 양수여야 합니다");
		}
		if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > POLICY_VERSION_MAX_LENGTH) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_TEXT, "policyVersion", "policyVersion 값이 유효하지 않습니다");
		}
	}
}
