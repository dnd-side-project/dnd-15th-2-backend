package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseRetryGate;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.domain.ManualReviewBand;
import com.dnd.qello.filtering.domain.ManualReviewCase;
import com.dnd.qello.filtering.domain.ManualReviewPriorityDecision;
import com.dnd.qello.filtering.domain.ManualReviewPriorityEvaluation;
import com.dnd.qello.filtering.domain.ManualReviewPriorityPolicy;
import com.dnd.qello.filtering.domain.ManualReviewPriorityReasonCode;
import com.dnd.qello.filtering.domain.RetryGateConfig;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRetryGateRepository;
import com.dnd.qello.filtering.repository.ManualReviewCaseRepository;
import com.dnd.qello.filtering.repository.ManualReviewPriorityEvaluationRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.domain.OutboxRetryDecision;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// MODERATION_EXECUTION_REQUESTED 하나를 pipeline 시도로 변환한다(#107). #108부터는
// timeout·error를 만나도 이벤트를 곧바로 DEAD로 종결하지 않고, AnswerModerationRetryPolicy
// 판정에 따라 같은 이벤트를 backoff+jitter 뒤 재시도하거나(RETRY_SCHEDULED), 예산을
// 소진하면 job을 RETRY_EXHAUSTED → MANUAL_REVIEW_REQUIRED로 넘긴다(RETRY_EXHAUSTED).
// snapshot 단위 retry gate가 release를 DEGRADED로 판단하면 pipeline을 아예 호출하지
// 않고 이벤트를 짧게 미룬다(RETRY_DEFERRED_BY_GATE) — 이 경로는 logicalAttemptCount를
// 늘리지 않는다.
//
// 의도적으로 Spring 빈이 아니다. ModerationPipelineService(#105)를 들고 있고,
// 그 하위 구현체(TextNormalizer 등)가 아직 없어 컴포넌트 스캔 대상이 되면 컨텍스트
// 기동이 실패한다 — NicknameSyncModerationGate(#106)와 같은 이유다.
//
// pipeline 호출(외부 I/O)은 트랜잭션 밖에서 executor로 경계 짓고, 그 결과를 적용하는
// FilterJob 전이·outbox 발행·claim 완료만 트랜잭션으로 묶는다
// (ModerationPipelineService 자신의 트랜잭션 정책과 동일한 이유).
public class AnswerModerationExecutionWorker {

