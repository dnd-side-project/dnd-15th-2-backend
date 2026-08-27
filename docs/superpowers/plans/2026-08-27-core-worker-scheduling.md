# Core Worker Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 worker 로직과 DB schema를 바꾸지 않고 7개 core worker를 설정 기반으로 안전하게 주기 실행하고 결과를 Micrometer로 관측한다.

**Architecture:** `com.dnd.qello.scheduling`에 조건부 scheduling infrastructure와 worker별 얇은 `fixedDelay` adapter를 둔다. Outbox는 process singleton owner와 기존 lease/generation을, Push는 기존 group/device generation을, Sweep은 기존 행 잠금과 멱등 전이를 사용한다. Global default는 OFF이며 enabled worker의 운영 수치는 반드시 외부 설정으로 주입한다.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Scheduling, Spring Configuration Properties, Micrometer, JUnit 5, AssertJ, Mockito, PostgreSQL/Testcontainers

**Spec:** `docs/superpowers/specs/2026-08-27-core-worker-scheduling-design.md`

**Approval:** Human partner approved this test and implementation plan for SDD execution at `2026-08-27T14:19:55+09:00`.

## Global Constraints

- GitHub Issue와 branch type은 `#182`, `chore`로 유지한다.
- 테스트 계약은 `docs/test-plans/gh-182-TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING.md`를 따른다.
- `qello.worker.scheduling.enabled`는 기본 `false`이고 실제 운영 수치 fallback을 추가하지 않는다.
- Scheduling은 `fixedDelay`만 사용하며 같은 process의 같은 adapter 실행을 겹치지 않는다.
- 기존 7개 worker 업무 클래스, repository interface/SQL과 Flyway migration은 수정하지 않는다.
- 실제 FCM, Slack, moderation provider, secret, `.env` 값과 인프라 변경을 사용하지 않는다.
- Metric tag는 enum에서 나온 `worker`, `outcome`만 허용한다.
- 신규 JUnit 5 class header에는 실행 시점의 `date -Iseconds` 결과와 원본 scenario ID를 기록하고 모든 test method에 `@DisplayName`을 붙인다.
- 구현 에이전트는 자신에게 배정된 파일만 수정하며 다른 변경을 되돌리지 않는다.

---

### Task 1: Scheduling properties와 instance identity

**Files:**
- Create: `src/main/java/com/dnd/qello/scheduling/config/WorkerSchedulingProperties.java`
- Create: `src/main/java/com/dnd/qello/scheduling/WorkerInstanceIdentity.java`
- Create: `src/test/java/com/dnd/qello/scheduling/config/WorkerSchedulingPropertiesTest.java`
- Create: `src/test/java/com/dnd/qello/scheduling/WorkerInstanceIdentityTest.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: `WorkerSchedulingProperties` with explicit settings for four Outbox, two Sweep and one Push worker
- Produces: `WorkerInstanceIdentity.random()` and `String owner()` with a maximum length of 100
- Consumes: Spring Boot relaxed configuration binding and `outbox_event.lease_owner VARCHAR(100)`

- [ ] **Step 1: Record the exact test creation timestamp**

Run:

```bash
date -Iseconds
```

Put that exact value in both new test class headers. Use source scenarios
`TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-001 through UNIT-005`.

- [ ] **Step 2: Write failing properties validation and binding tests**

Create `WorkerSchedulingPropertiesTest` with `ApplicationContextRunner` and direct record construction.
The test fixtures use these exact non-production values:

```java
private static final String PREFIX = "qello.worker.scheduling";
private static final String[] ENABLED_DIRECTION_MATCHING = {
    PREFIX + ".enabled=true",
    PREFIX + ".pool-size=3",
    PREFIX + ".direction-matching.enabled=true",
    PREFIX + ".direction-matching.fixed-delay=PT0.05S",
    PREFIX + ".direction-matching.batch-size=7",
    PREFIX + ".direction-matching.lease-duration=PT30S",
    PREFIX + ".direction-matching.retry.max-attempts=3",
    PREFIX + ".direction-matching.retry.base-delay=PT1S",
    PREFIX + ".direction-matching.retry.max-delay=PT30S"
};
```

Add tests with these exact assertions:

```java
@Test
@DisplayName("UNIT-001: global OFF는 worker 수치가 없어도 안전하게 binding된다")
void disabledSchedulingDoesNotRequireOperationalValues() {
    runner.withPropertyValues(PREFIX + ".enabled=false")
        .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WorkerSchedulingProperties.class).enabled()).isFalse();
        });
}

@Test
@DisplayName("UNIT-002: global ON은 양수 pool size를 요구한다")
void enabledSchedulingRejectsMissingPoolSize() {
    runner.withPropertyValues(PREFIX + ".enabled=true")
        .run(context -> assertThat(context).hasFailed());
}

@Test
@DisplayName("UNIT-003: enabled Outbox worker는 delay·batch·lease·retry 전부를 요구한다")
void enabledOutboxWorkerRejectsMissingLeaseAndRetry() {
    runner.withPropertyValues(
        PREFIX + ".enabled=true",
        PREFIX + ".pool-size=1",
        PREFIX + ".direction-matching.enabled=true",
        PREFIX + ".direction-matching.fixed-delay=PT1S",
        PREFIX + ".direction-matching.batch-size=1")
        .run(context -> assertThat(context).hasFailed());
}
```

Add one binding test that supplies distinct values for all seven worker keys and asserts that no
settings are exchanged. Add direct constructor tests for zero/negative delay, batch, lease,
maxAttempts and maxDelay smaller than baseDelay.

- [ ] **Step 3: Run the properties test and confirm RED**

Run:

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.config.WorkerSchedulingPropertiesTest'
```

