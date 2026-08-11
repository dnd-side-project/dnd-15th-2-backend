package com.dnd.qello.filtering.repository.jpa;

import com.dnd.qello.filtering.domain.FilterRelease;

final class FilterReleaseJpaMapper {

	private FilterReleaseJpaMapper() { }

	static FilterRelease toDomain(FilterReleaseJpaEntity entity) {
		return FilterRelease.restore(entity.getId(), entity.getNormalizationRef(), entity.getLocalRulesetRef(),
			entity.getCategoryMappingRef(), entity.getModelSnapshot(), entity.getStatus(), entity.getPromotedAt(),
			entity.getCreatedAt());
	}
}
