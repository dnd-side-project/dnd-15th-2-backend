/**
 * Created at: 2026-08-17T19:00:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-010 through INT-014,
 * INT-017, INT-018, INT-021
 * Source scenario: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION-INT-007 through INT-010 (targetType overloads, added 2026-10-07T22:14:59+09:00)
 */
package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code AnswerModerationEventPayloads}는 package-private이라
 * {@code com.dnd.qello} 패키지의 통합 테스트가 직접 verdict/deadline outbox payload를 만들 수
 * 없다. 같은 패키지(`com.dnd.qello.filtering.moderation`)의 integrationTest source set에
 * 이 지원 클래스를 둬 package-private 접근만 넓히고, main 소스의 접근 제한 자체는 바꾸지 않는다.
 */
public final class AnswerModerationEventPayloadsTestSupport {

	private AnswerModerationEventPayloadsTestSupport() {
	}

	public static String verdictReadyJson(ObjectMapper objectMapper, long filterJobId, long answerId,
			FilterVerdict verdict) {
		return verdictReadyJson(objectMapper, filterJobId, FilterTargetType.ANSWER, answerId, verdict);
	}

	public static String verdictReadyJson(ObjectMapper objectMapper, long filterJobId, FilterTargetType targetType,
			long targetId, FilterVerdict verdict) {
		AnswerModerationEventPayloads.VerdictReady payload = new AnswerModerationEventPayloads.VerdictReady(
				filterJobId, targetType, targetId, 0L, verdict);
		return AnswerModerationEventPayloads.toJson(objectMapper, payload);
	}

	public static String deadlineElapsedJson(ObjectMapper objectMapper, long filterJobId, long answerId) {
		return deadlineElapsedJson(objectMapper, filterJobId, FilterTargetType.ANSWER, answerId);
	}

	public static String deadlineElapsedJson(ObjectMapper objectMapper, long filterJobId, FilterTargetType targetType,
			long targetId) {
		AnswerModerationEventPayloads.DeadlineElapsed payload = new AnswerModerationEventPayloads.DeadlineElapsed(
				filterJobId, targetType, targetId, 0L);
		return AnswerModerationEventPayloads.toJson(objectMapper, payload);
	}
}
