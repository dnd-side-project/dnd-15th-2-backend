package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// priority 평가 append-only 감사 이력 한 건(#110). ManualReviewCase.band/
// priorityReasonCode는 "현재" 값만 담고, 이 레코드가 "언제 왜 바뀌었는지"의
// 전체 이력을 담당한다(재평가 이력).
public record ManualReviewPriorityEvaluation(
	Long id, long manualReviewCaseId, ManualReviewBand band, ManualReviewPriorityReasonCode reasonCode,
	String policyVersion, Instant evaluatedAt
) {

	private static final int POLICY_VERSION_MAX_LENGTH = 50;

	public ManualReviewPriorityEvaluation {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (manualReviewCaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "manualReviewCaseId", "manualReviewCaseId는 양수여야 합니다");
		}
		if (band == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "band");
		}
		if (reasonCode == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "reasonCode");
		}
		if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > POLICY_VERSION_MAX_LENGTH) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_TEXT, "policyVersion", "policyVersion 값이 유효하지 않습니다");
		}
		if (evaluatedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "evaluatedAt");
		}
	}

	public static ManualReviewPriorityEvaluation of(long manualReviewCaseId, ManualReviewBand band,
		ManualReviewPriorityReasonCode reasonCode, String policyVersion, Instant evaluatedAt) {
		return new ManualReviewPriorityEvaluation(null, manualReviewCaseId, band, reasonCode, policyVersion,
			evaluatedAt);
	}
}
