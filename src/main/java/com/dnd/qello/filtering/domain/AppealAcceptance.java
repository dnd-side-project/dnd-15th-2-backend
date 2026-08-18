package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// AppealWindow.evaluate의 결과. 접수 허용 여부, 그 근거와 실제로 적용할 기산점을
// 함께 돌려준다.
//
// effectiveWindowStartedAt이 별도 필드인 이유: fallback 경로에서는 원래 기산점을
// 쓸 수 없어 접수 시각으로 대체하는데, 그 대체값이 곧 만료 계산의 기준이 되므로
// 호출자가 다시 추론하지 않고 그대로 저장할 수 있어야 한다.
public record AppealAcceptance(
	boolean accepted, AppealAcceptanceReasonCode reasonCode, Instant effectiveWindowStartedAt
) {

	public AppealAcceptance {
		if (reasonCode == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "reasonCode");
		}
		if (effectiveWindowStartedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "effectiveWindowStartedAt");
		}
		if (accepted == (reasonCode == AppealAcceptanceReasonCode.WINDOW_ELAPSED)) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "reasonCode",
				"WINDOW_ELAPSED만 거절이고 나머지는 접수여야 합니다");
		}
	}
}
