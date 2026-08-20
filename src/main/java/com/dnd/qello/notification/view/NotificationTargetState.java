package com.dnd.qello.notification.view;

/**
 * 알림이 가리키는 대상의 현재 생존 상태. 우선순위는 선언 순서와 같다 —
 * {@code GONE > BLOCKED > HIDDEN > EXPIRED > AVAILABLE}. 삭제된 대상에 차단
 * 이유를 노출하면 상대의 차단 사실이 새어 나가므로 {@code GONE}이 항상 앞선다.
 */
public enum NotificationTargetState {
	GONE,
	BLOCKED,
	HIDDEN,
	EXPIRED,
	AVAILABLE
}
