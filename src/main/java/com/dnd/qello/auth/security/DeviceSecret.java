package com.dnd.qello.auth.security;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

// 서버가 발급하는 고엔트로피 기기 시크릿.
//
// 클라이언트가 만든 값이 아니라 SecureRandom 32바이트를 서버가 생성해 등록 응답에서
// 단 한 번만 평문으로 내려준다. 이후 인증은 이 값의 SHA-256 해시(SecretHash)로만 한다.
public record DeviceSecret(String value) {

	public DeviceSecret {
		if (value == null || value.isBlank()) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "deviceSecret", "deviceSecret은 비어 있을 수 없습니다");
		}
	}

	@Override
	public String toString() {
		return "DeviceSecret[REDACTED]";
	}

}
