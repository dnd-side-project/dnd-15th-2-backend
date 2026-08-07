package com.dnd.qello.auth.security;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

public record RawPassword(String value) {

	public RawPassword {
		if (value == null || value.isBlank()) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "password", "password는 비어 있을 수 없습니다");
		}
	}

	@Override
	public String toString() {
		return "RawPassword[REDACTED]";
	}

}
