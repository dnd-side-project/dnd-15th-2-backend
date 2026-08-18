package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 작성자 이의제기(#103 정체성 + #112 접수 기간·결정). "동일 대상·HIDDEN decision에
// 활성 appeal 하나"라는 유일성 불변식(INV-APL-002)은 여전히 targetType+targetId+
// filterDecisionId 조합이 표현하고, 실제 강제는 DB unique index가 한다.
//
// expiresAt은 접수 시점에 고정한다. extendExpiry로 늘릴 수만 있고 줄이는 경로는
// 이 객체에 없다(INV-APL-008, INV-APL-009).
public record AppealCase(
	Long id, FilterTargetType targetType, long targetId, long filterDecisionId, long appellantUserId,
	AppealCaseStatus status, Instant windowStartedAt, Instant expiresAt,
	AppealAcceptanceReasonCode acceptanceReasonCode, AppealDecision decision, Instant decidedAt,
	Long decidedByOperatorUserId, String restoreBlockedReasonCode, Instant createdAt
) {

	private static final int RESTORE_BLOCKED_REASON_MAX_LENGTH = 30;

	public AppealCase {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (targetType == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "targetType");
		}
		if (targetId <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "targetId", "targetId는 양수여야 합니다");
		}
		if (filterDecisionId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterDecisionId", "filterDecisionId는 양수여야 합니다");
		}
		if (appellantUserId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "appellantUserId", "appellantUserId는 양수여야 합니다");
		}
		if (status == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "status");
		}
		if (windowStartedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "windowStartedAt");
		}
		if (expiresAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "expiresAt");
		}
		// 어떤 경로로 만들어진 case든 접수 기간이 6개월보다 짧을 수 없다.
		// AppealWindow 하한과 DB CHECK 사이의 중간 방어선이다.
		if (expiresAt.isBefore(windowStartedAt.plus(AppealWindow.GLOBAL_ACCEPTANCE_WINDOW))) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "expiresAt",
				"만료 시각은 기산점으로부터 6개월보다 이를 수 없습니다");
		}
		if (acceptanceReasonCode == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "acceptanceReasonCode");
		}
		// 거절된 접수는 case가 되지 않는다. WINDOW_ELAPSED를 들고 있는 행이
		// 존재한다면 그것은 거절해야 할 접수가 저장됐다는 뜻이다.
		if (acceptanceReasonCode == AppealAcceptanceReasonCode.WINDOW_ELAPSED) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "acceptanceReasonCode",
				"거절된 접수는 case로 저장할 수 없습니다");
		}
		if ((status == AppealCaseStatus.RESOLVED)
			!= (decision != null && decidedAt != null && decidedByOperatorUserId != null)) {
			throw new FilteringException(FilteringErrorCode.INVALID_APPEAL_CASE_STATUS, "decision",
				"RESOLVED 상태와 decision/decidedAt/decidedByOperatorUserId는 함께 있어야 합니다");
		}
		if (decidedByOperatorUserId != null && decidedByOperatorUserId <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "decidedByOperatorUserId",
				"decidedByOperatorUserId는 양수여야 합니다");
		}
		if (restoreBlockedReasonCode != null) {
			// UPHOLD_HIDDEN은 애초에 복원을 시도하지 않으므로 차단 사유가 존재할 수 없다.
			if (decision != AppealDecision.OVERTURN_HIDDEN) {
				throw new FilteringException(FilteringErrorCode.INVALID_APPEAL_CASE_STATUS, "restoreBlockedReasonCode",
					"복원 차단 사유는 OVERTURN_HIDDEN 결정에만 붙일 수 있습니다");
			}
			if (restoreBlockedReasonCode.isBlank()
				|| restoreBlockedReasonCode.length() > RESTORE_BLOCKED_REASON_MAX_LENGTH) {
				throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "restoreBlockedReasonCode",
					"restoreBlockedReasonCode 값이 유효하지 않습니다");
			}
		}
		if (createdAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "createdAt");
		}
	}

	// 접수. 만료 시각은 여기서 한 번 고정되고 이후 extendExpiry로만 바뀐다.
	// acceptance가 거절이면 case를 만들지 않는다 — 호출 서비스가 그 전에 거절해야 한다.
	public static AppealCase file(FilterTargetType targetType, long targetId, long filterDecisionId,
		long appellantUserId, AppealAcceptance acceptance, AppealWindow window, Instant now) {
		if (acceptance == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "acceptance");
		}
		if (window == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "window");
		}
		if (!acceptance.accepted()) {
			throw new FilteringException(FilteringErrorCode.APPEAL_WINDOW_ELAPSED, "acceptance",
				"접수 기간이 지난 이의제기입니다");
		}
		Instant windowStartedAt = acceptance.effectiveWindowStartedAt();
		return new AppealCase(null, targetType, targetId, filterDecisionId, appellantUserId, AppealCaseStatus.OPEN,
			windowStartedAt, window.expiresAt(windowStartedAt), acceptance.reasonCode(), null, null, null, null, now);
	}

	public static AppealCase restore(Long id, FilterTargetType targetType, long targetId, long filterDecisionId,
		long appellantUserId, AppealCaseStatus status, Instant windowStartedAt, Instant expiresAt,
		AppealAcceptanceReasonCode acceptanceReasonCode, AppealDecision decision, Instant decidedAt,
		Long decidedByOperatorUserId, String restoreBlockedReasonCode, Instant createdAt) {
		return new AppealCase(id, targetType, targetId, filterDecisionId, appellantUserId, status, windowStartedAt,
			expiresAt, acceptanceReasonCode, decision, decidedAt, decidedByOperatorUserId, restoreBlockedReasonCode,
			createdAt);
	}

	// 검토자 결정을 적용한다. case는 한 번만 닫힌다.
	//
	// restoreBlockedReasonCode는 OVERTURN_HIDDEN인데도 다른 공개 금지 사유가 남아
	// 복원 콜백을 내보내지 않은 경우에만 채운다. 이 객체는 그 사유를 판단하지
	// 않고 호출 서비스가 재검증한 결과를 기록만 한다.
	public AppealCase decide(AppealDecision newDecision, long operatorUserId, Instant now,
		String newRestoreBlockedReasonCode) {
		if (status == AppealCaseStatus.RESOLVED) {
			throw new FilteringException(FilteringErrorCode.INVALID_APPEAL_CASE_STATUS, "status",
				"이미 종료된 appeal case입니다");
		}
		if (newDecision == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "decision");
		}
		if (operatorUserId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "operatorUserId", "operatorUserId는 양수여야 합니다");
		}
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		return new AppealCase(id, targetType, targetId, filterDecisionId, appellantUserId, AppealCaseStatus.RESOLVED,
			windowStartedAt, expiresAt, acceptanceReasonCode, newDecision, now, operatorUserId,
			newRestoreBlockedReasonCode, createdAt);
	}

	// 법률·정책상 접수 기간을 연장한다. 현재 만료 시각보다 이르거나 같은 값은
	// 거절한다 — 이 객체에 기간을 줄이는 경로를 두지 않는다(INV-APL-008, INV-APL-009).
	public AppealCase extendExpiry(Instant newExpiresAt) {
		if (newExpiresAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "newExpiresAt");
		}
		if (!newExpiresAt.isAfter(expiresAt)) {
			throw new FilteringException(FilteringErrorCode.APPEAL_EXPIRY_NOT_EXTENDABLE, "newExpiresAt",
				"만료 시각은 현재보다 늦은 시각으로만 바꿀 수 있습니다");
		}
		return new AppealCase(id, targetType, targetId, filterDecisionId, appellantUserId, status, windowStartedAt,
			newExpiresAt, acceptanceReasonCode, decision, decidedAt, decidedByOperatorUserId, restoreBlockedReasonCode,
			createdAt);
	}

	// 복원 콜백을 실제로 발행해야 하는 결정인지. OVERTURN_HIDDEN이면서 다른 공개
	// 금지 사유가 없을 때만 참이다.
	public boolean requiresRestoreCallback() {
		return decision == AppealDecision.OVERTURN_HIDDEN && restoreBlockedReasonCode == null;
	}
}
