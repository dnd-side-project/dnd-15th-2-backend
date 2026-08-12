package com.dnd.qello.filtering.moderation.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.ModerationProviderClient;
import com.dnd.qello.filtering.moderation.ModerationProviderResult;

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
	@Override
	public ModerationProviderResult moderate(String normalizedContent, String modelSnapshot) {
		OpenAiModerationResponse response;
		try {
			response = restClient.post()
				.uri(MODERATIONS_PATH)
				.body(new OpenAiModerationRequest(normalizedContent, modelSnapshot))
				.retrieve()
				.body(OpenAiModerationResponse.class);
		} catch (RestClientException e) {
			String reason = e.getClass().getSimpleName();
			log.warn("OpenAI moderation 호출 실패: model={}, reason={}", modelSnapshot, reason);
			throw new FilteringException(FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", reason);
		}
		return OpenAiModerationResponseMapper.toProviderResult(response);
	}
}
