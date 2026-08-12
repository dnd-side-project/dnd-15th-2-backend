package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;

// 공급자 원시 응답을 서비스 정책으로 해석해 최종 판정을 만드는 포트. 공급자의
// flagged 값을 그대로 반환하지 않는다(INV-PIPE-003) — categories·categoryScores와
// 콘텐츠 종류·언어·categoryMappingRef를 함께 해석해야 한다. 실제 category
// mapping·threshold 내용은 이 이슈의 범위가 아니다.
public interface PolicyEngine {

	FilterVerdict decide(
		ModerationProviderResult providerResult,
		FilterTargetType contentType,
		ModerationLanguage language,
		String categoryMappingRef
	);
}
