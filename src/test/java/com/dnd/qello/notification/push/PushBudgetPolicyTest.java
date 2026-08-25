/**
 * Created at: 2026-08-25T13:14:21+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-010, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-011, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-012, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-017
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy.BudgetSnapshot;
import com.dnd.qello.notification.push.policy.PushBudgetPolicy.Decision;

class PushBudgetPolicyTest {

	private final PushBudgetPolicy policy = new PushBudgetPolicy(PushPolicyPropertiesTest.validProperties());

	@Test
	@DisplayName("UNIT-010: 일반 알림은 예약량을 제외한 일반 한도까지만 허용한다")
	void deniesGeneralNotificationAfterGeneralLimit() {
		assertThat(policy.decide(new BudgetSnapshot(2, 2), NotificationType.ANSWER_RECEIVED))
			.isEqualTo(Decision.ALLOW_GENERAL);
		assertThat(policy.decide(new BudgetSnapshot(3, 3), NotificationType.ANSWER_RECEIVED))
			.isEqualTo(Decision.DENY);
	}

	@Test
	@DisplayName("UNIT-011: 방향글은 전체 한도 안에서 예약 우선권을 사용한다")
	void allowsDirectionPostUntilTotalLimit() {
		assertThat(policy.decide(new BudgetSnapshot(4, 3), NotificationType.DIRECTION_POST_RECEIVED))
			.isEqualTo(Decision.ALLOW_PRIORITY);
		assertThat(policy.decide(new BudgetSnapshot(5, 3), NotificationType.DIRECTION_POST_RECEIVED))
			.isEqualTo(Decision.DENY);
	}

	@Test
	@DisplayName("UNIT-012: budget date는 같은 Instant라도 caller가 준 account zone에 따라 계산한다")
	void calculatesBudgetDateFromSuppliedAccountZone() {
		Instant instant = Instant.parse("2026-08-25T00:30:00Z");

		assertThat(policy.budgetDate(instant, ZoneId.of("Asia/Seoul"))).isEqualTo(LocalDate.of(2026, 8, 25));
		assertThat(policy.budgetDate(instant, ZoneId.of("America/Los_Angeles"))).isEqualTo(LocalDate.of(2026, 8, 24));
		assertThatThrownBy(() -> policy.budgetDate(null, ZoneId.of("Asia/Seoul")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy.budgetDate(instant, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("UNIT-017: policy는 snapshot의 이미 소비된 group을 다시 차감하지 않고 한도만 결정한다")
	void makesDecisionFromSuppliedBudgetSnapshotOnly() {
		BudgetSnapshot snapshot = new BudgetSnapshot(3, 3);

		assertThat(policy.decide(snapshot, NotificationType.ANSWER_REACTED)).isEqualTo(Decision.DENY);
		assertThat(snapshot.consumedTotal()).isEqualTo(3);
		assertThat(snapshot.consumedGeneral()).isEqualTo(3);
	}
}
