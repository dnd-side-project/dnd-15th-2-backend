package com.dnd.qello.notification.push.group;

/** 사용자 local-date 예산 예약 결과. retry는 추가 소비 없이 ALREADY_RESERVED다. */
public enum PushBudgetReservation {
	RESERVED,
	ALREADY_RESERVED,
	LIMIT_EXCEEDED,
	STALE_GROUP
}
