package com.dnd.qello.safety.domain;

import java.time.Instant;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

public record ModerationReview(Long id, long reportId, long reviewerId, ModerationDecision decision,
	String actionType, String internalNote, Instant reviewedAt) {

	public ModerationReview {
		if (id != null && id <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, "id", "id는 양수여야 합니다");
		}
		if (reportId <= 0 || reviewerId <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, null, "ID는 양수여야 합니다");
		}
		if (decision == null || actionType == null || actionType.isBlank() || reviewedAt == null) {
			throw new SafetyException(
				SafetyErrorCode.REQUIRED_VALUE_MISSING, null, "검토 필수 값이 유효하지 않습니다");
		}
	}
}
