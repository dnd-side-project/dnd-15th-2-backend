package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// pipeline 실행 요청. filterJobId가 있으면 같은 filterJobId·attemptGeneration으로
// FilterDecision을 저장한다(이미 FilterJob이 존재하는 답변 비동기 경로를 가정).
// filterJobId가 없으면 영속화 없이 판정 결과만 반환한다(예: 닉네임 동기 사전
// 검증). 이 조건부 영속화는 TEST-PLAN-GH-105-MODERATION-PIPELINE §2의 설계
// 가정이며, 닉네임·답변 실제 호출 배선(#106/#107)에서 확정 여부를 재검토한다.
public record ModerationPipelineRequest(
	FilterTargetType contentType,
	ModerationLanguage language,
	String rawContent,
	FilterRelease release,
	Long filterJobId,
	Integer attemptGeneration
) {

	public ModerationPipelineRequest {
		if (contentType == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "contentType");
		}
		if (language == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "language");
		}
		if (rawContent == null || rawContent.isBlank()) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "rawContent");
		}
		if (release == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "release");
		}
		if (filterJobId != null && filterJobId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterJobId", "filterJobId는 양수여야 합니다");
		}
		if (filterJobId != null && (attemptGeneration == null || attemptGeneration < 1)) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "attemptGeneration",
				"filterJobId가 있으면 attemptGeneration은 1 이상이어야 합니다");
		}
	}

	public static ModerationPipelineRequest ephemeral(
		FilterTargetType contentType, ModerationLanguage language, String rawContent, FilterRelease release
	) {
		return new ModerationPipelineRequest(contentType, language, rawContent, release, null, null);
	}

	public static ModerationPipelineRequest forJob(
		FilterTargetType contentType, ModerationLanguage language, String rawContent, FilterRelease release,
		long filterJobId, int attemptGeneration
	) {
		return new ModerationPipelineRequest(
			contentType, language, rawContent, release, filterJobId, attemptGeneration);
	}
}
