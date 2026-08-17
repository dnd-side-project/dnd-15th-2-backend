package com.dnd.qello.filtering.domain;

// appeal case의 수명. 접수와 동시에 OPEN이고, 검토자 결정으로 한 번만 RESOLVED가
// 된다. 만료는 별도 상태가 아니다 — 만료는 접수 시점에만 평가하며, 이미 접수된
// case는 만료 시각이 지나도 검토 대상으로 남는다.
public enum AppealCaseStatus {
	OPEN,
	RESOLVED
}
