package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.repository.FilterJobRepository;

@Repository
@Transactional(readOnly = true)
public class JpaFilterJobRepository implements FilterJobRepository {

	private final SpringDataFilterJobRepository repository;

	public JpaFilterJobRepository(SpringDataFilterJobRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public FilterJob save(FilterJob job) {
		return FilterJobJpaMapper.toDomain(repository.saveAndFlush(new FilterJobJpaEntity(job)));
	}

	@Override
	public Optional<FilterJob> findById(long id) {
		return repository.findById(id).map(FilterJobJpaMapper::toDomain);
	}

	@Override
	public Optional<FilterJob> findByIdempotencyKey(String idempotencyKey) {
		return repository.findByIdempotencyKey(idempotencyKey).map(FilterJobJpaMapper::toDomain);
	}
}
