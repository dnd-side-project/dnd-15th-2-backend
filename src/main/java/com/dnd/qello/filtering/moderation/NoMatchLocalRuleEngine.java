package com.dnd.qello.filtering.moderation;

// LocalRuleEngine의 최소 실제 구현체(#168). 고신뢰 로컬 사전·패턴은 아직 없다 —
// 항상 no-match를 반환해 판정을 전적으로 공급자 호출과 PolicyEngine에 위임한다.
// 로컬 사전이 생기면 이 구현체를 교체한다.
public class NoMatchLocalRuleEngine implements LocalRuleEngine {

	@Override
	public LocalRuleVerdict evaluate(String normalizedContent, String localRulesetRef) {
		return LocalRuleVerdict.noMatch();
	}
}
