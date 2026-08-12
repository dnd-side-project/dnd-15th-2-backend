package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 고신뢰 로컬 규칙의 판정 결과. blocked=true면 이 결과만으로 pipeline이 공급자
// 호출 없이 최종 BLOCK을 확정한다(단락 경로). 실제 규칙·사전 내용은 이 이슈의
// 범위가 아니다 — 규칙 집합의 선택과 구현은 LocalRuleEngine 구현체가 맡는다.
public record LocalRuleVerdict(boolean blocked, String ruleId) {

	public LocalRuleVerdict {
		if (blocked && (ruleId == null || ruleId.isBlank())) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "ruleId");
		}
	}

	public static LocalRuleVerdict noMatch() {
		return new LocalRuleVerdict(false, null);
	}

	public static LocalRuleVerdict block(String ruleId) {
		return new LocalRuleVerdict(true, ruleId);
	}
}
