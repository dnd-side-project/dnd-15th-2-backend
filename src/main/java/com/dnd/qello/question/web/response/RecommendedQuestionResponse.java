package com.dnd.qello.question.web.response;

import com.dnd.qello.question.domain.ApprovedQuestion;

import io.swagger.v3.oas.annotations.media.Schema;

/** 질문 전송 화면에서 고를 수 있는 질문. */
@Schema(description = "질문 전송에 사용할 수 있는 추천 질문입니다.")
public record RecommendedQuestionResponse(
		@Schema(description = "질문 전송 요청의 approvedQuestionId로 그대로 사용하는 식별자입니다.", example = "101") long approvedQuestionId,
		@Schema(description = "질문 문구입니다.") String questionText,
		@Schema(description = "허용할 답변 형식입니다. PHOTO, TEXT, BOTH 중 하나입니다.", example = "BOTH") String answerFormat) {
	public static RecommendedQuestionResponse from(ApprovedQuestion question) {
		return new RecommendedQuestionResponse(
				question.getId(),
				question.getQuestionText(),
				question.getAnswerFormat().name());
	}
}
