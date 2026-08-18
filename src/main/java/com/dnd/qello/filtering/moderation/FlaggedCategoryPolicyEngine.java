package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;

// PolicyEngine의 최소 실제 구현체(#168). 카테고리별 threshold, 언어별 차등 정책,
// categoryMappingRef 해석은 이 이슈의 범위 밖이다 — 공급자가 flagged로 표시한
// 카테고리가 하나라도 있으면 BLOCK, 없으면 ALLOW로만 판단한다. 세밀한 정책이
// 필요해지면 이 구현체를 교체한다.
public class FlaggedCategoryPolicyEngine implements PolicyEngine {

	@Override
	public FilterVerdict decide(
		ModerationProviderResult providerResult,
		FilterTargetType contentType,
		ModerationLanguage language,
		String categoryMappingRef
	) {
		return providerResult.flagged() ? FilterVerdict.BLOCK : FilterVerdict.ALLOW;
	}
}
