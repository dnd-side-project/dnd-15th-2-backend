package com.dnd.qello.filtering.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.ManualReviewCase;

public interface ManualReviewCaseRepository {

	/** id가 없으면 INSERT, 있으면 UPDATE한다. 신규 삽입 시 유일성 위반은
	 * {@link org.springframework.dao.DataIntegrityViolationException}으로 전파된다
	 * (INV-MAN-001, 호출자가 흡수). */
	ManualReviewCase save(ManualReviewCase reviewCase);

	Optional<ManualReviewCase> findById(long id);

	/** 동일 대상·release의 기존 case 조회. INV-MAN-001 유일성의 멱등 생성 지점. */
	Optional<ManualReviewCase> findByTargetAndFilterReleaseId(FilterTarget target, long filterReleaseId);

	/**
	 * release 무관하게 대상의 가장 최근 case를 조회한다(#156, 신고 자동 숨김의
	 * filter_job 상관관계 판단용). 대상이 filtering 파이프라인을 거친 적이 없으면
	 * 빈 값을 반환한다 — 예외를 던지지 않는다.
	 */
	Optional<ManualReviewCase> findLatestByTarget(FilterTarget target);

	/**
	 * 검토자 큐 조회. OPEN case만, effectiveBand(HIGH가 먼저 — 저장된 band가
	 * HIGH이거나 created_at이 agedBeforeThreshold 이전이면 HIGH로 취급) 내림차순,
	 * band 내에서는 created_at 오름차순(FIFO)으로 반환한다.
	 */
	List<ManualReviewCase> findOpenQueue(Instant agedBeforeThreshold, int limit);
}
