package com.dnd.qello.question.web.request;

import java.time.Instant;

import com.dnd.qello.question.domain.AnswerFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 제안을 승인해 승인 질문 풀에 올릴 때 필요한 운영자 입력. */
public record ApproveQuestionProposalRequest(
	@NotNull(message = "answerFormat은 필수입니다")
	@Schema(description = "이 질문에 허용할 답변 형식")
	AnswerFormat answerFormat,

	@NotNull(message = "activeFrom은 필수입니다")
	@Schema(description = "질문 배정에 사용할 수 있게 되는 시각(포함)")
	Instant activeFrom,

	@Schema(description = "질문 배정을 중단할 시각(미포함). 비우면 무기한 활성")
	Instant activeUntil
) {
}
