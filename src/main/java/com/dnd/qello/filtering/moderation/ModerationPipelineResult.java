package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterVerdict;

// pipeline 실행 원시 결과. ruleVerdict·providerResult·verdict를 각각 독립적으로
// 노출해 규칙 적중, 공급자 원시 응답과 최종 정책 결정을 분리 관측할 수 있게
// 한다(INV-PIPE-005). 로컬 규칙이 BLOCK을 확정한 경우 providerResult와
// actualModel은 null이다 — 응답이 비어 있다는 뜻이 아니라 공급자를 호출하지
// 않았다는 뜻이다.
public record ModerationPipelineResult(
	FilterVerdict verdict,
	LocalRuleVerdict ruleVerdict,
	ModerationProviderResult providerResult,
	long requestedReleaseId,
	String actualModel
) {

	public boolean shortCircuitedByRule() {
		return providerResult == null;
	}
}
