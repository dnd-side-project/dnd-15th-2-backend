package com.dnd.qello.filtering.repository.jpa;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;

final class FilterJobJpaMapper {

	private FilterJobJpaMapper() { }

	static FilterJob toDomain(FilterJobJpaEntity entity) {
		FilterTarget target = new FilterTarget(entity.getTargetType(), entity.getTargetId(), entity.getTargetVersion());
		return FilterJob.restore(entity.getId(), target, entity.getFilterReleaseId(), entity.getStatus(),
			entity.getAttemptGeneration(), entity.isManuallyResolved(), entity.getResolvedVerdict(),
			entity.getIdempotencyKey(), entity.getDeadlineAt(), entity.getCreatedAt(), entity.getUpdatedAt());
	}
}
