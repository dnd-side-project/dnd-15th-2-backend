package com.dnd.qello.account.domain;

import com.dnd.qello.account.error.AccountErrorCode;
import com.dnd.qello.account.error.AccountException;

public record PasswordHash(String value) {

	public PasswordHash {
		if (value == null || value.isBlank()) {
			throw new AccountException(
				AccountErrorCode.REQUIRED_VALUE_MISSING, "passwordHash", "passwordHash는 비어 있을 수 없습니다");
		}
	}

	@Override
	public String toString() {
		return "PasswordHash[REDACTED]";
	}

}
