package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.AppealDecision;

import jakarta.validation.constraints.NotNull;

// 검토자 이의제기 결정 요청 본문. UPHOLD_HIDDEN 또는 OVERTURN_HIDDEN만 허용한다.
public record AppealDecisionRequest(
	@NotNull(message = "decision은 필수입니다") AppealDecision decision
) {
}
