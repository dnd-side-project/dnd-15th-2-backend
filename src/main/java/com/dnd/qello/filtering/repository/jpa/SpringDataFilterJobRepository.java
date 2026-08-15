package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dnd.qello.filtering.domain.FilterJobStatus;

interface SpringDataFilterJobRepository extends JpaRepository<FilterJobJpaEntity, Long> {

	Optional<FilterJobJpaEntity> findByIdempotencyKey(String idempotencyKey);

	List<FilterJobJpaEntity> findByStatusNotAndDeadlineAtLessThanEqualOrderByDeadlineAtAscIdAsc(
		FilterJobStatus resolvedStatus, Instant at, Pageable pageable);

	List<FilterJobJpaEntity> findByFilterReleaseIdAndStatus(long filterReleaseId, FilterJobStatus status);
}
