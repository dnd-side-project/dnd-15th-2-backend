package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.repository.AppealCaseRepository;

@Repository
@Transactional(readOnly = true)
public class JpaAppealCaseRepository implements AppealCaseRepository {

	private final SpringDataAppealCaseRepository repository;

	public JpaAppealCaseRepository(SpringDataAppealCaseRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public AppealCase save(AppealCase appealCase) {
		return AppealCaseJpaMapper.toDomain(repository.saveAndFlush(new AppealCaseJpaEntity(appealCase)));
	}

	@Override
	public Optional<AppealCase> findById(long id) {
		return repository.findById(id).map(AppealCaseJpaMapper::toDomain);
	}

	@Override
	public Optional<AppealCase> findByTargetAndFilterDecisionId(FilterTargetType targetType, long targetId,
		long filterDecisionId) {
		return repository.findByTargetTypeAndTargetIdAndFilterDecisionId(targetType, targetId, filterDecisionId)
			.map(AppealCaseJpaMapper::toDomain);
	}
}
