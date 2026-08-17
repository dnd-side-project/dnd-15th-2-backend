package com.dnd.qello.filtering.moderation;

import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;
import com.dnd.qello.answer.service.AnswerNotificationService;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * MODERATION_VERDICT_READY와 MODERATION_DEADLINE_ELAPSED를 소비해 답변 공개 여부에
 * 반영하는 consumer. AnswerModerationExecutionWorker와 같은 outbox
 * claim/lease 패턴을 재사용한다 — 이 worker는 pipeline을 호출하지 않고 이미 내려진
 * 판정을 답변 상태에 적용만 하므로 재시도·gate 로직은 없다.
 *
 * <p>ALLOW는 {@link AnswerNotificationService#publish}에, BLOCK은
 * {@link AnswerNotificationService#reject}에 위임한다 — 두 메서드 모두 이미 종결
 * 상태(PUBLISHED/REJECTED)면 멱등하게 반환하므로 이 worker는 자체 중복 방지 로직을
 * 두지 않는다.</p>
 *
 * <p>MODERATION_DEADLINE_ELAPSED는 fail-closed로 답변을 건드리지 않고 이벤트만
 * 소비한다(INV-ANS-003, INV-ANS-004) — 늦게 도착한 유효 ALLOW/BLOCK도 그대로
 * 적용할 수 있어야 하므로 이 시점에 답변을 종결 상태로 만들지 않는다.</p>
 *
 * <p>AnswerModerationJobIntakeService·AnswerModerationExecutionWorker와 달리 이 worker는
 * deadlineWindow 같은 미정 운영값에 의존하지 않으므로 Spring bean으로 등록한다.
 * AnswerModerationDeadlineWorker와 같은 이유로 아무 trigger(@Scheduled 등)도 갖지
 * 않는다 — 운영 주기 실행 활성화는 이 이슈의 범위가 아니다.</p>
 */
@Service
public class AnswerModerationVerdictWorker {

    private static final Set<OutboxEventType> CONSUMED_EVENT_TYPES =
            Set.of(OutboxEventType.MODERATION_VERDICT_READY, OutboxEventType.MODERATION_DEADLINE_ELAPSED);

    private final OutboxEventRepository outboxEventRepository;
    private final AnswerNotificationService answerNotificationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AnswerModerationVerdictWorker(
            OutboxEventRepository outboxEventRepository,
            AnswerNotificationService answerNotificationService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.answerNotificationService = answerNotificationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public BatchResult processBatch(BatchCommand command) {
        requireCommand(command);
        Instant claimAt = command.at() == null ? Instant.now(clock) : command.at();
        if (!command.leaseExpiresAt().isAfter(claimAt)) {
            throw new FilteringException(
                    FilteringErrorCode.INVALID_VALUE_RANGE, "batch", "실행 batch 입력이 유효하지 않습니다");
        }
        List<OutboxEvent> claimed = outboxEventRepository.claimDue(
                CONSUMED_EVENT_TYPES, command.limit(), command.leaseOwner(), claimAt, command.leaseExpiresAt());
        List<Outcome> outcomes = claimed.stream().map(this::processClaimed).toList();
        return new BatchResult(claimed.size(), outcomes);
    }

    // 이벤트별로 예외를 격리한다 — 한 이벤트의 실패(제약 위반이 아닌 다른 오류 포함)가
    // 같은 batch의 나머지 이벤트 처리를 막지 않게 한다(AnswerModerationDeadlineWorker.processOne과 동일한 패턴).
    private Outcome processClaimed(OutboxEvent event) {
        try {
            return event.eventType() == OutboxEventType.MODERATION_VERDICT_READY
                    ? processVerdictReady(event)
                    : finishWithoutStateChange(event);
        } catch (StaleLeaseException staleLease) {
            return Outcome.STALE_LEASE;
        } catch (RuntimeException failed) {
            return Outcome.FAILED;
        }
    }

    private Outcome processVerdictReady(OutboxEvent event) {
        AnswerModerationEventPayloads.VerdictReady payload = AnswerModerationEventPayloads.fromJson(
                objectMapper, event.payload(), AnswerModerationEventPayloads.VerdictReady.class);
        if (payload.targetType() != FilterTargetType.ANSWER) {
            return finishWithoutStateChange(event);
        }
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now(clock);
            if (payload.verdict() == FilterVerdict.ALLOW) {
                applyAllow(payload.targetId(), now);
            } else {
                answerNotificationService.reject(payload.targetId(), now);
            }
            return completeClaimOrThrow(event, now);
        });
    }

    // releaseSlot 실패(post_recipient가 이미 EXPIRED/BLOCKED/SKIPPED로 선점됨)는 재시도로
    // 해소되지 않는 정책 결과다 — 이벤트를 완료 처리하고 답변은 공개되지 않은 채로 둔다.
    private void applyAllow(long answerId, Instant now) {
        try {
            answerNotificationService.publish(answerId, now);
        } catch (AnswerException ineligible) {
            if (ineligible.getErrorCode() != AnswerErrorCode.INVALID_ANSWER_STATUS) {
                throw ineligible;
            }
        }
    }

    // MODERATION_DEADLINE_ELAPSED와, ANSWER 대상이 아닌 VERDICT_READY 둘 다 답변 상태를 바꾸지 않고
    // claim만 완료 처리한다는 점에서 동일하다.
    private Outcome finishWithoutStateChange(OutboxEvent event) {
        return transactionTemplate.execute(status -> completeClaimOrThrow(event, Instant.now(clock)));
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

    public enum Outcome {RESOLVED, STALE_LEASE, FAILED}

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
                throw new FilteringException(
                        FilteringErrorCode.INVALID_VALUE_RANGE, "batch", "실행 batch 입력이 유효하지 않습니다");
            }
        }
    }

    private static final class StaleLeaseException extends RuntimeException {
    }
}
