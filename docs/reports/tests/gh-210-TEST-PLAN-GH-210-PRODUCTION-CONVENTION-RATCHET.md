# Test Report: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET

> Created at: `2026-09-02T17:54:32+09:00`
> GitHub Issue: `#210`
> Branch: `chore/gh-210-production-convention-ratchet`
> Commit: `91fd66d` (HEAD of `origin/main`; implementation is uncommitted)

## 1. Executive summary

- Result: `PASS`
- Tested scope: shared ArchUnit rules, setter/constructor injection fixtures, production TX/injection audit, changed-file ratchet, `javaConventionCheck`, unit 1,032개와 integration 717개, harness/PR readiness
- Unverified scope: 실제 GitHub Actions 원격 실행, 실제 staged pre-commit index fixture
- Release recommendation: 로컬 구현 검증은 통과했다. 커밋은 별도 사람 승인 후 진행한다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Toolchain 21 (`build.gradle`); Gradle host JVM OpenJDK 25.0.3 |
| Spring Boot | 3.5.16 |
| Database | Docker Testcontainers PostgreSQL/PostGIS (existing integration suite; this Issue did not add DB fixtures) |
| Test runner | JUnit 5 |
| Gradle | 8.14.3 |
| ArchUnit | 1.4.2 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Architecture convention | PASS | 22 | `javaConventionArchitectureTest` | fixture 7, selector 5, audit 4, ratchet 6 |
| Unit | PASS | 1,032 | ~19s | `./gradlew test` via `./harness test-run` |
| Integration | PASS | 717 | Gradle UP-TO-DATE after unchanged production/integration sources | `./gradlew integrationTest` |
| Java convention | PASS | focused + aggregate | ~5s | `./gradlew javaConventionCheck` |
| Harness | PASS | n/a | under 1s | `./harness check` |
| PR readiness | PASS | `check` 14 tasks | ~27s | `./harness pr-ready --project-tests` |
| Path diff | PASS | n/a | n/a | `src/main/java`, migration, `baseline.json` 변경 없음 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~004, UNIT-018 | PASS | `ProductionConventionAuditTest` | production scan, DeviceTokenService class write canary, baseline 불변, PushDeviceService not INJECTION-001 |
| UNIT-005~012 | PASS | `JavaConventionArchitectureTest` | field/setter fail, constructor Autowired pass, TX-001/002/003 fixtures, shared `ProductionConventionRules` |
| UNIT-013~017 | PASS | `ChangedJavaTypesTest` | unchanged empty, modified/new included, staged=path list, missing origin/main is configuration failure |
| UNIT-019 | PASS | `JavaConventionBaselineTest#rejectsLegacyAdditionOrHashExtension` | origin/main 대비 LEGACY 추가·hash 변경은 `QELLO-JAVA-BASELINE-007`. 기존 006 assertion은 bootstrap 이후 unreachable이라 007로 정정 |
| INT-001~006 | PASS | `ProductionConventionRatchetTest`, `ChangedJavaTypesTest`, Gradle architecture task XML | empty set 통과, changed class write 실패, compliant 통과, unstaged 무시, task가 4개 테스트 클래스를 실행 |
| INT-007 | PASS | `ProductionConventionRatchetTest#currentRepositoryUntouchedProductionPasses` + `javaConventionCheck` | 현재 branch는 production Service를 수정하지 않아 ratchet 통과 |
| INT-008 | PASS | `git diff` / `git ls-files` | production Java, migration, baseline 없음 |
| INT-009 | PASS | `./harness test-run`, `./harness pr-ready --project-tests` | unit 1,032 · integration 717 · `check` 통과 |

## 5. Failures and diagnostics

처음 `JavaConventionBaselineTest#rejectsStaleLegacyHash`는 `QELLO-JAVA-BASELINE-006`을
기대했으나 origin/main에 이미 `JAVA-CONV-0003`이 있어 lifecycle가
`QELLO-JAVA-BASELINE-007`로 먼저 실패했다. validator는 document hash가 origin/main과
다를 때 007을 내고, 006은 origin/main과 문서 hash가 같을 때만 git blob을 검사한다.
standalone fake baseline으로는 006에 도달할 수 없다. 테스트를 007 거절로 맞추었고
실제 repository `validateJavaConventionBaseline`은 통과한다.

ArchUnit은 빈 ratchet 집합에서 `failOnEmptyShould`가 기본 true라 미수정 분기를
막았다. `ProductionConventionRatchet`만 `allowEmptyShould(true)`를 사용한다.

## 6. Potential issues

### Application code

- constructor `@Autowired`(`PushDeviceService`)는 이번 규칙 밖이다. notification
  package Project draft에서 검토한다.
- `DeviceTokenService` class write canary는 auth package 정리 시 테스트 수정이 필요하다.

### Infrastructure and resource limits

- 원격 GitHub Actions `java-conventions` job은 이 환경에서 실행하지 않았다.

### Database and migrations

- schema, migration, query를 변경하지 않았다. 새 Testcontainers fixture를 추가하지 않았다.

### Concurrency and idempotency

- `ChangedJavaTypes`는 Git을 읽기만 한다. 같은 diff 입력은 결정적이다.

### Transactions and event ordering

- ArchUnit은 annotation과 call graph만 본다. 실제 PostgreSQL snapshot/isolation은
  검증하지 않는다. Inbox `REPEATABLE_READ` 결함은 Wave 1A draft다.

### External APIs

- GitHub API, FCM, S3, moderation client를 호출하지 않았다.

### Failure recovery and reconciliation

- `origin/main` 누락은 TX rule ID가 아니라 configuration failure다.
- ratchet 실패는 새 `LEGACY`가 아니라 해당 Service 수정을 요구한다.

## 7. Regression and residual risk

production source와 `baseline.json`은 변경하지 않았다. 미수정 TX/injection 위반은
inventory와 Project draft로만 남는다. 전체 production scan 전환은 Wave 6 draft다.
후속 feed 리팩터링이 production Service를 수정하면 그 파일의 TX/injection 위반이
즉시 `javaConventionCheck`를 실패시킨다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-210-TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET.md`
- Design: `docs/superpowers/specs/2026-09-02-production-convention-ratchet-design.md`
- Implementation plan: `docs/superpowers/plans/2026-09-02-production-convention-ratchet.md`
- CI run: 로컬만 실행. 원격 Actions는 미실행
- Related ADR: `APP-DESIGN-GH-210-001`
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (Project draft: auth/notification, Wave 1A, Wave 6)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 미생성)
