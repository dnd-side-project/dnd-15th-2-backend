package com.dnd.qello.safety.web.response;

import com.dnd.qello.answer.domain.Answer;

// 운영자 복원 응답. 답변 본문은 담지 않는다 — 상태 전이 확인 용도다.
public record AnswerRestoreResponse(long answerId, String status) {
	public static AnswerRestoreResponse from(Answer answer) {
		return new AnswerRestoreResponse(answer.getId(), answer.getStatus().name());
	}
}
