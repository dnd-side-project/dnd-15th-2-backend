package com.dnd.qello.question.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 제안을 반려할 때 필요한 운영자 입력. */
@Schema(description = "질문 제안을 반려할 때 보내는 운영자 입력입니다.")
public record RejectQuestionProposalRequest(
	@NotBlank(message = "reason은 필수입니다")
	@Size(max = 2_000, message = "reason은 2000자를 초과할 수 없습니다")
	@Schema(description = "반려 사유입니다. 제안자에게 그대로 노출될 수 있습니다.", example = "이미 승인된 질문과 의미가 중복됩니다")
	String reason
) {
}
