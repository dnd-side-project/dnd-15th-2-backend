package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.AppealDecision;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 검토자 이의제기 결정 요청 본문. UPHOLD_HIDDEN 또는 OVERTURN_HIDDEN만 허용한다.
//
// reason은 #113이 추가했다(INV-APL-012).
@Schema(description = "운영자 이의제기 결정 요청입니다.")
public record AppealDecisionRequest(
	@Schema(description = "이의제기를 유지하거나 취소하는 결정입니다.")
	@NotNull(message = "decision은 필수입니다") AppealDecision decision,
	@Schema(description = "결정 사유입니다.")
	@NotNull(message = "reason은 필수입니다") @Valid OperatorReasonRequest reason
) {
}
