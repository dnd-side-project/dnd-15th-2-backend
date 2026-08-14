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
			return finishDead(event);
		}
		FilterJob job = jobOpt.get();
		if (job.status() != FilterJobStatus.AUTOMATED) {
			return finishSkipped(event);
		}

		ModerationPipelineResult result;
		try {
			result = callPipelineBounded(payload, job);
		} catch (PipelineUnavailableException unavailable) {
			return finishDead(event);
		}
		return applyVerdict(event, job, result.verdict());
	}

	private ModerationPipelineResult callPipelineBounded(
		AnswerModerationEventPayloads.ExecutionRequested payload, FilterJob job
	) {
		FilterRelease release = filterReleaseRepository.findById(job.filterReleaseId())
			.orElseThrow(() -> new FilteringException(FilteringErrorCode.RELEASE_NOT_FOUND, "filterReleaseId"));
		ModerationPipelineRequest request = ModerationPipelineRequest.forJob(payload.targetType(),
			payload.language(), payload.rawContent(), release, job.id(), job.attemptGeneration());

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

	private Outcome applyVerdict(OutboxEvent event, FilterJob job, FilterVerdict verdict) {
		try {
			Outcome outcome = transactionTemplate.execute(status -> {
				Instant now = Instant.now(clock);
				FilterJob resolved = job.applyAutomatedDecision(job.attemptGeneration(), verdict, now);
				filterJobRepository.save(resolved);
				filterJobStatusHistoryRepository.save(FilterJobStatusHistoryEntry.of(
					job.id(), job.status(), FilterJobStatus.RESOLVED, "automated decision", now));
				outboxEventRepository.save(verdictReadyEvent(resolved, now));
				return completeClaimOrThrow(event, now);
			});
			return outcome;
		} catch (StaleLeaseException staleLease) {
			return Outcome.STALE_LEASE;
		} catch (FilteringException raceOnJobState) {
			// 이 worker가 pipeline을 기다리는 사이 job이 이미 다른 경로(수동 결정 등)로
			// 종결된 race. 이 결과를 덮어쓰지 않고 claim만 완료한다.
			return finishSkipped(event);
		}
	}

	private Outcome finishDead(OutboxEvent event) {
		return transactionTemplate.execute(status -> {
			Instant now = Instant.now(clock);
			boolean updated = outboxEventRepository.fail(
				event.id(), event.leaseOwner(), event.leaseGeneration(), now, now, true);
			return updated ? Outcome.PIPELINE_UNAVAILABLE : Outcome.STALE_LEASE;
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
		String dedupKey = "filter-job:" + job.id() + ":VERDICT_READY";
		return OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, job.id(), OutboxEventType.MODERATION_VERDICT_READY,
			dedupKey, AnswerModerationEventPayloads.toJson(objectMapper, payload), now);
	}

	private void requireCommand(BatchCommand command) {
		if (command == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "command");
		}
	}

	public enum Outcome {RESOLVED, SKIPPED_NOT_ELIGIBLE, PIPELINE_UNAVAILABLE, STALE_LEASE}

	public record BatchResult(int claimed, List<Outcome> outcomes) {
		public BatchResult {
			if (claimed < 0) {
				throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "claimed", "claimed는 음수일 수 없습니다");
			}
			outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
		}
	}

	/** at이 null이면 claim 시각을 Clock에서 읽는다. */
	public record BatchCommand(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt) {
		public BatchCommand {
			if (limit <= 0 || leaseOwner == null || leaseOwner.isBlank() || leaseExpiresAt == null
				|| (at != null && !leaseExpiresAt.isAfter(at))) {
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
