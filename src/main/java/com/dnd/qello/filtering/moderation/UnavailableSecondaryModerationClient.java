package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// SecondaryModerationClient의 fail-closed placeholder(#168). 독립 보조 판정기의
// 실제 공급자는 아직 미정이다(#106 제외 범위) — 대기나 재시도 없이 즉시
// FilteringException(SECONDARY_MODERATOR_UNAVAILABLE)을 던져, 주 판정기가
// timeout/error일 때 NicknameSyncModerationGate가 곧바로
// REJECTED(UNAVAILABLE)로 fail-closed 처리하게 한다. 실제 공급자가 정해지면
// 이 구현체를 교체한다.
public class UnavailableSecondaryModerationClient implements SecondaryModerationClient {

	@Override
	public FilterVerdict moderate(String rawContent, ModerationLanguage language) {
		throw new FilteringException(FilteringErrorCode.SECONDARY_MODERATOR_UNAVAILABLE, "secondaryProvider",
			"실제 독립 보조 판정기 공급자가 아직 결정되지 않았습니다");
	}
}
