package com.dnd.qello.filtering.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
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

	@Override
	public Optional<AppealCase> findByIdForUpdate(long id) {
		return repository.findByIdForUpdate(id).map(AppealCaseJpaMapper::toDomain);
	}

	@Override
	public List<AppealCase> findByAppellantUserId(long appellantUserId) {
		return repository.findByAppellantUserIdOrderByCreatedAtDescIdDesc(appellantUserId).stream()
			.map(AppealCaseJpaMapper::toDomain)
			.toList();
	}

	@Override
	public List<AppealCase> findOpenQueue(int limit) {
		if (limit <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "limit", "limit은 양수여야 합니다");
		}
		return repository
			.findByStatusOrderByCreatedAtAscIdAsc(AppealCaseStatus.OPEN, PageRequest.of(0, limit)).stream()
			.map(AppealCaseJpaMapper::toDomain)
			.toList();
	}
}