Expected: compile failure because `WorkerSchedulingProperties` does not exist.

- [ ] **Step 4: Implement the configuration properties model**

Implement this public shape:

```java
@ConfigurationProperties(prefix = "qello.worker.scheduling")
public record WorkerSchedulingProperties(
    boolean enabled,
    int poolSize,
    OutboxSettings directionMatching,
    OutboxSettings recipientNotificationFanOut,
    OutboxSettings notificationFanOut,
    OutboxSettings reportResolutionFanOut,
    SweepSettings recipientExpirationSweep,
    SweepSettings skipConfirmationSweep,
    PushSettings pushDeliveryDispatch
) {
    public record OutboxSettings(
        boolean enabled, Duration fixedDelay, int batchSize,
        Duration leaseDuration, OutboxRetrySettings retry) {}

    public record SweepSettings(
        boolean enabled, Duration fixedDelay, int batchSize) {}

    public record PushSettings(
        boolean enabled, Duration fixedDelay, int batchSize,
        Duration leaseDuration, PushRetrySettings retry) {}

    public record OutboxRetrySettings(
        int maxAttempts, Duration baseDelay, Duration maxDelay) {}

    public record PushRetrySettings(
        int maxAttempts, Duration baseBackoff, Duration backoffCap) {}
}
```

Validate only the global configuration and nested settings whose `enabled` flag is true. Use
`IllegalArgumentException` with field names only; do not include property values in messages.

Add this safe default to `application.properties`:

```properties
# Core worker scheduling은 운영 수치 승인과 명시적 활성화 전까지 꺼 둔다.
qello.worker.scheduling.enabled=${QELLO_WORKER_SCHEDULING_ENABLED:false}
```

Do not add pool, delay, batch, lease or retry defaults.

- [ ] **Step 5: Write the failing identity tests**

```java
@Test
@DisplayName("UNIT-005: 한 identity는 안정적이고 서로 새로 만든 identity는 다르다")
void ownerIsStablePerIdentityAndUniqueAcrossIdentities() {
    WorkerInstanceIdentity first = WorkerInstanceIdentity.random();
    WorkerInstanceIdentity second = WorkerInstanceIdentity.random();

    assertThat(first.owner()).isEqualTo(first.owner());
    assertThat(first.owner()).isNotBlank().hasSizeLessThanOrEqualTo(100);
    assertThat(second.owner()).isNotEqualTo(first.owner());
}
```

- [ ] **Step 6: Run the identity test and confirm RED**

Run:

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.WorkerInstanceIdentityTest'
```

Expected: compile failure because `WorkerInstanceIdentity` does not exist.

- [ ] **Step 7: Implement the identity value**

```java
public record WorkerInstanceIdentity(String owner) {
    private static final String PREFIX = "worker-";
    private static final int MAX_OWNER_LENGTH = 100;

    public WorkerInstanceIdentity {
        if (owner == null || owner.isBlank() || owner.length() > MAX_OWNER_LENGTH) {
            throw new IllegalArgumentException("worker owner는 1~100자여야 합니다");
        }
    }

    public static WorkerInstanceIdentity random() {
        return new WorkerInstanceIdentity(PREFIX + UUID.randomUUID());
    }
}
```

- [ ] **Step 8: Run Task 1 tests and confirm GREEN**

Run:

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.config.WorkerSchedulingPropertiesTest' --tests 'com.dnd.qello.scheduling.WorkerInstanceIdentityTest'
```

Expected: PASS.

- [ ] **Step 9: Commit Task 1**

```bash
git add src/main/java/com/dnd/qello/scheduling/config/WorkerSchedulingProperties.java src/main/java/com/dnd/qello/scheduling/WorkerInstanceIdentity.java src/test/java/com/dnd/qello/scheduling/config/WorkerSchedulingPropertiesTest.java src/test/java/com/dnd/qello/scheduling/WorkerInstanceIdentityTest.java src/main/resources/application.properties
git commit -m "chore(scheduling): add worker scheduling properties and identity (#182)"
```

### Task 2: Conditional scheduler infrastructure와 safe metrics

**Files:**
- Create: `src/main/java/com/dnd/qello/scheduling/WorkerSchedulingConfiguration.java`
- Create: `src/main/java/com/dnd/qello/scheduling/observability/WorkerMetrics.java`
- Create: `src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java`
- Create: `src/test/java/com/dnd/qello/scheduling/observability/WorkerMetricsTest.java`

**Interfaces:**
- Consumes: `WorkerSchedulingProperties`, `WorkerInstanceIdentity`, Micrometer `MeterRegistry`
- Produces: conditional bean named `taskScheduler`, singleton `WorkerInstanceIdentity`, `WorkerMetrics`
- Produces: `WorkerMetrics.WorkerName` and `WorkerMetrics.Outcome` enum allowlists

