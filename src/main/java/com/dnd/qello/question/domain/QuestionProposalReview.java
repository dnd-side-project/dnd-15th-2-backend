package com.dnd.qello.question.domain;

import java.time.Instant;
import java.util.Objects;

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
		this.decision = Objects.requireNonNull(decision, "decision은 필수입니다");
		this.reason = decision == QuestionProposalReviewDecision.REJECTED
			? requireText(reason, "reason") : validateReason(reason);
		this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt은 필수입니다");
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

	private static long requirePositive(Long value, String field) {
		if (value == null || value <= 0) throw new IllegalArgumentException(field + "는 양수여야 합니다");
		return value;
	}

	private static String validateReason(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "은 필수입니다");
		return value;
	}

	public Long getId() { return id; }
	public Long getProposalId() { return proposalId; }
	public Long getReviewerId() { return reviewerId; }
	public QuestionProposalReviewDecision getDecision() { return decision; }
	public String getReason() { return reason; }
	public Instant getReviewedAt() { return reviewedAt; }
}
