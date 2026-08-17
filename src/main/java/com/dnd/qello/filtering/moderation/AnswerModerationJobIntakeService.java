package com.dnd.qello.filtering.moderation;

import com.dnd.qello.filtering.domain.*;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// 답변 담당 코드가 호출하는 moderation job 접수 진입점(GitHub #107). 활성 release와
// deadline을 원자적으로 고정하고, durable job과 pipeline 실행 요청을 같은
// 트랜잭션에 커밋한다 — 이 메서드가 정상 반환하면 job은 유실되지 않는다.
//
// filtering.config.AnswerModerationIntakeConfig가 맡는다 — 이 클래스 자신은 여전히
// @Component로 스캔되지 않고, Duration 등 운영값을 명시적으로 주입받는 구조를 유지한다.
// @Transactional 프록시 없이 TransactionTemplate으로 트랜잭션 경계를 직접 여는 것도
// 그대로다(DirectionMatchingWorker와 같은 방식).
//
// 실행 자체(공급자 호출)는 이 서비스가 하지 않는다 — job 생성과 pipeline 실행을
// 분리해 답변 제출 요청 스레드가 외부 I/O를 기다리지 않게 한다
// (AnswerModerationExecutionWorker가 MODERATION_EXECUTION_REQUESTED를 소비한다).
public class AnswerModerationJobIntakeService implements AnswerModerationIntake {

    private final FilterJobRepository filterJobRepository;
    private final FilterReleaseRepository filterReleaseRepository;
    private final FilterJobStatusHistoryRepository filterJobStatusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Duration deadlineWindow;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AnswerModerationJobIntakeService(
            FilterJobRepository filterJobRepository,
            FilterReleaseRepository filterReleaseRepository,
            FilterJobStatusHistoryRepository filterJobStatusHistoryRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Duration deadlineWindow,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.filterJobRepository = filterJobRepository;
        this.filterReleaseRepository = filterReleaseRepository;
        this.filterJobStatusHistoryRepository = filterJobStatusHistoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.deadlineWindow = deadlineWindow;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // idempotencyKey가 이미 접수된 job을 가리키면 그 job을 그대로 반환하고 아무것도
    // 새로 만들지 않는다(INV-GEN-003) — 중복 호출이 job이나 콜백/이벤트를 추가로
    // 만들지 않는다. 현재 승격된 release가 없으면 fail-closed로 거부한다.
    //
    // 조회 후 삽입 사이에는 경쟁 구간이 있다 — 두 트랜잭션이 모두 부재를 읽고 동시에
    // createJob을 실행하면 idempotencyKey unique 제약이 최종 중재자로서 후발 트랜잭션의
    // 삽입만 막는다. 그 경우 예외를 그대로 전파하지 않고, 새 트랜잭션에서 먼저 커밋된
    // job을 다시 조회해 반환한다.
    @Override
    public FilterJob submit(
            FilterTarget target, String rawContent, ModerationLanguage language, String idempotencyKey
    ) {
        try {
            return transactionTemplate.execute(status -> filterJobRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseGet(() -> createJob(target, rawContent, language, idempotencyKey)));
        } catch (DataIntegrityViolationException concurrentInsert) {
            return transactionTemplate.execute(status -> filterJobRepository.findByIdempotencyKey(idempotencyKey))
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private FilterJob createJob(
            FilterTarget target, String rawContent, ModerationLanguage language, String idempotencyKey
    ) {
        FilterRelease release = filterReleaseRepository.findCurrentlyPromoted()
                .orElseThrow(() -> new FilteringException(FilteringErrorCode.NO_ACTIVE_RELEASE, "release"));
        Instant now = Instant.now(clock);
        Instant deadlineAt = now.plus(deadlineWindow);

        FilterJob job = filterJobRepository.save(
                FilterJob.create(target, release.id(), idempotencyKey, deadlineAt, now));
        filterJobStatusHistoryRepository.save(
                FilterJobStatusHistoryEntry.of(job.id(), null, FilterJobStatus.AUTOMATED, "job submitted", now));
        outboxEventRepository.save(executionRequestedEvent(job, target, rawContent, language, release, now));
        return job;
    }

    private OutboxEvent executionRequestedEvent(
            FilterJob job, FilterTarget target, String rawContent, ModerationLanguage language,
            FilterRelease release, Instant now
    ) {
        AnswerModerationEventPayloads.ExecutionRequested payload = new AnswerModerationEventPayloads.ExecutionRequested(
                job.id(), target.targetType(), target.targetId(), target.targetVersion(),
                rawContent, language, release.id(), job.attemptGeneration());
        return OutboxEvent.pending(OutboxAggregateType.FILTER_JOB, job.id(),
                OutboxEventType.MODERATION_EXECUTION_REQUESTED,
                AnswerModerationEventPayloads.executionRequestedDedupKey(job.id()),
                AnswerModerationEventPayloads.toJson(objectMapper, payload), now);
    }
}