- [ ] **Step 1: Write scheduler configuration RED tests**

Use `ApplicationContextRunner` with `ConfigurationPropertiesAutoConfiguration` and
`SimpleMeterRegistry`. Assert the disabled context has no `ThreadPoolTaskScheduler`, identity or
metrics bean. Assert enabled `pool-size=3` creates one scheduler with pool size 3 and prefix
`qello-worker-`.

```java
@Test
@DisplayName("UNIT-007: global OFF는 scheduling infrastructure를 등록하지 않는다")
void disabledGlobalGateRegistersNoSchedulingInfrastructure() {
    runner.withPropertyValues("qello.worker.scheduling.enabled=false")
        .run(context -> {
            assertThat(context).doesNotHaveBean(ThreadPoolTaskScheduler.class);
            assertThat(context).doesNotHaveBean(WorkerInstanceIdentity.class);
            assertThat(context).doesNotHaveBean(WorkerMetrics.class);
        });
}
```

- [ ] **Step 2: Run configuration test and confirm RED**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.WorkerSchedulingConfigurationTest'
```

Expected: compile failure because configuration and metrics types do not exist.

- [ ] **Step 3: Implement conditional scheduling configuration**

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(WorkerSchedulingProperties.class)
@ConditionalOnProperty(prefix = "qello.worker.scheduling", name = "enabled", havingValue = "true")
public class WorkerSchedulingConfiguration {

    @Bean
    WorkerInstanceIdentity workerInstanceIdentity() {
        return WorkerInstanceIdentity.random();
    }

    @Bean(name = "taskScheduler")
    ThreadPoolTaskScheduler taskScheduler(WorkerSchedulingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.poolSize());
        scheduler.setThreadNamePrefix("qello-worker-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    WorkerMetrics workerMetrics(MeterRegistry registry) {
        return new WorkerMetrics(registry);
    }
}
```

Let Spring manage initialization and shutdown. Do not call `initialize()` manually.

- [ ] **Step 4: Write metrics RED tests**

Test the three meter names, exact counts, and every recorded tag key/value. The public API must
accept only enums and counts:

```java
metrics.recordClaimed(WorkerName.DIRECTION_MATCHING, 3);
metrics.recordScanned(WorkerName.RECIPIENT_EXPIRATION_SWEEP, 5);
metrics.recordOutcome(WorkerName.DIRECTION_MATCHING, Outcome.PROCESSED, 2);
```

Assert the complete tag-key set is a subset of `worker`, `outcome`. Add a registry double that
throws and assert all three methods return normally.

- [ ] **Step 5: Run metrics test and confirm RED**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.observability.WorkerMetricsTest'
```

Expected: compile failure because `WorkerMetrics` does not exist.

- [ ] **Step 6: Implement WorkerMetrics**

Use these exact public constants and enum tags:

```java
public static final String CLAIMED_TOTAL = "qello.worker.claimed.total";
public static final String SCANNED_TOTAL = "qello.worker.scanned.total";
public static final String OUTCOME_TOTAL = "qello.worker.outcome.total";

public enum WorkerName {
    DIRECTION_MATCHING("direction_matching"),
    RECIPIENT_NOTIFICATION_FAN_OUT("recipient_notification_fan_out"),
    NOTIFICATION_FAN_OUT("notification_fan_out"),
    REPORT_RESOLUTION_FAN_OUT("report_resolution_fan_out"),
    RECIPIENT_EXPIRATION_SWEEP("recipient_expiration_sweep"),
    SKIP_CONFIRMATION_SWEEP("skip_confirmation_sweep"),
    PUSH_DELIVERY_DISPATCH("push_delivery_dispatch");
}

public enum Outcome {
    PROCESSED, RETRYABLE, RETRY_SCHEDULED, DEAD, STALE_LEASE, STALE_CLAIM,
    FAILURE_RECORDING_FAILED, RELEASED, INELIGIBLE, FAILED, SENT, CANCELLED,
    BATCH_FAILED
}
```

All instrumentation goes through a private `record(Runnable)` that catches `RuntimeException`.
Reject negative counts before instrumentation and increment counters by the supplied count.

- [ ] **Step 7: Run Task 2 tests and confirm GREEN**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.WorkerSchedulingConfigurationTest' --tests 'com.dnd.qello.scheduling.observability.WorkerMetricsTest'
```

Expected: PASS and no scheduler thread remains after context close.

- [ ] **Step 8: Commit Task 2**

```bash
git add src/main/java/com/dnd/qello/scheduling/WorkerSchedulingConfiguration.java src/main/java/com/dnd/qello/scheduling/observability/WorkerMetrics.java src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java src/test/java/com/dnd/qello/scheduling/observability/WorkerMetricsTest.java
git commit -m "chore(scheduling): add conditional scheduler and worker metrics (#182)"
```

### Task 3: Four Outbox scheduled adapters

**Files:**
- Create: `src/main/java/com/dnd/qello/scheduling/adapter/DirectionMatchingScheduledAdapter.java`
- Create: `src/main/java/com/dnd/qello/scheduling/adapter/RecipientNotificationFanOutScheduledAdapter.java`
- Create: `src/main/java/com/dnd/qello/scheduling/adapter/NotificationFanOutScheduledAdapter.java`
- Create: `src/main/java/com/dnd/qello/scheduling/adapter/ReportResolutionFanOutScheduledAdapter.java`
- Create: `src/test/java/com/dnd/qello/scheduling/adapter/CoreWorkerScheduledAdapterTest.java`

