package com.dnd.qello.filtering.moderation;

// account 도메인이 의존하는 안정적인 진입점(#168). NicknameSyncModerationGate(#106)를
// 직접 노출하지 않고 이 포트로 감싸, production gate(#113)가 꺼져 있을 때도
// 호출자가 조건 분기 없이 항상 같은 타입을 주입받게 한다. 실제 게이트로
// 위임하는 구현체와, gate off일 때 중복 검사만 통과시키는 no-op 구현체 중
// production.enabled 설정에 따라 정확히 하나만 빈으로 등록된다.
public interface NicknameModerationChecker {

	NicknameModerationOutcome check(String nickname, ModerationLanguage language);
}
