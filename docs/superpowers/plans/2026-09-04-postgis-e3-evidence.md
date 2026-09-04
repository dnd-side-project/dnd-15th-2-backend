# Qello PostGIS E3 Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing GH-163 PostGIS E2 evidence into one reproducible local E3 experiment with controlled before/after measurements, `pg_stat_statements`, and correctness guardrails, without changing production SQL, indexes, schema, or application behavior.

**Architecture:** Keep the JUnit/Testcontainers execution surface independent from Compose. Enable `pg_stat_statements` only for the manual `performanceTest` JVM, add focused test-only probes for sanitized SQL and plan evidence, and preserve the completed GH-163 class by placing the new experiment in a separate performance test class. Use a 10K/50K/100K account cardinality sweep to locate plan transitions, then use a fixed 100K-account/10K-presence fixture for the actual E3 comparison, changing only PostgreSQL `default_statistics_target` from 100 to 1000.

**Tech Stack:** Java 21, Spring Boot 3.5.16, JUnit 5, Spring JDBC, Testcontainers, PostgreSQL 16, PostGIS 3.5, `pg_stat_statements`, Gradle.

**Spec:** `docs/reports/private/resume/qello-local-observability-performance-strategy.local.md`

## Global Constraints

- This plan implements only strategy path A: JUnit `pg_stat_statements` → cardinality sweep → fixed-fixture correctness guardrail → one E3 evidence report.
- The source strategy is a planning input, not an implementation approval. Before Task 1, create a new GitHub Project draft item, convert it to an open Repository Issue, branch from current `origin/main` with type `perf`, and replace `TASK.md` with a matching approved contract.
- Current pre-plan state is `main` at `fff2b18`; `TASK.md` still describes completed Issue #163 and therefore does not authorize this work.
- Preserve the user-owned untracked file `scratch.py`. Do not stage, edit, delete, or mention its contents in evidence.
- Keep `DirectionMatchingIndexPlanPerformanceIntegrationTest` unchanged so the original #163 evidence remains reproducible.
- Do not modify `ActiveUserPresenceSql`, production repositories, indexes, Flyway migrations, direction policy, or the 20,100km operating radius.
- Do not create a production recommendation from a local planner result. A supported or rejected hypothesis is an acceptable experiment outcome.
- Use exactly three scale points: 10,000 accounts with 1,000 valid presences, 50,000 accounts with 5,000 valid presences, and 100,000 accounts with 10,000 valid presences. This preserves the existing 10:1 account-to-presence ratio.
- Preserve the GH-163 deterministic distribution: valid presences are distributed in a 100km disk; observe both the current `GLOBAL / 0..20,100km` policy radius and the diagnostic 5km radius.
- Use exactly two statistics conditions for the fixed-fixture comparison: `default_statistics_target=100` and `default_statistics_target=1000`. Apply the value only to the Testcontainers session that runs `ANALYZE`, then `RESET` it on the same connection.
- Warm each query once, reset `pg_stat_statements`, execute 20 measured calls, and record client p50/p95/p99 plus database calls, total/mean execution time, rows, shared blocks, and temp blocks. Timings are observations, never pass/fail thresholds.
- Assert correctness in memory and log only counts, enum labels, access paths, timings, buffer totals, and aggregate statistics. Never log synthetic or real user IDs, nicknames, coordinates, SQL text, raw EXPLAIN JSON, credentials, URLs, tokens, or `.env` values.
- JUnit classes must use exact ISO 8601 `Created at` headers, source scenario identifiers derived from the approved test plan, and `@DisplayName` on every test method.
- The completed evidence must distinguish local synthetic results from production behavior and must not claim fixed Testcontainers CPU or memory resources.

## Scope Decomposition

This strategy spans independent subsystems and must not be implemented as one Issue. This plan owns only `PATH-A`. Create separate specs and implementation plans for the remaining packages after this plan has produced one E3 report.

| Package | Scope | Dependency | Outcome |
| --- | --- | --- | --- |
| `PATH-A` | JUnit `pg_stat_statements`, PostGIS scale sweep, fixed-fixture before/after, correctness guardrails | New performance Issue and approved test plan | One PostGIS E3 report |
| `PATH-B1` | `observability`/`performance` profile contract, Prometheus registry, endpoint security tests | Separate application Issue | Scrapeable metrics on private management listener |
| `PATH-B2` | Observability Compose overlay, Prometheus/Grafana provisioning, performance DB volume | `PATH-B1` | Reproducible local observability stack |
| `PATH-B3` | Deterministic fixture/authentication and k6 Hikari contention scenario | `PATH-B2` | One API/pool-contention E3 report |
| `LOGGING` | ECS structured stdout, validated request ID, MDC cleanup | Separate application Issue | Stable synchronous log correlation |
| `CORRELATION` | Correlation ID persistence through outbox tables and worker restore | `LOGGING`, migration approval | Cross-transaction correlation |
| `WORKER-OBS` | Batch duration, pending, oldest age, batch size, reclaim metrics | `PATH-B1` | Worker backlog visibility |
| `FAULTS` | Fake FCM/moderation, LocalStack failure modes, Toxiproxy, crash/reclaim scenarios | `PATH-B2`, `WORKER-OBS` | Controlled dependency-failure evidence |

