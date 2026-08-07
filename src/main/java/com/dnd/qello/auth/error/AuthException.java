package com.dnd.qello.auth.error;

import com.dnd.qello.common.error.DomainException;

// auth 기능이 던지는 유일한 예외. 개별 실패는 AuthErrorCode로 구분.
public class AuthException extends DomainException {

	public AuthException(AuthErrorCode errorCode) {
		super(errorCode);
	}

	public AuthException(AuthErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public AuthException(AuthErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public AuthException(AuthErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
