package com.dnd.qello.filtering.moderation;

// NicknameModerationChecker의 production gate 비활성 구현체(#168, ASSUMED —
// docs/test-plans/gh-168-TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION.md §4).
// qello.filtering.production.enabled가 false인 로컬·테스트 환경에서 빈으로
// 등록된다. moderation을 호출하지 않고 항상 통과시킨다 — 이 환경에서는 닉네임
// 중복 검사만 실제로 적용된다.
public class NoOpNicknameModerationChecker implements NicknameModerationChecker {

	@Override
	public NicknameModerationOutcome check(String nickname, ModerationLanguage language) {
		return NicknameModerationOutcome.allowed();
	}
}