**Interfaces:**
- Consumes: four existing worker `processBatch(BatchCommand)` methods
- Consumes: worker-specific `OutboxSettings`, singleton owner, `Clock`, `WorkerMetrics`
- Produces: four conditional `@Scheduled(fixedDelayString=...)` methods

- [ ] **Step 1: Write adapter RED tests with argument capture**

Use fixed `NOW`, deterministic identity `worker-test-182`, mock workers and `SimpleMeterRegistry`.
For every adapter, call its package-visible `runOnce()` and capture the existing `BatchCommand`.

```java
private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");
private static final WorkerInstanceIdentity IDENTITY =
    new WorkerInstanceIdentity("worker-test-182");

@Test
@DisplayName("UNIT-009: matching adapter는 설정된 limit·owner·lease·retry로 한 batch를 실행한다")
void matchingAdapterBuildsTheConfiguredCommand() {
    when(worker.processBatch(any())).thenReturn(
        new DirectionMatchingWorker.BatchResult(2,
            List.of(DirectionMatchingWorker.Outcome.PROCESSED,
                DirectionMatchingWorker.Outcome.STALE_LEASE)));

    adapter.runOnce();

    ArgumentCaptor<DirectionMatchingWorker.BatchCommand> command =
        ArgumentCaptor.forClass(DirectionMatchingWorker.BatchCommand.class);
    verify(worker).processBatch(command.capture());
    assertThat(command.getValue().limit()).isEqualTo(7);
    assertThat(command.getValue().leaseOwner()).isEqualTo("worker-test-182");
    assertThat(command.getValue().at()).isEqualTo(NOW);
    assertThat(command.getValue().leaseExpiresAt()).isEqualTo(NOW.plusSeconds(30));
}
```

Add explicit tests for recipient fan-out, general fan-out and report fan-out. Their expected metric
worker tags are respectively `recipient_notification_fan_out`, `notification_fan_out` and
`report_resolution_fan_out`. Map all returned outcomes including `FAILURE_RECORDING_FAILED`.

Add reflection assertions that each `runOnce` has `@Scheduled.fixedDelayString` equal to its own
`${qello.worker.scheduling.<worker>.fixed-delay}` placeholder and `fixedRateString` is empty.

Add a first-call exception/second-call success worker test. `runOnce()` records `BATCH_FAILED` and
rethrows the first exception; a direct second call succeeds.

- [ ] **Step 2: Run adapter tests and confirm RED**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.adapter.CoreWorkerScheduledAdapterTest'
```

Expected: compile failure because scheduled adapter classes do not exist.

- [ ] **Step 3: Implement DirectionMatchingScheduledAdapter completely**

Use this exact pattern:

```java
@Component
@ConditionalOnProperty(
    prefix = "qello.worker.scheduling",
    name = {"enabled", "direction-matching.enabled"},
    havingValue = "true")
public class DirectionMatchingScheduledAdapter {
    private final DirectionMatchingWorker worker;
    private final WorkerSchedulingProperties.OutboxSettings settings;
    private final WorkerInstanceIdentity identity;
    private final Clock clock;
    private final WorkerMetrics metrics;
    private final OutboxRetryPolicy retryPolicy;

    public DirectionMatchingScheduledAdapter(
        DirectionMatchingWorker worker,
        WorkerSchedulingProperties properties,
        WorkerInstanceIdentity identity,
        Clock clock,
        WorkerMetrics metrics) {
        this.worker = worker;
        this.settings = properties.directionMatching();
        this.identity = identity;
        this.clock = clock;
        this.metrics = metrics;
        var retry = settings.retry();
        this.retryPolicy = new OutboxRetryPolicy(retry.maxAttempts(),
            ExponentialJitterBackoffStrategy.withRandomJitter(
                retry.baseDelay(), retry.maxDelay()));
    }

    @Scheduled(fixedDelayString =
        "${qello.worker.scheduling.direction-matching.fixed-delay}")
    void runOnce() {
        try {
            Instant at = clock.instant();
            var result = worker.processBatch(new DirectionMatchingWorker.BatchCommand(
                settings.batchSize(), identity.owner(), at,
                at.plus(settings.leaseDuration()), retryPolicy));
            metrics.recordClaimed(WorkerName.DIRECTION_MATCHING, result.claimed());
            result.outcomes().forEach(outcome -> metrics.recordOutcome(
                WorkerName.DIRECTION_MATCHING, map(outcome), 1));
        } catch (RuntimeException failure) {
            metrics.recordOutcome(WorkerName.DIRECTION_MATCHING, Outcome.BATCH_FAILED, 1);
            throw failure;
        }
    }
}
```

The private `map` method must use an exhaustive switch from the worker enum to `WorkerMetrics.Outcome`.
Do not use `valueOf`, arbitrary strings or reflection for metric tags.

- [ ] **Step 4: Implement the other three Outbox adapters with exact mappings**

Each class repeats the explicit pattern so it owns one worker and one configuration path:

```text
RecipientNotificationFanOutScheduledAdapter
  property: recipient-notification-fan-out
  worker tag: RECIPIENT_NOTIFICATION_FAN_OUT
  outcomes: PROCESSED, RETRYABLE, DEAD, STALE_LEASE, FAILURE_RECORDING_FAILED

