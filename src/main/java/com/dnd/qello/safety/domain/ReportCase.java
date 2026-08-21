package com.dnd.qello.safety.domain;

import java.time.Instant;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 대상 콘텐츠(사용자/질문글/답변)당 열린 사건은 최대 하나라는 불변식(INV-RPT-001)의
// 실제 강제는 DB 부분 유일 인덱스가 한다. 이 레코드는 사건 하나의 생애주기만
// 표현하고, 여러 신고자의 제보를 하나로 묶는 병합 로직은 소유하지 않는다(#154).
//
// SLA 재계산 값(slaDueAt)은 이 클래스가 스스로 계산하지 않는다 — 큐별 SLA
// 기간은 주입값(#156 SlaPolicy)이라 도메인이 정책을 알면 안 되고, 호출자가
// 계산해 open/escalate/deescalate에 넘긴다.
public record ReportCase(Long id, Long targetUserId, Long directionPostId, Long answerId,
	ReportCaseStatus status, ReportCaseSeverity severity, ReportCaseQueue queue,
	ModerationDecision decision, Instant createdAt, Instant resolvedAt,
	Instant slaDueAt, Long linkedManualReviewCaseId) {

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
		requirePositiveOrNull(linkedManualReviewCaseId, "linkedManualReviewCaseId");
		if (status == null || severity == null || queue == null || createdAt == null || slaDueAt == null) {
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

	/**
	 * severity·queue는 호출자가 산출해 넘긴다(#156) — subReason 기반 산출 로직은
	 * {@link ReportCaseSeverity#of(ReportSubReason)}에 있다. slaDueAt도 호출자가
	 * `now + slaPolicy.of(queue)`로 계산해 넘긴다.
	 */
	public static ReportCase open(Long targetUserId, Long directionPostId, Long answerId,
		ReportCaseSeverity severity, ReportCaseQueue queue, Instant now, Instant slaDueAt) {
		return new ReportCase(null, targetUserId, directionPostId, answerId, ReportCaseStatus.OPEN,
			severity, queue, null, now, null, slaDueAt, null);
	}

	public static ReportCase restore(Long id, Long targetUserId, Long directionPostId, Long answerId,
		ReportCaseStatus status, ReportCaseSeverity severity, ReportCaseQueue queue,
		ModerationDecision decision, Instant createdAt, Instant resolvedAt,
		Instant slaDueAt, Long linkedManualReviewCaseId) {
		return new ReportCase(id, targetUserId, directionPostId, answerId, status, severity, queue,
			decision, createdAt, resolvedAt, slaDueAt, linkedManualReviewCaseId);
	}

	public ReportCase startReview() {
		requireStatus(ReportCaseStatus.OPEN);
		return new ReportCase(id, targetUserId, directionPostId, answerId, ReportCaseStatus.UNDER_REVIEW,
			severity, queue, decision, createdAt, resolvedAt, slaDueAt, linkedManualReviewCaseId);
	}

	public ReportCase resolve(ModerationDecision nextDecision, Instant at) {
		requireStatus(ReportCaseStatus.OPEN, ReportCaseStatus.UNDER_REVIEW);
		ModerationDecision resolvedDecision = requireValue(nextDecision, "decision");
		Instant resolvedInstant = requireValue(at, "resolvedAt");
		return new ReportCase(id, targetUserId, directionPostId, answerId, ReportCaseStatus.RESOLVED,
			severity, queue, resolvedDecision, createdAt, resolvedInstant, slaDueAt, linkedManualReviewCaseId);
	}

	/**
	 * 이미 열린 사건에 더 심각한(subReason 기반) 신고가 붙을 때만 호출한다(#156).
	 * 강등은 이 경로로 하지 않는다 — {@link #deescalate}는 운영자 전용.
	 */
	public ReportCase escalate(Instant at, Instant nextSlaDueAt) {
		requireStatus(ReportCaseStatus.OPEN, ReportCaseStatus.UNDER_REVIEW);
		requireValue(at, "at");
		Instant nextSla = requireValue(nextSlaDueAt, "slaDueAt");
		return new ReportCase(id, targetUserId, directionPostId, answerId, status,
			ReportCaseSeverity.CRITICAL, ReportCaseQueue.URGENT, decision, createdAt, resolvedAt,
			nextSla, linkedManualReviewCaseId);
	}

	/** 운영자만 강등할 수 있다 — 신고 접수 경로는 이 메서드를 호출하지 않는다(#156). */
	public ReportCase deescalate(Instant at, Instant nextSlaDueAt) {
		requireStatus(ReportCaseStatus.OPEN, ReportCaseStatus.UNDER_REVIEW);
		requireValue(at, "at");
		Instant nextSla = requireValue(nextSlaDueAt, "slaDueAt");
		return new ReportCase(id, targetUserId, directionPostId, answerId, status,
			ReportCaseSeverity.NORMAL, ReportCaseQueue.STANDARD, decision, createdAt, resolvedAt,
			nextSla, linkedManualReviewCaseId);
	}

	/**
	 * 비종결 판정(#156) — {@code decision}만 세팅하고 {@code status}·{@code resolvedAt}은
	 * 그대로 둔다. {@code ck_report_case_resolution} CHECK가 이 조합(RESOLVED가 아니면서
	 * decision이 있고 resolvedAt은 없는 상태)을 이미 허용한다.
	 */
	public ReportCase requestMoreInfo(Instant at) {
		requireStatus(ReportCaseStatus.OPEN, ReportCaseStatus.UNDER_REVIEW);
		requireValue(at, "at");
		return new ReportCase(id, targetUserId, directionPostId, answerId, status,
			severity, queue, ModerationDecision.MORE_INFO_REQUIRED, createdAt, resolvedAt,
			slaDueAt, linkedManualReviewCaseId);
	}

	/** filtering.ManualReviewCase와의 상관관계만 기록한다(#156) — 테이블 통합은 하지 않는다. */
	public ReportCase withLinkedManualReviewCase(long manualReviewCaseId) {
		requireStatus(ReportCaseStatus.OPEN, ReportCaseStatus.UNDER_REVIEW);
		return new ReportCase(id, targetUserId, directionPostId, answerId, status,
			severity, queue, decision, createdAt, resolvedAt, slaDueAt, manualReviewCaseId);
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
