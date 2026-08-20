/**
 * Created at: 2026-08-20T15:30:00+09:00
 * Source scenario: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ-UNIT-002
 */
package com.dnd.qello.notification.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationTargetDecisionTest {

	@Test
	@DisplayName("AVAILABLE 상태는 navigable이 true이고 reason이 없다")
	void availableIsNavigableWithoutReason() {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, NotificationTargetState.AVAILABLE,
			NotificationTargetDecision.Fallback.NONE);

		assertThat(decision.navigable()).isTrue();
		assertThat(decision.reason()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = NotificationTargetState.class, names = "AVAILABLE", mode = EXCLUDE)
	@DisplayName("AVAILABLE이 아닌 5개 상태는 navigable이 false이고 reason이 state와 같다")
	void nonAvailableStatesAreNotNavigableAndCarryReason(NotificationTargetState state) {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, state, NotificationTargetDecision.Fallback.FEED_HOME);

		assertThat(decision.navigable()).isFalse();
		assertThat(decision.reason()).isEqualTo(state);
	}

	@Test
	@DisplayName("NONE 대상은 id·state 없이 만들 수 있고 navigable이 false, reason이 없다")
	void noneTargetIsNotNavigableWithoutReason() {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.NONE, null, null, NotificationTargetDecision.Fallback.NONE);

		assertThat(decision.navigable()).isFalse();
		assertThat(decision.reason()).isNull();
	}

	@Test
	@DisplayName("NONE 대상에 id를 주면 거부한다")
	void rejectsIdForNoneTarget() {
		assertThatThrownBy(() -> new NotificationTargetDecision(
			NotificationTargetKind.NONE, 1L, null, NotificationTargetDecision.Fallback.NONE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("non-NONE 대상에 state가 없으면 거부한다")
	void rejectsMissingStateForNonNoneTarget() {
		assertThatThrownBy(() -> new NotificationTargetDecision(
			NotificationTargetKind.ANSWER, 5L, null, NotificationTargetDecision.Fallback.NONE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("fallback이 없으면 거부한다")
	void rejectsMissingFallback() {
		assertThatThrownBy(() -> new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, NotificationTargetState.AVAILABLE, null))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