`PATH-B1` through `FAULTS` are intentionally excluded from the task list below. Each package is independently reviewable and must receive its own Issue, `TASK.md`, test plan, and implementation plan.

## File Structure

### Files modified by this plan

- `build.gradle` — correct stale synthetic-scale descriptions and pass the performance-only preload flag into the `performanceTest` JVM.
- `src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java` — allowlist a performance-only PostgreSQL startup command while leaving normal `integrationTest` unchanged.

### Files created by this plan

- `src/integrationTest/java/com/dnd/qello/PgStatStatementsPerformanceIntegrationTest.java` — focused proof that preload, extension creation, reset, and safe summary reads work in the performance execution surface.
- `src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbe.java` — test-only measurement utility for timed calls, `pg_stat_statements` summaries, EXPLAIN plan parsing, percentile calculation, and sanitized evidence rendering.
- `src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbeIntegrationTest.java` — focused probe-contract tests against the ephemeral PostgreSQL container.
- `src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java` — deterministic cardinality sweep, fixed-fixture statistics comparison, and candidate/recipient correctness guardrails.
- `templates/performance-experiment-report.md` — reusable safe evidence structure for both paths A and B.
- The Issue-derived test-plan file produced by `./harness test-plan` — owns approved scenario IDs and acceptance criteria.
- The Issue-derived test-report file produced by `./harness test-run` — records the fresh performance command and sanitized result summary.

---

### Task 1: Establish the Issue, task contract, and approved test plan

**Files:**
- Modify: `TASK.md`
- Create: Issue-derived file under `docs/test-plans/`

**Interfaces:**
- Consumes: the source strategy and this implementation plan
- Produces: an open performance Issue, matching `perf/gh-N-postgis-e3-evidence` branch, approved `TASK.md`, and approved test scenario IDs used by every new JUnit class

- [ ] **Step 1: Verify the work gate before changing tracked files**

Run:

```bash
git status --short
git branch --show-current
./harness status
```

Expected before starting implementation:

```text
branch: perf/gh-N-postgis-e3-evidence
gate: ready
```

`N` here denotes the actual open Repository Issue selected by `./harness start`; it is not known at plan-authoring time. If the branch is `main`, `TASK.md` still names #163, the Issue is closed, or `scratch.py` is no longer the only unrelated worktree entry, stop and reconcile the gate without cleaning user changes.

- [ ] **Step 2: Initialize the task contract from the actual Issue branch**

Run the repository workflow with the real Issue number:

```bash
./harness start --issue "$GITHUB_ISSUE" --type perf --slug postgis-e3-evidence
./harness task-init --title "PostGIS 후보 조회 E3 증거 확장" --replace
```

Populate `TASK.md` with these exact scope decisions:

```text
Included:
- performanceTest 전용 pg_stat_statements preload와 extension
- 10K:1K, 50K:5K, 100K:10K 결정적 cardinality sweep
- statistics target 100과 1000의 고정 100K:10K before/after
- preview counts, matching candidate order/set, persisted recipient set guardrails
- sanitized experiment report

Excluded:
- production SQL, index, migration, policy 변경
- Compose, Prometheus, Grafana, k6
- 운영 임계값 또는 운영 개선 주장
```

- [ ] **Step 3: Scaffold and fill the risk-based test plan**

Use an Issue-derived identifier in the exact form `TEST-PLAN-GH-N-POSTGIS-E3-EVIDENCE`, where `N` is the branch Issue number:

```bash
./harness test-plan --id "$TEST_PLAN_ID"
```

Define these scenarios in the generated file:

```text
PERF-001 performanceTest JVM에서 pg_stat_statements preload와 extension 조회가 성공한다.
PERF-002 일반 integrationTest JVM은 performance preload flag를 받지 않는다.
PERF-003 10K:1K cardinality의 preview·matching 네 query/radius 조합을 측정한다.
PERF-004 50K:5K cardinality의 preview·matching 네 query/radius 조합을 측정한다.
PERF-005 100K:10K cardinality의 preview·matching 네 query/radius 조합을 측정한다.
PERF-006 고정 100K:10K fixture에서 statistics target 100과 1000 결과를 비교한다.
PERF-007 before/after preview segment counts와 matching logical candidate order/set이 같다.
PERF-008 before/after persisted recipient logical set이 같고 중복·누락이 0이다.
PERF-009 양방향 block, inactive account, expired presence와 receive-capacity 정책이 유지된다.
PERF-010 evidence 출력에는 허용된 aggregate field만 존재한다.
REPORT-001 최소 3회 이상의 fresh result, median/p95/p99, DB summary, plan summary, guardrail, 한계를 기록한다.
```

