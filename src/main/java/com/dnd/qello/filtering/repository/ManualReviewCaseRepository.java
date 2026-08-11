package com.dnd.qello.filtering.repository;

import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.ManualReviewCase;

public interface ManualReviewCaseRepository {

	ManualReviewCase save(ManualReviewCase reviewCase);

	Optional<ManualReviewCase> findById(long id);

	/** 동일 대상·release의 기존 case 조회. INV-MAN-001 유일성의 멱등 생성 지점. */
	Optional<ManualReviewCase> findByTargetAndFilterReleaseId(FilterTarget target, long filterReleaseId);
}
