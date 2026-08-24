package com.dnd.qello.question.web.request;

import java.time.Instant;

import com.dnd.qello.question.domain.AnswerFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 제안을 승인해 승인 질문 풀에 올릴 때 필요한 운영자 입력. */
@Schema(description = "질문 제안을 승인할 때 보내는 운영자 입력입니다.")
public record ApproveQuestionProposalRequest(
	@NotNull(message = "answerFormat은 필수입니다")
	@Schema(description = "승인 질문에 허용할 답변 형식입니다.")
	AnswerFormat answerFormat,

	@NotNull(message = "activeFrom은 필수입니다")
	@Schema(description = "질문을 배정할 수 있게 되는 시각입니다. 이 시각을 포함합니다.")
	Instant activeFrom,

	@Schema(description = "질문 배정을 중단할 시각입니다. 이 시각은 포함하지 않으며, 비우면 무기한 활성입니다.")
	Instant activeUntil
) {
}
