package com.dnd.qello.notification.push;

/** Provider 호출 전 dispatch 정책의 제한된 terminal decision. */
public enum PushDispatchDecision {
	SEND,
	CANCELLED,
	DEAD
}
