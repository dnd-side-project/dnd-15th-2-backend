package com.dnd.qello.safety.domain;

import java.time.Instant;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

public record Report(Long id, long reporterId, Long targetUserId, Long directionPostId, Long answerId,
	String reasonCode, String detail, ReportStatus status, Instant createdAt, Instant resolvedAt) {

	private static final int REASON_CODE_MAX_LENGTH = 50;

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
	}

	public static Report forUser(long reporterId, long targetUserId, String reasonCode, String detail, Instant at) {
		return new Report(null, reporterId, targetUserId, null, null, reasonCode, detail,
			ReportStatus.RECEIVED, at, null);
	}

	public static Report forPost(long reporterId, long directionPostId, String reasonCode, String detail, Instant at) {
		return new Report(null, reporterId, null, directionPostId, null, reasonCode, detail,
			ReportStatus.RECEIVED, at, null);
	}

	public static Report forAnswer(long reporterId, long answerId, String reasonCode, String detail, Instant at) {
		return new Report(null, reporterId, null, null, answerId, reasonCode, detail,
			ReportStatus.RECEIVED, at, null);
	}

	public Report startReview() { return transition(ReportStatus.UNDER_REVIEW, null); }

	public Report resolve(ReportStatus nextStatus, Instant at) {
		if (nextStatus != ReportStatus.ACTIONED && nextStatus != ReportStatus.NO_VIOLATION
			&& nextStatus != ReportStatus.MORE_INFO_REQUIRED) {
			throw new SafetyException(SafetyErrorCode.INVALID_REPORT_STATUS, "status", "종결 상태가 아닙니다");
		}
		return transition(nextStatus, requireValue(at, "resolvedAt"));
	}

	private Report transition(ReportStatus nextStatus, Instant nextResolvedAt) {
		return new Report(id, reporterId, targetUserId, directionPostId, answerId, reasonCode,
			detail, nextStatus, createdAt, nextResolvedAt);
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
