package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.AppealAcceptanceReasonCode;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.AppealDecision;
import com.dnd.qello.filtering.domain.FilterTargetType;

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
