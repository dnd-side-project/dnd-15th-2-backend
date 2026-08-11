package com.dnd.qello.filtering.repository.jpa;

import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.ManualReviewCase;

final class ManualReviewCaseJpaMapper {

	private ManualReviewCaseJpaMapper() { }

	static ManualReviewCase toDomain(ManualReviewCaseJpaEntity entity) {
		FilterTarget target = new FilterTarget(entity.getTargetType(), entity.getTargetId(), entity.getTargetVersion());
		return ManualReviewCase.restore(entity.getId(), target, entity.getFilterReleaseId(), entity.getCreatedAt());
	}
}
