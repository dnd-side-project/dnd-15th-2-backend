package com.dnd.qello.filtering.repository;

import java.util.List;

import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;

public interface FilterJobStatusHistoryRepository {

	FilterJobStatusHistoryEntry save(FilterJobStatusHistoryEntry entry);

	List<FilterJobStatusHistoryEntry> findByFilterJobId(long filterJobId);
}
