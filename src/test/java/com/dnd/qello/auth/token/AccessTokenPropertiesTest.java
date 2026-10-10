/*
 * Created at: 2026-10-10T15:00:12+09:00
 * Source scenario: TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION-UNIT-001 through UNIT-004
 */
package com.dnd.qello.auth.token;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AccessTokenPropertiesTest {

	private static final String SECRET_31_BYTES = "test-only-signing-key-31-bytes!";
	private static final String SECRET_32_BYTES = "test-only-signing-key-32-bytes!!";
	// 한글은 UTF-8에서 3바이트다. 11자 = 33바이트라 문자 수(11)로 세면 거절되고 바이트 수로 세면 통과한다.
	private static final String SECRET_11_KOREAN_CHARS = "테스트전용서명키한글값";

	@Test
	@DisplayName("UNIT-001: secret이 없으면 설정 키 이름을 담은 예외로 바인딩에 실패한다")
	void rejectsMissingSecret() {
		assertThatNullPointerException()
				.isThrownBy(() -> properties(null))
				.withMessageContaining("qello.auth.access-token.secret");
	}

	@Test
	@DisplayName("UNIT-002: 31바이트 secret은 거절하고 오류 메시지에 키 값을 넣지 않는다")
	void rejectsSecretShorterThan32Bytes() {
		assertThat(SECRET_31_BYTES.getBytes(StandardCharsets.UTF_8)).hasSize(31);

		assertThatIllegalArgumentException()
				.isThrownBy(() -> properties(SECRET_31_BYTES))
				.withMessageContaining("qello.auth.access-token.secret")
				.withMessageNotContaining(SECRET_31_BYTES);
	}

	@Test
	@DisplayName("UNIT-003: 정확히 32바이트인 secret은 받는다")
	void acceptsSecretOfExactly32Bytes() {
		assertThat(SECRET_32_BYTES.getBytes(StandardCharsets.UTF_8)).hasSize(32);

		assertThat(properties(SECRET_32_BYTES).secret()).isEqualTo(SECRET_32_BYTES);
	}

	@Test
	@DisplayName("UNIT-003: 길이는 문자 수가 아니라 UTF-8 바이트 수로 센다")
	void measuresSecretLengthInUtf8Bytes() {
		assertThat(SECRET_11_KOREAN_CHARS).hasSize(11);
		assertThat(SECRET_11_KOREAN_CHARS.getBytes(StandardCharsets.UTF_8)).hasSize(33);

		assertThat(properties(SECRET_11_KOREAN_CHARS).secret()).isEqualTo(SECRET_11_KOREAN_CHARS);
	}

	@Test
	@DisplayName("UNIT-004: toString()에는 secret 값이 들어가지 않는다")
	void redactsSecretInToString() {
		assertThat(properties(SECRET_32_BYTES).toString())
				.doesNotContain(SECRET_32_BYTES)
				.contains("secret=REDACTED");
	}

	private static AccessTokenProperties properties(String secret) {
		return new AccessTokenProperties("qello", "qello-app", 1800, secret);
	}
}
