package com.dnd.qello.account.error;

import com.dnd.qello.common.error.DomainException;

// account 기능이 던지는 유일한 예외. 개별 실패는 AccountErrorCode로 구분.
public class AccountException extends DomainException {

	public AccountException(AccountErrorCode errorCode) {
		super(errorCode);
	}

	public AccountException(AccountErrorCode errorCode, String field) {
		super(errorCode, field, null);
	}

	public AccountException(AccountErrorCode errorCode, String field, String reason) {
		super(errorCode, field, reason);
	}

	public AccountException(AccountErrorCode errorCode, String field, String reason, Throwable cause) {
		super(errorCode, field, reason, null, cause);
	}
}