Classify `PERF-001`, `PERF-006` through `PERF-010`, and `REPORT-001` as P0. Classify the three scale-point scenarios as P1 because they explain the transition but are not the fixed-fixture correctness gate.

- [ ] **Step 4: Obtain human approval of the test plan**

Record reviewer, decision, and exact approval timestamp in both the test plan and `TASK.md`. Do not continue to Task 2 while the approval state is draft, ambiguous, or missing.

- [ ] **Step 5: Commit the approved contract**

```bash
git add TASK.md docs/test-plans
git commit -m "perf(direction): define PostGIS E3 experiment contract"
```

Expected completed commit message: the hooks append the branch Issue number and preserve type `perf`.

### Task 2: Enable `pg_stat_statements` only for `performanceTest`

**Files:**
- Create: `src/integrationTest/java/com/dnd/qello/PgStatStatementsPerformanceIntegrationTest.java`
- Modify: `src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: Gradle system property `qello.test.postgres.pg-stat-statements-enabled`
- Produces: a Testcontainers PostgreSQL command containing `shared_preload_libraries=pg_stat_statements` only when the property is exactly `true`
- Produces: an installed extension only inside the focused performance test database

- [ ] **Step 1: Capture one exact timestamp for the new test headers**

```bash
date -Iseconds
```

Put the returned value in the `Created at` header of the test created in this task. Use the approved `PERF-001` source scenario identifier.

- [ ] **Step 2: Write the failing preload and extension test**

Create `PgStatStatementsPerformanceIntegrationTest` with the exact timestamp and
Issue-derived source-scenario header required by Task 1, followed by this test
shape:

```java
@Tag("performance")
@SpringBootTest
@ActiveProfiles("test")
class PgStatStatementsPerformanceIntegrationTest extends PostgisContainerIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void installExtension(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
    }

    @Test
    @DisplayName("PERF-001: performanceTest는 pg_stat_statements를 preload하고 안전한 요약을 조회한다")
    void preloadsAndQueriesPgStatStatements() {
        String preload = jdbc.queryForObject("SHOW shared_preload_libraries", String.class);
        assertThat(preload).contains("pg_stat_statements");

        jdbc.queryForObject("SELECT 1", Integer.class);
        jdbc.execute("SELECT pg_stat_statements_reset()");

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM pg_stat_statements", Integer.class);
        assertThat(rows).isNotNull().isGreaterThanOrEqualTo(0);
    }
}
```

Do not print the contents of the `query` column.

- [ ] **Step 3: Run the focused test and confirm RED**

```bash
./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'
```

Expected: FAIL because the PostgreSQL server was not started with `pg_stat_statements` in `shared_preload_libraries`.

- [ ] **Step 4: Add the performance-only Gradle flag**

Change `performanceTest` to include:

```groovy
systemProperty 'qello.test.postgres.pg-stat-statements-enabled', 'true'
```

Also correct the stale descriptions found by the source strategy:

```groovy
// 대규모 합성 데이터 적재와 EXPLAIN ANALYZE는 별도 performanceTest에서만 실행한다.
description = 'Runs @Tag("performance") integration tests, including controlled synthetic scale and EXPLAIN ANALYZE evidence. check에 포함되지 않으며 수동으로만 실행한다.'
```

Keep the `integrationTest` exclusion and `performanceTest` inclusion of the `performance` tag unchanged.

- [ ] **Step 5: Allowlist the PostgreSQL startup command**

Refactor the static container creation in `PostgisContainerIntegrationTestSupport` to this shape:

```java
private static final String PG_STAT_STATEMENTS_ENABLED =
    "qello.test.postgres.pg-stat-statements-enabled";

@Container
@ServiceConnection
static final PostgreSQLContainer<?> postgres = postgresContainer();

