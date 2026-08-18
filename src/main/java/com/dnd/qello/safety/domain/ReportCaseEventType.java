package com.dnd.qello.safety.domain;

// 이 이슈(#153)가 실제로 발행하는 값은 CASE_OPENED뿐이다. 나머지는 #154
// (REPORT_ATTACHED/DUPLICATE_SUPPRESSED)·#156(ESCALATED/MORE_INFO_REQUESTED/
// RESOLVED)이 소비한다 — NotificationType이 미래 값을 포함해 먼저 정의된
// 기존 관례와 동일하다.
public enum ReportCaseEventType {
	CASE_OPENED,
	REPORT_ATTACHED,
	DUPLICATE_SUPPRESSED,
	ESCALATED,
	MORE_INFO_REQUESTED,
	RESOLVED
}
