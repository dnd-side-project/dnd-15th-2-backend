/*
 * Created at: 2026-10-09T20:42:16+09:00
 * Source scenario: TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME-UNIT-004
 */
package com.dnd.qello.auth.config;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OperatorSessionPropertiesTest {

	@Test
	@DisplayName("UNIT-004: absolute-timeout이 없으면 바인딩에 실패한다")
	void rejectsMissingAbsoluteTimeout() {
		assertThatNullPointerException()
				.isThrownBy(() -> new OperatorSessionProperties(null))
				.withMessageContaining("qello.auth.operator-session.absolute-timeout");
	}

	@ParameterizedTest(name = "absolute-timeout = {0}")
	@ValueSource(strings = {"PT0S", "-PT1S"})
	@DisplayName("UNIT-004: absolute-timeout이 0 이하면 바인딩에 실패한다")
	void rejectsNonPositiveAbsoluteTimeout(String value) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new OperatorSessionProperties(Duration.parse(value)))
				.withMessageContaining("qello.auth.operator-session.absolute-timeout");
	}

	@Test
	@DisplayName("UNIT-004: 양수 absolute-timeout은 그대로 보관한다")
	void keepsPositiveAbsoluteTimeout() {
		assertThat(new OperatorSessionProperties(Duration.ofHours(12)).absoluteTimeout())
				.isEqualTo(Duration.ofHours(12));
	}
}
