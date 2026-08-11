package com.dnd.qello.filtering.moderation;

// release에 귀속된 고신뢰 로컬 규칙 포트. 실제 사전·패턴 내용은 이 이슈의 범위가
// 아니다 — localRulesetRef가 가리키는 규칙 집합의 선택과 구현은 구현체의 책임이다.
public interface LocalRuleEngine {

	LocalRuleVerdict evaluate(String normalizedContent, String localRulesetRef);
}