private static PostgreSQLContainer<?> postgresContainer() {
    PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGIS_IMAGE)
        .withDatabaseName("qello_test")
        .withUsername("qello_test")
        .withPassword("test-only")
        .withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));
    if (Boolean.getBoolean(PG_STAT_STATEMENTS_ENABLED)) {
        container.withCommand(
            "postgres",
            "-c",
            "shared_preload_libraries=pg_stat_statements");
    }
    return container;
}
```

Do not accept a library name or arbitrary command from a property. The boolean selects one hard-coded safe command.

- [ ] **Step 6: Run focused GREEN and ordinary-integration isolation checks**

```bash
./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'
./gradlew integrationTest --tests '*QelloApplicationIntegrationTest'
```

Expected: both PASS. The first proves the extension can be used; the second proves the ordinary execution surface still starts without the performance-only flag.

- [ ] **Step 7: Commit the execution-surface change**

```bash
git add build.gradle src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java src/integrationTest/java/com/dnd/qello/PgStatStatementsPerformanceIntegrationTest.java
git commit -m "perf(direction): enable performance SQL statistics"
```

### Task 3: Add a sanitized performance evidence probe

**Files:**
- Create: `src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbe.java`
- Create: `src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbeIntegrationTest.java`

**Interfaces:**
- Consumes: `JdbcTemplate`, `NamedParameterJdbcTemplate`, `ObjectMapper`, SQL text already owned by `ActiveUserPresenceSql`, named parameters, and an allowlisted query fingerprint
- Produces: `Measurement<T>`, `PgStatObservation`, `PlanObservation`, `PlanNodeObservation`, `SortObservation`, and `LatencyObservation`
- Produces: sanitized one-line evidence containing no query text or logical identifiers

- [ ] **Step 1: Write the probe contract test first**

Use the exact timestamp captured for this task and approved `PERF-010` scenario ID. The test must create `pg_stat_statements`, execute `SELECT 1 AS e3_probe_contract`, use fingerprint pattern `%AS e3_probe_contract%`, and assert all of the following:

```java
assertThat(measurement.results()).hasSize(20).containsOnly(1);
assertThat(measurement.pgStat().calls()).isEqualTo(20L);
assertThat(measurement.pgStat().totalExecTimeMs()).isGreaterThanOrEqualTo(0.0);
assertThat(measurement.pgStat().meanExecTimeMs()).isGreaterThanOrEqualTo(0.0);
assertThat(measurement.latency().p50Ms()).isGreaterThanOrEqualTo(0.0);
assertThat(measurement.latency().p95Ms()).isGreaterThanOrEqualTo(measurement.latency().p50Ms());
assertThat(measurement.latency().p99Ms()).isGreaterThanOrEqualTo(measurement.latency().p95Ms());
assertThat(measurement.sanitizedLine())
    .contains("experiment=PROBE-CONTRACT", "calls=20")
    .doesNotContain("SELECT", "query=", "userId", "nickname", "latitude", "longitude");
```

Add an EXPLAIN parser scenario using `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM generate_series(1, 3) ORDER BY 1`. Assert the parsed observation has planning time, execution time, and one sort observation without retaining or rendering raw JSON.

- [ ] **Step 2: Run the probe tests and confirm RED**

```bash
./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'
```

Expected: compile failure because `DirectionMatchingPerformanceProbe` and its records do not exist.

- [ ] **Step 3: Implement the exact measurement API**

Use this package-private top-level shape. The two declarations below are the
method signatures; their required implementation order is specified immediately
after the type definitions.

```java
final class DirectionMatchingPerformanceProbe {

    static final int MEASURED_CALLS = 20;

    enum QueryFingerprint {
        PREVIEW("%candidate_bearings AS (%"),
        MATCHING("%LEFT JOIN recipient_receive_state%"),
        PROBE_CONTRACT("%e3_probe_contract%");

        private final String likePattern;
    }

    // <T> Measurement<T> measure(
    //     String experimentId,
    //     String condition,
    //     QueryFingerprint fingerprint,
    //     Supplier<T> query)

    // PlanObservation explain(
    //     String queryKind,
    //     String radius,
    //     String sql,
    //     MapSqlParameterSource parameters)

    record Measurement<T>(
        List<T> results,
        PgStatObservation pgStat,
        LatencyObservation latency,
        String sanitizedLine) { }

    record PgStatObservation(
        long calls,
        double totalExecTimeMs,
        double meanExecTimeMs,
        long rows,
        long sharedBlocksHit,
        long sharedBlocksRead,
        long tempBlocksRead,
        long tempBlocksWritten) { }

    record LatencyObservation(double p50Ms, double p95Ms, double p99Ms) { }

    record PlanObservation(
        String queryKind,
        String radius,
        List<PlanNodeObservation> targetNodes,
        List<SortObservation> sorts,
        double planningTimeMs,
        double executionTimeMs,
        boolean usesPartialGist) { }

    record PlanNodeObservation(
        String relation,
        String nodeType,
        String indexName,
        double planRows,
        double actualRows,
        double actualLoops,
        double rowsRemovedByFilter,
        double sharedBlocksHit,
        double sharedBlocksRead,
        double tempBlocksRead,
        double tempBlocksWritten) { }

    record SortObservation(
        String method,
        String spaceType,
        double spaceUsedKb,
        double actualRows) { }
}
```

The constructor accepts `JdbcTemplate`, `NamedParameterJdbcTemplate`, and `ObjectMapper`. `measure` must perform these operations in order:

1. Call the supplier once as warm-up and discard its value.
2. Execute `SELECT pg_stat_statements_reset()`.
3. Call the supplier exactly 20 times, storing immutable results and `System.nanoTime()` elapsed samples.
4. Read one aggregate row using only the enum-owned `LIKE` pattern.
5. Calculate nearest-rank p50, p95, and p99 from the sorted client elapsed samples.
6. Render only the fields contained in the records above.

Use this percentile calculation so all executors produce the same result:

```java
private static double percentileMillis(List<Long> sortedNanos, double percentile) {
    int index = Math.max(0, (int) Math.ceil(percentile * sortedNanos.size()) - 1);
    return sortedNanos.get(index) / 1_000_000.0;
}
```

The sanitized line format is exact and contains no free-form values:

```java
"experiment=%s condition=%s calls=%d rows=%d total_exec_ms=%.3f "
    + "mean_exec_ms=%.3f client_p50_ms=%.3f client_p95_ms=%.3f "
    + "client_p99_ms=%.3f shared_hit=%d shared_read=%d temp_read=%d temp_written=%d"
