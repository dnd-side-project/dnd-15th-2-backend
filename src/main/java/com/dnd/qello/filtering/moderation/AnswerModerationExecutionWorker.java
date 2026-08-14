package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.repository.FilterJobStatusHistoryRepository;
import com.dnd.qello.filtering.repository.FilterReleaseRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// MODERATION_EXECUTION_REQUESTED 하나를 최초 1회 pipeline 시도로 변환한다(GitHub
// #107). retry/backoff는 이 클래스의 책임이 아니다(#108) — 이 시도가 timeout·
// error로 끝나면 job은 AUTOMATED·미해결 상태로 남고, 이 worker는 같은 이벤트를
// 다시 시도하지 않는다(event를 DEAD로 종결).
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
		List<Outcome> outcomes = claimed.stream().map(this::processClaimed).toList();
		return new BatchResult(claimed.size(), outcomes);
	}

	private Outcome processClaimed(OutboxEvent event) {
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

		ModerationPipelineResult result;
		try {
			result = callPipelineBounded(payload, job);
		} catch (PipelineUnavailableException unavailable) {
			return finishDead(event, Outcome.PIPELINE_UNAVAILABLE);
		}
		return applyVerdict(event, job, payload.attemptGeneration(), result.verdict());
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
			throw new PipelineUnavailableException();
		} catch (Exception e) {
			// ExecutionException(대개 FilteringException(MODERATION_PROVIDER_UNAVAILABLE))과
			// InterruptedException을 모두 "이번 시도 실패"로 취급한다 — 어떤 예외도
			// 여기서 ALLOW/BLOCK으로 바뀌지 않는다.
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new PipelineUnavailableException();
		}
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
				FilterJob current = filterJobRepository.findById(job.id())
					.orElseThrow(() -> new FilteringException(
						FilteringErrorCode.INVALID_JOB_STATUS, "filterJobId", "job을 찾을 수 없습니다"));
				FilterJob resolved = current.applyAutomatedDecision(decisionAttemptGeneration, verdict, now);
				filterJobRepository.save(resolved);
				filterJobStatusHistoryRepository.save(FilterJobStatusHistoryEntry.of(
					current.id(), current.status(), FilterJobStatus.RESOLVED, "automated decision", now));
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

	private Outcome finishSkipped(OutboxEvent event) {
		try {
			return transactionTemplate.execute(status -> {
				Instant now = Instant.now(clock);
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

	public enum Outcome {RESOLVED, SKIPPED_NOT_ELIGIBLE, PIPELINE_UNAVAILABLE, JOB_NOT_FOUND, STALE_LEASE}

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

	private static final class PipelineUnavailableException extends RuntimeException {
	}

	private static final class StaleLeaseException extends RuntimeException {
	}
}
