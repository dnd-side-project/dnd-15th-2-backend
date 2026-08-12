package com.dnd.qello.filtering.moderation;

// 외부 관리형 Moderation API(예: OpenAI)를 감싸는 벤더 중립 포트(DESIGN.md 결정 7).
// timeout/error는 구현체가 FilteringException(MODERATION_PROVIDER_UNAVAILABLE)으로
// 던진다 — 어떤 구현도 판정 불가를 ALLOW/BLOCK으로 바꿔 반환하지 않는다.
public interface ModerationProviderClient {

	ModerationProviderResult moderate(String normalizedContent, String modelSnapshot);
}