NotificationFanOutScheduledAdapter
  property: notification-fan-out
  worker tag: NOTIFICATION_FAN_OUT
  outcomes: PROCESSED, RETRYABLE, DEAD, STALE_LEASE, FAILURE_RECORDING_FAILED

ReportResolutionFanOutScheduledAdapter
  property: report-resolution-fan-out
  worker tag: REPORT_RESOLUTION_FAN_OUT
  outcomes: PROCESSED, RETRYABLE, DEAD, STALE_LEASE, FAILURE_RECORDING_FAILED
```

Do not introduce a generic adapter that obscures the concrete worker command types.

- [ ] **Step 5: Run Task 3 tests and confirm GREEN**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.adapter.CoreWorkerScheduledAdapterTest'
```

Expected: PASS with four exact worker metric tags and no free-text tags.

- [ ] **Step 6: Commit Task 3**

```bash
git add src/main/java/com/dnd/qello/scheduling/adapter/DirectionMatchingScheduledAdapter.java src/main/java/com/dnd/qello/scheduling/adapter/RecipientNotificationFanOutScheduledAdapter.java src/main/java/com/dnd/qello/scheduling/adapter/NotificationFanOutScheduledAdapter.java src/main/java/com/dnd/qello/scheduling/adapter/ReportResolutionFanOutScheduledAdapter.java src/test/java/com/dnd/qello/scheduling/adapter/CoreWorkerScheduledAdapterTest.java
git commit -m "chore(scheduling): schedule outbox core workers (#182)"
```

### Task 4: Recipient sweep scheduled adapters

**Files:**
- Create: `src/main/java/com/dnd/qello/scheduling/adapter/RecipientExpirationSweepScheduledAdapter.java`
- Create: `src/main/java/com/dnd/qello/scheduling/adapter/SkipConfirmationSweepScheduledAdapter.java`
- Modify: `src/test/java/com/dnd/qello/scheduling/adapter/CoreWorkerScheduledAdapterTest.java`

**Interfaces:**
- Consumes: two existing sweep `processBatch(BatchCommand)` methods and `SweepSettings`
- Produces: `scanned`, `released`, `ineligible`, `failed` metric counts without Outbox owner

- [ ] **Step 1: Add failing sweep adapter tests**

```java
@Test
@DisplayName("UNIT-011: expiration sweep adapter는 scan 결과 네 종류를 정확히 기록한다")
void expirationSweepRecordsAllResultCounters() {
    when(worker.processBatch(any())).thenReturn(new SweepBatchResult(7, 3, 2, 2));

    adapter.runOnce();

    ArgumentCaptor<RecipientExpirationSweepWorker.BatchCommand> command =
        ArgumentCaptor.forClass(RecipientExpirationSweepWorker.BatchCommand.class);
    verify(worker).processBatch(command.capture());
    assertThat(command.getValue().limit()).isEqualTo(7);
    assertThat(command.getValue().at()).isEqualTo(NOW);
    assertCounter("recipient_expiration_sweep", "released", 3);
    assertCounter("recipient_expiration_sweep", "ineligible", 2);
    assertCounter("recipient_expiration_sweep", "failed", 2);
}
```

Add the equivalent skip confirmation assertion and `fixedDelayString` reflection checks.

- [ ] **Step 2: Run sweep tests and confirm RED**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.adapter.CoreWorkerScheduledAdapterTest'
```

Expected: compile failure because the two sweep adapters do not exist.

- [ ] **Step 3: Implement the two adapters**

Each adapter calls its worker with `new BatchCommand(settings.batchSize(), clock.instant())`, records
`recordScanned`, then records exact counts for `RELEASED`, `INELIGIBLE`, and `FAILED`. A batch exception
records `BATCH_FAILED` and is rethrown. Neither adapter receives `WorkerInstanceIdentity` or constructs an
Outbox retry policy.

```java
@Scheduled(fixedDelayString =
    "${qello.worker.scheduling.recipient-expiration-sweep.fixed-delay}")
void runOnce() {
    Instant at = clock.instant();
    SweepBatchResult result = worker.processBatch(
        new RecipientExpirationSweepWorker.BatchCommand(settings.batchSize(), at));
    metrics.recordScanned(WorkerName.RECIPIENT_EXPIRATION_SWEEP, result.scanned());
    metrics.recordOutcome(WorkerName.RECIPIENT_EXPIRATION_SWEEP,
        WorkerOutcome.RELEASED, result.released());
    metrics.recordOutcome(WorkerName.RECIPIENT_EXPIRATION_SWEEP,
        WorkerOutcome.INELIGIBLE, result.ineligible());
    metrics.recordOutcome(WorkerName.RECIPIENT_EXPIRATION_SWEEP,
        WorkerOutcome.FAILED, result.failed());
}

@Scheduled(fixedDelayString =
    "${qello.worker.scheduling.skip-confirmation-sweep.fixed-delay}")
