/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-SECRET-HASH-UNIT-001 through UNIT-003
 */
package com.dnd.qello.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

class SecretHashTest {

	private static final String VALID_HEX_64 =
		"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

	@Test
	@DisplayName("빈 값은 거절한다")
	void rejectsBlankValue() {
		assertThatThrownBy(() -> new SecretHash(" "))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("64자가 아니면 거절한다")
	void rejectsWrongLength() {
		assertThatThrownBy(() -> new SecretHash("abcd"))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIAL_STATE);
	}

	@Test
	@DisplayName("toString은 해시 값을 노출하지 않는다")
	void redactsToString() {
		SecretHash hash = new SecretHash(VALID_HEX_64);

		assertThat(hash.toString()).doesNotContain(VALID_HEX_64);
	}

}
