/**
 * Created at: 2026-08-17T21:00:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-INT-001 through INT-022
 */
package com.dnd.qello;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;

/**
 * GH-125 답변 제출·공개 통합 테스트 3개(AnswerSubmissionApiIntegrationTest,
 * AnswerSubmissionConcurrencyIntegrationTest, AnswerModerationPublicationIntegrationTest)가
 * 공통으로 필요로 하는 "승격된 filter release" fixture. moderation job intake는 승격된
 * release가 없으면 fail-closed로 거절하므로 각 테스트가 시작 전에 이 fixture를 호출해야 한다.
 */
final class AnswerModerationReleaseTestFixture {

	private static final String MODEL_SNAPSHOT = "omni-moderation-2026-08-01";

	private AnswerModerationReleaseTestFixture() {
	}

	static FilterRelease promotedRelease(FilterReleaseRegistryService releaseRegistryService, long operatorUserId) {
		var candidate = releaseRegistryService.createCandidate("norm-v1", "ruleset-v1", "category-map-v1", MODEL_SNAPSHOT);
		releaseRegistryService.markOfflineEvaluated(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		releaseRegistryService.designateShadow(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		releaseRegistryService.designateCanary(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		return releaseRegistryService.promote(candidate.id(), operatorUserId, new OperatorReason("TEST", "테스트 근거"));
	}
}