void runOnce() {
    Instant at = clock.instant();
    SweepBatchResult result = worker.processBatch(
        new SkipConfirmationSweepWorker.BatchCommand(settings.batchSize(), at));
    metrics.recordScanned(WorkerName.SKIP_CONFIRMATION_SWEEP, result.scanned());
    metrics.recordOutcome(WorkerName.SKIP_CONFIRMATION_SWEEP,
        WorkerOutcome.RELEASED, result.released());
    metrics.recordOutcome(WorkerName.SKIP_CONFIRMATION_SWEEP,
        WorkerOutcome.INELIGIBLE, result.ineligible());
    metrics.recordOutcome(WorkerName.SKIP_CONFIRMATION_SWEEP,
        WorkerOutcome.FAILED, result.failed());
}
```

- [ ] **Step 4: Run Task 4 tests and confirm GREEN**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.adapter.CoreWorkerScheduledAdapterTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```bash
git add src/main/java/com/dnd/qello/scheduling/adapter/RecipientExpirationSweepScheduledAdapter.java src/main/java/com/dnd/qello/scheduling/adapter/SkipConfirmationSweepScheduledAdapter.java src/test/java/com/dnd/qello/scheduling/adapter/CoreWorkerScheduledAdapterTest.java
git commit -m "chore(scheduling): schedule recipient sweep workers (#182)"
```

### Task 5: Push worker bean wiring와 scheduled adapter

**Files:**
- Modify: `src/main/java/com/dnd/qello/notification/config/PushConfiguration.java`
- Create: `src/main/java/com/dnd/qello/scheduling/adapter/PushDeliveryDispatchScheduledAdapter.java`
- Modify: `src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java`
- Create: `src/test/java/com/dnd/qello/scheduling/adapter/PushDeliveryDispatchScheduledAdapterTest.java`

**Interfaces:**
- Consumes: existing Push repositories/services, policy beans, provider, protector, `Clock`, transaction manager
- Produces: conditional `PushDispatchGroupPlanner`, `PushPayloadFactory`, `PushDeliveryRetryPolicy`, `PushDeliveryDispatchWorker`
- Produces: Push `@Scheduled` adapter and explicit outcome mapping

- [ ] **Step 1: Extend PushConfigurationTest with RED bean-wiring scenarios**

Update its class header with an `Extended at` timestamp from `date -Iseconds` and extension scenarios
`TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING-UNIT-018 through UNIT-019`.

Add a context with global/push enabled properties, production profile property fixtures already used by
the class, and Mockito beans for repository/service dependencies. Assert exactly one bean of each type:

```java
assertThat(context).hasSingleBean(PushDispatchGroupPlanner.class);
assertThat(context).hasSingleBean(PushPayloadFactory.class);
assertThat(context).hasSingleBean(PushDeliveryRetryPolicy.class);
assertThat(context).hasSingleBean(PushDeliveryDispatchWorker.class);
assertThat(context.getBean(PushDeliveryRetryPolicy.class))
    .extracting(PushDeliveryRetryPolicy::maxAttempts,
        PushDeliveryRetryPolicy::baseBackoff,
        PushDeliveryRetryPolicy::backoffCap)
    .containsExactly(3, Duration.ofSeconds(1), Duration.ofSeconds(30));
```

Keep the existing test that policy-only/scheduling-OFF context has no dispatch worker. Add a test/local
profile assertion that the provider remains NoOp and no real FCM client method is called.

- [ ] **Step 2: Run PushConfigurationTest and confirm RED**

```bash
./gradlew test --tests 'com.dnd.qello.notification.config.PushConfigurationTest'
```

Expected: missing Push worker/helper/retry beans in the enabled context.

- [ ] **Step 3: Add conditional Push worker configuration**

Import a nested configuration guarded by both properties:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "qello.worker.scheduling",
    name = {"enabled", "push-delivery-dispatch.enabled"},
    havingValue = "true")
@EnableConfigurationProperties(WorkerSchedulingProperties.class)
static class PushDispatchWorkerConfiguration {
    @Bean
    PushDispatchGroupPlanner pushDispatchGroupPlanner(
        PushDispatchGroupRepository repository, PushGroupingPolicy policy) {
        return new PushDispatchGroupPlanner(repository, policy);
    }

    @Bean
    PushPayloadFactory pushPayloadFactory() {
        return new PushPayloadFactory();
    }

    @Bean
    PushDeliveryRetryPolicy pushDeliveryRetryPolicy(
        WorkerSchedulingProperties properties) {
        var retry = properties.pushDeliveryDispatch().retry();
        return new PushDeliveryRetryPolicy(
            retry.maxAttempts(), retry.baseBackoff(), retry.backoffCap());
    }
}
```

In the same nested configuration, declare `PushDeliveryDispatchWorker` with every existing constructor
dependency and `new TransactionTemplate(transactionManager)`. Do not annotate the worker, planner or
payload factory classes and do not change their business code.

- [ ] **Step 4: Write Push adapter RED tests**

Use fixed `NOW`, a push `BatchResult` containing every existing Push outcome, and capture
`PushDeliveryDispatchWorker.BatchCommand`.

