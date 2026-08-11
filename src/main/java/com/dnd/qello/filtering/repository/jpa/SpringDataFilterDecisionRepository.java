package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFilterDecisionRepository extends JpaRepository<FilterDecisionJpaEntity, Long> {

	Optional<FilterDecisionJpaEntity> findByFilterJobIdAndAttemptGeneration(long filterJobId, int attemptGeneration);
}
