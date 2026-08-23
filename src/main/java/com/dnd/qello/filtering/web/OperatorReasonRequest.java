package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.OperatorReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 운영자 행위의 근거. authority를 바꾸는 모든 필터링 endpoint가 이 형태를 요구한다.
//
// 서버가 기본값을 채우지 않는다 — 채우면 감사 이력에 "왜"가 없는 것과 같아
// INV-APL-012를 만족하지 못한다.
@Schema(description = "운영자 행위의 근거를 담은 요청입니다.")
public record OperatorReasonRequest(
	@Schema(description = "집계에 사용할 분류 코드입니다.", example = "POLICY_VIOLATION")
	@NotBlank(message = "reasonCode는 필수입니다")
	@Size(max = 30, message = "reasonCode는 30자를 넘을 수 없습니다") String reasonCode,

	@Schema(description = "운영자가 직접 입력한 결정 근거입니다.")
	@NotBlank(message = "reasonText는 필수입니다")
	@Size(max = 500, message = "reasonText는 500자를 넘을 수 없습니다") String reasonText
) {

	public OperatorReason toDomain() {
		return new OperatorReason(reasonCode, reasonText);
	}
}
