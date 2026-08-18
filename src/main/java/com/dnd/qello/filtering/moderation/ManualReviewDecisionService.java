package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.audit.OperatorActionAuditRecorder;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.OperatorActionTargetType;
import com.dnd.qello.filtering.domain.OperatorActionType;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// 검토자 결정의 유일한 진입점(#110). FilterReleaseRegistryService(#104)와 동일하게
// alias나 다른 자동 경로는 없다. filtering.moderation 패키지에 두는 이유는
// AnswerModerationEventPayloads(MODERATION_VERDICT_READY 페이로드)가
// package-private이라 이 패키지 안에서만 그 계약을 재사용할 수 있기 때문이다
// (SnapshotHealthProbeRecorder, #109와 같은 이유).
@Service
@Transactional(readOnly = true)
public class ManualReviewDecisionService {

	private final ManualReviewCaseRepository manualReviewCaseRepository;
	private final FilterJobRepository filterJobRepository;
	private final FilterJobStatusHistoryRepository filterJobStatusHistoryRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final OperatorActionAuditRecorder auditRecorder;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public ManualReviewDecisionService(
		ManualReviewCaseRepository manualReviewCaseRepository,
		FilterJobRepository filterJobRepository,
		FilterJobStatusHistoryRepository filterJobStatusHistoryRepository,
		OutboxEventRepository outboxEventRepository,
		OperatorActionAuditRecorder auditRecorder,
		ObjectMapper objectMapper,
		Clock clock
	) {
		this.auditRecorder = auditRecorder;
		this.manualReviewCaseRepository = manualReviewCaseRepository;
		this.filterJobRepository = filterJobRepository;
		this.filterJobStatusHistoryRepository = filterJobStatusHistoryRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	// 검토자 큐. effectiveBand 내림차순 + band 내 created_at 오름차순(FIFO)으로
	// 반환한다. aging은 조회 시점에 계산하며 어떤 행도 갱신하지 않는다.
	//
	// agingThreshold를 호출자가 넘긴다 — 실제 운영 aging 시간이 이슈 본문에서
	// 미결정이라, 이 서비스는 값을 고정하지 않고 매 호출마다 명시적으로 받는다
	// (ManualReviewPriorityPolicy 전체를 요구하지 않는 이유이기도 하다 — 이
	// 조회는 report signal threshold나 policyVersion을 쓰지 않는다).
	public List<ManualReviewCase> findQueue(Duration agingThreshold, int limit) {
		Instant now = Instant.now(clock);
		return manualReviewCaseRepository.findOpenQueue(now.minus(agingThreshold), limit);
	}

	// 검토자 결정을 적용한다. job을 findByIdForUpdate로 잠가 자동 결과 적용
	// (AnswerModerationExecutionWorker.applyVerdict)과의 경합을 직렬화한다.
	//
	// job이 이미 RESOLVED(자동 결과가 먼저 도착)면 job은 건드리지 않고 그 기존
	// resolvedVerdict로 case만 종료한다(INV-MAN-003) — 검토자가 제출한 verdict로
	// 자동 판정을 덮어쓰지 않는다.
	@Transactional
	public ManualReviewCase decide(long caseId, FilterVerdict operatorVerdict, long operatorUserId,
		OperatorReason reason) {
		Instant now = Instant.now(clock);
		ManualReviewCase reviewCase = manualReviewCaseRepository.findById(caseId)
			.orElseThrow(() -> new FilteringException(FilteringErrorCode.MANUAL_REVIEW_CASE_NOT_FOUND, "caseId"));
		// case가 열릴 때 적용된 우선순위 정책이 이 결정의 정책 버전이다(INV-APL-012).
		auditRecorder.record(operatorUserId, OperatorActionType.MANUAL_REVIEW_DECIDE,
			OperatorActionTargetType.MANUAL_REVIEW_CASE, String.valueOf(caseId), reason,
			reviewCase.priorityPolicyVersion());

		FilterJob job = filterJobRepository.findByIdForUpdate(reviewCase.filterJobId())
			.orElseThrow(() -> new FilteringException(
				FilteringErrorCode.INVALID_JOB_STATUS, "filterJobId", "job을 찾을 수 없습니다"));

		if (job.status() == FilterJobStatus.RESOLVED) {
			return manualReviewCaseRepository.save(reviewCase.resolve(job.resolvedVerdict(), operatorUserId, now));
		}

		FilterJob resolved = job.applyManualDecision(operatorVerdict, now);
		filterJobRepository.save(resolved);
		filterJobStatusHistoryRepository.save(FilterJobStatusHistoryEntry.of(
			job.id(), job.status(), FilterJobStatus.RESOLVED, "manual review decision", now));
		outboxEventRepository.save(verdictReadyEvent(resolved, now));
		return manualReviewCaseRepository.save(reviewCase.resolve(operatorVerdict, operatorUserId, now));
	}

	private OutboxEvent verdictReadyEvent(FilterJob job, Instant now) {
		FilterTarget target = job.target();
		AnswerModerationEventPayloads.VerdictReady payload = new AnswerModerationEventPayloads.VerdictReady(
			job.id(), target.targetType(), target.targetId(), target.targetVersion(), job.resolvedVerdict());
		return OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, job.id(), OutboxEventType.MODERATION_VERDICT_READY,
			AnswerModerationEventPayloads.verdictReadyDedupKey(job.id()),
			AnswerModerationEventPayloads.toJson(objectMapper, payload), now);
	}
}
