package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewCaseStatus;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;

public record ManualReviewCaseResponse(
	long id,
	long filterJobId,
	long filterReleaseId,
	ManualReviewCaseStatus status,
	ManualReviewBand band,
	int validatedReportSignalCount,
	String priorityPolicyVersion,
	ManualReviewPriorityReasonCode priorityReasonCode,
	Instant resolvedAt,
	Long resolvedByOperatorUserId,
	FilterVerdict resolvedVerdict,
	Instant createdAt
) {

	public static ManualReviewCaseResponse from(ManualReviewCase reviewCase) {
		return new ManualReviewCaseResponse(
			reviewCase.id(),
			reviewCase.filterJobId(),
			reviewCase.filterReleaseId(),
			reviewCase.status(),
			reviewCase.band(),
			reviewCase.validatedReportSignalCount(),
			reviewCase.priorityPolicyVersion(),
			reviewCase.priorityReasonCode(),
			reviewCase.resolvedAt(),
			reviewCase.resolvedByOperatorUserId(),
			reviewCase.resolvedVerdict(),
			reviewCase.createdAt()
		);
	}
}
