package com.dnd.qello.filtering.repository.jpa;

import com.dnd.qello.filtering.domain.AppealCase;

final class AppealCaseJpaMapper {

	private AppealCaseJpaMapper() { }

	static AppealCase toDomain(AppealCaseJpaEntity entity) {
		return AppealCase.restore(entity.getId(), entity.getTargetType(), entity.getTargetId(),
			entity.getFilterDecisionId(), entity.getCreatedAt());
	}
}
