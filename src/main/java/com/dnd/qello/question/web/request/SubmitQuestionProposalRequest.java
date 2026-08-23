package com.dnd.qello.question.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 질문 제안 제출에 필요한 사용자 입력만 표현한다. 제안자와 제출 시각은 인증과
 * 서버 Clock에서 결정하므로 요청 본문에 두지 않는다.
 */
@Schema(description = "질문 제안을 제출할 때 보내는 사용자 입력입니다.")
public record SubmitQuestionProposalRequest(
	@NotBlank(message = "proposedText는 필수입니다")
	@Size(max = 2_000, message = "proposedText는 2000자를 초과할 수 없습니다")
	@Schema(description = "제안하는 질문 문구입니다.", example = "요즘 가장 몰두하고 있는 취미는 무엇인가요?")
	String proposedText
) {
}
