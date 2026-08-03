package com.dnd.qello.question.domain;

import java.time.Instant;
import java.util.Objects;

public final class QuestionProposal {

	private static final int TEXT_MAX_LENGTH = 2_000;

	private final Long id;
	private final Long proposerId;
	private final QuestionProposalStatus status;
	private final String proposedText;
	private final String decisionReason;
	private final Instant submittedAt;
	private final Instant createdAt;
	private final Instant updatedAt;

	private QuestionProposal(
		Long id,
		Long proposerId,
		QuestionProposalStatus status,
		String proposedText,
		String decisionReason,
		Instant submittedAt,
		Instant createdAt,
		Instant updatedAt
	) {
		this.id = validateId(id, "id");
		this.proposerId = requirePositive(proposerId, "proposerId");
		this.status = Objects.requireNonNull(status, "status는 필수입니다");
		this.proposedText = requireText(proposedText, "proposedText");
		this.decisionReason = validateReason(decisionReason);
		this.submittedAt = submittedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		validateState();
	}

	public static QuestionProposal create(Long proposerId, String proposedText) {
		return new QuestionProposal(
			null, proposerId, QuestionProposalStatus.DRAFT, proposedText,
			null, null, null, null);
	}

	public static QuestionProposal restore(
		Long id,
		Long proposerId,
		QuestionProposalStatus status,
		String proposedText,
		String decisionReason,
		Instant submittedAt,
		Instant createdAt,
		Instant updatedAt
	) {
		return new QuestionProposal(
			id, proposerId, status, proposedText, decisionReason,
			submittedAt, createdAt, updatedAt);
	}

	public QuestionProposal reviseDraft(String text) {
		if (status != QuestionProposalStatus.DRAFT) {
			throw new IllegalStateException("DRAFT 제안만 수정할 수 있습니다");
		}
		return copy(status, text, decisionReason, submittedAt);
	}

	public QuestionProposal submit(Instant at) {
		requireStatus(QuestionProposalStatus.DRAFT, "제출");
		return copy(QuestionProposalStatus.SUBMITTED, proposedText, null, requireTime(at, "submittedAt"));
	}

	public QuestionProposal startReview() {
		requireStatus(QuestionProposalStatus.SUBMITTED, "검수 시작");
		return copy(QuestionProposalStatus.UNDER_REVIEW, proposedText, null, submittedAt);
	}

	public QuestionProposal approve(String reason) {
		requireStatus(QuestionProposalStatus.UNDER_REVIEW, "승인");
		return copy(QuestionProposalStatus.APPROVED, proposedText, validateReason(reason), submittedAt);
	}

	public QuestionProposal reject(String reason) {
		requireStatus(QuestionProposalStatus.UNDER_REVIEW, "반려");
		return copy(QuestionProposalStatus.REJECTED, proposedText, requireReason(reason), submittedAt);
	}

	private QuestionProposal copy(
		QuestionProposalStatus nextStatus,
		String nextText,
		String nextReason,
		Instant nextSubmittedAt
	) {
		return new QuestionProposal(
			id, proposerId, nextStatus, nextText, nextReason,
			nextSubmittedAt, createdAt, updatedAt);
	}

	private void requireStatus(QuestionProposalStatus expected, String action) {
		if (status != expected) {
			throw new IllegalStateException(
				status + " 상태에서는 " + action + "할 수 없습니다");
		}
	}

	private void validateState() {
		if (status != QuestionProposalStatus.DRAFT && submittedAt == null) {
			throw new IllegalArgumentException("제출 이후 상태에는 submittedAt이 필요합니다");
		}
		if (status == QuestionProposalStatus.DRAFT && decisionReason != null) {
			throw new IllegalArgumentException("DRAFT에는 decisionReason을 저장할 수 없습니다");
		}
		if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt은 createdAt보다 빠를 수 없습니다");
		}
	}

	private static Long validateId(Long value, String field) {
		return value == null ? null : requirePositive(value, field);
	}

	private static long requirePositive(Long value, String field) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(field + "는 양수여야 합니다");
		}
		return value;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + "은 비어 있을 수 없습니다");
		}
		if (value.length() > TEXT_MAX_LENGTH) {
			throw new IllegalArgumentException(field + "이 너무 깁니다");
		}
		return value;
	}

	private static String validateReason(String value) {
		return value == null ? null : requireText(value, "decisionReason");
	}

	private static String requireReason(String value) {
		return requireText(value, "reason");
	}

	private static Instant requireTime(Instant value, String field) {
		return Objects.requireNonNull(value, field + "은 필수입니다");
	}

	public Long getId() { return id; }
	public Long getProposerId() { return proposerId; }
	public QuestionProposalStatus getStatus() { return status; }
	public String getProposedText() { return proposedText; }
	public String getDecisionReason() { return decisionReason; }
	public Instant getSubmittedAt() { return submittedAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
