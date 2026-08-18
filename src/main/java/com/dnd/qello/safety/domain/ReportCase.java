package com.dnd.qello.safety.domain;

import java.time.Instant;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 대상 콘텐츠(사용자/질문글/답변)당 열린 사건은 최대 하나라는 불변식(INV-RPT-001)의
// 실제 강제는 DB 부분 유일 인덱스가 한다. 이 레코드는 사건 하나의 생애주기만
// 표현하고, 여러 신고자의 제보를 하나로 묶는 병합 로직은 소유하지 않는다(#154).
public record ReportCase(Long id, Long targetUserId, Long directionPostId, Long answerId,
	ReportCaseStatus status, ReportCaseSeverity severity, ReportCaseQueue queue,
	ModerationDecision decision, Instant createdAt, Instant resolvedAt) {

	public ReportCase {
		if (id != null && id <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, "id", "id는 양수여야 합니다");
		}
		if ((targetUserId == null ? 0 : 1) + (directionPostId == null ? 0 : 1)
			+ (answerId == null ? 0 : 1) != 1) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REPORT_TARGET, null, "사건 대상은 정확히 하나여야 합니다");
		}
		requirePositiveOrNull(targetUserId, "targetUserId");
		requirePositiveOrNull(directionPostId, "directionPostId");
		requirePositiveOrNull(answerId, "answerId");
		if (status == null || severity == null || queue == null || createdAt == null) {
			throw new SafetyException(
				SafetyErrorCode.REQUIRED_VALUE_MISSING, null, "사건 필수 값이 없습니다");
		}
		if ((status == ReportCaseStatus.RESOLVED) != (decision != null && resolvedAt != null)) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING,
				"resolvedAt", "종결 상태와 판정·종결 시각은 함께 있어야 합니다");
		}
		if (resolvedAt != null && resolvedAt.isBefore(createdAt)) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_TIME_ORDER, "resolvedAt", "resolvedAt은 createdAt보다 빠를 수 없습니다");
		}
	}

	/** severity·queue는 항상 NORMAL/STANDARD로 연다 — 실제 산출 로직은 #156의 몫이다. */
	public static ReportCase open(Long targetUserId, Long directionPostId, Long answerId, Instant now) {
		return new ReportCase(null, targetUserId, directionPostId, answerId, ReportCaseStatus.OPEN,
			ReportCaseSeverity.NORMAL, ReportCaseQueue.STANDARD, null, now, null);
	}

	public static ReportCase restore(Long id, Long targetUserId, Long directionPostId, Long answerId,
		ReportCaseStatus status, ReportCaseSeverity severity, ReportCaseQueue queue,
		ModerationDecision decision, Instant createdAt, Instant resolvedAt) {
		return new ReportCase(id, targetUserId, directionPostId, answerId, status, severity, queue,
			decision, createdAt, resolvedAt);
	}

	public ReportCase startReview() {
		requireStatus(ReportCaseStatus.OPEN);
		return new ReportCase(id, targetUserId, directionPostId, answerId, ReportCaseStatus.UNDER_REVIEW,
			severity, queue, decision, createdAt, resolvedAt);
	}

	public ReportCase resolve(ModerationDecision nextDecision, Instant at) {
		requireStatus(ReportCaseStatus.OPEN, ReportCaseStatus.UNDER_REVIEW);
		ModerationDecision resolvedDecision = requireValue(nextDecision, "decision");
		Instant resolvedInstant = requireValue(at, "resolvedAt");
		return new ReportCase(id, targetUserId, directionPostId, answerId, ReportCaseStatus.RESOLVED,
			severity, queue, resolvedDecision, createdAt, resolvedInstant);
	}

	private void requireStatus(ReportCaseStatus... allowed) {
		for (ReportCaseStatus candidate : allowed) {
			if (status == candidate) {
				return;
			}
		}
		throw new SafetyException(SafetyErrorCode.REPORT_CASE_ALREADY_RESOLVED,
			"status", status + " 상태에서는 진행할 수 없습니다");
	}

	private static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, field, field + "은 필수입니다");
		}
		return value;
	}

	private static void requirePositiveOrNull(Long value, String field) {
		if (value != null && value <= 0) {
			throw new SafetyException(SafetyErrorCode.INVALID_ID, field, field + "는 양수여야 합니다");
		}
	}
}