	private final ModerationPipelineService pipeline;
	private final FilterJobRepository filterJobRepository;
	private final FilterReleaseRepository filterReleaseRepository;
	private final FilterJobStatusHistoryRepository filterJobStatusHistoryRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final AnswerModerationRetryPolicy retryPolicy;
	private final FilterReleaseRetryGateRepository filterReleaseRetryGateRepository;
	private final RetryGateConfig retryGateConfig;
	private final ManualReviewCaseRepository manualReviewCaseRepository;
	private final ManualReviewPriorityEvaluationRepository manualReviewPriorityEvaluationRepository;
	private final ManualReviewPriorityPolicy manualReviewPriorityPolicy;
	private final Duration gateDeferDelay;
	private final ObjectMapper objectMapper;
	private final ExecutorService executor;
	private final Duration pipelineTimeout;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public AnswerModerationExecutionWorker(
		ModerationPipelineService pipeline,
		FilterJobRepository filterJobRepository,
		FilterReleaseRepository filterReleaseRepository,
		FilterJobStatusHistoryRepository filterJobStatusHistoryRepository,
		OutboxEventRepository outboxEventRepository,
		AnswerModerationRetryPolicy retryPolicy,
		FilterReleaseRetryGateRepository filterReleaseRetryGateRepository,
		RetryGateConfig retryGateConfig,
		ManualReviewCaseRepository manualReviewCaseRepository,
		ManualReviewPriorityEvaluationRepository manualReviewPriorityEvaluationRepository,
		ManualReviewPriorityPolicy manualReviewPriorityPolicy,
		Duration gateDeferDelay,
		ObjectMapper objectMapper,
		ExecutorService executor,
		Duration pipelineTimeout,
		PlatformTransactionManager transactionManager,
		Clock clock
	) {
		this.pipeline = pipeline;
		this.filterJobRepository = filterJobRepository;
		this.filterReleaseRepository = filterReleaseRepository;
		this.filterJobStatusHistoryRepository = filterJobStatusHistoryRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.retryPolicy = retryPolicy;
		this.filterReleaseRetryGateRepository = filterReleaseRetryGateRepository;
		this.retryGateConfig = retryGateConfig;
		this.manualReviewCaseRepository = manualReviewCaseRepository;
		this.manualReviewPriorityEvaluationRepository = manualReviewPriorityEvaluationRepository;
		this.manualReviewPriorityPolicy = manualReviewPriorityPolicy;
		this.gateDeferDelay = gateDeferDelay;
		this.objectMapper = objectMapper;
		this.executor = executor;
		this.pipelineTimeout = pipelineTimeout;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public BatchResult processBatch(BatchCommand command) {
		requireCommand(command);
		Instant claimAt = command.at() == null ? clock.instant() : command.at();
		if (!command.leaseExpiresAt().isAfter(claimAt)) {
			throw new FilteringException(
				FilteringErrorCode.INVALID_VALUE_RANGE, "batch", "실행 batch 입력이 유효하지 않습니다");
		}
		List<OutboxEvent> claimed = outboxEventRepository.claimDue(
			Set.of(OutboxEventType.MODERATION_EXECUTION_REQUESTED), command.limit(), command.leaseOwner(),
			claimAt, command.leaseExpiresAt());
		// release별 이번 배치 admitted 수를 센다 — 배치 하나를 처리하는 동안만 유효하고
		// worker 인스턴스 간 공유되지 않는다(다른 배치의 동시 admit까지 정확히 막지는
		// 않지만, 게이트 폭주 완화가 목적이지 상한의 엄격한 강제가 목적이 아니다).
		Map<Long, Integer> admittedPerRelease = new HashMap<>();
		List<Outcome> outcomes = claimed.stream().map(event -> processClaimed(event, admittedPerRelease)).toList();
		return new BatchResult(claimed.size(), outcomes);
	}

	private Outcome processClaimed(OutboxEvent event, Map<Long, Integer> admittedPerRelease) {
		AnswerModerationEventPayloads.ExecutionRequested payload = AnswerModerationEventPayloads.fromJson(
			objectMapper, event.payload(), AnswerModerationEventPayloads.ExecutionRequested.class);
		Optional<FilterJob> jobOpt = filterJobRepository.findById(payload.filterJobId());
		if (jobOpt.isEmpty()) {
			return finishDead(event, Outcome.JOB_NOT_FOUND);
		}
		FilterJob job = jobOpt.get();
		if (job.status() != FilterJobStatus.AUTOMATED) {
			return finishSkipped(event);
		}

		GateAdmission admission = checkGateAdmission(job, admittedPerRelease);
		if (!admission.admitted()) {
			return deferForGate(event, admission.deferUntil());
		}

		ModerationPipelineResult result;
		try {
			result = callPipelineBounded(payload, job);
		} catch (PipelineUnavailableException unavailable) {
			return handlePipelineFailure(event, job, payload.attemptGeneration(), unavailable.retryAfterHint());
		}
		return applyVerdict(event, job, payload.attemptGeneration(), result.verdict());
	}

	// FOR UPDATE로 게이트 행을 잠가 admittedSoFar와 currentLimit을 비교한다. 이 짧은
	// 트랜잭션은 아무것도 쓰지 않는다 — 실제 성공/실패 반영(onSuccess/onFailure)은
	// pipeline을 실제로 호출한 뒤에만 별도 트랜잭션에서 일어난다(ADR-0002).
	private GateAdmission checkGateAdmission(FilterJob job, Map<Long, Integer> admittedPerRelease) {
		int admittedSoFar = admittedPerRelease.getOrDefault(job.filterReleaseId(), 0);
		Instant now = Instant.now(clock);
		boolean admitted = transactionTemplate.execute(status -> filterReleaseRetryGateRepository
			.findOrCreateForUpdate(job.filterReleaseId(), now)
			.allowsClaim(admittedSoFar));
		if (admitted) {
			admittedPerRelease.merge(job.filterReleaseId(), 1, Integer::sum);
			return GateAdmission.allow();
		}
		return GateAdmission.defer(now.plus(gateDeferDelay));
	}

	private Outcome deferForGate(OutboxEvent event, Instant nextAttemptAt) {
		try {
			return transactionTemplate.execute(status -> {
				failClaimOrThrow(event, Instant.now(clock), new OutboxRetryDecision(false, nextAttemptAt));
				return Outcome.RETRY_DEFERRED_BY_GATE;
			});
		} catch (StaleLeaseException staleLease) {
			return Outcome.STALE_LEASE;
		}
	}

	private ModerationPipelineResult callPipelineBounded(
		AnswerModerationEventPayloads.ExecutionRequested payload, FilterJob job
	) {
		FilterRelease release = filterReleaseRepository.findById(job.filterReleaseId())
			.orElseThrow(() -> new FilteringException(FilteringErrorCode.RELEASE_NOT_FOUND, "filterReleaseId"));
		ModerationPipelineRequest request = ModerationPipelineRequest.forJob(payload.targetType(),
			payload.language(), payload.rawContent(), release, job.id(), payload.attemptGeneration());

		Future<ModerationPipelineResult> future = executor.submit(() -> pipeline.execute(request));
		try {
			return future.get(pipelineTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			future.cancel(true);
			throw new PipelineUnavailableException(null);
		} catch (Exception e) {
			// ExecutionException(대개 FilteringException(MODERATION_PROVIDER_UNAVAILABLE)
			// 또는 ModerationRateLimitedException)과 InterruptedException을 모두 "이번
			// 시도 실패"로 취급한다 — 어떤 예외도 여기서 ALLOW/BLOCK으로 바뀌지 않는다.
			// ModerationRateLimitedException만 Retry-After 힌트를 다음 단계로 전달한다.
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new PipelineUnavailableException(retryAfterHintOf(e));
		}
	}

	private static Duration retryAfterHintOf(Exception e) {
		if (e instanceof ExecutionException executionException
			&& executionException.getCause() instanceof ModerationRateLimitedException rateLimited) {
			return rateLimited.retryAfter();
		}
		return null;
	}

	// decisionAttemptGeneration은 이 이벤트가 발행된 시점의 세대(payload.attemptGeneration())여야
	// 한다 — job의 현재 세대를 그대로 넘기면 STALE_ATTEMPT_GENERATION 검사가 자기 자신과
	// 비교하는 셈이 되어 emergency migration으로 세대가 넘어간 뒤 도착한 낡은 결과를
	// 걸러내지 못한다(fencing 무력화).
	//
	// pipeline 호출은 트랜잭션 밖에서 실행되므로, 그 사이 job의 세대가 바뀌었을 수 있다.
	// processClaimed가 넘긴 job은 pipeline 호출 전 스냅샷이라 여기서 그대로 저장하면
	// 그 사이의 변경을 덮어쓸 수 있다 — 저장 직전 같은 트랜잭션에서 다시 조회해 검증한다.
	private Outcome applyVerdict(OutboxEvent event, FilterJob job, int decisionAttemptGeneration, FilterVerdict verdict) {
		try {
			return transactionTemplate.execute(status -> {
				Instant now = Instant.now(clock);
				// findByIdForUpdate로 행을 잠근다(#110) — ManualReviewDecisionService가
				// 동시에 같은 job에 수동 결정을 적용하려 할 수 있다. 둘 다 잠금 조회를
				// 쓰지 않으면 나중에 커밋하는 쪽이 먼저 커밋된 결과를 낡은 스냅샷으로
				// 덮어쓸 수 있다(lost update, INV-MAN-003/004).
				FilterJob current = filterJobRepository.findByIdForUpdate(job.id())
					.orElseThrow(() -> new FilteringException(
						FilteringErrorCode.INVALID_JOB_STATUS, "filterJobId", "job을 찾을 수 없습니다"));
				FilterJob attempted = current.recordAutomatedAttempt(decisionAttemptGeneration, now);
				FilterJob resolved = attempted.applyAutomatedDecision(decisionAttemptGeneration, verdict, now);
				filterJobRepository.save(resolved);
				filterJobStatusHistoryRepository.save(FilterJobStatusHistoryEntry.of(
					current.id(), current.status(), FilterJobStatus.RESOLVED, "automated decision", now));

				FilterReleaseRetryGate gate = filterReleaseRetryGateRepository
					.findOrCreateForUpdate(resolved.filterReleaseId(), now)
					.onSuccess(now, retryGateConfig);
				filterReleaseRetryGateRepository.save(gate);

				outboxEventRepository.save(verdictReadyEvent(resolved, now));
				return completeClaimOrThrow(event, now);
			});
		} catch (StaleLeaseException staleLease) {
			return Outcome.STALE_LEASE;
		} catch (FilteringException raceOnJobState) {
			// job이 이미 다른 경로로 종결된 race만 흡수한다 — 그 외 FilteringException(예:
			// payload 직렬화 실패)은 판정 유실을 감추게 되므로 그대로 전파한다.
			if (!isJobStateRace(raceOnJobState)) {
				throw raceOnJobState;
			}
			return finishSkipped(event);
		}
	}

	// pipeline 실패 후 재시도할지 소진할지 판정하고 반영한다. 소진이면 그 전에 별도
	// 트랜잭션으로 ManualReviewCase부터 만든다 — FilterJob과 ManualReviewCase를 한
	// 트랜잭션에 묶으면, ManualReviewCase의 유일성 제약 위반이 PostgreSQL에서 트랜잭션
	// 전체를 abort 상태로 만들어 뒤이은 FilterJob 저장·outbox 갱신까지 함께 실패한다.
	// case를 먼저 만들고(멱등, 실패해도 이 메서드 자체에는 영향 없음) job을
	// MANUAL_REVIEW_REQUIRED로 전이하는 순서를 지키면, 그 사이 크래시가 나도 outbox
	// lease 만료 후 재클레임된 다음 시도가 같은 순서를 다시 밟아 수렴한다(durable
	// 재처리) — case가 없는데 job만 MANUAL_REVIEW_REQUIRED인 상태는 만들어지지 않는다.
	private Outcome handlePipelineFailure(
		OutboxEvent event, FilterJob job, int decisionAttemptGeneration, Duration retryAfterHint
	) {
		boolean willExhaust;
		Instant decideAt = Instant.now(clock);
		try {
			FilterJob attempted = filterJobRepository.findById(job.id())
				.orElseThrow(() -> new FilteringException(
					FilteringErrorCode.INVALID_JOB_STATUS, "filterJobId", "job을 찾을 수 없습니다"))
				.recordAutomatedAttempt(decisionAttemptGeneration, decideAt);
			willExhaust = retryPolicy.decide(attempted, retryAfterHint, decideAt).exhausted();
		} catch (FilteringException raceOnJobState) {
			if (!isJobStateRace(raceOnJobState)) {
				throw raceOnJobState;
			}
			return finishSkipped(event);
		}

		if (willExhaust) {
			openManualReviewCaseIfAbsent(job.target(), job.filterReleaseId(), job.id(), decideAt);
		}

		try {
			return transactionTemplate.execute(status -> {
				Instant now = Instant.now(clock);
				FilterJob current = filterJobRepository.findById(job.id())
					.orElseThrow(() -> new FilteringException(
						FilteringErrorCode.INVALID_JOB_STATUS, "filterJobId", "job을 찾을 수 없습니다"));
				FilterJob attempted = current.recordAutomatedAttempt(decisionAttemptGeneration, now);
				// 소진 여부는 사전 판정과 반드시 같은 시각(decideAt)으로 다시 계산한다 —
				// 서로 다른 시각을 쓰면 maxRetryLifetime 경계가 그 사이에 걸릴 때 case 없이
				// job만 소진 전이되어 위 불변식이 깨진다.
				AnswerModerationRetryPolicy.Decision decision =
					retryPolicy.decide(attempted, retryAfterHint, decideAt);

				FilterReleaseRetryGate gate = filterReleaseRetryGateRepository
					.findOrCreateForUpdate(attempted.filterReleaseId(), now)
					.onFailure(now, retryGateConfig);
				filterReleaseRetryGateRepository.save(gate);

				if (!decision.exhausted()) {
					filterJobRepository.save(attempted);
					failClaimOrThrow(event, now, new OutboxRetryDecision(false, decision.nextAttemptAt()));
					return Outcome.RETRY_SCHEDULED;
				}

				FilterJob reviewOpened = attempted.exhaustRetries(now).openManualReview(now);
				filterJobRepository.save(reviewOpened);
				filterJobStatusHistoryRepository.save(FilterJobStatusHistoryEntry.of(current.id(),
					FilterJobStatus.AUTOMATED, FilterJobStatus.RETRY_EXHAUSTED, "retry budget exhausted", now));
				filterJobStatusHistoryRepository.save(FilterJobStatusHistoryEntry.of(current.id(),
					FilterJobStatus.RETRY_EXHAUSTED, FilterJobStatus.MANUAL_REVIEW_REQUIRED, "manual review handoff",
					now));
				// MODERATION_VERDICT_READY를 발행하지 않는다 — 소진은 판정이 아니며 공개
				// 상태를 바꾸지 않는다(INV-MAN-002). 이 이벤트는 여기서 영구 종결된다.
				failClaimOrThrow(event, now, new OutboxRetryDecision(true, now));
				return Outcome.RETRY_EXHAUSTED;
			});
		} catch (StaleLeaseException staleLease) {
			return Outcome.STALE_LEASE;
		} catch (FilteringException raceOnJobState) {
			if (!isJobStateRace(raceOnJobState)) {
				throw raceOnJobState;
			}
			return finishSkipped(event);
		}
	}

	// case 생성과 최초 priority 평가 기록을 이 호출만의 독립 트랜잭션으로 묶는다
	// (handlePipelineFailure의 메인 트랜잭션과 의도적으로 분리 — case가 먼저 존재해야
	// 메인 트랜잭션의 job 소진 전이가 크래시 후에도 안전하게 재수렴한다).
	//
	// 이 경로는 report signal을 모른다(#110 — safety 패키지 통합은 범위 밖) — 항상
	// validatedReportSignalCount=0으로 평가하고, 실제 report signal 기반 재평가는
	// ManualReviewDecisionService/검토자 endpoint가 별도로 수행한다. 평가 자체가
	// 실패해도(이론상 불가능에 가깝지만) STANDARD+CALCULATION_FAILED로 case 생성을
	// 계속한다(INV-MAN-009).
	private void openManualReviewCaseIfAbsent(FilterTarget target, long filterReleaseId, long filterJobId, Instant now) {
		if (manualReviewCaseRepository.findByTargetAndFilterReleaseId(target, filterReleaseId).isPresent()) {
			return;
		}
		ManualReviewPriorityDecision decision;
		try {
			decision = ManualReviewCase.evaluatePriority(0, manualReviewPriorityPolicy);
		} catch (FilteringException evaluationFailed) {
			decision = new ManualReviewPriorityDecision(
				ManualReviewBand.STANDARD, ManualReviewPriorityReasonCode.CALCULATION_FAILED);
		}
		ManualReviewPriorityDecision finalDecision = decision;
		try {
			transactionTemplate.executeWithoutResult(status -> {
				ManualReviewCase opened = manualReviewCaseRepository.save(ManualReviewCase.open(target,
					filterReleaseId, filterJobId, finalDecision, 0, manualReviewPriorityPolicy.policyVersion(), now));
				manualReviewPriorityEvaluationRepository.save(ManualReviewPriorityEvaluation.of(opened.id(),
					finalDecision.band(), finalDecision.reasonCode(), manualReviewPriorityPolicy.policyVersion(), now));
			});
		} catch (DataIntegrityViolationException raceAlreadyOpened) {
			// 이미 다른 경로(동시 claim, 재클레임된 재시도)로 열렸다 — INV-MAN-001
			// 유일성 제약이 감지한 경쟁을 멱등하게 흡수한다.
		}
	}

	private static boolean isJobStateRace(FilteringException e) {
		return e.getErrorCode() == FilteringErrorCode.ALREADY_MANUALLY_RESOLVED
			|| e.getErrorCode() == FilteringErrorCode.STALE_ATTEMPT_GENERATION
			|| e.getErrorCode() == FilteringErrorCode.INVALID_JOB_STATUS;
	}

	private Outcome finishDead(OutboxEvent event, Outcome deadOutcome) {
		return transactionTemplate.execute(status -> {
			Instant now = Instant.now(clock);
			boolean updated = outboxEventRepository.fail(
				event.id(), event.leaseOwner(), event.leaseGeneration(), now, now, true);
			return updated ? deadOutcome : Outcome.STALE_LEASE;
		});
	}

	// job이 이미 수동으로 종결된 뒤 도착한 자동 처리 시도를 흡수한다. manuallyResolved인
	// job이면 감사 기록만 남기고 상태를 바꾸지 않는다(#110, INV-MAN-004) — 이전에는
	// 이 경로가 outbox claim만 완료하고 어떤 흔적도 남기지 않았다.
	private Outcome finishSkipped(OutboxEvent event) {
		try {
			return transactionTemplate.execute(status -> {
				Instant now = Instant.now(clock);
				filterJobRepository.findById(event.aggregateId()).ifPresent(current -> {
					if (current.manuallyResolved()) {
						filterJobStatusHistoryRepository.save(FilterJobStatusHistoryEntry.of(current.id(),
							current.status(), current.status(),
							"late automated result ignored (already manually resolved)", now));
					}
				});
				completeClaimOrThrow(event, now);
				return Outcome.SKIPPED_NOT_ELIGIBLE;
			});
		} catch (StaleLeaseException staleLease) {
			return Outcome.STALE_LEASE;
		}
	}

	private Outcome completeClaimOrThrow(OutboxEvent event, Instant at) {
		if (!outboxEventRepository.complete(event.id(), event.leaseOwner(), event.leaseGeneration(), at)) {
			throw new StaleLeaseException();
		}
		return Outcome.RESOLVED;
	}

	private void failClaimOrThrow(OutboxEvent event, Instant at, OutboxRetryDecision decision) {
		if (!outboxEventRepository.fail(event.id(), event.leaseOwner(), event.leaseGeneration(), at, decision)) {
			throw new StaleLeaseException();
		}
	}

	private OutboxEvent verdictReadyEvent(FilterJob job, Instant now) {
		FilterTarget target = job.target();
		AnswerModerationEventPayloads.VerdictReady payload = new AnswerModerationEventPayloads.VerdictReady(
			job.id(), target.targetType(), target.targetId(), target.targetVersion(), job.resolvedVerdict());
		return OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, job.id(), OutboxEventType.MODERATION_VERDICT_READY,
			AnswerModerationEventPayloads.verdictReadyDedupKey(job.id()),
			AnswerModerationEventPayloads.toJson(objectMapper, payload), now);
	}

	private void requireCommand(BatchCommand command) {
		if (command == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "command");
		}
	}

