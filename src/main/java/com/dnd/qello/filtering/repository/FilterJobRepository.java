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

	/**
	 * 행 잠금(`SELECT ... FOR UPDATE`)을 건 조회(#110). 자동 결과 적용
	 * (AnswerModerationExecutionWorker.applyVerdict)과 수동 결정 적용
	 * (ManualReviewDecisionService)이 같은 job을 동시에 읽고 쓸 수 있어, 둘 다 이
	 * 메서드로 조회해야 한쪽이 커밋할 때까지 다른 쪽이 블록되어 오래된 스냅샷으로
	 * 결정하지 않는다 — 단순 재조회만으로는 두 트랜잭션이 같은 시점의 낡은 값을
	 * 읽고 서로를 덮어쓸 수 있다(lost update).
	 */
	Optional<FilterJob> findByIdForUpdate(long id);
}
