package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;

@Repository
@Transactional(readOnly = true)
public class JpaFilterDecisionRepository implements FilterDecisionRepository {

	private final SpringDataFilterDecisionRepository repository;

	public JpaFilterDecisionRepository(SpringDataFilterDecisionRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public FilterDecision save(FilterDecision decision) {
		return FilterDecisionJpaMapper.toDomain(repository.saveAndFlush(new FilterDecisionJpaEntity(decision)));
	}

	@Override
	public Optional<FilterDecision> findById(long id) {
		return repository.findById(id).map(FilterDecisionJpaMapper::toDomain);
	}

	@Override
	public Optional<FilterDecision> findByFilterJobIdAndAttemptGeneration(long filterJobId, int attemptGeneration) {
		return repository.findByFilterJobIdAndAttemptGeneration(filterJobId, attemptGeneration)
			.map(FilterDecisionJpaMapper::toDomain);
	}
}
