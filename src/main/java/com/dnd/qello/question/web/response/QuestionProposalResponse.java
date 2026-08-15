package com.dnd.qello.question.web.response;

import java.time.Instant;

import com.dnd.qello.question.domain.QuestionProposal;

/** 질문 제안의 현재 상태. 검토 사유는 반려된 제안에만 값이 있다. */
public record QuestionProposalResponse(
	long id,
	long proposerId,
	String status,
	String proposedText,
	String decisionReason,
	Instant submittedAt,
	Instant createdAt,
	Instant updatedAt
) {
	public static QuestionProposalResponse from(QuestionProposal proposal) {
		return new QuestionProposalResponse(
			proposal.getId(),
			proposal.getProposerId(),
			proposal.getStatus().name(),
			proposal.getProposedText(),
			proposal.getDecisionReason(),
			proposal.getSubmittedAt(),
			proposal.getCreatedAt(),
			proposal.getUpdatedAt());
	}
}
