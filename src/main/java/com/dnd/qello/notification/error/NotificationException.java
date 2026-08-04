package com.dnd.qello.notification.error;

import com.dnd.qello.common.error.DomainException;

// notification 기능이 던지는 유일한 예외. 개별 실패는 NotificationErrorCode로 구분.
public class NotificationException extends DomainException {

	public NotificationException(NotificationErrorCode errorCode) {
		super(errorCode);
	}

	public NotificationException(NotificationErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public NotificationException(NotificationErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public NotificationException(NotificationErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
