package com.dnd.qello.safety.domain;

import java.time.Instant;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

public record Report(Long id, long reporterId, Long targetUserId, Long directionPostId, Long answerId,
	String reasonCode, String detail, ReportStatus status, Instant createdAt, Instant resolvedAt,
	Long caseId, String subReasonCode) {

	private static final int REASON_CODE_MAX_LENGTH = 50;
	private static final int SUB_REASON_CODE_MAX_LENGTH = 50;

	public Report {
		if (id != null && id <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, "id", "id는 양수여야 합니다");
		}
		requirePositive(reporterId, "reporterId");
		if ((targetUserId == null ? 0 : 1) + (directionPostId == null ? 0 : 1)
			+ (answerId == null ? 0 : 1) != 1) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REPORT_TARGET, null, "신고 대상은 정확히 하나여야 합니다");
		}
		requirePositiveOrNull(targetUserId, "targetUserId");
		requirePositiveOrNull(directionPostId, "directionPostId");
		requirePositiveOrNull(answerId, "answerId");
		if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > REASON_CODE_MAX_LENGTH) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REASON_CODE, "reasonCode", "reasonCode가 유효하지 않습니다");
		}
		if (status == null || createdAt == null) {
			throw new SafetyException(
				SafetyErrorCode.REQUIRED_VALUE_MISSING, null, "신고 상태와 생성 시각은 필수입니다");
		}
		if (resolvedAt != null && resolvedAt.isBefore(createdAt)) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_TIME_ORDER, "resolvedAt", "resolvedAt은 createdAt보다 빠를 수 없습니다");
		}
		requirePositiveOrNull(caseId, "caseId");
		if (subReasonCode != null
			&& (subReasonCode.isBlank() || subReasonCode.length() > SUB_REASON_CODE_MAX_LENGTH)) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REASON_CODE, "subReasonCode", "subReasonCode가 유효하지 않습니다");
		}
	}

	public static Report forUser(long reporterId, long targetUserId, String reasonCode, String detail, Instant at) {
		return forUser(reporterId, targetUserId, reasonCode, null, detail, at);
	}

	public static Report forUser(long reporterId, long targetUserId, String reasonCode, String subReasonCode,
		String detail, Instant at) {
		return new Report(null, reporterId, targetUserId, null, null, reasonCode, detail,
			ReportStatus.RECEIVED, at, null, null, subReasonCode);
	}

	public static Report forPost(long reporterId, long directionPostId, String reasonCode, String detail, Instant at) {
		return forPost(reporterId, directionPostId, reasonCode, null, detail, at);
	}

	public static Report forPost(long reporterId, long directionPostId, String reasonCode, String subReasonCode,
		String detail, Instant at) {
		return new Report(null, reporterId, null, directionPostId, null, reasonCode, detail,
			ReportStatus.RECEIVED, at, null, null, subReasonCode);
	}

	public static Report forAnswer(long reporterId, long answerId, String reasonCode, String detail, Instant at) {
		return forAnswer(reporterId, answerId, reasonCode, null, detail, at);
	}

	public static Report forAnswer(long reporterId, long answerId, String reasonCode, String subReasonCode,
		String detail, Instant at) {
		return new Report(null, reporterId, null, null, answerId, reasonCode, detail,
			ReportStatus.RECEIVED, at, null, null, subReasonCode);
	}

	public Report startReview() { return transition(ReportStatus.UNDER_REVIEW, null); }

	/**
	 * 종결이 아니다 — resolvedAt을 설정하지 않는다. 현재 uq_open_report_* 세 인덱스의
	 * 열린 상태 술어에 MORE_INFO_REQUIRED가 포함돼 있어야 이 전이 이후에도 같은
	 * 신고자의 중복 신고 차단이 유지된다(INV-RPT-002).
	 */
	public Report requestMoreInfo(Instant at) {
		if (status != ReportStatus.RECEIVED && status != ReportStatus.UNDER_REVIEW) {
			throw new SafetyException(SafetyErrorCode.INVALID_REPORT_STATUS,
				"status", "추가 정보 요청은 RECEIVED 또는 UNDER_REVIEW 상태에서만 가능합니다");
		}
		return transition(ReportStatus.MORE_INFO_REQUIRED, null);
	}

	public Report resolve(ReportStatus nextStatus, Instant at) {
		if (nextStatus != ReportStatus.ACTIONED && nextStatus != ReportStatus.NO_VIOLATION) {
			throw new SafetyException(SafetyErrorCode.INVALID_REPORT_STATUS, "status", "종결 상태가 아닙니다");
		}
		return transition(nextStatus, requireValue(at, "resolvedAt"));
	}

	/** 같은 사건으로 재시도돼도 멱등하다. 다른 사건으로의 재연결만 거절한다. */
	public Report attachToCase(long newCaseId) {
		requirePositive(newCaseId, "caseId");
		if (caseId != null && caseId != newCaseId) {
			throw new SafetyException(
				SafetyErrorCode.REPORT_ALREADY_LINKED_TO_CASE, "caseId", "이미 다른 사건에 연결된 신고입니다");
		}
		if (caseId != null) {
			return this;
		}
		return new Report(id, reporterId, targetUserId, directionPostId, answerId, reasonCode, detail,
			status, createdAt, resolvedAt, newCaseId, subReasonCode);
	}

	private Report transition(ReportStatus nextStatus, Instant nextResolvedAt) {
		return new Report(id, reporterId, targetUserId, directionPostId, answerId, reasonCode,
			detail, nextStatus, createdAt, nextResolvedAt, caseId, subReasonCode);
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static void requirePositive(long value, String field) {
		if (value <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
	}

	private static void requirePositiveOrNull(Long value, String field) {
		if (value != null && value <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
	}
}
