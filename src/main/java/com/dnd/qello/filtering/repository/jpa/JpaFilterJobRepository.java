package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
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

	@Override
	public List<FilterJob> findDeadlineElapsedCandidates(Instant at, int limit) {
		if (limit <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "limit", "limit은 양수여야 합니다");
		}
		return repository
			.findByStatusNotAndDeadlineAtLessThanEqualOrderByDeadlineAtAscIdAsc(
				FilterJobStatus.RESOLVED, at, PageRequest.of(0, limit))
			.stream()
			.map(FilterJobJpaMapper::toDomain)
			.toList();
	}

	@Override
	public List<FilterJob> findAutomatedByFilterReleaseId(long filterReleaseId) {
		return repository.findByFilterReleaseIdAndStatus(filterReleaseId, FilterJobStatus.AUTOMATED)
			.stream()
			.map(FilterJobJpaMapper::toDomain)
			.toList();
	}

	// 호출자가 이미 열어 둔 쓰기 트랜잭션에 참여한다(propagation REQUIRED) —
	// PESSIMISTIC_WRITE 잠금은 쓰기 트랜잭션 안에서만 유효하다.
	@Override
	@Transactional
	public Optional<FilterJob> findByIdForUpdate(long id) {
		return repository.findByIdForUpdate(id).map(FilterJobJpaMapper::toDomain);
	}
}
