package com.dnd.qello.filtering.repository;

import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterJob;

public interface FilterJobRepository {

	FilterJob save(FilterJob job);

	Optional<FilterJob> findById(long id);

	/** 동일 트리거의 중복 접수를 걸러내는 조회. INV-GEN-003 멱등성의 구현 지점. */
	Optional<FilterJob> findByIdempotencyKey(String idempotencyKey);
}
