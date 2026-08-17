package com.dnd.qello.filtering.web;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

// 접수 기간 연장 요청 본문. 현재 만료 시각보다 늦은 값만 허용하며, 그 판단은
// AppealCase.extendExpiry가 한다 — 여기서 검증하면 "현재 만료 시각"을 알아야 해서
// 경계 계층이 도메인 상태에 의존하게 된다.
public record ExtendAppealExpiryRequest(
	@NotNull(message = "expiresAt은 필수입니다") Instant expiresAt
) {
}
