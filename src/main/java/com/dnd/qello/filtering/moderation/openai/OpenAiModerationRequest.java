package com.dnd.qello.filtering.moderation.openai;

// OpenAI Moderation API 요청(DESIGN.md 결정 8·9). model은 release에 고정된
// modelSnapshot을 그대로 전달한다 — "latest" 같은 alias를 조립하지 않는다
// (FilterRelease가 이미 alias 값 자체를 거절한다).
public record OpenAiModerationRequest(String input, String model) {
}
