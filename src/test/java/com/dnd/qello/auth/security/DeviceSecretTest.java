/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-SECRET-UNIT-001, UNIT-002
 */
package com.dnd.qello.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;

class DeviceSecretTest {

	@Test
	@DisplayName("빈 값은 거절한다")
	void rejectsBlankValue() {
		assertThatThrownBy(() -> new DeviceSecret(" "))
			.isInstanceOf(AuthException.class)
			.hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REQUIRED_VALUE_MISSING);
	}

	@Test
	@DisplayName("toString은 평문 값을 노출하지 않는다")
	void redactsToString() {
		DeviceSecret secret = new DeviceSecret("super-secret-device-value");

		assertThat(secret.toString()).doesNotContain("super-secret-device-value");
		assertThat(secret.toString()).isEqualTo("DeviceSecret[REDACTED]");
	}

}
