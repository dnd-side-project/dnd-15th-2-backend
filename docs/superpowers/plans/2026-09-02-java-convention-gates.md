# Java 코드 컨벤션 자동 검증 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Issue #208의 Java formatter, source/architecture convention, baseline, Husky와 CI gate를 도입해 변경된 Java 코드의 새 위반을 자동 차단한다.

**Architecture:** Gradle task가 Spotless, Checkstyle, source-contract JUnit test, ArchUnit과 baseline validator를 묶는 실행 기준이다. Python validator는 Git index/blob과 baseline lifecycle만 다루고, `scripts/run-hook.py`는 staged manifest를 만들어 Gradle에 전달한다. CI와 pre-push는 전체 gate를, pre-commit은 staged Java의 좁은 gate를 실행한다.

**Tech Stack:** Java 21, Gradle 8.14.3, Spotless 8.10.1, Eclipse JDT 4.26 formatter, Checkstyle 14.1.0, ArchUnit 1.4.2, JavaParser 3.28.2, JUnit 5, Python 3, Husky 9.1.7, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-02-java-convention-gates-design.md`

**Test plan:** `docs/test-plans/gh-208-TEST-PLAN-GH-208-JAVA-CONVENTION-GATES.md`

## Global Constraints

- Java toolchain은 21이고 Gradle wrapper는 8.14.3이다.
- Spotless, Eclipse JDT, Checkstyle, ArchUnit과 JavaParser 버전은 header 값으로 고정하며 동적 버전을 쓰지 않는다.
- formatter는 `origin/main` ratchet과 staged manifest만 대상으로 하며 기존 Java source 전체를 일괄 포맷하지 않는다.
- production Service, API, domain, DB schema, migration과 query를 수정하지 않는다.
- 모든 신규 JUnit class는 정확한 ISO 8601 생성 시각, `TEST-PLAN-GH-208-JAVA-CONVENTION-GATES` source scenario와 모든 method의 `@DisplayName`을 가진다.
- `LEGACY`는 Issue #208 bootstrap에서만 추가할 수 있고, 이후에는 삭제만 가능하다. `JUSTIFIED_EXCEPTION`에는 exact target, design reference와 `TASK.md` decision ID가 필요하다.
- formatter, hook과 validator는 source나 Git index를 자동 수정하거나 stage하지 않는다.
- pre-commit은 staged Java 파일에 unstaged diff가 있으면 실패한다. non-Java staged file이나 unrelated unstaged Java file은 새 Java gate의 대상이 아니다.
- 민감정보를 baseline, fixture, log와 report에 기록하지 않는다.
- 커밋은 별도 사람 승인 게이트다. 아래 commit message는 승인 후 사용할 제안이며 모두 `chore(...): ... (#208)`을 사용한다.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `build.gradle` | pinned quality tool dependency, Spotless/Checkstyle config, staged/full task wiring |
| `config/spotless/eclipse-java-formatter.xml` | tab, wrapping, braces와 newline Eclipse JDT profile |
| `config/spotless/java-import-order.txt` | import group order |
| `config/checkstyle/checkstyle.xml` | import, wildcard, 50-line, complexity-15, bypass source rules |
| `config/checkstyle/suppressions.xml` | permanent generated-source path exclusion만 보관 |
| `config/java-conventions/baseline.json` | exact target·hash·reason·tracking source of truth |
| `scripts/validate-java-conventions.py` | baseline schema, Git blob hash, base comparison, suppression generation, self-test |
| `scripts/run-hook.py` | partial staging guard, temporary manifest, staged Gradle dispatch |
| `scripts/harness.py` | validator self-test를 harness check에 추가 |
| `scripts/validate-workflows.py` | convention CI job contract validation |
| `docs/harness/JAVA_CONVENTIONS.md` | 사람이 읽는 rule, exception과 remediation guide |
| `src/test/java/com/dnd/qello/convention/JavaConventionBaselineTest.java` | validator CLI/schema/hash/lifecycle unit contract |
| `src/test/java/com/dnd/qello/convention/JavaSourceConventionTest.java` | JavaParser Lombok annotation, explicit constructor, inline bypass contract |
| `src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureTest.java` | ArchUnit injection, transaction visibility/read-only/self-invocation contract |
| `src/test/java/com/dnd/qello/**/fixture/**` | architecture/source positive·negative fixture |
| `src/integrationTest/java/com/dnd/qello/convention/*IntegrationTest.java` | Gradle TestKit, temporary Git hook, workflow contract integration |
| `.github/workflows/harness-policy.yml` | `java-conventions` job and explicit checkout history |

## Task 1: Pin formatter and static-analysis foundation

**Files:**

- Create: `config/spotless/eclipse-java-formatter.xml`
- Create: `config/spotless/java-import-order.txt`
- Create: `config/checkstyle/checkstyle.xml`
- Create: `config/checkstyle/suppressions.xml`
- Create: `src/test/java/com/dnd/qello/convention/staticanalysis/JavaStaticAnalysisRuleTest.java`
- Create: `src/test/resources/java-conventions/static-analysis/CompliantService.java`
- Create: `src/test/resources/java-conventions/static-analysis/MethodLengthBoundary.java`
- Create: `src/test/resources/java-conventions/static-analysis/ComplexityBoundary.java`
- Create: `src/test/resources/java-conventions/static-analysis/WildcardAndBypass.java`
- Modify: `build.gradle:1-115`
- Modify: design spec and test plan source-parser wording

**Interfaces:**

- Consumes `UNIT-015` through `UNIT-019`.
- Produces `spotlessJavaCheck`, `spotlessApply`, `checkstyleMain`.
- Produces Checkstyle IDs `QELLO-JAVA-IMPORT-001`, `QELLO-JAVA-SIZE-001`, `QELLO-JAVA-CPLX-001`, `QELLO-JAVA-BYPASS-001`.
- Reserves `QELLO-JAVA-CTOR-001` for the JavaParser source-contract test in Task 3. This preserves approved behavior while avoiding a regex that cannot safely relate a class annotation to its constructor.

- [ ] **Step 1: Write failing static-analysis boundary tests.**

~~~java
@Test
@DisplayName("51줄 method는 QELLO-JAVA-SIZE-001로 거절한다")
void rejectsMethodWithFiftyOneLines() {
    CheckResult result = check("MethodLengthBoundary.java", "tooLong");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("QELLO-JAVA-SIZE-001");
}

@Test
@DisplayName("복잡도 15는 허용하고 16은 QELLO-JAVA-CPLX-001로 거절한다")
void enforcesComplexityBoundary() {
    assertThat(check("ComplexityBoundary.java", "atMostFifteen").exitCode()).isZero();
    assertThat(check("ComplexityBoundary.java", "sixteenBranches").output())
        .contains("QELLO-JAVA-CPLX-001");
}
~~~

- [ ] **Step 2: Run the new test before configuration exists.**

Run:

~~~bash
./gradlew test --tests '*JavaStaticAnalysisRuleTest'
~~~

Expected: FAIL because the fixture runner and Checkstyle configuration do not exist.

- [ ] **Step 3: Add pinned plugins, dependencies and standard tasks.**

~~~groovy
id 'com.diffplug.spotless' version '8.10.1'

checkstyle {
    toolVersion = '14.1.0'
}

dependencies {
    testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.2'
    testImplementation 'com.github.javaparser:javaparser-core:3.28.2'
    testImplementation gradleTestKit()
}
~~~

Configure Spotless with `ratchetFrom 'origin/main'`, UTF-8, committed Eclipse 4.26 profile and import-order file. Do not enable member sorting, `toggleOffOn`, `ignoreErrorForPath`, `ignoreErrorForStep` or `enforceCheck false`.

Configure Checkstyle only for `src/main/java`, with `maxWarnings = 0`, committed XML and generated baseline suppression property. Do not apply it to test source.

- [ ] **Step 4: Implement formatter and Checkstyle rules.**

Set the Eclipse profile to tab indentation and 120 columns. Configure import groups:

~~~text
java
javax
jakarta
org
com
lombok
\#
~~~

Configure `AvoidStarImport`, `CustomImportOrder`, `MethodLength(max=50)`, `CyclomaticComplexity(max=15)`, and a bypass rule rejecting `spotless:off`, `spotless:on`, `CHECKSTYLE:OFF` and `@SuppressWarnings("qello:...")`. Bind each to the stable ID.

- [ ] **Step 5: Run fixture and formatter checks.**

~~~bash
./gradlew test --tests '*JavaStaticAnalysisRuleTest'
./gradlew spotlessApply -PspotlessFiles='.*java-conventions.*CompliantService\\.java'
./gradlew spotlessJavaCheck -PspotlessFiles='.*java-conventions.*CompliantService\\.java'
~~~

Expected: expected rule IDs occur only on negative fixtures, compliant fixture passes, and the second formatter check has no diff.

- [ ] **Step 6: Prepare commit 1 for review.**

Proposed paths: `build.gradle`, `config/spotless/**`, `config/checkstyle/**`, static-analysis test/fixtures, design spec and test plan.

Suggested message:

~~~text
chore(conventions): add formatter and static analysis foundation (#208)
~~~

## Task 2: Implement baseline validator and lifecycle

**Files:**

- Create: `config/java-conventions/baseline.json`
- Create: `scripts/validate-java-conventions.py`
- Create: `src/test/java/com/dnd/qello/convention/JavaConventionBaselineTest.java`
- Create: `src/test/resources/java-conventions/baseline/valid.json`
- Create: `src/test/resources/java-conventions/baseline/duplicate-id.json`
- Create: `src/test/resources/java-conventions/baseline/wildcard-target.json`
- Create: `src/test/resources/java-conventions/baseline/missing-legacy-metadata.json`
- Create: `src/test/resources/java-conventions/baseline/missing-justified-decision.json`
- Create: `src/test/resources/java-conventions/baseline/stale-hash.json`
- Modify: `build.gradle:quality configuration`
- Modify: `scripts/harness.py:349-361`

**Interfaces:**

~~~text
python scripts/validate-java-conventions.py --self-test
python scripts/validate-java-conventions.py validate --mode repository \
  --baseline config/java-conventions/baseline.json --task-file TASK.md \
  --base-ref origin/main --suppression-output build/generated/checkstyle/baseline-suppressions.xml
python scripts/validate-java-conventions.py validate --mode staged \
  --manifest <absolute-json-path> --baseline config/java-conventions/baseline.json \
  --task-file TASK.md --base-ref origin/main \
  --suppression-output build/generated/checkstyle/baseline-suppressions.xml
~~~

Produces `QELLO-JAVA-BASELINE-001` through `QELLO-JAVA-BASELINE-008` for schema, duplicate ID, target, metadata, decision, hash, lifecycle and manifest failures.

- [ ] **Step 1: Write failing baseline JUnit contracts.**

~~~java
@Test
@DisplayName("변경된 LEGACY target은 기존 hash를 갱신해 연장할 수 없다")
void rejectsLegacyHashOnlyExtension() {
    CommandResult result = runValidator("head-with-updated-hash", "origin/main");

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("QELLO-JAVA-BASELINE-007");
}
~~~

- [ ] **Step 2: Run tests before the validator exists.**

~~~bash
./gradlew test --tests '*JavaConventionBaselineTest'
~~~

Expected: FAIL because the validator is absent.

- [ ] **Step 3: Add schema and canonical Git blob reader.**

~~~json
{
  "schemaVersion": 1,
  "bootstrapIssue": 208,
  "entries": []
}
~~~

Read bytes only through `git show <revision>:<relative-path>` and hash with `hashlib.sha256`. Parse Git file lists with `-z`; reject absolute/out-of-root/NUL paths. Write suppression XML only below `build/generated/checkstyle/`.

- [ ] **Step 4: Enforce lifecycle and exception metadata.**

Require every entry to have `id`, known `rule`, exact `target`, lowercase 64-character `sourceSha256`, non-empty `reason` and valid classification. Require `trackingReference` and ISO `reviewBy` for `LEGACY`; require `designReference` and matching `DEC-...` in `TASK.md` for `JUSTIFIED_EXCEPTION`.

If `origin/main` lacks the baseline, permit entries only when `bootstrapIssue=208` matches current task issue. Once base has it, reject new legacy IDs and legacy hash-only updates; allow deletion. Changed justified targets require renewed matching decision/reference.

- [ ] **Step 5: Generate exact Checkstyle suppressions and wire tasks.**

Generate deterministic XML sorted by entry ID, mapping only Checkstyle-owned rules to escaped `<suppress checks="..." files="..."/>` entries. Keep committed `suppressions.xml` free of legacy paths.

Add `validateJavaConventionBaseline` Gradle task that executes repository mode before Checkstyle and exposes generated path through `qelloBaselineSuppressions`. Add validator `--self-test` to `scripts/harness.py`.

- [ ] **Step 6: Run focused verification.**

~~~bash
./gradlew test --tests '*JavaConventionBaselineTest'
python scripts/validate-java-conventions.py --self-test
./gradlew validateJavaConventionBaseline
./harness check
~~~

Expected: valid fixture/current bootstrap passes; each invalid fixture fails with one expected baseline ID.

- [ ] **Step 7: Prepare commit 2 for review.**

Proposed paths: baseline JSON, validator script, harness, build file, baseline test and fixtures.

Suggested message:

~~~text
chore(conventions): add baseline lifecycle validation (#208)
~~~

## Task 3: Add Lombok source-contract and transaction architecture rules

**Files:**

- Create: `src/test/java/com/dnd/qello/convention/JavaSourceConventionTest.java`
- Create: `src/test/java/com/dnd/qello/convention/fixture/CompliantRequiredArgsService.java`
- Create: `src/test/java/com/dnd/qello/convention/fixture/ExplicitConstructorService.java`
- Create: `src/test/java/com/dnd/qello/convention/fixture/JustifiedTransactionTemplateService.java`
- Create: `src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureTest.java`
- Create: `src/test/java/com/dnd/qello/architecture/fixture/FieldInjectedService.java`
- Create: `src/test/java/com/dnd/qello/architecture/fixture/ClassWriteTransactionalService.java`
- Create: `src/test/java/com/dnd/qello/architecture/fixture/PrivateTransactionalService.java`
- Create: `src/test/java/com/dnd/qello/architecture/fixture/SelfInvokingTransactionalService.java`
- Create: `src/test/java/com/dnd/qello/architecture/fixture/ReadOnlyTransactionalService.java`
- Modify: `build.gradle:quality test task configuration`

**Interfaces:**

- `qello.convention.manifest` absent scans all `src/main/java/**/*.java`; supplied manifest scans only listed files.
- Source rules: `QELLO-JAVA-CTOR-001`, `QELLO-JAVA-BYPASS-001`.
- Architecture rules: `QELLO-JAVA-INJECTION-001`, `QELLO-JAVA-TX-001`, `QELLO-JAVA-TX-002`, `QELLO-JAVA-TX-003`.

- [ ] **Step 1: Write failing JavaParser tests.**

~~~java
@Test
@DisplayName("단순 명시적 생성자는 QELLO-JAVA-CTOR-001로 보고한다")
void reportsSimpleExplicitConstructor() {
    List<Violation> violations = inspect(fixture("ExplicitConstructorService.java"));

    assertThat(violations).singleElement()
        .extracting(Violation::ruleId)
        .isEqualTo("QELLO-JAVA-CTOR-001");
}
~~~

- [ ] **Step 2: Run before scanner implementation.**

~~~bash
./gradlew test --tests '*JavaSourceConventionTest'
~~~

Expected: FAIL because the scanner is absent.

- [ ] **Step 3: Implement JavaParser source contract.**

Recognize stereotype simple names `Service`, `Component`, `Repository`, `Controller`, `RestController`, `Configuration`; ignore JPA entities. A stereotype class with declared constructor and instance dependency field must have `RequiredArgsConstructor` unless `baseline.json` contains current exact `JUSTIFIED_EXCEPTION` for `QELLO-JAVA-CTOR-001`.

Reject `spotless:off`, `spotless:on`, `CHECKSTYLE:OFF` and `@SuppressWarnings("qello:...")` with `QELLO-JAVA-BYPASS-001`. Print deterministic `rule_id, file, line_or_target, reason, remediation_command, exception_process` fields and never modify input.

- [ ] **Step 4: Write failing ArchUnit fixture tests.**

~~~java
@Test
@DisplayName("class-level write Transactional은 QELLO-JAVA-TX-001로 거절한다")
void rejectsClassWriteTransaction() {
    assertThatThrownBy(() -> classTransactionRule().check(importFixture(ClassWriteTransactionalService.class)))
        .hasMessageContaining("QELLO-JAVA-TX-001");
}
~~~

Cover `UNIT-008` through `UNIT-014`, one negative violation per fixture.

- [ ] **Step 5: Implement ArchUnit predicates and conditions.**

Reject field/setter injection and injected non-`private final` state. A Service that depends on a repository package, `EntityManager`, JDBC template, transaction manager, or declares method `@Transactional` is transaction-aware. Require class `@Transactional(readOnly = true)` unless exact exception applies. Reject class default write annotation, non-public transaction method and same-class call to a transaction-annotated target.

Do not infer business write behavior. Keep `TransactionTemplate`, `NOT_SUPPORTED`, external-I/O and parameter-metadata exceptions exact and baseline-backed.

- [ ] **Step 6: Register focused test tasks and execute them.**

Add `javaConventionSourceTest` and `javaConventionArchitectureTest` Test tasks that run only these classes. Pass `qello.convention.manifest` when `qelloConventionManifest` is supplied.

~~~bash
./gradlew javaConventionSourceTest javaConventionArchitectureTest
./gradlew test --tests '*JavaSourceConventionTest' --tests '*JavaConventionArchitectureTest'
~~~

Expected: compliant fixtures pass and every negative fixture is asserted inside a passing JUnit test.

- [ ] **Step 7: Prepare commit 3 for review.**

Proposed paths: build file, both rule tests and all source/architecture fixture files.

Suggested message:

~~~text
chore(conventions): enforce constructor injection and transaction rules (#208)
~~~

## Task 4: Compose staged and full Gradle gates

**Files:**

- Modify: `build.gradle:quality task configuration`
- Modify: `scripts/validate-java-conventions.py:manifest validation`
- Create: `src/integrationTest/java/com/dnd/qello/convention/JavaConventionGradleIntegrationTest.java`
- Create: `src/integrationTest/resources/java-conventions/gradle/settings.gradle`
- Create: `src/integrationTest/resources/java-conventions/gradle/build.gradle`
- Create: `src/integrationTest/resources/java-conventions/gradle/src/main/java/example/Compliant.java`
- Create: `src/integrationTest/resources/java-conventions/gradle/src/main/java/example/Misformatted.java`

**Interfaces:**

- `-PqelloConventionManifest=<absolute JSON path>` contains `{"paths":["src/main/java/...java"]}`.
- `-PspotlessFiles=<comma-separated absolute-path regular expressions>` contains only `Pattern.quote`-escaped paths from the manifest.
- Produces `checkstyleStagedJava`, `javaConventionStagedCheck`, `javaConventionCheck`, `validateJavaConventionBaseline`.

- [ ] **Step 1: Write failing Gradle TestKit tests.**

~~~java
@Test
@DisplayName("check는 controlled convention violation을 포함하면 실패한다")
void checkDependsOnConventionGate() {
    BuildResult result = runner("check").buildAndFail();

    assertThat(result.getOutput()).contains("QELLO-JAVA-");
}
~~~

Cover `INT-001`, `INT-002`, `INT-006` through `INT-009`, and `INT-013`.

- [ ] **Step 2: Run before aggregate tasks exist.**

~~~bash
./gradlew integrationTest --tests '*JavaConventionGradleIntegrationTest'
~~~

Expected: FAIL because staged/full convention tasks do not exist.

- [ ] **Step 3: Implement manifest-to-task wiring.**

Parse manifest JSON in Gradle configuration. Require relative, existing, in-root, unique `.java` paths. Convert absolute paths with `Pattern.quote(path.toString())` before joining for Spotless.

Register `checkstyleStagedJava` with manifest source, same classpath/config/properties as `checkstyleMain`, and no source for empty manifest. Register `javaConventionStagedCheck` to depend on staged Spotless, staged Checkstyle, baseline validation, source and architecture tasks.

Register `javaConventionCheck` to depend on ratcheted Spotless, `checkstyleMain`, baseline validation, source and architecture tasks. Make `check` depend on it without a cycle.

- [ ] **Step 4: Implement missing-ref classification.**

Before Spotless, require `origin/main`. On absence fail with:

~~~text
QELLO-JAVA-BASELINE-008
reason: origin/main is required for changed-file formatting
remediation_command: git fetch origin main
~~~

Do not skip formatting or use `enforceCheck false`. Formatter mismatches must instead include `./gradlew spotlessApply`.

- [ ] **Step 5: Run focused integration suite.**

~~~bash
./gradlew integrationTest --tests '*JavaConventionGradleIntegrationTest'
./gradlew javaConventionCheck
~~~

Expected: idempotent formatter, changed-only ratchet, aggregate child rule IDs, explicit missing-ref failure and Java 21/Gradle 8.14.3 resolution.

- [ ] **Step 6: Prepare commit 4 for review.**

Proposed paths: build file, validator script, Gradle integration test and fixtures.

Suggested message:

~~~text
chore(conventions): add staged and full Gradle gates (#208)
~~~

## Task 5: Connect Husky and GitHub Actions

**Files:**

- Modify: `scripts/run-hook.py:20-97`
- Modify: `scripts/validate-husky.py:15-88`
- Modify: `scripts/validate-workflows.py:15-95`
- Modify: `.github/workflows/harness-policy.yml:1-105`
- Create: `src/test/java/com/dnd/qello/convention/hook/JavaConventionHookDispatchTest.java`
- Create: `src/integrationTest/java/com/dnd/qello/convention/JavaConventionHookIntegrationTest.java`
- Create: `src/integrationTest/java/com/dnd/qello/convention/JavaConventionWorkflowContractIntegrationTest.java`
- Create: `src/integrationTest/resources/java-conventions/hook/Compliant.java`
- Create: `src/integrationTest/resources/java-conventions/hook/Violation.java`

**Interfaces:**

- `run-hook.py pre-commit` calls `./gradlew javaConventionStagedCheck -PqelloConventionManifest=<temp> -PspotlessFiles=<escaped>` once only when staged Java exists.
- The temporary manifest is UTF-8 `{"paths":[...relative Java paths...]}`, owned and deleted by `run-hook.py`.
- CI job is `java-conventions`; it uses `fetch-depth: 0`, Java 21 and `./gradlew javaConventionCheck`.

- [ ] **Step 1: Write failing hook and workflow tests.**

~~~java
@Test
@DisplayName("partial staged Java는 index와 working tree를 바꾸지 않고 실패한다")
void rejectsPartialStagingWithoutMutation() {
    BlobPair before = blobsOf("src/main/java/example/Violation.java");
    CommandResult result = runPreCommit();

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("QELLO-JAVA-STAGED-001");
    assertThat(blobsOf("src/main/java/example/Violation.java")).isEqualTo(before);
}
~~~

Cover `UNIT-020`, `INT-003` through `INT-005`, and `INT-010` through `INT-012`.

- [ ] **Step 2: Run before runner/workflow changes.**

~~~bash
./gradlew test --tests '*JavaConventionHookDispatchTest'
./gradlew integrationTest --tests '*JavaConventionHookIntegrationTest' --tests '*JavaConventionWorkflowContractIntegrationTest'
~~~

Expected: FAIL because Java dispatch and CI job are absent.

- [ ] **Step 3: Implement partial staging and manifest lifecycle.**

Use existing NUL-delimited `staged_files()`. For every staged Java path, run `git diff --quiet -- <path>`; a non-zero result is unstaged diff and produces `QELLO-JAVA-STAGED-001` before Gradle starts.

Use `tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=False)` outside the repository; write JSON, invoke Gradle, remove it in `finally`. Preserve existing branch, diff, secret, JUnit, workflow and Husky validation order. Run the Java gate after cached diff check and before existing conditional checks. Never call `spotlessApply`, `git add`, `git reset`, `git checkout` or modify hooksPath.

- [ ] **Step 4: Extend Husky/workflow validators and CI.**

Make `validate-husky.py` verify the shared runner includes staged Java dispatch while package scripts still call only the runner. Make `validate-workflows.py` require for `harness-policy.yml`: `java-conventions:`, `actions/checkout@v5`, `fetch-depth: 0`, `java-version: "21"`, `./gradlew javaConventionCheck`.

Add the job with full checkout, Temurin 21, Gradle cache, executable wrapper and convention command. Preserve existing policy, test and sync-api-docs behavior and permissions.

- [ ] **Step 5: Run focused hook/workflow verification.**

~~~bash
./gradlew test --tests '*JavaConventionHookDispatchTest'
./gradlew integrationTest --tests '*JavaConventionHookIntegrationTest' --tests '*JavaConventionWorkflowContractIntegrationTest'
npm run hooks:validate
python scripts/validate-workflows.py .github/workflows/harness-policy.yml
~~~

Expected: Java staging dispatches one bounded Gradle command, non-Java skips it, partial staging is read-only failure, repeated fixture leaves no manifest, and CI contract passes.

- [ ] **Step 6: Prepare commit 5 for review.**

Proposed paths: hook scripts, workflow, hook/workflow tests and hook fixtures.

Suggested message:

~~~text
chore(conventions): enforce Java checks in hooks and CI (#208)
~~~

## Task 6: Audit bootstrap baseline, document policy and prove regression

**Files:**

- Modify: `config/java-conventions/baseline.json`
- Create: `docs/harness/JAVA_CONVENTIONS.md`
- Modify: `TASK.md:Work gate, Existing user-owned changes, Validation, Completion criteria`
- Modify: `docs/test-plans/gh-208-TEST-PLAN-GH-208-JAVA-CONVENTION-GATES.md:execution evidence`
- Create: `docs/test-reports/gh-208-TEST-REPORT-GH-208-JAVA-CONVENTION-GATES.md`

**Interfaces:**

- Consumes all prior gates and current production source without modifying it.
- Produces a baseline whose every entry has actual hash, exact target, classification, rationale and tracking reference.
- Produces test report from `templates/test-report.md` with counts, commands, timings, blocked checks and residual risks.

- [ ] **Step 1: Run audit mode and write failing inventory assertion.**

~~~bash
python scripts/validate-java-conventions.py audit \
  --baseline config/java-conventions/baseline.json \
  --task-file TASK.md --base-ref origin/main
~~~

Expected before inventory: uncovered legacy targets with stable IDs; no source, baseline or index mutation.

- [ ] **Step 2: Generate exact baseline entries and Project references.**

Record every discovered violation as an exact target. Use `LEGACY` for pre-existing constructor/transaction/size/complexity findings and `JUSTIFIED_EXCEPTION` only for approved programmatic transaction, `NOT_SUPPORTED`, external-I/O or injection-metadata cases. Compute hash from `origin/main:<path>` bytes.

Before creating Project draft items, show the exact package groups and titles to the human partner. After approval, add the returned Project draft reference to each related legacy entry. Do not bulk-create follow-up Repository Issues.

- [ ] **Step 3: Write the developer contract.**

Document:

~~~bash
./gradlew spotlessApply
./gradlew javaConventionStagedCheck
./gradlew javaConventionCheck
~~~

Explain tabs/120/imports, constructor and transaction exceptions, rule IDs, classifications, partial staging, no inline suppression, CI authority and package-by-package removal. An intentional exception requires approved `DEC-...`, exact baseline metadata, focused gate and reviewer evidence.

- [ ] **Step 4: Run bootstrap and full regression.**

~~~bash
./gradlew javaConventionCheck
./gradlew test --tests '*JavaConvention*'
./gradlew integrationTest --tests '*JavaConvention*'
./gradlew check
npm run hooks:validate
./harness test-run --id TEST-PLAN-GH-208-JAVA-CONVENTION-GATES
./harness check
./harness pr-ready --project-tests
git diff --check
~~~

Expected: every current legacy violation is baseline-backed, no production source/schema file changed, controlled negative fixtures fail only inside assertions, and full unit/integration regression passes. If a required environment prevents a command, report command, failure, scenario IDs, impact and remaining risk as `BLOCKED`.

- [ ] **Step 5: Write evidence and update contract.**

Use `templates/test-report.md`. Record actual test counts, command outcomes, cold/warm hook timing, baseline category counts, no-production-source diff evidence, and application/DB/concurrency/transaction/external API/failure-recovery analysis. Mark only observed completion criteria.

- [ ] **Step 6: Prepare commit 6 for review.**

Proposed paths: baseline, developer doc, task contract, approved test plan and test report.

Suggested message:

~~~text
chore(conventions): document baseline and verification evidence (#208)
~~~

## Plan Self-Review

| Spec requirement | Implementing task |
| --- | --- |
| Gradle single execution contract | Tasks 1, 2, 4 |
| Eclipse formatter, tabs, 120 columns, imports and ratchet | Tasks 1, 4 |
| Checkstyle import/size/complexity/bypass | Task 1 |
| Lombok source relation, injection and transaction rules | Task 3 |
| baseline, canonical blobs, lifecycle and Project tracking | Tasks 2, 6 |
| staged-only gate and partial staging protection | Tasks 4, 5 |
| Husky and CI final contract | Task 5 |
| fixture/TestKit/temporary Git verification | Tasks 1 through 5 |
| no production refactor/full formatter churn and final evidence | Task 6 |

The plan uses the same stable names throughout: `baseline.json`, `qelloConventionManifest`, `qelloBaselineSuppressions`, `validateJavaConventionBaseline`, `javaConventionSourceTest`, `javaConventionArchitectureTest`, `javaConventionStagedCheck`, `javaConventionCheck`, and `QELLO-JAVA-*`. Runtime baseline entries are deliberately audited from the current source; the plan gives the exact admission command and schema rather than inventing target/hash data.

## Execution Handoff

This plan has six independently reviewable tasks. Execute in order. Keep Task 6 after independent verification of every code, test, hook and CI task. Before each commit, show named paths and wait for the repository’s separate human commit approval.
