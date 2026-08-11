package com.dnd.qello.filtering.repository;

import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterDecision;

public interface FilterDecisionRepository {

	FilterDecision save(FilterDecision decision);

	Optional<FilterDecision> findById(long id);

	/** 같은 attempt의 중복 기록을 막는 조회. DB unique index와 짝을 이룬다. */
	Optional<FilterDecision> findByFilterJobIdAndAttemptGeneration(long filterJobId, int attemptGeneration);
}
