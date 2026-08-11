package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dnd.qello.filtering.domain.FilterReleaseStatus;

interface SpringDataFilterReleaseRepository extends JpaRepository<FilterReleaseJpaEntity, Long> {

	Optional<FilterReleaseJpaEntity> findByStatus(FilterReleaseStatus status);
}