	public enum Outcome {
		RESOLVED, SKIPPED_NOT_ELIGIBLE, RETRY_SCHEDULED, RETRY_EXHAUSTED, RETRY_DEFERRED_BY_GATE, JOB_NOT_FOUND,
		STALE_LEASE
	}

	public record BatchResult(int claimed, List<Outcome> outcomes) {
		public BatchResult {
			if (claimed < 0) {
				throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "claimed", "claimed는 음수일 수 없습니다");
			}
			outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
		}
	}

	// at이 null이면 claim 시각을 Clock에서 읽는다. leaseExpiresAt이 실제 claim 시각보다
	// 뒤인지는 그 시각이 확정되는 processBatch에서 검증한다 — 여기서 at != null일 때만
	// 검증하면 at이 null인 호출은 과거 시각의 leaseExpiresAt도 그대로 통과시킬 수 있다.
	public record BatchCommand(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt) {
		public BatchCommand {
			if (limit <= 0 || leaseOwner == null || leaseOwner.isBlank() || leaseExpiresAt == null) {
				throw new FilteringException(
					FilteringErrorCode.INVALID_VALUE_RANGE, "batch", "실행 batch 입력이 유효하지 않습니다");
			}
		}
	}

	private record GateAdmission(boolean admitted, Instant deferUntil) {
		static GateAdmission allow() {
			return new GateAdmission(true, null);
		}

		static GateAdmission defer(Instant deferUntil) {
			return new GateAdmission(false, deferUntil);
		}
	}

	private static final class PipelineUnavailableException extends RuntimeException {
		private final Duration retryAfterHint;

		PipelineUnavailableException(Duration retryAfterHint) {
			this.retryAfterHint = retryAfterHint;
		}

		Duration retryAfterHint() {
			return retryAfterHint;
		}
	}

	private static final class StaleLeaseException extends RuntimeException {
	}
}
