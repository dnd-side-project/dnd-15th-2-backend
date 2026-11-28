/*
 * Created at: 2026-11-24T23:50:07+09:00
 * Source scenario: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-001 through UNIT-005
 */
package com.dnd.qello.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OperatorPasswordPolicyTest {

	private static final LoginId LOGIN_ID = new LoginId("qello-admin");

	@ParameterizedTest(name = "{0}")
	@DisplayName("UNIT-001: 3종 8자, 2종 10자, 4종 8자는 통과한다")
	@ValueSource(strings = {"abcdef1!", "abcdefgh12", "Abcdef1!"})
	void acceptsCompliantPasswords(String password) {
		assertThatCode(() -> OperatorPasswordPolicy.validate(LOGIN_ID, new RawPassword(password)))
				.doesNotThrowAnyException();
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("UNIT-001: 2종 9자와 3종·4종 7자는 길이 부족, 1종 30자는 종류 부족으로 AUT-VAL-005다")
	@CsvSource({
			"abcdefg12, 2종류를 섞은 비밀번호는 10자 이상이어야 합니다",
			"abcde1!, 3종류 이상을 섞은 비밀번호는 8자 이상이어야 합니다",
			"Abcde1!, 3종류 이상을 섞은 비밀번호는 8자 이상이어야 합니다",
			"abcdefghijklmnopqrstuvwxyzabcd, '영문 대문자, 소문자, 숫자, 특수문자 중 2종류 이상을 섞어야 합니다'"
	})
	void rejectsShortOrSingleTypePasswords(String password, String reason) {
		AuthException exception = violationOf(password);

		assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PASSWORD_POLICY_VIOLATION);
		assertThat(exception.getField()).isEqualTo("newPassword");
		assertThat(exception.getReason()).isEqualTo(reason);
	}

	@Test
	@DisplayName("UNIT-002: 공백은 허용하고 특수문자로 센다")
	void countsSpaceAsSpecialCharacter() {
		// 소문자 + 공백 2종, 10자
		assertThatCode(() -> OperatorPasswordPolicy.validate(LOGIN_ID, new RawPassword("abcd efghi")))
				.doesNotThrowAnyException();
		// 소문자 + 공백 2종, 9자는 길이 부족이다. 공백이 종류로 세지지 않았다면 종류 부족이 나온다
		assertThat(violationOf("abcd efgh").getReason()).isEqualTo("2종류를 섞은 비밀번호는 10자 이상이어야 합니다");
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("UNIT-002: 한글·이모지·탭·제어문자·DEL·ASCII 밖 문자가 섞이면 AUT-VAL-005다")
	@ValueSource(strings = {"Abcdef1!한글", "Abcdef1!😀", "Abcdef1!\tx", "Abcdef1!\u0007x", "Abcdef1!\u007Fx",
			"Abcdef1!éx"})
	void rejectsNonPrintableAscii(String password) {
		AuthException exception = violationOf(password);

		assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PASSWORD_POLICY_VIOLATION);
		assertThat(exception.getReason()).isEqualTo("영문, 숫자, 공백을 포함한 ASCII 특수문자만 사용할 수 있습니다");
	}

	@Test
	@DisplayName("UNIT-003: 72자는 통과하고 73자는 AUT-VAL-005다")
	void enforcesBcryptInputLimit() {
		String seventyTwo = "Aa1!".repeat(18);
		String seventyThree = seventyTwo + "x";

		assertThat(seventyTwo).hasSize(OperatorPasswordPolicy.MAX_LENGTH);
		assertThatCode(() -> OperatorPasswordPolicy.validate(LOGIN_ID, new RawPassword(seventyTwo)))
				.doesNotThrowAnyException();
		assertThat(violationOf(seventyThree).getReason()).isEqualTo("비밀번호는 72자를 넘을 수 없습니다");
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("UNIT-004: login_id와 대소문자만 다른 값은 AUT-VAL-005다")
	@ValueSource(strings = {"qello-admin", "QELLO-ADMIN", "Qello-Admin"})
	void rejectsLoginIdIgnoringCase(String password) {
		AuthException exception = violationOf(password);

		assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PASSWORD_POLICY_VIOLATION);
		assertThat(exception.getReason()).isEqualTo("로그인 식별자와 같은 비밀번호는 사용할 수 없습니다");
	}

	@Test
	@DisplayName("UNIT-004: login_id를 포함하기만 하는 값은 통과한다")
	void acceptsPasswordContainingLoginId() {
		assertThatCode(() -> OperatorPasswordPolicy.validate(LOGIN_ID, new RawPassword("qello-admin-2026!")))
				.doesNotThrowAnyException();
	}

	@ParameterizedTest(name = "{0}")
	@DisplayName("UNIT-005: 규칙 위반 예외의 field·reason·message 어디에도 입력값이 없다")
	@ValueSource(strings = {"secret12", "Abcde1!", "QELLO-ADMIN", "Abcdef1!한글", "Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!"
			+ "Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!Z"})
	void violationCarriesNoInput(String password) {
		AuthException exception = violationOf(password);

		assertThat(exception.getField()).doesNotContain(password);
		assertThat(exception.getReason()).doesNotContain(password);
		assertThat(exception.getMessage()).doesNotContain(password);
		assertThat(String.valueOf(exception)).doesNotContain(password);
	}

	private static AuthException violationOf(String password) {
		AuthException exception = catchThrowableOfType(AuthException.class,
				() -> OperatorPasswordPolicy.validate(LOGIN_ID, new RawPassword(password)));
		assertThat(exception).as("규칙 위반이어야 한다").isNotNull();
		return exception;
	}
}
