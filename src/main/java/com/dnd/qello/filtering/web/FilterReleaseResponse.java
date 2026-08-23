package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "필터링 검사 설정과 적용 상태를 담은 응답입니다.")
public record FilterReleaseResponse(
	@Schema(description = "검사 설정 식별자입니다.")
	long id,
	@Schema(description = "정규화 규칙을 가리키는 참조 값입니다.")
	String normalizationRef,
	@Schema(description = "로컬 규칙을 가리키는 참조 값입니다.")
	String localRulesetRef,
	@Schema(description = "분류 매핑을 가리키는 참조 값입니다.")
	String categoryMappingRef,
	@Schema(description = "사용한 모델 snapshot을 가리키는 참조 값입니다.")
	String modelSnapshot,
	@Schema(description = "검사 설정의 현재 상태입니다.")
	FilterReleaseStatus status,
	@Schema(description = "검사 설정이 적용된 시각입니다. 아직 적용되지 않았으면 값이 없습니다.")
	Instant promotedAt,
	@Schema(description = "검사 설정이 생성된 시각입니다.")
	Instant createdAt
) {

	public static FilterReleaseResponse from(FilterRelease release) {
		return new FilterReleaseResponse(
			release.id(),
			release.normalizationRef(),
			release.localRulesetRef(),
			release.categoryMappingRef(),
			release.modelSnapshot(),
			release.status(),
			release.promotedAt(),
			release.createdAt()
		);
	}
}
