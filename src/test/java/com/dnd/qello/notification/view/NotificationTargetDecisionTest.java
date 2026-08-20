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
	@DisplayName("AVAILABLE 상태는 navigable이 true이고 reason이 없고 fallback이 NONE이다")
	void availableIsNavigableWithoutReason() {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, NotificationTargetState.AVAILABLE);

		assertThat(decision.navigable()).isTrue();
		assertThat(decision.reason()).isNull();
		assertThat(decision.fallback()).isEqualTo(NotificationTargetDecision.Fallback.NONE);
	}

	@ParameterizedTest
	@EnumSource(value = NotificationTargetState.class, names = "AVAILABLE", mode = EXCLUDE)
	@DisplayName("AVAILABLE이 아닌 5개 상태는 navigable이 false이고 reason이 state와 같다")
	void nonAvailableStatesAreNotNavigableAndCarryReason(NotificationTargetState state) {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, state);

		assertThat(decision.navigable()).isFalse();
		assertThat(decision.reason()).isEqualTo(state);
	}

	@Test
	@DisplayName("EXPIRED는 fallback이 INBOX다")
	void expiredFallsBackToInbox() {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, NotificationTargetState.EXPIRED);

		assertThat(decision.fallback()).isEqualTo(NotificationTargetDecision.Fallback.INBOX);
	}

	@ParameterizedTest
	@EnumSource(value = NotificationTargetState.class, names = {"GONE", "BLOCKED", "HIDDEN"})
	@DisplayName("GONE·BLOCKED·HIDDEN은 fallback이 FEED_HOME이다")
	void goneBlockedHiddenFallBackToFeedHome(NotificationTargetState state) {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.DIRECTION_POST, 771L, state);

		assertThat(decision.fallback()).isEqualTo(NotificationTargetDecision.Fallback.FEED_HOME);
	}

	@Test
	@DisplayName("NONE 대상은 id·state 없이 만들 수 있고 navigable이 false, reason이 없고 fallback이 FEED_HOME이다")
	void noneTargetIsNotNavigableWithoutReason() {
		NotificationTargetDecision decision = new NotificationTargetDecision(
			NotificationTargetKind.NONE, null, null);

		assertThat(decision.navigable()).isFalse();
		assertThat(decision.reason()).isNull();
		assertThat(decision.fallback()).isEqualTo(NotificationTargetDecision.Fallback.FEED_HOME);
	}

	@Test
	@DisplayName("NONE 대상에 id를 주면 거부한다")
	void rejectsIdForNoneTarget() {
		assertThatThrownBy(() -> new NotificationTargetDecision(NotificationTargetKind.NONE, 1L, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("non-NONE 대상에 state가 없으면 거부한다")
	void rejectsMissingStateForNonNoneTarget() {
		assertThatThrownBy(() -> new NotificationTargetDecision(NotificationTargetKind.ANSWER, 5L, null))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
