/*
 * Created at: 2026-08-07T20:52:09+09:00
 * Source scenario: TEST-PLAN-GH-73-DEVICE-SECRET-HASHER-UNIT-001 through UNIT-003
 */
package com.dnd.qello.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.auth.domain.SecretHash;

class DeviceSecretHasherTest {

	private final DeviceSecretHasher hasher = new DeviceSecretHasher();

	@Test
	@DisplayName("같은 값은 항상 같은 해시를 만든다")
	void isDeterministic() {
		DeviceSecret secret = new DeviceSecret("fixed-test-secret-value");

		SecretHash first = hasher.hash(secret);
		SecretHash second = hasher.hash(secret);

		assertThat(first).isEqualTo(second);
	}

	@Test
	@DisplayName("SHA-256 hex 64자를 만든다")
	void producesSha256HexLength() {
		SecretHash hash = hasher.hash(new DeviceSecret("fixed-test-secret-value"));

		assertThat(hash.value()).hasSize(64);
		assertThat(hash.value()).matches("[0-9a-f]{64}");
	}

	@Test
	@DisplayName("잘 알려진 SHA-256 벡터와 일치한다")
	void matchesKnownVector() {
		// echo -n "abc" | sha256sum
		SecretHash hash = hasher.hash(new DeviceSecret("abc"));

		assertThat(hash.value())
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

}
