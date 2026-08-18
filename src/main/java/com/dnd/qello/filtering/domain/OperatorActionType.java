package com.dnd.qello.filtering.domain;

// 감사 대상이 되는 운영자 행위. "필터링이 무엇을 통과시키고 무엇을 막는가"를
// 사람이 바꾸는 경로만 여기 있다.
//
// release 후보 생성(createCandidate)은 목록에 없다 — 후보를 만드는 것만으로는
// 어떤 판정도 달라지지 않는다. 그 후보가 실제로 쓰이기 시작하는 지점
// (shadow/canary/promote)부터 authority 변경으로 본다.
public enum OperatorActionType {

	RELEASE_MARK_OFFLINE_EVALUATED,
	RELEASE_DESIGNATE_SHADOW,
	RELEASE_DESIGNATE_CANARY,
	RELEASE_PROMOTE,
	RELEASE_ROLLBACK,

	// 다른 release가 승격되면서 기존 PROMOTED release가 자동으로 내려간 경우.
	// 운영자가 직접 요청한 RELEASE_ROLLBACK과 구분한다 — 같은 값을 쓰면 감사
	// 이력만 보고 "누가 이 release를 내리기로 했는가"에 답할 수 없다.
	RELEASE_DEMOTED_BY_PROMOTION,

	MANUAL_REVIEW_DECIDE,

	APPEAL_DECIDE,
	APPEAL_EXTEND_EXPIRY,

	SNAPSHOT_HEALTH_CONFIRM_PERMANENT,
	SNAPSHOT_EMERGENCY_MIGRATION
}
