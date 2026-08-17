package com.dnd.qello.filtering.web;

import com.dnd.qello.filtering.domain.FilterTargetType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 이의제기 접수 요청 본문.
//
// targetType을 본문에 두는 이유: 현재 접수 가능한 대상은 ANSWER뿐이지만, 서버가
// 유형을 암묵적으로 가정하면 나중에 대상이 늘어날 때 기존 클라이언트의 요청이
// 조용히 다른 의미가 된다. 명시하게 하고 ANSWER 외에는 거절한다.
public record FileAppealRequest(
	@NotNull(message = "targetType은 필수입니다") FilterTargetType targetType,
	@Positive(message = "targetId는 양수여야 합니다") long targetId,
	@Positive(message = "filterDecisionId는 양수여야 합니다") long filterDecisionId
) {
}
