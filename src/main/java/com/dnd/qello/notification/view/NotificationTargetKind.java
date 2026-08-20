package com.dnd.qello.notification.view;

/**
 * 알림이 가리키는 대상의 종류. {@code REPORT}는 {@code notification.report_id}
 * 컬럼과 함께 별도 이슈(#155)가 소유하므로 이 이슈에서는 추가하지 않는다.
 */
public enum NotificationTargetKind {
	DIRECTION_POST,
	ANSWER,
	NONE
}
