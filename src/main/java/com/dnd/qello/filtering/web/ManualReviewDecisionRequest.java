package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.FilterVerdict;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 검토자 결정 요청 본문. verdict는 ALLOW 또는 BLOCK만 허용한다.
//
// reason은 #113이 추가했다. 검토자가 자동 판정을 뒤집은 근거가 남지 않으면
// INV-APL-012를 만족하지 못한다.
@Schema(description = "수동 검토 결정을 적용하는 요청입니다.")
public record ManualReviewDecisionRequest(
	@Schema(description = "검토 결과로 적용할 판정입니다.")
	@NotNull(message = "verdict는 필수입니다") FilterVerdict verdict,
	@Schema(description = "검토 결정 사유입니다.")
	@NotNull(message = "reason은 필수입니다") @Valid OperatorReasonRequest reason
) {
}
