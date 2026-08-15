package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

// #107이 발행/소비하는 outbox payload의 JSON 직렬화 지점과 dedup key 형식을 한 곳으로
// 모은다. answer 콘텐츠(rawContent)는 자유 텍스트라서 다른 outbox 이벤트처럼 수동 문자열
// 결합으로 JSON을 만들면 이스케이프 오류로 깨질 수 있다 — 반드시 ObjectMapper를 쓴다.
final class AnswerModerationEventPayloads {

	private AnswerModerationEventPayloads() { }

	// MODERATION_EXECUTION_REQUESTED payload. pipeline 실행에 필요한 입력을 그대로
	// 담는다 — 이 시스템은 target reference로 answer 콘텐츠를 다시 조회하지 않는다.
	// ignoreUnknown: 배포된 producer가 필드를 먼저 추가해도 구 버전 consumer가
	// 역직렬화에 실패하지 않게 한다.
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExecutionRequested(
		long filterJobId, FilterTargetType targetType, long targetId, long targetVersion,
		String rawContent, ModerationLanguage language, long filterReleaseId, int attemptGeneration
	) { }

	// MODERATION_VERDICT_READY payload. deadline 전후 어느 쪽에서 도착했든 같은
	// 모양이다 — 늦게 도착했다는 사실 자체는 이 payload가 아니라 별도로 이미
	// MODERATION_DEADLINE_ELAPSED가 나갔는지 여부로 판단한다.
	@JsonIgnoreProperties(ignoreUnknown = true)
	record VerdictReady(
		long filterJobId, FilterTargetType targetType, long targetId, long targetVersion, FilterVerdict verdict
	) { }

	// MODERATION_DEADLINE_ELAPSED payload. 이 신호는 승인(ALLOW)을 의미하지 않는다
	// (INV-ANS-003, INV-ANS-004) — 판정이 deadline 전에 도착하지 않았다는 사실만
	// 전달한다. 공개 여부·PUBLISHED_UNREVIEWED 적용은 답변 담당 코드의 정책 결정이다.
	@JsonIgnoreProperties(ignoreUnknown = true)
	record DeadlineElapsed(
		long filterJobId, FilterTargetType targetType, long targetId, long targetVersion
	) { }

	static String toJson(ObjectMapper objectMapper, Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new FilteringException(
				FilteringErrorCode.PAYLOAD_SERIALIZATION_FAILED, "payload", "payload 직렬화에 실패했습니다", e);
		}
	}

	static <T> T fromJson(ObjectMapper objectMapper, String json, Class<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException e) {
			throw new FilteringException(
				FilteringErrorCode.PAYLOAD_SERIALIZATION_FAILED, "payload", "payload 역직렬화에 실패했습니다", e);
		}
	}

	static String executionRequestedDedupKey(long filterJobId) {
		return "filter-job:" + filterJobId + ":EXECUTION_REQUESTED";
	}

	static String verdictReadyDedupKey(long filterJobId) {
		return "filter-job:" + filterJobId + ":VERDICT_READY";
	}

	static String deadlineElapsedDedupKey(long filterJobId) {
		return "filter-job:" + filterJobId + ":DEADLINE_ELAPSED";
	}
}
