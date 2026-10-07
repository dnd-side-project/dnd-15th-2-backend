package com.dnd.qello.filtering.moderation;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MODERATION_VERDICT_READY와 MODERATION_DEADLINE_ELAPSED를 소비해 대상 콘텐츠 상태에 반영하는
 * consumer. AnswerModerationExecutionWorker와 같은 outbox claim/lease 패턴을 재사용한다 —
 * 이 worker는 pipeline을 호출하지 않고 이미 내려진 판정을 적용만 하므로 재시도·gate 로직은 없다.
 *
 * <p>
 * 두 이벤트 모두 targetType이 같은 {@link ModerationVerdictApplier}에 위임한다(#137). 이벤트
 * 타입으로만 claim하므로 답변·질문글 판정이 모두 이 worker로 들어온다. 적용기가 없는 대상(NICKNAME 등)은 상태를 바꾸지
 * 않고 claim만 완료한다. 판정의 의미(공개, 매칭 가능, 보류)와 중복·늦은 이벤트 방어는 적용기가 맡는다.
 * </p>
 *
 * <p>
 * AnswerModerationJobIntakeService·AnswerModerationExecutionWorker와 달리 이
 * worker는 deadlineWindow 같은 미정 운영값에 의존하지 않으므로 Spring bean으로 등록한다. 주기 실행은
 * AnswerModerationVerdictScheduledAdapter가 맡는다.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class AnswerModerationVerdictWorker {

	private static final Set<OutboxEventType> CONSUMED_EVENT_TYPES = Set.of(OutboxEventType.MODERATION_VERDICT_READY,
			OutboxEventType.MODERATION_DEADLINE_ELAPSED);

	private final OutboxEventRepository outboxEventRepository;
	private final Map<FilterTargetType, ModerationVerdictApplier> appliers;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public AnswerModerationVerdictWorker(OutboxEventRepository outboxEventRepository,
			List<ModerationVerdictApplier> appliers, ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.outboxEventRepository = outboxEventRepository;
		this.appliers = indexByTargetType(appliers);
		this.objectMapper = objectMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	// 이벤트별 적용과 claim 완료는 각자 트랜잭션을 연다. 클래스 read-only 트랜잭션에 합류하지 않도록
	// 진입 메서드는 트랜잭션 없이 실행한다.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public BatchResult processBatch(BatchCommand command) {
		requireCommand(command);
		Instant claimAt = command.at() == null ? Instant.now(clock) : command.at();
		if (!command.leaseExpiresAt().isAfter(claimAt)) {
			throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "batch", "실행 batch 입력이 유효하지 않습니다");
		}
		List<OutboxEvent> claimed = outboxEventRepository.claimDue(CONSUMED_EVENT_TYPES, command.limit(),
				command.leaseOwner(), claimAt, command.leaseExpiresAt());
		List<Outcome> outcomes = claimed.stream().map(this::processClaimed).toList();
		return new BatchResult(claimed.size(), outcomes);
	}

	// 이벤트별로 예외를 격리한다 — 한 이벤트의 실패(제약 위반이 아닌 다른 오류 포함)가
	// 같은 batch의 나머지 이벤트 처리를 막지 않게 한다(AnswerModerationDeadlineWorker.processOne과 동일한
	// 패턴).
	//
	// 적용과 completeClaimOrThrow를 의도적으로 별도 물리 transaction으로 나눈다. 적용기가 자신의
	// 트랜잭션에서 정책상 예상된 실패를 rollback해도 claim 완료는 영향받지 않는 새 transaction에서
	// 실행된다(AnswerModerationVerdictApplier 참고). 적용 후 완료 전에 실패하면 이벤트가 다시 전달되므로
	// 적용기는 멱등해야 한다.
	private Outcome processClaimed(OutboxEvent event) {
		try {
			Instant now = Instant.now(clock);
			if (event.eventType() == OutboxEventType.MODERATION_VERDICT_READY) {
				applyVerdictReady(event, now);
			} else {
				applyDeadlineElapsed(event, now);
			}
			return transactionTemplate.execute(status -> completeClaimOrThrow(event, now));
		} catch (StaleLeaseException staleLease) {
			return Outcome.STALE_LEASE;
		} catch (RuntimeException failed) {
			return Outcome.FAILED;
		}
	}

	private void applyVerdictReady(OutboxEvent event, Instant now) {
		AnswerModerationEventPayloads.VerdictReady payload = AnswerModerationEventPayloads.fromJson(objectMapper,
				event.payload(), AnswerModerationEventPayloads.VerdictReady.class);
		applierFor(payload.targetType())
				.ifPresent(applier -> applier.applyVerdict(payload.targetId(), payload.verdict(), now));
	}

	private void applyDeadlineElapsed(OutboxEvent event, Instant now) {
		AnswerModerationEventPayloads.DeadlineElapsed payload = AnswerModerationEventPayloads.fromJson(objectMapper,
				event.payload(), AnswerModerationEventPayloads.DeadlineElapsed.class);
		applierFor(payload.targetType()).ifPresent(applier -> applier.applyDeadlineElapsed(payload.targetId(), now));
	}

	private Optional<ModerationVerdictApplier> applierFor(FilterTargetType targetType) {
		return Optional.ofNullable(targetType).map(appliers::get);
	}

	private Outcome completeClaimOrThrow(OutboxEvent event, Instant at) {
		if (!outboxEventRepository.complete(event.id(), event.leaseOwner(), event.leaseGeneration(), at)) {
			throw new StaleLeaseException();
		}
		return Outcome.RESOLVED;
	}

	private void requireCommand(BatchCommand command) {
		if (command == null) {
			throw new FilteringException(FilteringErrorCode.REQUIRED_VALUE_MISSING, "command");
		}
	}

	// 같은 대상 종류에 적용기가 둘이면 어느 쪽이 판정을 반영할지 정할 수 없다. 기동 시점에 실패시킨다.
	private static Map<FilterTargetType, ModerationVerdictApplier> indexByTargetType(
			List<ModerationVerdictApplier> appliers) {
		Map<FilterTargetType, ModerationVerdictApplier> indexed = new EnumMap<>(FilterTargetType.class);
		for (ModerationVerdictApplier applier : appliers == null ? List.<ModerationVerdictApplier>of() : appliers) {
			if (indexed.putIfAbsent(applier.targetType(), applier) != null) {
				throw new IllegalStateException("moderation verdict applier is duplicated: " + applier.targetType());
			}
		}
		return Map.copyOf(indexed);
	}

	public enum Outcome {
		RESOLVED, STALE_LEASE, FAILED
	}

	public record BatchResult(int claimed, List<Outcome> outcomes) {
		public BatchResult {
			if (claimed < 0) {
				throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "claimed", "claimed는 음수일 수 없습니다");
			}
			outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
		}
	}

	public record BatchCommand(int limit, String leaseOwner, Instant at, Instant leaseExpiresAt) {
		public BatchCommand {
			if (limit <= 0 || leaseOwner == null || leaseOwner.isBlank() || leaseExpiresAt == null) {
				throw new FilteringException(FilteringErrorCode.INVALID_VALUE_RANGE, "batch", "실행 batch 입력이 유효하지 않습니다");
			}
		}
	}

	private static final class StaleLeaseException extends RuntimeException {
	}
}