```java
assertThat(command.batchSize()).isEqualTo(7);
assertThat(command.at()).isEqualTo(NOW);
assertThat(command.leaseUntil()).isEqualTo(NOW.plusSeconds(30));
assertCounter("push_delivery_dispatch", "sent", 1);
assertCounter("push_delivery_dispatch", "retry_scheduled", 1);
assertCounter("push_delivery_dispatch", "dead", 1);
assertCounter("push_delivery_dispatch", "cancelled", 1);
assertCounter("push_delivery_dispatch", "stale_claim", 1);
assertCounter("push_delivery_dispatch", "failure_recording_failed", 1);
```

Also assert the method uses
`${qello.worker.scheduling.push-delivery-dispatch.fixed-delay}` and no fixed rate.

- [ ] **Step 5: Run Push adapter test and confirm RED**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.adapter.PushDeliveryDispatchScheduledAdapterTest'
```

Expected: compile failure because the adapter does not exist.

- [ ] **Step 6: Implement PushDeliveryDispatchScheduledAdapter**

The adapter is conditional on global and push worker gates, receives the configured worker, Push settings,
`Clock` and metrics, and performs one explicit outcome switch. It does not receive Outbox identity.

```java
@Scheduled(fixedDelayString =
    "${qello.worker.scheduling.push-delivery-dispatch.fixed-delay}")
void runOnce() {
    try {
        Instant at = clock.instant();
        var result = worker.dispatchBatch(new PushDeliveryDispatchWorker.BatchCommand(
            settings.batchSize(), at, at.plus(settings.leaseDuration())));
        metrics.recordClaimed(WorkerName.PUSH_DELIVERY_DISPATCH, result.claimed());
        result.outcomes().forEach(item -> metrics.recordOutcome(
            WorkerName.PUSH_DELIVERY_DISPATCH, map(item.outcome()), 1));
    } catch (RuntimeException failure) {
        metrics.recordOutcome(WorkerName.PUSH_DELIVERY_DISPATCH, Outcome.BATCH_FAILED, 1);
        throw failure;
    }
}
```

- [ ] **Step 7: Run Task 5 tests and confirm GREEN**

```bash
./gradlew test --tests 'com.dnd.qello.notification.config.PushConfigurationTest' --tests 'com.dnd.qello.scheduling.adapter.PushDeliveryDispatchScheduledAdapterTest'
```

Expected: PASS without network access.

- [ ] **Step 8: Commit Task 5**

```bash
git add src/main/java/com/dnd/qello/notification/config/PushConfiguration.java src/main/java/com/dnd/qello/scheduling/adapter/PushDeliveryDispatchScheduledAdapter.java src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java src/test/java/com/dnd/qello/scheduling/adapter/PushDeliveryDispatchScheduledAdapterTest.java
git commit -m "chore(scheduling): wire scheduled push dispatch worker (#182)"
```

### Task 6: Context lifecycle, gate와 repeat execution integration

**Files:**
- Create: `src/integrationTest/java/com/dnd/qello/CoreWorkerSchedulingIntegrationTest.java`
- Modify: `src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java`

**Interfaces:**
- Consumes: complete conditional configuration and scheduled adapter beans from Tasks 1–5
- Produces: evidence for default-OFF contexts, one selected repeating worker and same-adapter non-overlap

- [ ] **Step 1: Add RED context tests for actual repeated scheduling**

In `WorkerSchedulingConfigurationTest`, use `ApplicationContextRunner`, one mocked
`DirectionMatchingWorker`, and a `CountDownLatch(2)`. The mock answer records concurrent entry with an
`AtomicInteger` and sleeps behind a latch only in the first call. Configure only direction matching ON
with test fixed delay `PT0.05S`. Assert two calls arrive within two seconds and maximum concurrent calls is
one. Assert all other adapter beans are absent.

```java
assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue();
assertThat(maxConcurrent.get()).isEqualTo(1);
verify(worker, atLeast(2)).processBatch(any());
```

- [ ] **Step 2: Add full-profile default-OFF integration tests**

Create one source file containing two package-private test classes:

```java
@SpringBootTest
@ActiveProfiles("test")
class CoreWorkerSchedulingIntegrationTest extends PostgisContainerIntegrationTestSupport { }

@SpringBootTest(properties = {
    "qello.notification.push.policy.bundle-window=PT10M",
    "qello.notification.push.policy.max-delay=PT8H",
    "qello.notification.push.policy.daily-limit=5",
    "qello.notification.push.policy.direction-reserved=2",
    "qello.notification.push.policy.recommendation-min-interval=PT24H"
})
@ActiveProfiles("local")
class CoreWorkerSchedulingLocalProfileIntegrationTest
    extends PostgisContainerIntegrationTestSupport { }
```

Each class autowires `ApplicationContext` and asserts there is no `WorkerInstanceIdentity`,
`WorkerMetrics`, `ThreadPoolTaskScheduler` or scheduled adapter bean. Assert the profile is active and
the application context is otherwise healthy.

- [ ] **Step 3: Run focused context tests and confirm failures before completing wiring**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.WorkerSchedulingConfigurationTest'
./gradlew integrationTest --tests 'com.dnd.qello.CoreWorkerSchedulingIntegrationTest' --tests 'com.dnd.qello.CoreWorkerSchedulingLocalProfileIntegrationTest'
```

Expected before final fixes: any missing conditional or lifecycle behavior fails explicitly.

- [ ] **Step 4: Make only configuration/adapter lifecycle fixes required by the tests**

