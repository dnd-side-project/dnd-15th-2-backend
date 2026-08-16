package com.dnd.qello.question.web.response;

import java.time.Instant;

import com.dnd.qello.question.domain.ApprovedQuestion;

/** 제안이 승인되어 만들어진 승인 질문. */
public record ApprovedQuestionResponse(
	long id,
	Long sourceProposalId,
	String sourceType,
	String status,
	String questionText,
	String answerFormat,
	Instant activeFrom,
	Instant activeUntil,
	Instant approvedAt,
	Long approvedBy
) {
	public static ApprovedQuestionResponse from(ApprovedQuestion question) {
		return new ApprovedQuestionResponse(
			question.getId(),
			question.getSourceProposalId(),
			question.getSourceType().name(),
			question.getStatus().name(),
			question.getQuestionText(),
			question.getAnswerFormat().name(),
			question.getActiveFrom(),
			question.getActiveUntil(),
			question.getApprovedAt(),
			question.getApprovedBy());
	}
}
