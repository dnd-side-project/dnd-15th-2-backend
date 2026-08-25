/**
 * Created at: 2026-08-25T13:14:21+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-002, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-003, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-004, TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-UNIT-005
 */
package com.dnd.qello.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.notification.domain.NotificationType;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy;
import com.dnd.qello.notification.push.policy.PushGroupingPolicy.GroupingMode;

class PushGroupingPolicyTest {

	private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");
	private final PushGroupingPolicy policy = new PushGroupingPolicy(PushPolicyPropertiesTest.validProperties());

	@Test
	@DisplayName("UNIT-002: 답변 수신과 공감은 같은 수신자여도 서로 다른 window group이다")
	void separatesAnswerReceivedAndReactedGroups() {
		var received = policy.decide(NotificationType.ANSWER_RECEIVED, 11L, 101L, null, CREATED_AT);
		var reacted = policy.decide(NotificationType.ANSWER_REACTED, 11L, 102L, null, CREATED_AT);

		assertThat(received.mode()).isEqualTo(GroupingMode.WINDOWED);
		assertThat(reacted.mode()).isEqualTo(GroupingMode.WINDOWED);
		assertThat(received.aggregationKey()).isNotEqualTo(reacted.aggregationKey());
		assertThat(received.collectUntil()).isEqualTo(CREATED_AT.plusSeconds(600));
	}

	@Test
	@DisplayName("UNIT-003: 같은 recipient와 type은 collectUntil 정각까지 열린 window에 합류한다")
	void joinsOnlyWithinMatchingWindowInclusive() {
		var openGroup = new PushGroupingPolicy.OpenGroup(11L, NotificationType.ANSWER_RECEIVED,
			CREATED_AT.plusSeconds(600));

		assertThat(policy.joins(openGroup, NotificationType.ANSWER_RECEIVED, 11L,
			CREATED_AT.plusSeconds(600).minusNanos(1))).isTrue();
		assertThat(policy.joins(openGroup, NotificationType.ANSWER_RECEIVED, 11L,
			CREATED_AT.plusSeconds(600))).isTrue();
		assertThat(policy.joins(openGroup, NotificationType.ANSWER_RECEIVED, 11L,
			CREATED_AT.plusSeconds(600).plusNanos(1))).isFalse();
		assertThat(policy.joins(openGroup, NotificationType.ANSWER_REACTED, 11L, CREATED_AT)).isFalse();
		assertThat(policy.joins(openGroup, NotificationType.ANSWER_RECEIVED, 12L, CREATED_AT)).isFalse();
	}

	@Test
	@DisplayName("UNIT-004: window 대상 외 알림은 notification별 singleton이고 즉시 수집을 닫는다")
	void createsSingletonGroupsForNonWindowTypes() {
		for (NotificationType type : new NotificationType[] {
			NotificationType.DIRECTION_POST_RECEIVED,
			NotificationType.REPORT_RESOLVED,
			NotificationType.QUESTION_PROPOSAL_REVIEWED
		}) {
			var decision = policy.decide(type, 11L, 101L, null, CREATED_AT);

			assertThat(decision.mode()).isEqualTo(GroupingMode.SINGLETON);
			assertThat(decision.aggregationKey()).isEqualTo("push-notification:101");
			assertThat(decision.collectUntil()).isEqualTo(CREATED_AT);
		}
	}

	@Test
	@DisplayName("UNIT-005: 질문 추천은 assignment가 아니라 수신자와 cycle로 결정적 group key를 만든다")
	void groupsRecommendationsByRecipientAndCycle() {
		var first = policy.decide(NotificationType.QUESTION_RECOMMENDED, 11L, 101L, 31L, CREATED_AT);
		var sameCycle = policy.decide(NotificationType.QUESTION_RECOMMENDED, 11L, 102L, 31L,
			CREATED_AT.plusSeconds(1));
		var otherCycle = policy.decide(NotificationType.QUESTION_RECOMMENDED, 11L, 103L, 32L, CREATED_AT);

		assertThat(first.mode()).isEqualTo(GroupingMode.RECOMMENDATION_CYCLE);
		assertThat(first.aggregationKey()).isEqualTo(sameCycle.aggregationKey());
		assertThat(first.aggregationKey()).isNotEqualTo(otherCycle.aggregationKey());
		assertThatThrownBy(() -> policy.decide(NotificationType.QUESTION_RECOMMENDED, 11L, 104L, null, CREATED_AT))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
