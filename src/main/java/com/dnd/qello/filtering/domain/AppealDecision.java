package com.dnd.qello.filtering.domain;

// 검토자의 수동 이의제기 결과. FilterVerdict(ALLOW/BLOCK)와 분리한다 — appeal은
// 판정을 다시 내리는 절차가 아니라 이미 확정된 BLOCK 판정을 유지할지 되돌릴지를
// 정하는 별도 구제 절차이기 때문이다.
public enum AppealDecision {

	// 비공개 처리를 유지한다. 어떤 콜백도 발행하지 않는다.
	UPHOLD_HIDDEN,

	// 비공개 처리를 되돌린다. 다른 공개 금지 사유가 없을 때만 복원 콜백이 나간다.
	OVERTURN_HIDDEN
}
