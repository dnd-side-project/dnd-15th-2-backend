package com.dnd.qello.filtering.moderation.openai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// OpenAI Moderation API 응답(DESIGN.md 결정 8). 공급자가 문서화되지 않은 필드를
// 추가해도 파싱이 깨지지 않도록 알 수 없는 최상위 필드는 무시한다. flagged는
// 참고용 원시 신호일 뿐이며 이 레코드 자체는 어떤 최종 판정도 내리지 않는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiModerationResponse(String id, String model, List<Result> results) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(
		boolean flagged,
		Map<String, Boolean> categories,
		@JsonProperty("category_scores") Map<String, Double> categoryScores
	) {
	}
}
