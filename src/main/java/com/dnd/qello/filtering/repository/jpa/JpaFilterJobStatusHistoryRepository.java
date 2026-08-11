package com.dnd.qello.filtering.repository.jpa;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;

@Repository
@Transactional(readOnly = true)
public class JpaFilterJobStatusHistoryRepository implements FilterJobStatusHistoryRepository {

	private final SpringDataFilterJobStatusHistoryRepository repository;

	public JpaFilterJobStatusHistoryRepository(SpringDataFilterJobStatusHistoryRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public FilterJobStatusHistoryEntry save(FilterJobStatusHistoryEntry entry) {
		return FilterJobStatusHistoryJpaMapper.toDomain(
			repository.saveAndFlush(new FilterJobStatusHistoryJpaEntity(entry)));
	}

	@Override
	public List<FilterJobStatusHistoryEntry> findByFilterJobId(long filterJobId) {
		return repository.findByFilterJobId(filterJobId).stream()
			.map(FilterJobStatusHistoryJpaMapper::toDomain)
			.toList();
	}
}
