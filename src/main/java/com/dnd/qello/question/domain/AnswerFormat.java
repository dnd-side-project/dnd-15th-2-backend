package com.dnd.qello.question.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "질문에 허용할 답변 형식입니다.")
public enum AnswerFormat {
	PHOTO,
	TEXT,
	BOTH
}
