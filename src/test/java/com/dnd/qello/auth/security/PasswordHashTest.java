package com.dnd.qello.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

/**
 * Created at: 2026-08-04T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-48-ACCOUNT-PASSWORD-UNIT-002
 */
class PasswordHashTest {

	@Test
	@DisplayName("빈 값이나 공백으로는 PasswordHash를 만들 수 없다")
	void rejectsBlankValue() {
		assertThatThrownBy(() -> new PasswordHash(null))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);
		assertThatThrownBy(() -> new PasswordHash("   "))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("toString은 해시 값을 노출하지 않는다")
	void toStringDoesNotExposeHashValue() {
		PasswordHash passwordHash = new PasswordHash("$2a$10$super-secret-hash-value");

		assertThat(passwordHash.toString()).doesNotContain("super-secret-hash-value");
	}

}
