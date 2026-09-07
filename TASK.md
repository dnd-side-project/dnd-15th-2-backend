# GitHub Issue #204 Task Contract

> Generated at: `2026-09-07T09:32:32+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[W2] Answer moderation production wiring과 주기 실행`
- GitHub Issue: `#204`
- Branch: `feat/gh-204-answer-moderation-wiring`
- Base branch: `main`
- Test plan path: `docs/test-plans/gh-204-TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING.md`
- Test plan status: `Approved` (2026-09-07, "작업 진행" 지시로 승인)

## Objective

- Answer moderation intake 이후 실행 worker가 Spring bean으로 배선되지 않아
  실제 판정 pipeline이 동작하지 않는다. `#182` Core worker scheduling의 공통
  scheduling·identity·metrics 기반을 사용해 execution·deadline·verdict 전체
  흐름을 production gate 뒤에서 원자적으로 활성화한다.

## Scope

Included:

- answer 전용 `ModerationPipelineService` 구성
- answer 전용 provider client와 executor 구성
- `AnswerModerationExecutionWorker` Spring bean 등록
- retry gate, pipeline timeout, backoff와 batch·lease 설정
- 다음 worker의 scheduler adapter 등록
  - `AnswerModerationExecutionWorker`
  - `AnswerModerationDeadlineWorker`
  - `AnswerModerationVerdictWorker`
- `qello.filtering.production.enabled`와 기존 production gate 연동
- `#182`의 instance identity와 worker metrics 재사용
- worker 실행 실패·retry·deadline·verdict 결과 지표 기록

## Explicit exclusions

- `SlackNotifier` 구현과 Slack dispatch (`#205` 범위)
- nickname moderation 동작 변경
- moderation 알고리즘과 category threshold 재설계
- 실제 credential 생성·저장, 인프라 apply와 배포
- production 실제 활성화(gate on)는 DPA, data residency, retention,
  content-safety와 secret-handling 승인 이후로 별도 분리한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·설계·구현 계획 통합 | Orchestrator | Human partner |
| moderation pipeline·provider client·worker bean 배선 구현 | Execution agent | Independent verifier |
| gate·fail-closed·lease 계약 시나리오 구현 | Test executor | Independent verifier |
| 전체 diff·production gate·secret 비노출 검증 | Independent verifier | Human partner |

구현자는 승인된 구현 계획에 포함된 파일만 수정한다. 검증자는 테스트를
통과시키기 위해 production source나 테스트를 수정하지 않는다.

## Existing user-owned changes

- `./harness start` 실행 시점(브랜치 생성 직전)의 `git status --short`는
  clean이었다. 이 브랜치에서 생성·수정한 변경은 이 `TASK.md`뿐이다.
- 선행 관계 `#182` Core worker scheduling은 `main`에 머지되어 있다
  (`ae3d371` 등 `#182` 커밋이 `main`의 ancestor임을 확인함).

## Validation

```bash
./gradlew test
./gradlew spotlessCheck
python ./scripts/harness.py check
python ./scripts/harness.py pr-ready --project-tests
git diff --check
./gradlew integrationTest --tests '*AnswerModeration*' --tests '*CoreWorkerScheduling*'
```

이 환경의 `python3`가 Windows Store 앱 실행 별칭으로 깨져 있어(`python`은
정상) `./harness`·`./gradlew checkstyleMain`을 직접 호출하면 실패한다.
`python ./scripts/harness.py ...`로 우회했고, 세션 로컬 PATH에 실제
`python.exe`를 가리키는 `python3` shim을 앞에 추가해 `python ./scripts/harness.py check`까지는 통과시켰다. 다만 Gradle 데몬이 여는 하위 프로세스는 이
shim을 인식하지 못해 `./gradlew checkstyleMain`(→ `validateJavaConventionBaseline`)은
여전히 실패한다 — 저장소 코드가 아니라 이 Windows 세션의 App 실행 별칭 문제이며,
`origin/main`에서도 동일하게 재현됨을 확인했다(코드 변경과 무관).

