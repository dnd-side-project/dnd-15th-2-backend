package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// 수동 검토 case(#103 정체성 + #110 우선순위·authority). "동일 대상·release에
// case 하나"라는 유일성 불변식(INV-MAN-001)은 여전히 target+filterReleaseId
// 조합이 표현하고, 실제 강제는 DB unique index가 한다. filterJobId는 이 case가
// 열린 시점의 FilterJob을 직접 가리켜, target+release 조합의 모호성 없이 검토
// 결정을 적용할 대상을 특정한다.
//
// band는 report signal 평가 결과만 반영한다 — aging 승격은 이 필드를 갱신하지
// 않고 effectiveBand(now, policy)가 조회 시점에 계산한다.
public record ManualReviewCase(
	Long id, FilterTarget target, long filterReleaseId, long filterJobId, ManualReviewCaseStatus status,
	ManualReviewBand band, int validatedReportSignalCount, String priorityPolicyVersion,
	ManualReviewPriorityReasonCode priorityReasonCode, Instant resolvedAt, Long resolvedByOperatorUserId,
	FilterVerdict resolvedVerdict, Instant createdAt
) {

	private static final int POLICY_VERSION_MAX_LENGTH = 50;

	public ManualReviewCase {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (target == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "target");
		}
		if (filterReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterReleaseId", "filterReleaseId는 양수여야 합니다");
		}
		if (filterJobId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterJobId", "filterJobId는 양수여야 합니다");
		}
		if (status == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "status");
		}
		if (band == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "band");
		}
		if (validatedReportSignalCount < 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "validatedReportSignalCount",
				"validatedReportSignalCount는 음수일 수 없습니다");
		}
		if (priorityPolicyVersion == null || priorityPolicyVersion.isBlank()
			|| priorityPolicyVersion.length() > POLICY_VERSION_MAX_LENGTH) {
			throw new FilteringException(FilteringErrorCode.INVALID_TEXT, "priorityPolicyVersion",
				"priorityPolicyVersion 값이 유효하지 않습니다");
		}
		if (priorityReasonCode == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "priorityReasonCode");
		}
		if ((status == ManualReviewCaseStatus.RESOLVED)
			!= (resolvedAt != null && resolvedByOperatorUserId != null && resolvedVerdict != null)) {
			throw new FilteringException(FilteringErrorCode.INVALID_MANUAL_REVIEW_CASE_STATUS, "resolvedAt",
				"RESOLVED 상태와 resolvedAt/resolvedByOperatorUserId/resolvedVerdict는 함께 있어야 합니다");
		}
		if (resolvedByOperatorUserId != null && resolvedByOperatorUserId <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "resolvedByOperatorUserId",
				"resolvedByOperatorUserId는 양수여야 합니다");
		}
		if (createdAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "createdAt");
		}
	}

	public static ManualReviewCase open(FilterTarget target, long filterReleaseId, long filterJobId,
		ManualReviewPriorityDecision decision, int validatedReportSignalCount, String priorityPolicyVersion,
		Instant now) {
		if (decision == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "decision");
		}
		return new ManualReviewCase(null, target, filterReleaseId, filterJobId, ManualReviewCaseStatus.OPEN,
			decision.band(), validatedReportSignalCount, priorityPolicyVersion, decision.reasonCode(), null, null,
			null, now);
	}

	public static ManualReviewCase restore(Long id, FilterTarget target, long filterReleaseId, long filterJobId,
		ManualReviewCaseStatus status, ManualReviewBand band, int validatedReportSignalCount,
		String priorityPolicyVersion, ManualReviewPriorityReasonCode priorityReasonCode, Instant resolvedAt,
		Long resolvedByOperatorUserId, FilterVerdict resolvedVerdict, Instant createdAt) {
		return new ManualReviewCase(id, target, filterReleaseId, filterJobId, status, band,
			validatedReportSignalCount, priorityPolicyVersion, priorityReasonCode, resolvedAt,
			resolvedByOperatorUserId, resolvedVerdict, createdAt);
	}

	// report signal 기반 band 순수 평가. validatedReportSignalCount가 policy의
	// threshold 이상이면 HIGH+REPORT_SIGNAL, 아니면 STANDARD+DEFAULT. 호출 서비스는
	// 이 메서드가 던지는 예외를 흡수해 STANDARD+CALCULATION_FAILED로 대체해야
	// 한다(INV-MAN-009) — 이 메서드 자체는 그 fallback을 모른다.
	public static ManualReviewPriorityDecision evaluatePriority(
		int validatedReportSignalCount, ManualReviewPriorityPolicy policy
	) {
		if (validatedReportSignalCount < 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "validatedReportSignalCount",
				"validatedReportSignalCount는 음수일 수 없습니다");
		}
		if (policy == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "policy");
		}
		if (validatedReportSignalCount >= policy.highBandReportSignalThreshold()) {
			return new ManualReviewPriorityDecision(ManualReviewBand.HIGH, ManualReviewPriorityReasonCode.REPORT_SIGNAL);
		}
		return new ManualReviewPriorityDecision(ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.DEFAULT);
	}

	// aging을 조회 시점에 계산하는 순수 함수. 저장된 band가 이미 HIGH이거나
	// 경과 시간이 policy.agingThreshold() 이상이면 HIGH로 취급하되, 어떤 필드도
	// 갱신하지 않는다 — 스케줄러 없이도 큐 정렬에서 즉시 반영된다.
	public ManualReviewBand effectiveBand(Instant now, ManualReviewPriorityPolicy policy) {
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		if (policy == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "policy");
		}
		if (band == ManualReviewBand.HIGH) {
			return ManualReviewBand.HIGH;
		}
		if (!now.isBefore(createdAt.plus(policy.agingThreshold()))) {
			return ManualReviewBand.HIGH;
		}
		return ManualReviewBand.STANDARD;
	}

	// 검토자 결정 또는 자동 결과 도착으로 case를 종료한다(INV-MAN-003의 case 측
	// 구현). 이미 RESOLVED면 재종결을 거절한다 — case는 한 번만 닫힌다.
	public ManualReviewCase resolve(FilterVerdict verdict, long operatorUserId, Instant now) {
		if (status == ManualReviewCaseStatus.RESOLVED) {
			throw new FilteringException(FilteringErrorCode.INVALID_MANUAL_REVIEW_CASE_STATUS, "status",
				"이미 종료된 case입니다");
		}
		if (verdict == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "verdict");
		}
		if (operatorUserId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "operatorUserId", "operatorUserId는 양수여야 합니다");
		}
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		return new ManualReviewCase(id, target, filterReleaseId, filterJobId, ManualReviewCaseStatus.RESOLVED, band,
			validatedReportSignalCount, priorityPolicyVersion, priorityReasonCode, now, operatorUserId, verdict,
			createdAt);
	}
}
