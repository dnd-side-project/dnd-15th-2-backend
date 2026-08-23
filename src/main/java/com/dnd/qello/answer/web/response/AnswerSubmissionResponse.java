package com.dnd.qello.answer.web.response;

import java.time.Instant;

import com.dnd.qello.answer.domain.Answer;

import io.swagger.v3.oas.annotations.media.Schema;

/** 답변 제출 접수 결과. 공개 좌표, 내부 사용자 식별자와 본문은 포함하지 않는다. */
public record AnswerSubmissionResponse(
	@Schema(description = "접수된 답변의 식별자") long answerId,
	@Schema(description = "접수 직후의 답변 상태. 공개 여부가 아니라 접수 결과입니다") String submissionStatus,
	@Schema(description = "답변을 접수한 시각") Instant submittedAt
) {
	public static AnswerSubmissionResponse from(Answer answer) {
		return new AnswerSubmissionResponse(answer.getId(), answer.getStatus().name(), answer.getSubmittedAt());
	}
}