`build.gradle`의 `spotless { ratchetFrom 'origin/main' }` 설정 때문에, 이번에
건드린 6개 기존 파일은 `origin/main`과 한 글자라도 달라지는 순간 파일 전체가
Eclipse formatter(`config/spotless/eclipse-java-formatter.xml`) 기준으로 다시
포맷된다. `WorkerSchedulingConfigurationTest.java` 등의 diff가 실제 논리
변경보다 훨씬 커 보이는 이유이며, `./gradlew spotlessApply`가 만든 결과를
그대로 받아들였다(수동으로 되돌리지 않음) — 프로젝트가 의도한 ratchet 방식의
정상 동작이다.

## Completion criteria

- [x] filtering production gate가 꺼져 있으면 moderation scheduled task와
      외부 provider 호출이 실행되지 않는다. (`AnswerModerationExecutionConfigTest`
      UNIT-001, `WorkerSchedulingConfigurationTest` UNIT-008로 unit 레벨 검증;
      실제 DB로 실행되지 않음까지 보는 end-to-end 확인은 Docker 미가동으로 BLOCKED)
- [x] gate가 켜졌지만 승인 근거나 필수 credential이 누락되면 fail-closed로
      기동에 실패한다. (`AnswerModerationExecutionConfigTest` UNIT-003, UNIT-004)
- [ ] execution → verdict와 deadline → verdict 흐름이 설정된 주기로
      동작한다. BLOCKED — Docker 데몬이 이 세션에서 실행되지 않아 Testcontainers
      기반 통합 테스트(`AnswerModeration*IntegrationTest`,
      `CoreWorkerSchedulingIntegrationTest`)를 실행하지 못했다. worker 내부
      전이 로직 자체는 기존 `AnswerModerationExecutionWorkerTest` 등 단위
      테스트가 이미 덮는다.
- [ ] 두 instance가 같은 outbox 행을 동시에 처리하지 않고 stale lease가
      차단된다. 신규 동시성 로직을 추가하지 않았고 `#182`가 검증한
      `claimDue` 원자성을 그대로 재사용한다 — 이 이슈에서 별도 재검증은
      수행하지 않았다(테스트 계획 §7 명시).
- [x] 외부 provider timeout·rate limit·오류가 기존 retry 정책대로
      처리된다. (배선만 추가했고 `AnswerModerationRetryPolicy`/worker 내부
      로직은 변경하지 않음 — 기존 단위 테스트가 계속 통과함으로 회귀 없음 확인)
- [x] deadline과 verdict만 부분적으로 활성화되는 구성이 허용되지 않는다.
      (`WorkerSchedulingProperties.AnswerModerationSettings`이 execution·
      deadline·verdict 세 worker를 개별 enabled 없이 하나의 flag로만 묶어
      부분 활성화를 설정 자체로 표현할 수 없게 했다. `WorkerSchedulingPropertiesTest`
      UNIT-006, `WorkerSchedulingConfigurationTest` INT-005로 검증)
- [x] 원문·사용자 식별자·credential이 metric tag, 로그와 오류에 포함되지
      않는다. (신규 `WorkerName` 3종은 기존 `WorkerMetrics.recordOutcome`을
      그대로 재사용해 `worker`/`outcome` enum tag만 남긴다 — `WorkerMetricsTest`
      UNIT-014의 일반 계약이 그대로 적용됨. fail-closed 예외 메시지에도 실제
      credential 값을 넣지 않음)
- [x] `SlackNotifier`, nickname moderation, moderation 알고리즘/threshold를
      변경하지 않는다. (`git diff --name-only`로 확인 — 변경 파일 목록 참고)
