package com.dnd.qello.filtering.repository;

import java.util.Optional;

import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.FilterTargetType;

public interface AppealCaseRepository {

	AppealCase save(AppealCase appealCase);

	Optional<AppealCase> findById(long id);

	/** 동일 대상·decision의 기존 appeal 조회. INV-APL-002 유일성의 멱등 생성 지점. */
	Optional<AppealCase> findByTargetAndFilterDecisionId(FilterTargetType targetType, long targetId,
		long filterDecisionId);
}
