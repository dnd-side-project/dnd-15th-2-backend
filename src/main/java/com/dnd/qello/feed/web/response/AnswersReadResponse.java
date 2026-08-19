package com.dnd.qello.feed.web.response;

import java.time.Instant;

/** 답변 읽음 처리 공개 모델. 질문자·수신자 두 읽음 경로가 함께 쓴다. */
public record AnswersReadResponse(Instant answersReadAt) { }
