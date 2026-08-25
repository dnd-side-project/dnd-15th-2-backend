/**
 * Created at: 2026-08-25T13:14:21+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-016
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.group.ClaimedPushDispatchGroup;
import com.dnd.qello.notification.push.group.PushDispatchGroup;
import com.dnd.qello.notification.push.group.PushDispatchGroupStatus;

class PushDispatchGroupStateTest {

	private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");

	@Test
	@DisplayName("UNIT-016: group 상태는 허용된 전이만 허용하고 terminal 상태를 명확히 구분한다")
	void exposesAllowedStateTransitionsAndTerminalStates() {
		assertThat(PushDispatchGroupStatus.COLLECTING.allowsTransitionTo(PushDispatchGroupStatus.PENDING)).isTrue();
		assertThat(PushDispatchGroupStatus.PENDING.allowsTransitionTo(PushDispatchGroupStatus.PROCESSING)).isTrue();
		assertThat(PushDispatchGroupStatus.PROCESSING.allowsTransitionTo(PushDispatchGroupStatus.COMPLETED)).isTrue();
		assertThat(PushDispatchGroupStatus.COMPLETED.allowsTransitionTo(PushDispatchGroupStatus.PENDING)).isFalse();
		assertThat(PushDispatchGroupStatus.CANCELLED.isTerminal()).isTrue();
		assertThat(PushDispatchGroupStatus.FAILED.isTerminal()).isFalse();
	}

	@Test
	@DisplayName("UNIT-016: group은 V28의 time, budget, terminal 완료 시각 불변식을 생성 시점에 검증한다")
	void rejectsDatabaseInvariantViolations() {
		assertThatThrownBy(() -> group(PushDispatchGroupStatus.PENDING, CREATED_AT.plusSeconds(1), CREATED_AT,
			CREATED_AT.plusSeconds(3600), null, null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> group(PushDispatchGroupStatus.PENDING, CREATED_AT, CREATED_AT.plusSeconds(1),
			CREATED_AT.plusSeconds(3600), LocalDate.of(2026, 8, 25), null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> group(PushDispatchGroupStatus.COMPLETED, CREATED_AT, CREATED_AT,
			CREATED_AT.plusSeconds(3600), null, null, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-016: claim generation은 V28 attemptCount와 일치할 때만 fenced terminal 처리한다")
	void fencesStaleTerminalGenerationAgainstPersistedAttemptCount() {
		PushDispatchGroup group = group(PushDispatchGroupStatus.PROCESSING, CREATED_AT, CREATED_AT,
			CREATED_AT.plusSeconds(3600), null, null, null, 2);
		ClaimedPushDispatchGroup claim = new ClaimedPushDispatchGroup(10L, group.attemptCount(),
			CREATED_AT.plusSeconds(30));

		assertThat(claim.matchesGeneration(group.attemptCount())).isTrue();
		assertThat(claim.matchesGeneration(group.attemptCount() + 1)).isFalse();
		assertThatThrownBy(() -> new ClaimedPushDispatchGroup(10L, 0, CREATED_AT.plusSeconds(30)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static PushDispatchGroup group(PushDispatchGroupStatus status, Instant windowStartedAt, Instant collectUntil,
		Instant policyExpiresAt, LocalDate budgetLocalDate, Instant budgetConsumedAt, Instant completedAt) {
		return group(status, windowStartedAt, collectUntil, policyExpiresAt, budgetLocalDate, budgetConsumedAt,
			completedAt, 1);
	}

	private static PushDispatchGroup group(PushDispatchGroupStatus status, Instant windowStartedAt, Instant collectUntil,
		Instant policyExpiresAt, LocalDate budgetLocalDate, Instant budgetConsumedAt, Instant completedAt,
		int attemptCount) {
		return new PushDispatchGroup(10L, 1L, NotificationType.ANSWER_RECEIVED, "push-window:1:ANSWER_RECEIVED:0",
			status, windowStartedAt, collectUntil, policyExpiresAt, attemptCount, CREATED_AT, budgetLocalDate, budgetConsumedAt,
			null, CREATED_AT, completedAt);
	}
}
