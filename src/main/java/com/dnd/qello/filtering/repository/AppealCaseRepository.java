package com.dnd.qello.filtering.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.FilterTargetType;

public interface AppealCaseRepository {

	AppealCase save(AppealCase appealCase);

	Optional<AppealCase> findById(long id);

	/** 동일 대상·decision의 기존 appeal 조회. INV-APL-002 유일성의 멱등 생성 지점. */
	Optional<AppealCase> findByTargetAndFilterDecisionId(FilterTargetType targetType, long targetId,
		long filterDecisionId);

	/** 검토자 결정 직전의 행 잠금 조회. 동시 결정을 직렬화한다. */
	Optional<AppealCase> findByIdForUpdate(long id);

	/** 작성자 본인의 appeal 목록. 최신 접수가 앞에 온다. */
	List<AppealCase> findByAppellantUserId(long appellantUserId);

	/** 검토자 큐. OPEN case를 접수 순서(FIFO)로 반환한다. */
	List<AppealCase> findOpenQueue(int limit);
}
