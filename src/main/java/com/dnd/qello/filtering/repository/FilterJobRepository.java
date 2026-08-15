package com.dnd.qello.filtering.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterJob;

public interface FilterJobRepository {

	FilterJob save(FilterJob job);

	Optional<FilterJob> findById(long id);

	/** 동일 트리거의 중복 접수를 걸러내는 조회. INV-GEN-003 멱등성의 구현 지점. */
	Optional<FilterJob> findByIdempotencyKey(String idempotencyKey);

	/**
	 * deadline scheduler가 훑는 후보 조회. RESOLVED가 아니면서 deadlineAt이
	 * at 이전(포함)인 job만 반환한다 — 판정 완료 여부와 무관하게 deadline 경과
	 * 신호 발행 여부를 결정하는 것은 호출 서비스의 책임이다.
	 */
	List<FilterJob> findDeadlineElapsedCandidates(Instant at, int limit);

	/**
	 * emergency migration(#109) 대상 후보 조회. 지정한 release에 묶인 AUTOMATED
	 * job만 반환한다 — RESOLVED/RETRY_EXHAUSTED/MANUAL_REVIEW_REQUIRED job은
	 * 이관 대상이 아니다.
	 */
	List<FilterJob> findAutomatedByFilterReleaseId(long filterReleaseId);
}
