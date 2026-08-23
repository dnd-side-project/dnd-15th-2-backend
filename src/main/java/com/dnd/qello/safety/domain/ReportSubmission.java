package com.dnd.qello.safety.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.dnd.qello.safety.error.SafetyErrorCode;
import com.dnd.qello.safety.error.SafetyException;

// 신고 접수 요청의 사유·설명 조합을 검증하는 값 객체. Foundation(#153)은 이 검증을
// 의도적으로 DB CHECK(ck_report_sub_reason)에만 맡기고 도메인 레벨에서는 하지
// 않았다 — 이 이슈가 처음으로 도메인 레벨 검증을 추가해 잘못된 요청이 DB까지
// 가지 않고 400으로 끝나게 한다.
public record ReportSubmission(ReportReason reason, ReportSubReason subReason, String detail) {

	private static final int DETAIL_MAX_LENGTH = 500;

	private static final Map<ReportReason, Set<ReportSubReason>> ALLOWED_SUB_REASONS = Map.of(
		ReportReason.SEXUAL_CONTENT, EnumSet.of(ReportSubReason.CSAM, ReportSubReason.NCII),
		ReportReason.VIOLENCE_OR_THREAT, EnumSet.of(ReportSubReason.CREDIBLE_THREAT),
		ReportReason.ILLEGAL_OR_DANGEROUS, EnumSet.of(ReportSubReason.SELF_HARM_RISK));

	public ReportSubmission {
		if (reason == null) {
			throw new SafetyException(SafetyErrorCode.REQUIRED_VALUE_MISSING, "reason", "reason은 필수입니다");
		}
		if (subReason != null && !ALLOWED_SUB_REASONS.getOrDefault(reason, Set.of()).contains(subReason)) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REPORT_SUB_REASON, "subReason", "reason과 subReason 조합이 유효하지 않습니다");
		}
		if (reason == ReportReason.OTHER && (detail == null || detail.isBlank())) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REPORT_DETAIL, "detail", "OTHER 사유는 설명이 필수입니다");
		}
		if (detail != null && detail.length() > DETAIL_MAX_LENGTH) {
			throw new SafetyException(
				SafetyErrorCode.INVALID_REPORT_DETAIL, "detail", "detail이 허용 길이를 초과했습니다");
		}
	}
}
