package com.dnd.qello.filtering.domain;

public enum ManualReviewPriorityReasonCode {
	// 검증된 report signal이 policy의 threshold를 넘어 HIGH로 평가됨.
	REPORT_SIGNAL,

	// threshold 미만이라 STANDARD로 평가됨.
	DEFAULT,

	// priority 평가 도중 예외가 발생해 STANDARD로 fallback함(INV-MAN-009).
	CALCULATION_FAILED
}
