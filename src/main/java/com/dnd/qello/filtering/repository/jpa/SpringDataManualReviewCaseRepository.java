package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dnd.qello.filtering.domain.FilterTargetType;

interface SpringDataManualReviewCaseRepository extends JpaRepository<ManualReviewCaseJpaEntity, Long> {

	Optional<ManualReviewCaseJpaEntity> findByTargetTypeAndTargetIdAndTargetVersionAndFilterReleaseId(
		FilterTargetType targetType, long targetId, long targetVersion, long filterReleaseId);
}