```

Use this summary query and do not select the `query` column:

```sql
SELECT calls,
       total_exec_time,
       mean_exec_time,
       rows,
       shared_blks_hit,
       shared_blks_read,
       temp_blks_read,
       temp_blks_written
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT LIKE 'EXPLAIN%'
  AND query LIKE ?
ORDER BY total_exec_time DESC
```

Read the rows into a list and fail if the fingerprint matches zero rows or more than one row. Do not fall back to the highest-cost statement because that could silently attribute the wrong query.

`explain` must recursively collect only `active_user_presence` and `user_account` plan nodes plus sort nodes. Read the PostgreSQL JSON keys `Planning Time`, `Execution Time`, `Relation Name`, `Node Type`, `Index Name`, `Plan Rows`, `Actual Rows`, `Actual Loops`, `Rows Removed by Filter`, `Shared Hit Blocks`, `Shared Read Blocks`, `Temp Read Blocks`, `Temp Written Blocks`, `Sort Method`, `Sort Space Type`, and `Sort Space Used`.

- [ ] **Step 4: Run the probe tests and confirm GREEN**

```bash
./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'
```

Expected: PASS with 20 calls attributed to the contract query and no forbidden field in the sanitized line.

- [ ] **Step 5: Commit the probe**

```bash
git add src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbe.java src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbeIntegrationTest.java
git commit -m "perf(direction): add sanitized SQL evidence probe"
```

### Task 4: Measure the deterministic cardinality sweep

**Files:**
- Create: `src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java`

**Interfaces:**
- Consumes: `ActiveUserPresenceSql.FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`, `ActiveUserPresenceSql.FIND_CANDIDATES_SQL`, `DirectionMatchingPerformanceProbe`
- Produces: four observations per scale: preview/matching × policy/5km
- Produces: a measured transition table for 10K:1K, 50K:5K, and 100K:10K

- [ ] **Step 1: Define deterministic scale and condition records**

Create the test class without modifying the GH-163 class. Use these exact records and constants:

```java
private static final String REGION = "TEST-DIRECTION-PERF-E3";
private static final String ACCOUNT_PREFIX = "perf-e3-account-";
private static final String EXCLUDED_NICKNAME = "perf-e3-excluded";
private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
private static final double ORIGIN_LATITUDE = 37.5000;
private static final double ORIGIN_LONGITUDE = 127.0000;
private static final long SELECTIVITY_PROBE_MAX_DISTANCE_METERS = 5_000L;
private static final int BASELINE_STATISTICS_TARGET = 100;
private static final int EXPERIMENT_STATISTICS_TARGET = 1000;

private record FixtureScale(
    String label,
    int accountCount,
    int presenceCount,
    int expectedProbePresenceCount) {
}

private static Stream<FixtureScale> scales() {
    return Stream.of(
        new FixtureScale("10K_1K", 10_000, 1_000, 3),
        new FixtureScale("50K_5K", 50_000, 5_000, 13),
        new FixtureScale("100K_10K", 100_000, 10_000, 25));
}
```

Annotate the class with `@Import(DirectionMatchingE3TestClockConfiguration.class)`. Add a test-only mutable `Clock` configuration at the bottom of the file, inject the mutable clock, and reset it to `NOW` in `@BeforeEach`. Inject `JdbcTemplate`, `NamedParameterJdbcTemplate`, `ObjectMapper`, and `DirectionPostPolicy`, then construct the probe in the same setup method.

Install the extension for this class's own ephemeral container:

```java
@BeforeAll
static void installPgStatStatements(@Autowired JdbcTemplate jdbc) {
    jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
}
```

The performance preload flag starts each performance-class container with the library, but extension installation remains explicit per ephemeral database.

Use this test clock shape, following the existing GH-127 pattern without importing
the old test's clock type:

```java
@TestConfiguration
class DirectionMatchingE3TestClockConfiguration {

    @Bean
    @Primary
    DirectionMatchingE3MutableClock directionMatchingE3MutableClock() {
        return new DirectionMatchingE3MutableClock(
            Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);
    }
}

final class DirectionMatchingE3MutableClock extends Clock {

    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    DirectionMatchingE3MutableClock(Instant initial, ZoneId zone) {
        this.current = new AtomicReference<>(initial);
        this.zone = zone;
    }

