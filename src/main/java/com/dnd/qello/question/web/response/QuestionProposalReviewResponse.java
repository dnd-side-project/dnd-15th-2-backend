package com.dnd.qello.question.web.response;

import java.time.Instant;

import com.dnd.qello.question.domain.QuestionProposalReview;

/** 제안에 대한 운영자 판정 한 건. append-only 이력이므로 수정 시각이 없다. */
public record QuestionProposalReviewResponse(
	long id,
	long proposalId,
	long reviewerId,
	String decision,
	String reason,
	Instant reviewedAt
) {
	public static QuestionProposalReviewResponse from(QuestionProposalReview review) {
		return new QuestionProposalReviewResponse(
			review.getId(),
			review.getProposalId(),
			review.getReviewerId(),
			review.getDecision().name(),
			review.getReason(),
			review.getReviewedAt());
	}
}
