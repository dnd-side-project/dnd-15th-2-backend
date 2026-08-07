package com.dnd.qello.account.security.bcrypt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.PasswordHash;
import com.dnd.qello.account.security.RawPassword;

/**
 * Created at: 2026-08-04T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-48-ACCOUNT-PASSWORD-UNIT-003
 */
class BCryptPasswordHasherTest {

	private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

	@Test
	@DisplayName("올바른 평문은 해시와 일치하고 잘못된 평문은 거부된다")
	void matchesCorrectRawPasswordOnly() {
		PasswordHash hash = hasher.hash(new RawPassword("correct-horse-battery-staple"));

		assertThat(hasher.matches(new RawPassword("correct-horse-battery-staple"), hash)).isTrue();
		assertThat(hasher.matches(new RawPassword("wrong-password"), hash)).isFalse();
	}

	@Test
	@DisplayName("같은 평문도 매번 다른 해시 값을 생성한다")
	void producesDifferentHashesForSamePasswordDueToSalting() {
		RawPassword rawPassword = new RawPassword("same-plaintext-password");

		PasswordHash first = hasher.hash(rawPassword);
		PasswordHash second = hasher.hash(rawPassword);

		assertThat(first.value()).isNotEqualTo(second.value());
		assertThat(hasher.matches(rawPassword, first)).isTrue();
		assertThat(hasher.matches(rawPassword, second)).isTrue();
	}

}
