package com.dnd.qello.filtering.moderation.openai;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.ModerationProviderClient;
import com.dnd.qello.filtering.moderation.ModerationProviderResult;
import com.dnd.qello.filtering.moderation.ModerationRateLimitedException;

// ModerationProviderClient의 유일한 프로덕션 구현체(DESIGN.md 결정 7·8). 의도적으로
// Spring 컴포넌트가 아니다 — 호출자가 RestClient(timeout·연결 풀 설정과 API 키
// 헤더 포함)를 직접 구성해 넘겨야, 닉네임·답변 각 경로가 서로 다른 인스턴스로
// 실행 자원을 격리할 수 있다(INV-RES-001, INV-RES-002). API 키는 이 클래스 어디에도
// 저장·로그하지 않는다 — 호출자가 구성한 RestClient의 기본 헤더에만 존재한다.
public class OpenAiModerationProviderClient implements ModerationProviderClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiModerationProviderClient.class);
	private static final String MODERATIONS_PATH = "/v1/moderations";

	private final RestClient restClient;

	public OpenAiModerationProviderClient(RestClient restClient) {
		this.restClient = restClient;
	}

	// timeout/error는 여기서 흡수해 FilteringException(MODERATION_PROVIDER_UNAVAILABLE)
	// 으로만 내보낸다 — 어떤 실패도 ALLOW/BLOCK으로 바꾸지 않는다. 예외 메시지에는
	// 원인 클래스 이름만 담고, 요청·응답 본문이나 인증 헤더는 포함하지 않는다.
	//
	// 429만 예외적으로 별도 타입(ModerationRateLimitedException, #108)으로 구분해
	// 던진다 — 호출자가 Retry-After를 다음 재시도 지연의 최소 하한으로 쓸 수 있게
	// 하기 위해서다. 다른 4xx/5xx/timeout은 이 구분 없이 기존 경로를 그대로 탄다.
	@Override
	public ModerationProviderResult moderate(String normalizedContent, String modelSnapshot) {
		OpenAiModerationResponse response;
		try {
			response = restClient.post()
				.uri(MODERATIONS_PATH)
				.body(new OpenAiModerationRequest(normalizedContent, modelSnapshot))
				.retrieve()
				.body(OpenAiModerationResponse.class);
		} catch (HttpClientErrorException.TooManyRequests tooManyRequests) {
			Duration retryAfter = parseRetryAfter(tooManyRequests.getResponseHeaders());
			log.warn("OpenAI moderation rate limit: model={}, retryAfterPresent={}", modelSnapshot,
				retryAfter != null);
			throw new ModerationRateLimitedException(retryAfter);
		} catch (RestClientException e) {
			String reason = e.getClass().getSimpleName();
			log.warn("OpenAI moderation 호출 실패: model={}, reason={}", modelSnapshot, reason);
			throw new FilteringException(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", reason);
		}
		return OpenAiModerationResponseMapper.toProviderResult(response);
	}

	// OpenAI는 Retry-After를 정수 초 단위(delta-seconds)로 반환한다. 헤더가 없거나
	// 정수로 파싱할 수 없거나 0 이하이면 힌트 없이 처리한다 — 호출자는 이때 순수
	// capped exponential backoff+jitter로 대체한다.
	private static Duration parseRetryAfter(HttpHeaders headers) {
		if (headers == null) {
			return null;
		}
		String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			long seconds = Long.parseLong(value.trim());
			return seconds > 0 ? Duration.ofSeconds(seconds) : null;
		} catch (NumberFormatException notNumeric) {
			return null;
		}
	}
}
