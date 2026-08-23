package com.dnd.qello.feed.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공감 처리 공개 모델. 질문글·답변 공감 4개 경로가 함께 쓴다. reactionCount는
 * 서버가 반영 직후 다시 센 값이다 — 클라이언트가 직접 증감하며 생기는 어긋남을 없앤다.
 */
public record ReactionResponse(
	@Schema(description = "이 호출 뒤 내가 공감을 남긴 상태인지 여부") boolean reacted,
	@Schema(description = "반영 직후 서버가 다시 센 공감 수") long reactionCount
) { }
