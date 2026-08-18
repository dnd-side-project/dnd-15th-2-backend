package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.FilterVerdict;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 검토자 결정 요청 본문. verdict는 ALLOW 또는 BLOCK만 허용한다.
//
// reason은 #113이 추가했다. 검토자가 자동 판정을 뒤집은 근거가 남지 않으면
// INV-APL-012를 만족하지 못한다.
public record ManualReviewDecisionRequest(
	@NotNull(message = "verdict는 필수입니다") FilterVerdict verdict,
	@NotNull(message = "reason은 필수입니다") @Valid OperatorReasonRequest reason
) {
}
