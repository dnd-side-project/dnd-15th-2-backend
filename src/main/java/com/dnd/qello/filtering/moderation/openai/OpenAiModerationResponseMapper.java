package com.dnd.qello.filtering.moderation.openai;

import java.util.List;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.ModerationProviderResult;

// OpenAI 응답을 벤더 중립 ModerationProviderResult로 옮긴다. flagged·categories·
// category_scores를 그대로 전달할 뿐 최종 판정을 내리지 않는다(INV-PIPE-003) —
// 최종 판정은 PolicyEngine의 책임이다. 응답 자체가 기대한 모양이 아니면(결과 없음
// 등) 판정 불가로 처리한다 — 임의의 ALLOW/BLOCK을 만들어내지 않는다.
public final class OpenAiModerationResponseMapper {

	private OpenAiModerationResponseMapper() {
	}

	public static ModerationProviderResult toProviderResult(OpenAiModerationResponse response) {
		if (response == null || response.model() == null || response.model().isBlank()) {
			throw new FilteringException(
				FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "invalid_response");
		}
		List<OpenAiModerationResponse.Result> results = response.results();
		if (results == null || results.isEmpty()) {
			throw new FilteringException(
				FilteringErrorCode.MODERATION_PROVIDER_UNAVAILABLE, "openai", "empty_results");
		}
		OpenAiModerationResponse.Result result = results.get(0);
		return new ModerationProviderResult(
			result.flagged(), result.categories(), result.categoryScores(), response.model());
	}
}
