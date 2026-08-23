package com.dnd.qello.filtering.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// candidate release 생성 요청 본문. 각 ref는 정규화 규칙·로컬 사전·threshold·model
// snapshot 정의를 가리키는 opaque 문자열이며, "latest" alias는 서비스가 거절한다.
@Schema(description = "새 필터링 검사 설정을 만드는 요청입니다.")
public record CreateFilterReleaseRequest(
	@Schema(description = "정규화 규칙을 가리키는 참조 값입니다.")
	@NotBlank(message = "normalizationRef는 필수입니다") String normalizationRef,
	@Schema(description = "로컬 규칙을 가리키는 참조 값입니다.")
	@NotBlank(message = "localRulesetRef는 필수입니다") String localRulesetRef,
	@Schema(description = "분류 매핑을 가리키는 참조 값입니다.")
	@NotBlank(message = "categoryMappingRef는 필수입니다") String categoryMappingRef,
	@Schema(description = "사용할 모델 snapshot을 가리키는 참조 값입니다.")
	@NotBlank(message = "modelSnapshot은 필수입니다") String modelSnapshot
) {
}
