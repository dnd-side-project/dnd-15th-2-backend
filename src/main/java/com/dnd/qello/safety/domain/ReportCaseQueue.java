package com.dnd.qello.safety.domain;

// #156이 severity 기반으로 실제 라우팅한다. 이 이슈는 항상 STANDARD로 사건을 연다.
public enum ReportCaseQueue {
	STANDARD,
	URGENT
}
