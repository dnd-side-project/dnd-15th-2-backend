package com.dnd.qello.notification.slack;

// SlackNotifier 구현체가 던지는 유일한 예외(#111). retryable=false는 재시도로
// 해결되지 않는 실패(예: 4xx, 잘못된 payload)를 의미하며 dispatch worker가
// NotificationRetryPolicy 없이 즉시 DEAD로 판정하는 근거가 된다.
public class SlackDeliveryException extends RuntimeException {

	private final boolean retryable;

	public SlackDeliveryException(boolean retryable, String message, Throwable cause) {
		super(message, cause);
		this.retryable = retryable;
	}

	public boolean retryable() {
		return retryable;
	}
}
