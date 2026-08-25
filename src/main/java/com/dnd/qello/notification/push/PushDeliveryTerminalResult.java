package com.dnd.qello.notification.push;

/** provider 호출 이후 generation fence를 통과할 때만 저장할 수 있는 종결 상태. */
public enum PushDeliveryTerminalResult {
	SENT,
	FAILED,
	DEAD,
	CANCELLED
}
