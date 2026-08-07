/*
 * Created at: 2026-08-07T15:40:00+09:00
 * Source scenario: TEST-PLAN-GH-72-LOGIN-ID-UNIT-001 through UNIT-003
 */
package com.dnd.qello.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

class LoginIdTest {

	@Test
	@DisplayName("of는 대소문자와 앞뒤 공백 차이를 흡수해 저장 형식으로 맞춘다")
	void normalizesRawInput() {
		assertThat(LoginId.of("  Qello-Admin  ").value()).isEqualTo("qello-admin");
		assertThat(LoginId.of("qello-admin").value()).isEqualTo("qello-admin");
	}

	@Test
	@DisplayName("생성자는 이미 정규화된 값만 받는다")
	void rejectsNonNormalizedValue() {
		assertThatThrownBy(() -> new LoginId("Qello-Admin"))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_LOGIN_ID);
		assertThatThrownBy(() -> new LoginId(" qello-admin"))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_LOGIN_ID);
	}

	@Test
	@DisplayName("빈 값과 길이 초과는 거절한다")
	void rejectsBlankAndTooLongValue() {
		assertThatThrownBy(() -> LoginId.of("  "))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> LoginId.of(null))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> LoginId.of("a".repeat(51)))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_LOGIN_ID);
	}
}
