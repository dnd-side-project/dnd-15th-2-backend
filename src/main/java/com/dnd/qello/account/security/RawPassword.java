package com.dnd.qello.account.security;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

public record RawPassword(String value) {

	public RawPassword {
		if (value == null || value.isBlank()) {
			throw new AccountException(
				AccountErrorCode.REQUIRED_VALUE_MISSING, "password", "password는 비어 있을 수 없습니다");
		}
	}

	@Override
	public String toString() {
		return "RawPassword[REDACTED]";
	}

}
