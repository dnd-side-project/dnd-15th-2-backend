package com.dnd.qello.safety.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.safety.domain.ReportCase;
import com.dnd.qello.safety.domain.ReportCaseQueue;

public interface ReportCaseRepository {

	/** ON CONFLICT 없는 순수 INSERT. 같은 대상에 이미 열린 사건이 있으면 unique violation을 던진다. */
	ReportCase save(ReportCase reportCase);

	Optional<ReportCase> findById(long id);

	/** 동시 종결 시도를 직렬화한다 — 두 번째 트랜잭션은 첫 번째가 커밋한 뒤에야 행을 읽는다(#155). */
	Optional<ReportCase> findByIdForUpdate(long id);

	ReportCase update(ReportCase reportCase);

	/**
	 * ON CONFLICT DO NOTHING으로 사건을 연다. 이미 같은 대상에 열린 사건이 있으면 빈 값을
	 * 반환한다 — 호출자가 {@link #findOpenByTarget}으로 재조회해 병합한다(#154).
	 */
	Optional<ReportCase> tryOpen(ReportCase reportCase);

	Optional<ReportCase> findOpenByTarget(Long targetUserId, Long directionPostId, Long answerId);

	/**
	 * 운영자 대기열 조회(#156). OPEN·UNDER_REVIEW만 대상이다 — 종결된 사건은
	 * 대기열이 아니다. queue가 null이면 STANDARD·URGENT 모두 포함한다. SLA가
	 * 급한 순(sla_due_at 오름차순)으로 정렬한다.
	 */
	List<ReportCase> findQueue(ReportCaseQueue queue, Instant cursorSlaDueAt, Long cursorId, int limit);
}
