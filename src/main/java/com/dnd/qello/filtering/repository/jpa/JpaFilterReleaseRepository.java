package com.dnd.qello.filtering.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;

@Repository
@Transactional(readOnly = true)
public class JpaFilterReleaseRepository implements FilterReleaseRepository {

	private final SpringDataFilterReleaseRepository repository;

	public JpaFilterReleaseRepository(SpringDataFilterReleaseRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public FilterRelease save(FilterRelease release) {
		return FilterReleaseJpaMapper.toDomain(repository.saveAndFlush(new FilterReleaseJpaEntity(release)));
	}

	@Override
	public Optional<FilterRelease> findById(long id) {
		return repository.findById(id).map(FilterReleaseJpaMapper::toDomain);
	}

	@Override
	public Optional<FilterRelease> findCurrentlyPromoted() {
		return repository.findByStatus(FilterReleaseStatus.PROMOTED).map(FilterReleaseJpaMapper::toDomain);
	}

	@Override
	public List<FilterRelease> findAll() {
		return repository.findAll().stream().map(FilterReleaseJpaMapper::toDomain).toList();
	}
}
