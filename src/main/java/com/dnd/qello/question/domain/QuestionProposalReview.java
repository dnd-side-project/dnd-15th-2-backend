package com.dnd.qello.question.domain;

import java.time.Instant;

import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;

public final class QuestionProposalReview {

	private final Long id;
	private final Long proposalId;
	private final Long reviewerId;
	private final QuestionProposalReviewDecision decision;
	private final String reason;
	private final Instant reviewedAt;

	private QuestionProposalReview(
		Long id,
		Long proposalId,
		Long reviewerId,
		QuestionProposalReviewDecision decision,
		String reason,
		Instant reviewedAt
	) {
		this.id = id;
		this.proposalId = requirePositive(proposalId, "proposalId");
		this.reviewerId = requirePositive(reviewerId, "reviewerId");
		this.decision = requireValue(decision, "decision");
		this.reason = decision == QuestionProposalReviewDecision.REJECTED
			? requireText(reason, "reason") : validateReason(reason);
		this.reviewedAt = requireValue(reviewedAt, "reviewedAt");
	}

	public static QuestionProposalReview approve(Long proposalId, Long reviewerId, String reason, Instant reviewedAt) {
		return new QuestionProposalReview(null, proposalId, reviewerId,
			QuestionProposalReviewDecision.APPROVED, reason, reviewedAt);
	}

	public static QuestionProposalReview reject(Long proposalId, Long reviewerId, String reason, Instant reviewedAt) {
		return new QuestionProposalReview(null, proposalId, reviewerId,
			QuestionProposalReviewDecision.REJECTED, reason, reviewedAt);
	}

	public static QuestionProposalReview restore(
		Long id, Long proposalId, Long reviewerId,
		QuestionProposalReviewDecision decision, String reason, Instant reviewedAt
	) {
		return new QuestionProposalReview(id, proposalId, reviewerId, decision, reason, reviewedAt);
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new QuestionException(QuestionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static long requirePositive(Long value, String field) {
		if (value == null || value <= 0) {
			throw new QuestionException(QuestionErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
		return value;
	}

	private static String validateReason(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new QuestionException(QuestionErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	public Long getId() { return id; }
	public Long getProposalId() { return proposalId; }
	public Long getReviewerId() { return reviewerId; }
	public QuestionProposalReviewDecision getDecision() { return decision; }
	public String getReason() { return reason; }
	public Instant getReviewedAt() { return reviewedAt; }
}
