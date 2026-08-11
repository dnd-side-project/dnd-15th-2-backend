package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;

@Repository
@Transactional(readOnly = true)
public class JpaManualReviewCaseRepository implements ManualReviewCaseRepository {

	private final SpringDataManualReviewCaseRepository repository;

	public JpaManualReviewCaseRepository(SpringDataManualReviewCaseRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public ManualReviewCase save(ManualReviewCase reviewCase) {
		return ManualReviewCaseJpaMapper.toDomain(repository.saveAndFlush(new ManualReviewCaseJpaEntity(reviewCase)));
	}

	@Override
	public Optional<ManualReviewCase> findById(long id) {
		return repository.findById(id).map(ManualReviewCaseJpaMapper::toDomain);
	}

	@Override
	public Optional<ManualReviewCase> findByTargetAndFilterReleaseId(FilterTarget target, long filterReleaseId) {
		return repository.findByTargetTypeAndTargetIdAndTargetVersionAndFilterReleaseId(
				target.targetType(), target.targetId(), target.targetVersion(), filterReleaseId)
			.map(ManualReviewCaseJpaMapper::toDomain);
	}
}
