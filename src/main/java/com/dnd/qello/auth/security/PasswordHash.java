package com.dnd.qello.auth.security;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

public record PasswordHash(String value) {

	public PasswordHash {
		if (value == null || value.isBlank()) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "passwordHash", "passwordHash는 비어 있을 수 없습니다");
		}
	}

	@Override
	public String toString() {
		return "PasswordHash[REDACTED]";
	}

}
