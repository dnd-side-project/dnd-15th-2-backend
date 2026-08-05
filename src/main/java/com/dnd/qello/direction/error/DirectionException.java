package com.dnd.qello.direction.error;

import com.dnd.qello.common.error.DomainException;

// direction 기능이 던지는 유일한 예외. 개별 실패는 DirectionErrorCode로 구분.
public class DirectionException extends DomainException {

	public DirectionException(DirectionErrorCode errorCode) {
		super(errorCode);
	}

	public DirectionException(DirectionErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public DirectionException(DirectionErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public DirectionException(DirectionErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
