package com.dnd.qello.filtering.moderation.openai;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import com.dnd.qello.filtering.domain.ModerationFailureClassification;

// OpenAI moderation 호출 실패를 HTTP status 기준으로 분류한다(#109). 429는
// OpenAiModerationProviderClient가 이 분류기에 도달하기 전에 이미
// ModerationRateLimitedException으로 먼저 분기하므로 여기서는 다루지 않는다.
// 429를 제외한 4xx 전체는 우리 쪽 설정 문제(인증·권한·결제·quota·invalid
// request)로 간주해 NON_TARGET_CLIENT_ERROR로 묶는다 — target snapshot 자체의
// 장애 증거로 집계되지 않게 하기 위해서다(INV-HLT-003). HTTP status가 없는
// timeout/connection 실패는 TIMEOUT_OR_NETWORK로 분류한다.
final class OpenAiModerationFailureClassifier {

	private OpenAiModerationFailureClassifier() {
	}

	static ModerationFailureClassification classify(RestClientException e) {
		if (e instanceof HttpStatusCodeException httpError) {
			if (httpError.getStatusCode().is5xxServerError()) {
				return ModerationFailureClassification.SERVER_ERROR;
			}
			if (httpError.getStatusCode().is4xxClientError()) {
				return ModerationFailureClassification.NON_TARGET_CLIENT_ERROR;
			}
			return ModerationFailureClassification.UNKNOWN;
		}
		return ModerationFailureClassification.TIMEOUT_OR_NETWORK;
	}
}
