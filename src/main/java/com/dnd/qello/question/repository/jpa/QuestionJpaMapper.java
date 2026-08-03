package com.dnd.qello.question.repository.jpa;

import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionAssignmentCycle;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalReview;

final class QuestionJpaMapper {

	private QuestionJpaMapper() {
	}

	static QuestionProposalJpaEntity toEntity(QuestionProposal source) {
		return new QuestionProposalJpaEntity(source.getId(), source.getProposerId(), source.getStatus(),
			source.getProposedText(), source.getDecisionReason(), source.getSubmittedAt(),
			source.getCreatedAt(), source.getUpdatedAt());
	}

	static QuestionProposal toDomain(QuestionProposalJpaEntity source) {
		return QuestionProposal.restore(source.getId(), source.getProposerId(), source.getStatus(),
			source.getProposedText(), source.getDecisionReason(), source.getSubmittedAt(),
			source.getCreatedAt(), source.getUpdatedAt());
	}

	static QuestionProposalReviewJpaEntity toEntity(QuestionProposalReview source) {
		return new QuestionProposalReviewJpaEntity(source.getId(), source.getProposalId(),
			source.getReviewerId(), source.getDecision(), source.getReason(), source.getReviewedAt());
	}

	static QuestionProposalReview toDomain(QuestionProposalReviewJpaEntity source) {
		return QuestionProposalReview.restore(source.getId(), source.getProposalId(), source.getReviewerId(),
			source.getDecision(), source.getReason(), source.getReviewedAt());
	}

	static ApprovedQuestionJpaEntity toEntity(ApprovedQuestion source) {
		return new ApprovedQuestionJpaEntity(source.getId(), source.getSourceProposalId(), source.getSourceType(),
			source.getStatus(), source.getQuestionText(), source.getAnswerFormat(), source.getActiveFrom(),
			source.getActiveUntil(), source.getApprovedAt(), source.getApprovedBy(), source.getCreatedAt());
	}

	static ApprovedQuestion toDomain(ApprovedQuestionJpaEntity source) {
		return ApprovedQuestion.restore(source.getId(), source.getSourceProposalId(), source.getSourceType(),
			source.getStatus(), source.getQuestionText(), source.getAnswerFormat(), source.getActiveFrom(),
			source.getActiveUntil(), source.getApprovedAt(), source.getApprovedBy(), source.getCreatedAt());
	}

	static QuestionAssignmentCycleJpaEntity toEntity(QuestionAssignmentCycle source) {
		return new QuestionAssignmentCycleJpaEntity(source.getId(), source.getUserId(), source.getCycleKey(),
			source.getPoolVersion(), source.getStatus(), source.getStartsAt(), source.getEndsAt(), source.getCreatedAt());
	}

	static QuestionAssignmentCycle toDomain(QuestionAssignmentCycleJpaEntity source) {
		return QuestionAssignmentCycle.restore(source.getId(), source.getUserId(), source.getCycleKey(),
			source.getPoolVersion(), source.getStatus(), source.getStartsAt(), source.getEndsAt(), source.getCreatedAt());
	}

	static QuestionAssignmentJpaEntity toEntity(QuestionAssignment source) {
		return new QuestionAssignmentJpaEntity(source.getId(), source.getCycleId(), source.getApprovedQuestionId(),
			source.getDisplayOrder(), source.getAssignedAt(), source.getFirstViewedAt(), source.getUsedAt());
	}

	static QuestionAssignment toDomain(QuestionAssignmentJpaEntity source) {
		return QuestionAssignment.restore(source.getId(), source.getCycleId(), source.getApprovedQuestionId(),
			source.getDisplayOrder(), source.getAssignedAt(), source.getFirstViewedAt(), source.getUsedAt());
	}
}
