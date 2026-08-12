package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterVerdict;

// 닉네임 동기 경로 전용 독립 보조 판정기 포트(GitHub #106). 주 판정기
// (ModerationPipelineService)가 timeout/error일 때만 호출된다(INV-NICK-003).
//
// ModerationProviderClient와 달리 원시 공급자 응답이 아니라 이미 정책까지
// 결합된 최종 FilterVerdict를 직접 반환한다 — 보조 판정기는 이 이슈에서
// 별도 정책 해석 단계를 두지 않는다(TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER §2
// 설계 가정 2). 정상 완료 시 null을 반환하지 않으며, timeout/error는
// FilteringException(SECONDARY_MODERATOR_UNAVAILABLE)으로만 알린다 — 어떤
// 구현도 판정 불가를 ALLOW/BLOCK으로 바꿔 반환하지 않는다.
//
// 원문(정규화 전) 텍스트를 받는다 — 정규화는 release에 귀속된 주 판정기
// pipeline 내부에서만 일어나며(TextNormalizer), "독립" 보조 판정기는 주
// 판정기의 정규화 규칙에 결합되지 않는다. 정규화가 필요하면 구현체가 자체
// 규칙으로 수행한다.
//
// 실제 공급자 구현체는 이 이슈 범위 밖이다(production 차단 게이트) — 주
// 판정기와의 공통 장애 영역이 확정되지 않았다.
public interface SecondaryModerationClient {

	FilterVerdict moderate(String rawContent, ModerationLanguage language);
}
