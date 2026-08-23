package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.FilterTargetType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 이의제기 접수 요청 본문.
//
// targetType을 본문에 두는 이유: 현재 접수 가능한 대상은 ANSWER뿐이지만, 서버가
// 유형을 암묵적으로 가정하면 나중에 대상이 늘어날 때 기존 클라이언트의 요청이
// 조용히 다른 의미가 된다. 명시하게 하고 ANSWER 외에는 거절한다.
//
// 두 식별자는 primitive라 본문에서 빠지면 0으로 바인딩되고 @Positive가 400으로
// 거절한다. 즉 실제 계약은 "필수"이므로, 스펙에도 required로 명시해 누락 시
// 동작을 문서와 일치시킨다.
@Schema(description = "이의제기 접수 요청입니다.")
public record FileAppealRequest(
	@Schema(description = "이의제기를 접수할 대상 유형입니다. 현재 ANSWER만 지원합니다.")
	@NotNull(message = "targetType은 필수입니다") FilterTargetType targetType,
	@Schema(description = "이의제기를 접수할 답변 식별자입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
	@Positive(message = "targetId는 양수여야 합니다") long targetId,
	@Schema(description = "이의제기의 근거가 된 필터 판정 식별자입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
	@Positive(message = "filterDecisionId는 양수여야 합니다") long filterDecisionId
) {
}
