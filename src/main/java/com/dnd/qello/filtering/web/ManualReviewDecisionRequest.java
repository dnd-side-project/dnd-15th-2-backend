package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.FilterVerdict;

import jakarta.validation.constraints.NotNull;

// 검토자 결정 요청 본문. verdict는 ALLOW 또는 BLOCK만 허용한다.
public record ManualReviewDecisionRequest(
	@NotNull(message = "verdict는 필수입니다") FilterVerdict verdict
) {
}
