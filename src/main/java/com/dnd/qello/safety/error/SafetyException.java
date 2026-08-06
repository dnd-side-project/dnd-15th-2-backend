package com.dnd.qello.safety.error;

import com.dnd.qello.common.error.DomainException;

// safety 기능이 던지는 유일한 예외. 개별 실패는 SafetyErrorCode로 구분.
public class SafetyException extends DomainException {

	public SafetyException(SafetyErrorCode errorCode) {
		super(errorCode);
	}

	public SafetyException(SafetyErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public SafetyException(SafetyErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public SafetyException(SafetyErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
