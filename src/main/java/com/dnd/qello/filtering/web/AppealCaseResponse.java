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
@Schema(description = "이의제기 접수와 검토 결과를 담은 응답입니다.")
public record AppealCaseResponse(
	@Schema(description = "이의제기 식별자입니다.")
	long id,
	@Schema(description = "이의제기 대상 유형입니다.")
	FilterTargetType targetType,
	@Schema(description = "이의제기 대상 식별자입니다.")
	long targetId,
	@Schema(description = "이의제기의 근거가 된 필터 판정 식별자입니다.")
	long filterDecisionId,
	@Schema(description = "이의제기를 접수한 사용자 식별자입니다.")
	long appellantUserId,
	@Schema(description = "이의제기 상태입니다.")
	AppealCaseStatus status,
	@Schema(description = "이의제기 접수 기간의 시작 시각입니다.")
	Instant windowStartedAt,
	@Schema(description = "이의제기 접수 기간의 만료 시각입니다.")
	Instant expiresAt,
	// 도메인 enum에는 거절 결과인 WINDOW_ELAPSED도 있지만, 거절된 접수는 case가
	// 되지 않으므로 응답에는 절대 나타나지 않는다. 스펙이 저장할 수 없는 값을
	// 광고하지 않도록 두 값으로 좁힌다.
	@Schema(description = "접수 기간을 판단한 사유 코드입니다.", allowableValues = {"WITHIN_WINDOW", "WINDOW_UNVERIFIABLE"})
	AppealAcceptanceReasonCode acceptanceReasonCode,
	@Schema(description = "운영자가 내린 이의제기 결정입니다.")
	AppealDecision decision,
	@Schema(description = "이의제기 결정 시각입니다.")
	Instant decidedAt,
	@Schema(description = "결정을 내린 운영자 식별자입니다.")
	Long decidedByOperatorUserId,
	@Schema(description = "복원을 막은 사유 코드입니다. 복원되지 않은 경우에만 값이 있습니다.")
	String restoreBlockedReasonCode,
	@Schema(description = "이의제기 접수 시각입니다.")
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
