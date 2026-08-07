package com.dnd.qello.auth.domain;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

// 백오피스 로그인 식별자.
//
// 소문자로만 저장한다. 대소문자만 다른 두 식별자가 서로 다른 계정이 되면 운영자가
// 자기 계정을 헷갈리고, 유일성 제약도 우회된다. DB의 ck_operator_credential_login_id가
// 같은 규칙을 최종 경계로 강제한다.
public record LoginId(String value) {

	private static final int MAX_LENGTH = 50;

	public LoginId {
		if (value == null || value.isBlank()) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "loginId", "loginId는 비어 있을 수 없습니다");
		}
		if (value.length() > MAX_LENGTH) {
			throw new AuthException(
				AuthErrorCode.INVALID_LOGIN_ID, "loginId", "loginId는 " + MAX_LENGTH + "자를 초과할 수 없습니다");
		}
		if (!value.equals(value.toLowerCase())) {
			throw new AuthException(
				AuthErrorCode.INVALID_LOGIN_ID, "loginId", "loginId는 소문자만 사용할 수 있습니다");
		}
		if (!value.equals(value.strip())) {
			throw new AuthException(
				AuthErrorCode.INVALID_LOGIN_ID, "loginId", "loginId의 앞뒤에는 공백이 올 수 없습니다");
		}
	}

	// 입력을 저장 형식으로 맞춘다. 로그인 요청의 대소문자 차이를 여기서 흡수한다.
	public static LoginId of(String raw) {
		if (raw == null) {
			throw new AuthException(
				AuthErrorCode.REQUIRED_VALUE_MISSING, "loginId", "loginId는 비어 있을 수 없습니다");
		}
		return new LoginId(raw.strip().toLowerCase());
	}
}
