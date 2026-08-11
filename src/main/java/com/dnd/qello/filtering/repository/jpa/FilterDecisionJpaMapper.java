package com.dnd.qello.filtering.repository.jpa;

import com.dnd.qello.filtering.domain.FilterDecision;

final class FilterDecisionJpaMapper {

	private FilterDecisionJpaMapper() { }

	static FilterDecision toDomain(FilterDecisionJpaEntity entity) {
		return FilterDecision.restore(entity.getId(), entity.getFilterJobId(), entity.getAttemptGeneration(),
			entity.getVerdict(), entity.getRequestedReleaseId(), entity.getActualModel(), entity.getDecidedAt());
	}
}
