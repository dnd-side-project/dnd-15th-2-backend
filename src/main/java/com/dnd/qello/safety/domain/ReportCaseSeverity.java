package com.dnd.qello.safety.domain;

// #156이 subReason 기반으로 실제 산출한다. 이 이슈는 항상 NORMAL로 사건을 연다.
public enum ReportCaseSeverity {
	NORMAL,
	CRITICAL
}
