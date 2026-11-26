package com.dnd.qello.auth.security;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

// 운영자 비밀번호 작성 규칙(#376, 위치기반서비스 점검표 C4).
//
// 영문 대문자·소문자·숫자·특수문자 중 2종 이상이면 10자 이상, 3종 이상이면 8자 이상이어야 한다. 허용 문자는
// ASCII 출력 문자(공백 포함)이고 영숫자가 아닌 문자는 특수문자로 센다. 최대 72자는 BCrypt 입력 한도(72바이트)와
// 같다. ASCII만 허용하므로 글자 수가 곧 바이트 수다. 이 한도를 넘기면 BCryptPasswordEncoder가 해시하지 않고
// 예외를 던져 규칙 위반이 500이 된다.
//
// login_id와 같은 값은 대소문자를 무시하고 거부한다. login_id를 포함하기만 하는 값은 막지 않는다.
//
// 오류의 reason에 입력값을 담지 않는다. 응답과 로그에 비밀번호가 실린다.
public final class OperatorPasswordPolicy {

	public static final int MAX_LENGTH = 72;
	static final String FIELD = "newPassword";

	private static final char FIRST_PRINTABLE = ' ';
	private static final char LAST_PRINTABLE = '~';
	private static final int MIN_LENGTH_WITH_TWO_TYPES = 10;
	private static final int MIN_LENGTH_WITH_THREE_TYPES = 8;

	private OperatorPasswordPolicy() {
	}

	/**
	 * 새 비밀번호가 작성 규칙을 지키는지 검사한다.
	 *
	 * @throws AuthException
	 *             규칙을 어기면 {@link AuthErrorCode#PASSWORD_POLICY_VIOLATION}
	 */
	public static void validate(LoginId loginId, RawPassword password) {
		String value = password.value();
		if (!isPrintableAscii(value)) {
			throw violation("영문, 숫자, 공백을 포함한 ASCII 특수문자만 사용할 수 있습니다");
		}
		if (value.length() > MAX_LENGTH) {
			throw violation("비밀번호는 " + MAX_LENGTH + "자를 넘을 수 없습니다");
		}
		if (value.equalsIgnoreCase(loginId.value())) {
			throw violation("로그인 식별자와 같은 비밀번호는 사용할 수 없습니다");
		}
		int types = characterTypes(value);
		if (types < 2) {
			throw violation("영문 대문자, 소문자, 숫자, 특수문자 중 2종류 이상을 섞어야 합니다");
		}
		if (types >= 3 && value.length() < MIN_LENGTH_WITH_THREE_TYPES) {
			throw violation("3종류 이상을 섞은 비밀번호는 " + MIN_LENGTH_WITH_THREE_TYPES + "자 이상이어야 합니다");
		}
		if (types == 2 && value.length() < MIN_LENGTH_WITH_TWO_TYPES) {
			throw violation("2종류를 섞은 비밀번호는 " + MIN_LENGTH_WITH_TWO_TYPES + "자 이상이어야 합니다");
		}
	}

	private static boolean isPrintableAscii(String value) {
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character < FIRST_PRINTABLE || character > LAST_PRINTABLE) {
				return false;
			}
		}
		return true;
	}

	// isPrintableAscii를 통과한 값만 받는다. 영숫자가 아니면 특수문자다.
	private static int characterTypes(String value) {
		boolean upper = false;
		boolean lower = false;
		boolean digit = false;
		boolean special = false;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character >= 'A' && character <= 'Z') {
				upper = true;
			} else if (character >= 'a' && character <= 'z') {
				lower = true;
			} else if (character >= '0' && character <= '9') {
				digit = true;
			} else {
				special = true;
			}
		}
		return count(upper) + count(lower) + count(digit) + count(special);
	}

	private static int count(boolean present) {
		return present ? 1 : 0;
	}

	private static AuthException violation(String reason) {
		return new AuthException(AuthErrorCode.PASSWORD_POLICY_VIOLATION, FIELD, reason);
	}
}