    void setInstant(Instant instant) {
        current.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId requestedZone) {
        return new DirectionMatchingE3MutableClock(current.get(), requestedZone);
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
```

- [ ] **Step 2: Add deterministic fixture lifecycle**

Implement `seedFixture(FixtureScale scale)` by adapting the existing GH-163 insert statements. Preserve these rules exactly:

```text
- Insert accountCount ACTIVE accounts with six-digit logical suffixes.
- Give only suffixes 1 through presenceCount a valid presence.
- Use ST_Project with radius sqrt((gs - 0.5) / presenceCount) * 100000.
- Use azimuth radians(mod((gs - 1) * 137.50776405003785, 360)).
- Use NOW - 10 seconds for location_at and NOW + 3600 seconds for expires_at.
- Insert one separate excluded account.
```

Implement `cleanupFixture()` with foreign-key-safe ordering copied from the established performance fixtures: confirmed-event outbox rows, post recipients, matching outbox rows, audiences, posts, receive state, approved questions, presences, accounts, then the dedicated region. Scope every delete to the dedicated region or IDs reached from it.

- [ ] **Step 3: Add the statistics helper**

Implement this exact connection-scoped method:

```java
private void analyzeWithStatisticsTarget(int target) {
    assertThat(target).isIn(BASELINE_STATISTICS_TARGET, EXPERIMENT_STATISTICS_TARGET);
    jdbc.execute((ConnectionCallback<Void>) connection -> {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET default_statistics_target = " + target);
            statement.execute("ANALYZE user_account, active_user_presence");
            statement.execute("RESET default_statistics_target");
        }
        return null;
    });
}
```

The target is an internal allowlisted integer, not user input. Keep all three statements on the same connection. If `ANALYZE` fails, let the test fail; never continue with an unknown statistics state.

- [ ] **Step 4: Write the scale-sweep test**

Use one `@ParameterizedTest` with `@MethodSource("scales")` and an approved display name. For each scale:

1. Seed and assert exact account/presence/probe counts under `ACCOUNT_PREFIX`; the separate excluded sender is not part of those cardinalities.
2. Apply statistics target 100 and run `ANALYZE`.
3. Measure preview at policy radius, matching at policy radius, preview at 5km, and matching at 5km.
4. For every measurement, assert exactly 20 measured calls and identical results across all calls.
5. Parse EXPLAIN for the same four combinations.
6. Assert both target relations are present and every target node has plan rows, actual rows, loops, and block totals.
7. Print only the probe's sanitized evidence line and a sanitized plan line.

The preview result type is an immutable `Map<String, Long>` keyed by the eight known segment keys. The measured matching supplier returns an immutable ordered `List<Long>` so its client latency contains only the target query and JDBC row mapping; IDs remain in memory and never enter the evidence line. After the timed block, a separate helper maps the final result to logical account suffixes for correctness assertions.

Do not assert that GiST must be used or that latency must be below a threshold. Record `USED` or `NOT_USED`, planner estimates, actual rows, sort method, spill state, and the first scale at which each access path appears.

- [ ] **Step 5: Run the three scale scenarios**

```bash
./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest.cardinalitySweep*'
```

Expected: PASS for all three scale invocations. The access path and timing values are observations and may differ by host.

- [ ] **Step 6: Commit the cardinality experiment**

```bash
git add src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java
git commit -m "perf(direction): measure candidate cardinality transitions"
```

### Task 5: Add fixed-fixture before/after correctness guardrails

**Files:**
- Modify: `src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java`

**Interfaces:**
- Consumes: fixed `FixtureScale("100K_10K", 100_000, 10_000, 25)`, statistics targets 100 and 1000, `DirectionPostApplicationService`, `DirectionMatchingWorker`
- Produces: `LogicalQuerySnapshot` and `LogicalRecipientSnapshot` equality assertions across the two conditions

- [ ] **Step 1: Add policy-adversary rows to the guardrail fixture**

For the fixed-fixture guardrail only, add these logical rows outside the measured account prefix so the cardinality counts stay exact. Give the excluded sender a valid presence at the origin so `DirectionPostApplicationService` can submit the two synthetic posts; keep it excluded from the candidate query by ID.

```text
guardrail-eligible-near     ACTIVE, valid north-sector presence, no receive history
guardrail-eligible-old      ACTIVE, valid north-sector presence, older receive history
guardrail-eligible-recent   ACTIVE, valid north-sector presence, newer receive history
guardrail-blocked-by-sender ACTIVE, valid presence, active sender → candidate block
guardrail-blocked-sender    ACTIVE, valid presence, active candidate → sender block
guardrail-inactive          BLOCKED account, valid presence
guardrail-expired           ACTIVE, presence expires exactly at NOW
guardrail-outside           ACTIVE, valid presence outside the 5km diagnostic radius
guardrail-full-slot         ACTIVE, valid presence, active_unhandled_count at receive capacity
```

Place the three eligible rows and `guardrail-full-slot` inside the first 30 north-sector candidates by using distinct deterministic distances below the synthetic fixture's nearest radius. Assert both directions of block exclusion, ACTIVE account filtering, `expires_at > NOW`, distance filtering, relative fairness order among the three eligible rows, and full-slot exclusion from the persisted recipient set.

- [ ] **Step 2: Define logical snapshots that contain no database IDs**

```java
private record LogicalQuerySnapshot(
    Map<String, Long> previewCounts,
    List<String> matchingOrder,
    Set<String> matchingSet) {
}

private record LogicalRecipientSnapshot(
    List<String> orderedRecipients,
    Set<String> recipientSet,
    long duplicateCount,
    long missingExpectedCount,
    long blockedRecipientCount,
    long fullSlotRecipientCount) {
}
```

Map IDs to the deterministic nickname or suffix only inside the test process. Assertions may mention logical labels on failure, but evidence output must report counts only.

- [ ] **Step 3: Write the fixed-fixture query before/after test**

The test sequence is exact:

```text
seed fixed 100K:10K fixture and guardrail rows
apply statistics target 100 and ANALYZE
capture preview/matching results plus four query/radius measurements and plans
apply statistics target 1000 and ANALYZE on the unchanged data
capture the same results, measurements, and plans
assert previewCounts, matchingOrder, and matchingSet are equal
assert every 20-call batch returned the same logical result
assert blocked, inactive, and expired labels are absent in both conditions
record estimate, access-path, latency, calls, rows, block, sort, and spill deltas
```

The sole changed variable is `default_statistics_target`. Do not reseed between the query snapshots.

- [ ] **Step 4: Run query guardrails and confirm GREEN**

```bash
./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest.statisticsTargetComparison*'
```

Expected: PASS with equal logical query snapshots. A plan or timing improvement is not required for PASS.

- [ ] **Step 5: Write the persisted-recipient before/after test**

Inject `DirectionPostApplicationService` and `DirectionMatchingWorker`. Reuse the established #127 flow:

1. Create an active sender, current sender presence, and active approved question.
2. Submit an `N` direction post with a unique idempotency key.
3. Move only that synthetic post's moderation status to `PASSED` as the existing test seam.
4. Process one matching batch with limit 10, deterministic `NOW`, a 60-second lease, and `OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))`.
5. Read persisted recipients by joining `post_recipient` to `user_account`, normalize to logical nickname, and assert no duplicate recipient.
6. Delete only that run's confirmed-event outbox rows, recipients, matching event, audience, and post; reset only its touched `recipient_receive_state` rows.
7. Repeat with the same data after changing only statistics target from 100 to 1000.
8. Assert the two `LogicalRecipientSnapshot` values are equal, duplicate/missing/blocked/full-slot counts are zero, and both worker outcomes are exactly `PROCESSED`.

Do not include generated post, recipient, outbox, or account IDs in output.

- [ ] **Step 6: Run the persisted-recipient guardrail**

```bash
./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest.persistedRecipientGuardrail*'
```

Expected: PASS with identical logical recipient sets and zero policy violations.

- [ ] **Step 7: Run the full new E3 class**

```bash
./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'
```

Expected: PASS. Preserve the complete sanitized console output for the report; do not preserve raw SQL or EXPLAIN JSON.

- [ ] **Step 8: Commit the correctness guardrails**

```bash
git add src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java
git commit -m "perf(direction): guard PostGIS before-after correctness"
```

### Task 6: Add the performance experiment report contract

**Files:**
- Create: `templates/performance-experiment-report.md`
- Create or modify: Issue-derived report under `docs/reports/tests/`
- Modify: `TASK.md`

**Interfaces:**
- Consumes: sanitized performance output, Git commit metadata, runtime versions, approved test plan
- Produces: one reproducible E3 report with explicit evidence limits and a final repository status

- [ ] **Step 1: Create the reusable report template**

The template must contain these exact sections:

```markdown
# Performance Experiment Report

