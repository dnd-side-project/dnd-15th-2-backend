package com.dnd.qello.filtering.web;

import jakarta.validation.constraints.NotBlank;

// candidate release 생성 요청 본문. 각 ref는 정규화 규칙·로컬 사전·threshold·model
// snapshot 정의를 가리키는 opaque 문자열이며, "latest" alias는 서비스가 거절한다.
public record CreateFilterReleaseRequest(
	@NotBlank(message = "normalizationRef는 필수입니다") String normalizationRef,
	@NotBlank(message = "localRulesetRef는 필수입니다") String localRulesetRef,
	@NotBlank(message = "categoryMappingRef는 필수입니다") String categoryMappingRef,
	@NotBlank(message = "modelSnapshot은 필수입니다") String modelSnapshot
) {
}
