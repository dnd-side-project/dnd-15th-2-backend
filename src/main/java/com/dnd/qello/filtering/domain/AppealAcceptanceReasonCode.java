package com.dnd.qello.filtering.domain;

// 접수 판정의 근거. 어떤 경로로 접수를 허용하거나 거절했는지 남겨, 나중에
// "이 appeal이 왜 기간 밖인데 접수됐는가"를 추적할 수 있게 한다.
public enum AppealAcceptanceReasonCode {

	// 기산점이 확인됐고 접수 기간 안이다.
	WITHIN_WINDOW,

	// 기산점을 확정할 수 없어 거절 대신 접수를 허용했다. 데이터 결함이 곧
	// 구제 거부가 되지 않게 하는 fallback이다.
	WINDOW_UNVERIFIABLE,

	// 기산점이 확인됐고 접수 기간이 지났다. 거절 결과이므로 appeal_case 행으로
	// 저장되지 않는다 — AppealCase는 이 값을 거절한다.
	WINDOW_ELAPSED
}
