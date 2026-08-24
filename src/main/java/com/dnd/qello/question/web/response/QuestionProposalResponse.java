package com.dnd.qello.question.web.response;

import java.time.Instant;

import com.dnd.qello.question.domain.QuestionProposal;

import io.swagger.v3.oas.annotations.media.Schema;

/** 질문 제안의 현재 상태. 검토 사유는 반려된 제안에만 값이 있다. */
@Schema(description = "질문 제안의 상태와 제출 정보를 담는 응답입니다.")
public record QuestionProposalResponse(
	@Schema(description = "질문 제안 식별자입니다.")
	long id,
	@Schema(description = "제안한 사용자 계정 식별자입니다.")
	long proposerId,
	@Schema(description = "질문 제안 상태입니다.")
	String status,
	@Schema(description = "제안한 질문 문구입니다.")
	String proposedText,
	@Schema(description = "반려 사유입니다. 반려된 제안에서만 값이 있을 수 있습니다.")
	String decisionReason,
	@Schema(description = "질문을 제출한 시각입니다.")
	Instant submittedAt,
	@Schema(description = "질문 제안을 만든 시각입니다.")
	Instant createdAt,
	@Schema(description = "질문 제안이 마지막으로 변경된 시각입니다.")
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
