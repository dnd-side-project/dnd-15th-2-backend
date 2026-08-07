/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-SECRET-GENERATOR-UNIT-001, UNIT-002
 */
package com.dnd.qello.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceSecretGeneratorTest {

	private final DeviceSecretGenerator generator = new DeviceSecretGenerator();

	@Test
	@DisplayName("32바이트를 base64url로 인코딩해 43자 값을 만든다")
	void generatesBase64UrlEncoded32Bytes() {
		DeviceSecret secret = generator.generate();

		// base64url(패딩 없음)로 32바이트를 인코딩하면 ceil(32 * 4 / 3) = 43자다.
		assertThat(secret.value()).hasSize(43);
		assertThat(secret.value()).doesNotContain("+", "/", "=");
	}

	@Test
	@DisplayName("연속 생성한 값은 서로 겹치지 않는다")
	void generatesDistinctValues() {
		Set<String> values = new HashSet<>();
		for (int i = 0; i < 1_000; i++) {
			values.add(generator.generate().value());
		}

		assertThat(values).hasSize(1_000);
	}

}
