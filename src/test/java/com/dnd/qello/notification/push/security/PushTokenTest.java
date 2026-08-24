package com.dnd.qello.notification.push.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-24T19:13:08+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-001
 */
class PushTokenTest {

	private static final String VALID_TOKEN = "unit-001-token-sentinel";
	private static final String INVALID_TOKEN_SENTINEL = "unit-001-invalid-token-sentinel";
	private static final int MAX_TOKEN_LENGTH = 4096;

	@Test
	@DisplayName("UNIT-001: null token은 원문을 포함하지 않는 validation 오류로 거절한다")
	void rejectsNullTokenWithoutEchoingInput() {
		assertThatThrownBy(() -> PushToken.of(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("[REDACTED]");
	}

	@Test
	@DisplayName("UNIT-001: blank token은 원문을 포함하지 않는 validation 오류로 거절한다")
	void rejectsBlankTokenWithoutEchoingInput() {
		assertThatThrownBy(() -> PushToken.of(" \t\n"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("[REDACTED]");
	}

	@Test
	@DisplayName("UNIT-001: 허용 크기를 초과한 token은 원문을 포함하지 않는 validation 오류로 거절한다")
	void rejectsOversizedTokenWithoutEchoingInput() {
		String oversizedToken = "x".repeat(MAX_TOKEN_LENGTH + 1);

		assertThatThrownBy(() -> PushToken.of(oversizedToken))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("[REDACTED]")
			.hasMessageNotContaining(oversizedToken);
	}

	@Test
	@DisplayName("UNIT-001: 정상 PushToken의 문자열 표현은 REDACTED만 반환한다")
	void redactsTokenInStringRepresentation() {
		PushToken token = PushToken.of(VALID_TOKEN);

		assertThat(token.toString()).isEqualTo("[REDACTED]");
		assertThat(token.toString()).doesNotContain(VALID_TOKEN, INVALID_TOKEN_SENTINEL);
	}
}
