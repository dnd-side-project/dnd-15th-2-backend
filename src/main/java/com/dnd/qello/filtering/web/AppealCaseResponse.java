package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.AppealAcceptanceReasonCode;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.AppealDecision;
import com.dnd.qello.filtering.domain.FilterTargetType;

import io.swagger.v3.oas.annotations.media.Schema;

// appeal case 응답. 작성자용과 검토자용이 같은 표현을 쓴다 — appeal_case에는
// 답변 원문이나 판정 근거가 없고, 접수자 본인과 운영자만 볼 수 있는 경로라
// 두 응답을 나눌 이유가 없다.
public record AppealCaseResponse(
	long id,
	FilterTargetType targetType,
	long targetId,
	long filterDecisionId,
	long appellantUserId,
	AppealCaseStatus status,
	Instant windowStartedAt,
	Instant expiresAt,
	// 도메인 enum에는 거절 결과인 WINDOW_ELAPSED도 있지만, 거절된 접수는 case가
	// 되지 않으므로 응답에는 절대 나타나지 않는다. 스펙이 저장할 수 없는 값을
	// 광고하지 않도록 두 값으로 좁힌다.
	@Schema(allowableValues = {"WITHIN_WINDOW", "WINDOW_UNVERIFIABLE"})
	AppealAcceptanceReasonCode acceptanceReasonCode,
	AppealDecision decision,
	Instant decidedAt,
	Long decidedByOperatorUserId,
	String restoreBlockedReasonCode,
	Instant createdAt
) {

	public static AppealCaseResponse from(AppealCase appealCase) {
		return new AppealCaseResponse(
			appealCase.id(),
			appealCase.targetType(),
			appealCase.targetId(),
			appealCase.filterDecisionId(),
			appealCase.appellantUserId(),
			appealCase.status(),
			appealCase.windowStartedAt(),
			appealCase.expiresAt(),
			appealCase.acceptanceReasonCode(),
			appealCase.decision(),
			appealCase.decidedAt(),
			appealCase.decidedByOperatorUserId(),
			appealCase.restoreBlockedReasonCode(),
			appealCase.createdAt()
		);
	}
}
