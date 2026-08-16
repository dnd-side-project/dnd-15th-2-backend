package com.dnd.qello.filtering.domain;

// moderation 공급자 호출 실패의 원인 분류(#109). ALLOW/BLOCK 판정에는 관여하지
// 않는다 — SnapshotHealth 판정(target-only 실패 집계)에만 쓰인다. RATE_LIMITED는
// #108의 기존 ModerationRateLimitedException 경로와 별개 정보로만 존재하며 그
// 경로의 계약을 바꾸지 않는다.
//
// moderation 패키지가 아니라 domain 패키지에 둔다 — SnapshotHealth(domain)가 이
// 값을 직접 다루므로 domain에 두면 moderation → domain 단방향 의존을 유지할 수
// 있다. moderation 쪽 호출자(OpenAiModerationFailureClassifier 등)는 이미
// domain에 의존하고 있어 추가 의존이 생기지 않는다.
public enum ModerationFailureClassification {

	// 429. 재시도 가능하지만 target snapshot 자체의 장애 증거로는 쓰지 않는다.
	RATE_LIMITED,

	// 5xx. target-only로 반복·지속되면 snapshot health 저하 증거로 집계될 수 있다.
	SERVER_ERROR,

	// timeout, connection refused 등 HTTP status가 없는 전송 실패. SERVER_ERROR와
	// 동일하게 target-only 지속 시 증거로 집계될 수 있다.
	TIMEOUT_OR_NETWORK,

	// 429를 제외한 4xx 전체(인증·권한·결제·quota·invalid request 등 우리 쪽 설정
	// 문제). 어떤 반복 횟수에서도 snapshot health 저하 증거로 집계되지 않는다
	// (INV-HLT-003).
	NON_TARGET_CLIENT_ERROR,

	// 위 어디에도 속하지 않는 실패(응답 형태가 예상과 달라 해석 불가 등). 증거로
	// 집계되지 않고 자동으로 영구 장애를 만들지 않는다(INV-HLT-004).
	UNKNOWN
}
