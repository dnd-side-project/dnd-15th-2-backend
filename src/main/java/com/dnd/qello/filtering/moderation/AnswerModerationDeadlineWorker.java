package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// deadline이 지났는데 아직 RESOLVED되지 않은 job에 MODERATION_DEADLINE_ELAPSED를
// 한 번만 발행한다(GitHub #107). 이 신호는 승인이 아니다(INV-ANS-003,
// INV-ANS-004) — 공개 정책 결정은 답변 담당 코드가 한다.
//
// FilterJobRepository·OutboxEventRepository 모두 이미 구현체가 있어 컴포넌트
// 스캔이 가능하다 — DirectionMatchingWorker와 같은 이유로 이 클래스는 빈이지만
// 아무 trigger(@Scheduled 등)도 갖지 않는다. 운영 주기 실행 활성화는 이 이슈의
// 범위가 아니다.
//
// 재스캔에도 같은 job에 두 번 신호를 보내지 않도록 dedup_key 존재 여부를 먼저
// 확인하고, 그 확인과 삽입 사이의 경쟁은 DB unique 제약 위반을 잡아 흡수한다 —
// job마다 독립된 트랜잭션을 써야 한 job의 제약 위반이 나머지 batch를 막지 않는다
// (DirectionMatchingWorker의 event별 트랜잭션 분리와 같은 이유).
@Service
public class AnswerModerationDeadlineWorker {

	private final FilterJobRepository filterJobRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;
	private final PlatformTransactionManager transactionManager;
	private final Clock clock;

	public AnswerModerationDeadlineWorker(
		FilterJobRepository filterJobRepository,
		OutboxEventRepository outboxEventRepository,
		ObjectMapper objectMapper,
		PlatformTransactionManager transactionManager,
		Clock clock
	) {
		this.filterJobRepository = filterJobRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.objectMapper = objectMapper;
		this.transactionManager = transactionManager;
		this.clock = clock;
	}

	public BatchResult processBatch(int limit, Instant at) {
		if (limit <= 0) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "limit", "limit은 양수여야 합니다");
		}
		Instant scanAt = at == null ? clock.instant() : at;
		List<FilterJob> due = filterJobRepository.findDeadlineElapsedCandidates(scanAt, limit);
		List<Outcome> outcomes = due.stream().map(job -> processOne(job, scanAt)).toList();
		return new BatchResult(due.size(), outcomes);
	}

	private Outcome processOne(FilterJob job, Instant at) {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		try {
			return transaction.execute(status -> emitIfAbsent(job, at));
		} catch (DataIntegrityViolationException raceAlreadyFired) {
			return Outcome.ALREADY_SIGNALED;
		}
	}

	private Outcome emitIfAbsent(FilterJob job, Instant at) {
		String dedupKey = dedupKey(job.id());
		if (outboxEventRepository.findByDedupKey(dedupKey).isPresent()) {
			return Outcome.ALREADY_SIGNALED;
		}
		outboxEventRepository.save(deadlineElapsedEvent(job, dedupKey, at));
		return Outcome.SIGNALED;
	}

	private OutboxEvent deadlineElapsedEvent(FilterJob job, String dedupKey, Instant at) {
		FilterTarget target = job.target();
		AnswerModerationEventPayloads.DeadlineElapsed payload = new AnswerModerationEventPayloads.DeadlineElapsed(
			job.id(), target.targetType(), target.targetId(), target.targetVersion());
		return OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, job.id(),
			OutboxEventType.MODERATION_DEADLINE_ELAPSED, dedupKey,
			AnswerModerationEventPayloads.toJson(objectMapper, payload), at);
	}

	private static String dedupKey(long filterJobId) {
		return "filter-job:" + filterJobId + ":DEADLINE_ELAPSED";
	}

	public enum Outcome {SIGNALED, ALREADY_SIGNALED}

	public record BatchResult(int scanned, List<Outcome> outcomes) {
		public BatchResult {
			if (scanned < 0) {
				throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "scanned", "scanned는 음수일 수 없습니다");
			}
			outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
		}
	}
}