- [ ] 저장소 필수 검증이 통과하거나 최종 상태를 정확히 `FAIL`/`BLOCKED`로
      기록한다. → 아래 최종 검증 계약 참고. 상태: 부분 `BLOCKED`
      (Docker 미가동, Windows python3 별칭 문제 — 둘 다 코드 결함이 아님).

## Final verification contract (2026-09-07 세션)

```text
status: BLOCKED (unit 레벨은 PASS, 통합 레벨 일부 미실행)
issue_number: 204
task_id: (TASK.md 상단 Work gate 참고, 별도 Task ID 미부여)
design_id: 없음 (인프라 설계 게이트 대상 아님, 기능 구현)
changed_files: TASK.md; docs/test-plans/gh-204-TEST-PLAN-GH-204-ANSWER-MODERATION-PRODUCTION-WIRING.md; src/main/java/com/dnd/qello/filtering/config/AnswerModerationExecutionConfig.java; src/main/java/com/dnd/qello/scheduling/adapter/AnswerModerationExecutionScheduledAdapter.java; src/main/java/com/dnd/qello/scheduling/adapter/AnswerModerationDeadlineScheduledAdapter.java; src/main/java/com/dnd/qello/scheduling/adapter/AnswerModerationVerdictScheduledAdapter.java; src/main/java/com/dnd/qello/scheduling/config/WorkerSchedulingProperties.java; src/main/java/com/dnd/qello/scheduling/observability/WorkerMetrics.java; src/test/java/com/dnd/qello/filtering/config/AnswerModerationExecutionConfigTest.java; src/test/java/com/dnd/qello/scheduling/WorkerSchedulingConfigurationTest.java; src/test/java/com/dnd/qello/scheduling/config/WorkerSchedulingPropertiesTest.java; src/test/java/com/dnd/qello/scheduling/adapter/CoreWorkerScheduledAdapterTest.java; src/test/java/com/dnd/qello/scheduling/adapter/PushDeliveryDispatchScheduledAdapterTest.java
executed_checks: ./gradlew compileJava; ./gradlew compileTestJava; ./gradlew compileIntegrationTestJava; ./gradlew test (전체); ./gradlew spotlessCheck; python ./scripts/harness.py check; python ./scripts/harness.py pr-ready --project-tests (harness 부분만 통과, gradle check 하위 task 실패); git stash를 이용한 clean main 대비 실패 목록 비교
passed_checks: 위 executed_checks 중 ./gradlew test(1064개, 신규 8개 전부 PASS, 기존과 동일한 17개 사전 존재 실패 제외), ./gradlew spotlessCheck, python ./scripts/harness.py check, compile 3종
failed_checks: 없음 (신규 코드로 인한 실패 없음)
blocked_checks: ./gradlew integrationTest (Docker 데몬 미가동, Testcontainers 시작 불가); ./gradlew checkstyleMain 및 ./gradlew check 전체, ./harness 직접 호출 (이 Windows 세션의 python3 App 실행 별칭 문제 — origin/main에서도 동일 재현 확인, 코드와 무관)
assumptions: RetryGateConfig·AnswerModerationRetryPolicy·ManualReviewPriorityPolicy의 운영 수치는 #108/#110에서 "미결정"으로 명시된 값이라 기본값을 두지 않고 필수 property로 만들었다 — 실제 값은 이 이슈 범위 밖의 책임자 승인 후 배포 환경에서 주입한다.
risks: Docker 없이 세션을 마쳐 execution→verdict end-to-end 흐름과 두 instance 동시 클레임 회귀를 이 세션에서 직접 실행 확인하지 못했다. Docker Desktop을 켜고 `./gradlew integrationTest --tests '*AnswerModeration*'`을 재실행해 확인하는 것을 권장한다.
required_human_decisions: (1) Docker 가동 후 통합 테스트 재실행 여부, (2) retry-gate·retry-policy·manual-review-priority 운영 수치 확정과 배포 주입 방법, (3) PR 생성 시점(다른 병합 대기 이슈 없음 확인됨)
```
