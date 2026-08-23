package com.dnd.qello.question.web.response;

import java.time.Instant;

import com.dnd.qello.question.domain.ApprovedQuestion;

import io.swagger.v3.oas.annotations.media.Schema;

/** 제안이 승인되어 만들어진 승인 질문. */
@Schema(description = "승인된 질문의 상태와 활성 기간을 담는 응답입니다.")
public record ApprovedQuestionResponse(
	@Schema(description = "승인 질문 식별자입니다.")
	long id,
	@Schema(description = "이 승인 질문을 만든 질문 제안 식별자입니다.")
	Long sourceProposalId,
	@Schema(description = "질문 출처입니다. 사용자 제안 승인 결과에서는 USER_PROPOSAL입니다.")
	String sourceType,
	@Schema(description = "승인 질문 상태입니다. 승인 결과에서는 ACTIVE입니다.")
	String status,
	@Schema(description = "승인된 질문 문구입니다.")
	String questionText,
	@Schema(description = "허용할 답변 형식입니다.")
	String answerFormat,
	@Schema(description = "질문을 배정할 수 있게 되는 시각입니다.")
	Instant activeFrom,
	@Schema(description = "질문 배정을 중단하는 시각입니다. null이면 종료 시각이 없습니다.")
	Instant activeUntil,
	@Schema(description = "질문을 승인한 시각입니다.")
	Instant approvedAt,
	@Schema(description = "승인한 운영자 계정 식별자입니다.")
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
