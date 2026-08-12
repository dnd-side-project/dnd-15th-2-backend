package com.dnd.qello.filtering.moderation;

import java.util.Map;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 외부 Moderation 공급자 응답을 옮긴 벤더 중립 결과(DESIGN.md 결정 7·8). flagged는
// 공급자의 원시 신호일 뿐 서비스의 최종 판정이 아니다(INV-PIPE-003) — 최종 판정은
// PolicyEngine이 categories·categoryScores·콘텐츠 종류·언어를 해석해 결정한다.
// actualModel은 요청한 release의 modelSnapshot과 다를 수 있어 별도로 보존한다.
public record ModerationProviderResult(
	boolean flagged,
	Map<String, Boolean> categories,
	Map<String, Double> categoryScores,
	String actualModel
) {

	public ModerationProviderResult {
		categories = categories == null ? Map.of() : Map.copyOf(categories);
		categoryScores = categoryScores == null ? Map.of() : Map.copyOf(categoryScores);
		if (actualModel == null || actualModel.isBlank()) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "actualModel");
		}
	}
}
