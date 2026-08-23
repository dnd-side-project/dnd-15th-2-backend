package com.dnd.qello.feed.web.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** 답변 읽음 처리 공개 모델. 질문자·수신자 두 읽음 경로가 함께 쓴다. */
public record AnswersReadResponse(
	@Schema(description = "이번 호출로 갱신된 답변 열람 시각") Instant answersReadAt
) { }
