package com.dnd.qello.answer.web.response;

import java.time.Instant;

import com.dnd.qello.answer.domain.Answer;

/** 답변 제출 접수 결과. 공개 좌표, 내부 사용자 식별자와 본문은 포함하지 않는다. */
public record AnswerSubmissionResponse(
	long answerId,
	String submissionStatus,
	Instant submittedAt
) {
	public static AnswerSubmissionResponse from(Answer answer) {
		return new AnswerSubmissionResponse(answer.getId(), answer.getStatus().name(), answer.getSubmittedAt());
	}
}
