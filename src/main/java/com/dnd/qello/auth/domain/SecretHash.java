package com.dnd.qello.auth.domain;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

// device_secret의 SHA-256 해시(hex, 64자). 평문을 복원할 수 없지만 로그·응답 노출을
// 막는 관례는 RawPassword/PasswordHash와 동일하게 유지한다.
public record SecretHash(String value) {

	private static final int HEX_LENGTH = 64;

	public SecretHash {
		if (value == null || value.isBlank()) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "secretHash", "secretHash는 비어 있을 수 없습니다");
		}
		if (value.length() != HEX_LENGTH) {
			throw new AuthException(
				AuthErrorCode.INVALID_CREDENTIAL_STATE,
				"secretHash",
				"secretHash는 " + HEX_LENGTH + "자 hex여야 합니다"
			);
		}
	}

	@Override
	public String toString() {
		return "SecretHash[REDACTED]";
	}

}
