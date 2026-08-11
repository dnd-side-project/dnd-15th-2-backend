package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dnd.qello.filtering.domain.FilterTargetType;

interface SpringDataAppealCaseRepository extends JpaRepository<AppealCaseJpaEntity, Long> {

	Optional<AppealCaseJpaEntity> findByTargetTypeAndTargetIdAndFilterDecisionId(
		FilterTargetType targetType, long targetId, long filterDecisionId);
}
