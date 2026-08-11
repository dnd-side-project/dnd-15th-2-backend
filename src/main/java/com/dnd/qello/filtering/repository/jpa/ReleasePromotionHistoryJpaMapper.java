package com.dnd.qello.filtering.repository.jpa;

import com.dnd.qello.filtering.domain.ReleasePromotionHistoryEntry;

final class ReleasePromotionHistoryJpaMapper {

	private ReleasePromotionHistoryJpaMapper() { }

	static ReleasePromotionHistoryEntry toDomain(ReleasePromotionHistoryJpaEntity entity) {
		return new ReleasePromotionHistoryEntry(entity.getId(), entity.getReleaseId(), entity.getAction(),
			entity.getPreviousActiveReleaseId(), entity.getOperatorUserId(), entity.getOccurredAt());
	}
}