Allowed fixes are limited to Tasks 1–5 files. Do not modify worker business classes or add sleeps to
production code. Ensure the test always closes the context and releases blocking latches in `finally`.

- [ ] **Step 5: Run focused context tests and confirm GREEN**

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.WorkerSchedulingConfigurationTest'
./gradlew integrationTest --tests 'com.dnd.qello.CoreWorkerSchedulingIntegrationTest' --tests 'com.dnd.qello.CoreWorkerSchedulingLocalProfileIntegrationTest'
```

Expected: PASS, maximum same-adapter concurrency 1, scheduler threads shut down.

- [ ] **Step 6: Commit Task 6**

```bash
git add src/integrationTest/java/com/dnd/qello/CoreWorkerSchedulingIntegrationTest.java src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java src/main/java/com/dnd/qello/scheduling src/main/java/com/dnd/qello/notification/config/PushConfiguration.java
git commit -m "test(scheduling): verify gates and fixed-delay lifecycle (#182)"
```

### Task 7: PostgreSQL concurrency regression과 test report

**Files:**
- Create: `docs/test-reports/gh-182-TEST-REPORT-GH-182-CORE-WORKER-SCHEDULING.md`
- Do not modify: existing worker, repository, SQL, migration or existing integration test source

**Interfaces:**
- Consumes: existing PostgreSQL tests as immutable evidence
- Produces: test report based on `templates/test-report.md`

- [ ] **Step 1: Run Outbox multi-owner and fan-out regressions**

```bash
./gradlew integrationTest --tests 'OutboxLeaseIntegrationTest' --tests 'DirectionMatchingWorkerConcurrencyIntegrationTest' --tests 'RecipientNotificationFanOutWorkerConcurrencyIntegrationTest' --tests 'NotificationFanOutExpansionIntegrationTest'
```

Expected: PASS; claim sets do not overlap, expired lease increments generation, stale terminal is rejected.

- [ ] **Step 2: Run Push lease and recipient Sweep regressions**

```bash
./gradlew integrationTest --tests 'PushDeliveryLeaseIntegrationTest' --tests 'PushDispatchGroupingIntegrationTest' --tests 'RecipientSweepConcurrencyIntegrationTest'
```

Expected: PASS; Push group/device fencing and one-time recipient slot release remain unchanged.

- [ ] **Step 3: Verify scope diff**

```bash
git diff origin/main -- src/main/java/com/dnd/qello/direction/matching src/main/java/com/dnd/qello/direction/sweep src/main/java/com/dnd/qello/notification/fanout src/main/java/com/dnd/qello/notification/service/PushDeliveryDispatchWorker.java src/main/java/com/dnd/qello/notification/repository src/main/resources/db/migration
```

Expected: no output. Any output is a scope violation requiring human review.

- [ ] **Step 4: Run the approved test-plan harness**

After the human approval fields in the test plan are updated, run:

```bash
./harness test-run --id TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING
```

Use its scaffold/report workflow and do not mark scenarios passed without command evidence.

- [ ] **Step 5: Complete the test report**

Create the report from `templates/test-report.md`. Include command, exit code, scenario mapping, application,
DB, concurrency, transaction, external API and failure-recovery analysis. Record production scheduling
numbers and live FCM as unverified operational scope, not as failures of disabled code.

- [ ] **Step 6: Commit Task 7**

```bash
git add docs/test-reports/gh-182-TEST-REPORT-GH-182-CORE-WORKER-SCHEDULING.md
git commit -m "test(scheduling): document worker scheduling verification (#182)"
```

### Task 8: Final documentation and repository gates

**Files:**
- Modify: `TASK.md`
- Modify locally: `docs/reports/private/notification/07-core-worker-scheduling-architecture.local.md`
- Verify: all files changed by Tasks 1–7

**Interfaces:**
- Consumes: verified implementation and test report
- Produces: final task evidence and updated local architecture status

- [ ] **Step 1: Update the local architecture document with actual implementation evidence**

Change its status from implementation-before to implemented only after all focused tests pass. Add actual
class names, final property keys and verified commands. Because `*.local.md` is ignored, do not claim it is
part of a PR diff.

- [ ] **Step 2: Update TASK.md completion evidence**

Mark only criteria backed by executed checks. Keep production operational values and live FCM explicitly
unverified. Do not add secret values or environment identifiers.

- [ ] **Step 3: Run full required verification**

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

Expected: every command exits 0. If a command cannot run, report it as BLOCKED with reason, impact and
follow-up verification method.

- [ ] **Step 4: Inspect final diff and status**

```bash
git status --short --branch
git diff --stat origin/main
git diff origin/main -- src/main/resources/db/migration
```

Expected: branch is `chore/gh-182-core-worker-scheduling`, no migration diff, and no unrelated files.

- [ ] **Step 5: Commit final contract/documentation changes**

```bash
git add TASK.md docs/superpowers/specs/2026-08-27-core-worker-scheduling-design.md docs/superpowers/plans/2026-08-27-core-worker-scheduling.md docs/test-plans/gh-182-TEST-PLAN-GH-182-CORE-WORKER-SCHEDULING.md
git commit -m "chore(scheduling): finalize worker scheduling contract (#182)"
```

Do not commit or create a PR until the separate `harness-commit` or `harness-pr` approval workflow is invoked.
