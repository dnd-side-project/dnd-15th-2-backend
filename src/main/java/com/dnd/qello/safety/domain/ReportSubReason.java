package com.dnd.qello.safety.domain;

// 즉시 대응이 필요한 신고의 하위 사유. 상위 ReportReason과의 조합은 DB
// `ck_report_sub_reason` CHECK가 강제한다 — 도메인 레벨에서는 검증하지 않는다
// (설계 문서 §3.2).
public enum ReportSubReason {
	CSAM,
	NCII,
	CREDIBLE_THREAT,
	SELF_HARM_RISK
}
