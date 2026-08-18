package com.dnd.qello.filtering.domain;

// 감사 대상의 종류. FilterTargetType(사용자 콘텐츠 대상)과 다른 축이다 —
// 이쪽은 운영자가 조작한 필터링 시스템의 객체를 가리킨다.
public enum OperatorActionTargetType {

	FILTER_RELEASE,
	MANUAL_REVIEW_CASE,
	APPEAL_CASE,
	SNAPSHOT_HEALTH
}
