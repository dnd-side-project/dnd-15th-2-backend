package com.dnd.qello.safety.web.response;

import com.dnd.qello.answer.domain.Answer;

import io.swagger.v3.oas.annotations.media.Schema;

// 운영자 복원 응답. 답변 본문은 담지 않는다 — 상태 전이 확인 용도다.
public record AnswerRestoreResponse(
	@Schema(description = "복원한 답변의 식별자.") long answerId,
	@Schema(description = "복원 후 답변 상태. 공개 상태이면 PUBLISHED입니다.") String status) {
	public static AnswerRestoreResponse from(Answer answer) {
		return new AnswerRestoreResponse(answer.getId(), answer.getStatus().name());
	}
}
