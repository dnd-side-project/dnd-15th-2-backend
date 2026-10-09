/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-023
 */
package com.dnd.qello.account.config;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountWithdrawalPropertiesTest {

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidGracePeriods")
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-023: 유예 기간이 없거나 0·음수면 생성 시 거절한다")
	void rejectsMissingOrNonPositiveGracePeriod(String label, Duration gracePeriod) {
		assertThatThrownBy(() -> new AccountWithdrawalProperties(gracePeriod))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-023: 양수 유예 기간은 그대로 보존한다")
	void keepsPositiveGracePeriod() {
		assertThat(new AccountWithdrawalProperties(Duration.ofSeconds(1)).gracePeriod())
				.isEqualTo(Duration.ofSeconds(1));
	}

	private static Stream<Arguments> invalidGracePeriods() {
		return Stream.of(
				Arguments.of("null", null),
				Arguments.of("zero", Duration.ZERO),
				Arguments.of("negative", Duration.ofDays(-1)));
	}

}
