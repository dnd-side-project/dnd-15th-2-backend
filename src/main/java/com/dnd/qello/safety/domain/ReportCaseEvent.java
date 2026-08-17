package com.dnd.qello.safety.domain;

import java.time.Instant;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 사건 append-only 이력. DB 트리거가 UPDATE·DELETE를 전부 거부한다(INV-RPT-004).
public record ReportCaseEvent(Long id, long caseId, ReportCaseEventType eventType, String detail, Instant occurredAt) {

	public ReportCaseEvent {
		if (id != null && id <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, "id", "id는 양수여야 합니다");
		}
		if (caseId <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, "caseId", "caseId는 양수여야 합니다");
		}
		if (eventType == null || occurredAt == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, null, "사건 이력 필수 값이 없습니다");
		}
	}

	public static ReportCaseEvent of(long caseId, ReportCaseEventType eventType, Instant occurredAt) {
		return new ReportCaseEvent(null, caseId, eventType, null, occurredAt);
	}

	public static ReportCaseEvent restore(long id, long caseId, ReportCaseEventType eventType,
		String detail, Instant occurredAt) {
		return new ReportCaseEvent(id, caseId, eventType, detail, occurredAt);
	}
}