## Contract
- Experiment ID
- GitHub Issue
- Test plan ID
- Before commit and condition
- After commit and condition
- Fixture seed and logical distribution
- Primary metric
- Correctness, policy, resource, and cost guardrails
- Success and stop criteria

## Environment
- Java, Spring Boot, PostgreSQL, PostGIS, Docker Desktop versions
- Host resource summary without account, address, or device identifiers
- Container CPU and memory limits, or an explicit statement that Testcontainers limits were not fixed
- JVM, Hikari, and worker settings
- Dirty-worktree status excluding unrelated file contents

## Method
- Warm-up count
- Measured-call count
- Cold or warm state
- Statistics target
- Query/radius combinations
- Representative-value rule

## Results
- Client p50, p95, p99
- PostgreSQL calls, total/mean execution time, rows
- Shared and temp blocks
- Plan estimates, actual rows, access paths, GiST state
- Sort method, space type, and spill state

## Guardrails
- Preview count equality
- Matching order/set equality
- Persisted recipient equality
- Duplicate, missing, block, status, expiry, fairness, and capacity checks

## Interpretation
- Supported or rejected hypothesis
- Rejected alternatives
- Local-only applicability limits
- Follow-up decision requiring a separate Issue

## Verification
- Commands and results
- Failed or blocked checks
- Residual risks
```

Add a warning immediately under the title that raw query text, EXPLAIN JSON, IDs, coordinates, credentials, URLs, tokens, `.env` values, and complete logs must not be copied into the report.

- [ ] **Step 2: Scaffold the Issue report and populate only fresh evidence**

Run:

```bash
./harness test-run --id "$TEST_PLAN_ID"
```

This command does not execute the manual `performanceTest` suite, so record that limitation and then run the focused performance command separately. Populate the generated report using the performance template sections. Do not copy GH-163 timings as if they were fresh results.

For the E3 decision, use one of these exact conclusions based on observed evidence:

```text
SUPPORTED: statistics target 1000 changed planner estimates or access path while all guardrails remained equal.
REJECTED: statistics target 1000 did not materially change estimates/access path, while all guardrails remained equal.
INVALID: a correctness/policy guardrail failed or the controlled conditions were not preserved.
```

Only `SUPPORTED` or `REJECTED` with every P0 guardrail passing can be presented as a completed controlled E3 experiment. `INVALID` is `FAIL`, not a resume result.

- [ ] **Step 3: Run all performance and repository checks**

```bash
./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'
./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'
./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'
./gradlew performanceTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

