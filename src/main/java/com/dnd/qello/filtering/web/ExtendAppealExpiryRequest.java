package com.dnd.qello.filtering.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 접수 기간 연장 요청 본문. 현재 만료 시각보다 늦은 값만 허용하며, 그 판단은
// AppealCase.extendExpiry가 한다 — 여기서 검증하면 "현재 만료 시각"을 알아야 해서
// 경계 계층이 도메인 상태에 의존하게 된다.
//
// reason은 #113이 추가했다. 접수 기간을 늘리는 것도 authority 변경이다(INV-APL-012).
@Schema(description = "이의제기 접수 기간을 연장하는 요청입니다.")
public record ExtendAppealExpiryRequest(
	@Schema(description = "새로 지정할 접수 만료 시각입니다.")
	@NotNull(message = "expiresAt은 필수입니다") Instant expiresAt,
	@Schema(description = "접수 기간을 연장하는 사유입니다.")
	@NotNull(message = "reason은 필수입니다") @Valid OperatorReasonRequest reason
) {
}
