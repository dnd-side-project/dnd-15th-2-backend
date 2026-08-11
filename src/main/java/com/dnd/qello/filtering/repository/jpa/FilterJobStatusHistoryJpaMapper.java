package com.dnd.qello.filtering.repository.jpa;

import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;

final class FilterJobStatusHistoryJpaMapper {

	private FilterJobStatusHistoryJpaMapper() { }

	static FilterJobStatusHistoryEntry toDomain(FilterJobStatusHistoryJpaEntity entity) {
		return new FilterJobStatusHistoryEntry(entity.getId(), entity.getFilterJobId(), entity.getFromStatus(),
			entity.getToStatus(), entity.getReason(), entity.getOccurredAt());
	}
}