Expected: every required command PASS. If a command cannot run, record the command, reason, affected scope, residual risk, and follow-up verification; final status is `BLOCKED` when a required check is unavailable.

- [ ] **Step 4: Perform the security and scope scan**

Run:

```bash
git diff --name-only origin/main...HEAD
git diff --check origin/main...HEAD
rg -n "SELECT .*query|raw.*EXPLAIN|userId=|nickname=|latitude=|longitude=|token=|password=" docs/reports/tests src/integrationTest/java/com/dnd/qello
```

Review every match. SQL inside test setup and the intentionally fixed `test-only` Testcontainers password are allowed only in source; none may appear in evidence output. Confirm the diff contains no `src/main/java`, Flyway migration, Terraform, workflow, or production configuration changes.

- [ ] **Step 5: Update the task contract with final evidence**

Record this exact final contract shape in `TASK.md`:

```text
status: PASS | FAIL | BLOCKED
issue_number: actual branch Issue
task_id: approved task ID
design_id: N/A (local test-only experiment)
changed_files: complete tracked list
executed_checks: exact commands
passed_checks: exact passing commands
failed_checks: exact failing commands or none
blocked_checks: exact unavailable commands or none
assumptions: synthetic distribution and statistics targets
risks: local planner/host/cache differences and no fixed Testcontainers resources
required_human_decisions: production change remains a separate Issue
```

- [ ] **Step 6: Commit the report and final contract**

```bash
git add templates/performance-experiment-report.md docs/reports/tests TASK.md
git commit -m "perf(direction): record PostGIS E3 experiment evidence"
```

## Completion Criteria

- [ ] A new open Issue, matching performance branch, approved `TASK.md`, and approved test plan exist.
- [ ] Ordinary `integrationTest` remains independent of the performance-only PostgreSQL preload flag.
- [ ] `pg_stat_statements` is queryable only in the intended manual performance execution surface.
- [ ] All 10K:1K, 50K:5K, and 100K:10K scale observations are fresh and sanitized.
- [ ] The fixed 100K:10K comparison changes only statistics target 100 → 1000.
- [ ] Preview counts, matching logical order/set, and persisted recipient logical set are equal before/after.
- [ ] Duplicate, missing, bidirectional-block, inactive, expired, fairness, and capacity guardrails pass.
- [ ] No latency or GiST access path is used as a brittle pass/fail threshold.
- [ ] The report states `SUPPORTED`, `REJECTED`, or `INVALID` from fresh evidence and does not overstate production impact.
- [ ] No production source, query, index, migration, Compose, Terraform, workflow, or external service changes are present.
- [ ] All required performance and repository verification commands pass, or the final status is accurately `FAIL`/`BLOCKED`.

## Plan-Level Risks and Decisions

- Statistics target 1000 may not change PostGIS selectivity estimates. That is a valid rejected hypothesis, not a reason to edit SQL or indexes in this Issue.
- The 10K/50K/100K scale sweep cannot by itself satisfy set-equality guardrails because each scale has different logical data. It is transition evidence; the fixed 100K:10K statistics comparison is the E3 before/after.
- `pg_stat_statements` aggregates database execution, while client p50/p95/p99 include JDBC overhead. Report both and do not present them as interchangeable.
- Running all performance tests in one Gradle JVM preloads the library for every performance-tagged class, but extension creation remains scoped to classes that need it. The ordinary integration JVM receives no preload flag.
- Testcontainers CPU and memory are not fixed by this plan. The report must name this as a measurement limitation.
- Any recommendation to alter statistics, queries, indexes, schema, policy, or production infrastructure requires a new Issue and human approval based on the experiment result.
