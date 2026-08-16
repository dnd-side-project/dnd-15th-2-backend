package com.dnd.qello.filtering.domain;

import java.time.Instant;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

// moderation job. INV-GEN-001~005의 authority(현재 attemptGeneration, manuallyResolved)를
// 소유한다. 공개 여부(APPROVED/HIDDEN)는 이 객체에 없다 — resolvedVerdict를 호출자
// 도메인에 콜백으로 전달하는 것은 이 job을 다루는 서비스 계층의 책임이다.
//
// deadlineAt은 job 생성 시 한 번만 고정되고 이후 어떤 전이 메서드도 바꾸지 않는다
// (INV-ANS-002) — 재시도나 시스템 부하 변화로 연장되지 않는다. deadline 경과는
// 승인을 뜻하지 않으며(INV-ANS-003, INV-ANS-004), 그 신호를 만들지 여부는 이
// 값을 읽는 서비스 계층(deadline scheduler)의 책임이다.
//
// logicalAttemptCount는 outbox event의 attemptCount(claim마다 무조건 증가하는
// 인프라 카운터)와 다르다 — 이 값은 recordAutomatedAttempt(#108)를 통해 실제로
// pipeline을 호출했을 때만 증가한다. snapshot 단위 retry gate로 인해 pipeline
// 호출 없이 미뤄진 재클레임은 이 값을 늘리지 않는다(INV-RTY-001). deadline
// 경과는 이 값을 초기화하지 않는다(INV-RTY-006) — deadlineAt이 아닌 createdAt이
// retry lifetime의 origin이기 때문에 자동으로 성립한다.
public record FilterJob(Long id, FilterTarget target, long filterReleaseId, FilterJobStatus status,
	int attemptGeneration, int logicalAttemptCount, boolean manuallyResolved, FilterVerdict resolvedVerdict,
	String idempotencyKey, Instant deadlineAt, Instant createdAt, Instant updatedAt) {

	private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200;

	public FilterJob {
		if (id != null && id <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "id", "id는 양수여야 합니다");
		}
		if (target == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "target");
		}
		if (filterReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "filterReleaseId", "filterReleaseId는 양수여야 합니다");
		}
		if (status == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "status");
		}
		if (attemptGeneration < 1) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "attemptGeneration", "attemptGeneration은 1 이상이어야 합니다");
		}
		if (logicalAttemptCount < 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "logicalAttemptCount",
				"logicalAttemptCount는 음수일 수 없습니다");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
			throw new FilteringException(
				FilteringErrorCode.REQUIRED_VALUE_MISSING, "idempotencyKey", "idempotencyKey가 유효하지 않습니다");
		}
		if (deadlineAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "deadlineAt");
		}
		if (createdAt == null || updatedAt == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "createdAt/updatedAt");
		}
		if ((status == FilterJobStatus.RESOLVED) != (resolvedVerdict != null)) {
			throw new FilteringException(FilteringErrorCode.INVALID_JOB_STATUS, "resolvedVerdict",
				"RESOLVED 상태와 resolvedVerdict는 함께 있어야 합니다");
		}
		if (manuallyResolved && status != FilterJobStatus.RESOLVED) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_JOB_STATUS, "manuallyResolved", "수동 종결은 RESOLVED 상태에서만 유효합니다");
		}
	}

	public static FilterJob create(
		FilterTarget target, long filterReleaseId, String idempotencyKey, Instant deadlineAt, Instant now
	) {
		return new FilterJob(null, target, filterReleaseId, FilterJobStatus.AUTOMATED, 1, 0, false, null,
			idempotencyKey, deadlineAt, now, now);
	}

	public static FilterJob restore(Long id, FilterTarget target, long filterReleaseId, FilterJobStatus status,
		int attemptGeneration, int logicalAttemptCount, boolean manuallyResolved, FilterVerdict resolvedVerdict,
		String idempotencyKey, Instant deadlineAt, Instant createdAt, Instant updatedAt) {
		return new FilterJob(id, target, filterReleaseId, status, attemptGeneration, logicalAttemptCount,
			manuallyResolved, resolvedVerdict, idempotencyKey, deadlineAt, createdAt, updatedAt);
	}

	// 실제 pipeline을 호출했다는 사실만 기록한다(logicalAttemptCount 증가). 판정 적용이나
	// 소진 판정과 독립적인 원시 사실이라 별도 메서드로 분리했다 — 성공(applyAutomatedDecision)과
	// 실패(재시도 예약 또는 exhaustRetries) 양쪽 경로 모두 저장 전에 이 메서드를 먼저 거친다
	// (INV-RTY-001의 카운터 소스, "SDK 호출을 포함한 단일 logical attempt budget").
	//
	// applyAutomatedDecision과 같은 이유로 attemptGeneration을 검증한다 — 세대가 넘어간
	// 뒤 도착한 늦은 실패는 새 세대의 retry budget을 갉아먹지 않아야 한다(INV-GEN-005).
	public FilterJob recordAutomatedAttempt(int decisionAttemptGeneration, Instant now) {
		requireStatus(FilterJobStatus.AUTOMATED, "시도 기록을");
		if (decisionAttemptGeneration != attemptGeneration) {
			throw new FilteringException(FilteringErrorCode.STALE_ATTEMPT_GENERATION, "attemptGeneration");
		}
		return transition(FilterJobStatus.AUTOMATED, attemptGeneration, logicalAttemptCount + 1,
			manuallyResolved, resolvedVerdict, now);
	}

	// 자동 pipeline 결과 적용. decisionAttemptGeneration이 현재 세대와 다르면(마이그레이션으로
	// 세대가 넘어간 뒤 도착한 늦은 결과) 거절한다(INV-GEN-005, INV-REL-010).
	public FilterJob applyAutomatedDecision(int decisionAttemptGeneration, FilterVerdict verdict, Instant now) {
		requireVerdict(verdict);
		if (manuallyResolved) {
			throw new FilteringException(FilteringErrorCode.ALREADY_MANUALLY_RESOLVED, "status");
		}
		if (decisionAttemptGeneration != attemptGeneration) {
			throw new FilteringException(FilteringErrorCode.STALE_ATTEMPT_GENERATION, "attemptGeneration");
		}
		if (status != FilterJobStatus.AUTOMATED && status != FilterJobStatus.MANUAL_REVIEW_REQUIRED) {
			throw new FilteringException(FilteringErrorCode.INVALID_JOB_STATUS, "status",
				status + " 상태에서는 자동 결과를 적용할 수 없습니다");
		}
		return transition(FilterJobStatus.RESOLVED, attemptGeneration, logicalAttemptCount, false, verdict, now);
	}

	// 자동 retry 소진. 공개 상태는 바꾸지 않는다 — 호출 서비스가 별도로 manual case를 연다.
	public FilterJob exhaustRetries(Instant now) {
		requireStatus(FilterJobStatus.AUTOMATED, "retry 소진 처리를");
		return transition(FilterJobStatus.RETRY_EXHAUSTED, attemptGeneration, logicalAttemptCount, false, null, now);
	}

	// manual review case 개설. 이미 열려 있으면 멱등하게 같은 상태를 반환한다.
	public FilterJob openManualReview(Instant now) {
		if (status == FilterJobStatus.MANUAL_REVIEW_REQUIRED) {
			return this;
		}
		requireStatus(FilterJobStatus.RETRY_EXHAUSTED, "수동 검토 전환을");
		return transition(
			FilterJobStatus.MANUAL_REVIEW_REQUIRED, attemptGeneration, logicalAttemptCount, false, null, now);
	}

	// reviewer의 수동 결정. 항상 authoritative하며 attemptGeneration과 무관하게 적용된다.
	// 이미 수동으로 종결된 job에는 적용할 수 없다(재심은 이 시스템의 appeal 흐름이 다룬다).
	public FilterJob applyManualDecision(FilterVerdict verdict, Instant now) {
		requireVerdict(verdict);
		if (manuallyResolved) {
			throw new FilteringException(FilteringErrorCode.ALREADY_MANUALLY_RESOLVED, "status");
		}
		return transition(FilterJobStatus.RESOLVED, attemptGeneration, logicalAttemptCount, true, verdict, now);
	}

	// emergency migration으로 세대를 올린다. 이전 세대의 진행 중이던 attempt는 이 시점부터
	// 전부 비권위가 된다(INV-REL-010). 이미 RESOLVED된 job은 다시 열지 않는다.
	public FilterJob advanceAttemptGeneration(Instant now) {
		requireStatus(FilterJobStatus.AUTOMATED, "attempt generation 전환을");
		return transition(FilterJobStatus.AUTOMATED, attemptGeneration + 1, logicalAttemptCount, false, null, now);
	}

	// emergency migration(#109)이 확정된 대상 release로 job을 재배정한다.
	// filterReleaseId 재배정과 attemptGeneration 증가를 한 전이로 묶는다 —
	// AnswerModerationExecutionWorker.callPipelineBounded는 매 처리마다
	// filterReleaseId로 release를 다시 조회하므로, 이 필드만 바뀌면 다음 처리부터
	// 새 release의 modelSnapshot을 별도 worker 변경 없이 그대로 쓴다. generation을
	// 함께 올리는 이유는 advanceAttemptGeneration과 동일하다 — 이 시점 이전에
	// 발행된 결과를 STALE_ATTEMPT_GENERATION으로 거절해 INV-REL-010을 지킨다.
	// AUTOMATED 상태에서만 허용한다 — 이미 종결되었거나 수동 검토로 넘어간 job은
	// 이관 대상이 아니다.
	public FilterJob migrateToRelease(long newFilterReleaseId, Instant now) {
		requireStatus(FilterJobStatus.AUTOMATED, "emergency release 이관을");
		if (newFilterReleaseId <= 0) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "newFilterReleaseId", "newFilterReleaseId는 양수여야 합니다");
		}
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		return new FilterJob(id, target, newFilterReleaseId, FilterJobStatus.AUTOMATED, attemptGeneration + 1,
			logicalAttemptCount, false, null, idempotencyKey, deadlineAt, createdAt, now);
	}

	private FilterJob transition(FilterJobStatus nextStatus, int nextAttemptGeneration, int nextLogicalAttemptCount,
		boolean nextManuallyResolved, FilterVerdict nextResolvedVerdict, Instant now) {
		if (now == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "now");
		}
		return new FilterJob(id, target, filterReleaseId, nextStatus, nextAttemptGeneration,
			nextLogicalAttemptCount, nextManuallyResolved, nextResolvedVerdict, idempotencyKey, deadlineAt,
			createdAt, now);
	}

	private void requireStatus(FilterJobStatus expected, String action) {
		if (status != expected) {
			throw new FilteringException(FilteringErrorCode.INVALID_JOB_STATUS, "status",
				status + " 상태에서는 " + action + " 할 수 없습니다");
		}
	}

	private static void requireVerdict(FilterVerdict verdict) {
		if (verdict == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "verdict");
		}
	}
}
