package com.dnd.qello.filtering.domain;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// ManualReviewCase.evaluatePriority(...)의 순수 결과값(#110). band와 그 band를
// 매긴 이유를 함께 옮긴다.
public record ManualReviewPriorityDecision(ManualReviewBand band, ManualReviewPriorityReasonCode reasonCode) {

	public ManualReviewPriorityDecision {
		if (band == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "band");
		}
		if (reasonCode == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "reasonCode");
		}
	}
}
