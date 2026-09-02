# Test Plan: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET

> Created at: `2026-09-02T17:33:00+09:00`
> GitHub Issue: `#210`
> Status: Approved

## 1. Objective

PR #209 이후 ArchUnit TX/injection 규칙이 production `@Service`에도 적용되는지
검증한다. 미수정 legacy는 build를 막지 않고, 새로 추가되거나 `origin/main` 대비
변경된 production Service는 정확한 rule ID로 실패해야 한다. 기존 위반을
`baseline.json`에 숨기거나 production 본문을 이 작업에서 고치지 않는다.

가장 큰 실패 위험은 전체 production scan을 켜 기존 Service 수십 개가 CI를
막는 것, 기존 위반을 새 `LEGACY`로 등록하는 것, fixture 규칙과 production
규칙이 갈라지는 것, staged manifest와 `origin/main` diff가 다른 대상을 고르는
것, `origin/main` 누락을 skip으로 처리하는 것이다.

## 2. Scope

### Included

- production `@Service` 읽기 전용 감사 (`QELLO-JAVA-TX-001`·`TX-002`·`TX-003`·
  `QELLO-JAVA-INJECTION-001`)
- 감사 inventory가 baseline 51건과 분리되는지
- fixture와 production이 공유 ArchCondition을 사용하는지
- setter `@Autowired`를 `QELLO-JAVA-INJECTION-001`로 거절하는지
- constructor `@Autowired`는 이번 규칙 승격 대상이 아닌지
- `origin/main` changed-file ratchet과 staged manifest ratchet
- 새 production Service 파일은 즉시 강제되는지
- 미수정 violating Service는 `javaConventionCheck`를 막지 않는지
- 변경된 violating Service는 정확한 rule ID로 실패하는지
- 변경된 Service가 규칙을 모두 만족하면 통과하는지
- 새 `LEGACY` 추가·hash-only 갱신 금지가 유지되는지
- 기존 fixture ArchUnit·baseline·Checkstyle 회귀
- production source, schema, migration, API 무변경

### Excluded

- production Service 생성자·트랜잭션 본문 리팩터링
- Inbox isolation과 feed seam 분리 동작 검증
- baseline 51건 삭제
- 전체 production scan 전환
- 실제 DB transaction commit/rollback semantics
- 실제 GitHub Actions dispatch, Project API, branch protection 변경
- constructor `@Autowired`를 injection 위반으로 승격
- 배포·인프라·프로덕션 변경
- 실제 credential, `.env`, token, 계정·서버 식별자

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #210 | production 감사 inventory, changed-file ratchet, 미수정 legacy 통과 |
| GitHub Issue #210 | 새 Service·수정된 Service 위반은 정확한 rule ID로 실패 |
| GitHub Issue #210 | 새 `LEGACY`·hash-only 갱신 금지, production 본문 무변경 |
| `APP-DESIGN-GH-210-001` | DEC-210-001 production 실제 스캔 |
| `APP-DESIGN-GH-210-001` | DEC-210-002 기존 위반을 baseline에 넣지 않음 |
| `APP-DESIGN-GH-210-001` | DEC-210-003 origin/main·staged ratchet |
| `APP-DESIGN-GH-210-001` | DEC-210-005 공유 `ProductionConventionRules` |
| `APP-DESIGN-GH-210-001` | DEC-210-006 setter injection 포함, constructor `@Autowired` 비승격 |
| `APP-DESIGN-GH-210-001` | DEC-210-007 inventory는 gate가 아님 |
| `docs/harness/JAVA_CONVENTIONS.md` | field/setter injection 금지, class read-only 기본, private TX·self-invocation 금지 |
| `JavaConventionArchitectureTest` | 현재는 fixture-only import이며 production package를 스캔하지 않음 |
| `build.gradle` | `javaConventionArchitectureTest`가 fixture+ratchet 테스트를 실행해야 함 |
| `AGENTS.md` §3 | JUnit 5, `@DisplayName`, ISO 8601 class header, unit/integration 분리 |
| `TASK.md` | 테스트·구현 계획 승인 전 production/tooling 구현 금지 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 전체 production scan으로 미수정 Service가 CI 실패 | 모든 후속 PR 차단 | 높음 | P0 | untouched violating fixture가 ratchet에서 제외되고 실제 repo `javaConventionCheck` 통과 |
| 기존 TX 위반을 `LEGACY`로 등록 | baseline 영구화, #208 계약 우회 | 중간 | P0 | audit 전후 `baseline.json` entry 집합 불변, 새 LEGACY fixture 실패 |
| fixture rule과 production rule 복사 분기 | 한쪽만 고친 채 통과 | 중간 | P0 | 공유 rules 클래스 참조와 동일 rule ID |
| staged 대상과 origin/main 대상 불일치 | hook 통과·CI 실패 또는 반대 | 중간 | P0 | 같은 경로 집합 입력에서 selector 결과 동일 |
| `origin/main` 누락을 skip | CI가 production 규칙을 안 봄 | 중간 | P0 | missing-ref가 configuration failure |
| 새 Service 파일을 ratchet에서 빠뜨림 | 신규 위반 유입 | 중간 | P0 | origin/main에 없는 새 파일은 대상에 포함 |
| 변경된 Service가 위반을 남긴 채 통과 | Wave 0 목적 실패 | 높음 | P0 | 수정+위반 fixture가 해당 rule ID로 실패 |
| 변경된 Service가 규칙을 지켰는데 실패 | 정당한 후속 작업 차단 | 중간 | P0 | 수정+compliant fixture 통과 |
| setter injection을 계속 놓침 | 문서 계약과 구현 불일치 | 중간 | P0 | setter fixture가 `QELLO-JAVA-INJECTION-001` |
| constructor `@Autowired`를 갑자기 차단 | `PushDeviceService` 등 오탐 | 중간 | P0 | constructor `@Autowired` fixture 통과, production `PushDeviceService`가 INJECTION-001 inventory에 없음 |
| scanner가 fixture만 읽고 production canary를 못 찾음 | 가짜 감사 | 중간 | P0 | `DeviceTokenService` class write가 audit inventory에 존재 |
| Gradle filter가 새 테스트 클래스를 실행하지 않음 | ratchet이 `javaConventionCheck` 밖 | 높음 | P0 | task가 audit/ratchet 테스트를 포함 |
| tooling이 production source/schema를 변경 | Issue 범위 이탈 | 낮음 | P0 | path diff gate |
| 실제 DB transaction을 열어 데이터 변경 | 비범위 부작용 | 낮음 | P0 | ArchUnit bytecode만, Testcontainers 신규 fixture 없음 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-001 | production `com.dnd.qello` bytecode | 감사 scanner가 `@Service`를 수집 | fixture package가 아니라 production type이 포함되고 목록이 비어 있지 않다 | P0 | Audit Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-002 | 현재 `DeviceTokenService` | 전체 production에 `QELLO-JAVA-TX-001` 평가 | class write 대상으로 `DeviceTokenService`가 inventory에 있다 | P0 | Audit Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-003 | production `@Service` | missing class `readOnly`와 class write를 구분 | 두 집합이 겹치지 않고 missing-readOnly 건수가 0보다 크다 | P0 | Audit Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-004 | 감사 실행 전 `baseline.json` entry ID 집합 | 감사를 실행 | baseline entry ID·classification이 변하지 않는다 | P0 | Audit Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-005 | field `@Autowired` fixture | injection rule | `QELLO-JAVA-INJECTION-001`과 field target으로 실패 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-006 | setter `@Autowired` fixture | injection rule | `QELLO-JAVA-INJECTION-001`과 method target으로 실패 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-007 | constructor `@Autowired` fixture | injection rule | 실패하지 않는다 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-008 | private `@Transactional` fixture | TX-002 rule | `QELLO-JAVA-TX-002`로 실패하고 public method는 통과 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-009 | self-invocation fixture | TX-003 rule | origin·target method를 포함해 `QELLO-JAVA-TX-003`으로 실패 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-010 | class `readOnly=true` fixture | TX-001 rule | 통과 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-011 | class write fixture | TX-001 rule | `QELLO-JAVA-TX-001`로 실패 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-012 | fixture 테스트와 production ratchet | 사용하는 `ArchRule` 구현 | 같은 `ProductionConventionRules` 메서드를 호출한다 | P0 | Fixture Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-013 | `origin/main`과 동일한 violating production-like 경로 | changed-type selector | 해당 type이 ratchet 대상에서 빠진다 | P0 | Selector Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-014 | `origin/main` 대비 내용이 바뀐 violating 경로 | changed-type selector | 해당 type이 대상에 들어간다 | P0 | Selector Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-015 | `origin/main`에 없는 새 Service 경로 | changed-type selector | 해당 type이 대상에 들어간다 | P0 | Selector Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-016 | 같은 Java 경로 목록 | staged manifest selector와 origin/main selector | 대상 type 집합이 같다 | P0 | Selector Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-017 | `origin/main` ref가 없는 저장소 | selector 실행 | convention skip이 아니라 configuration failure 메시지를 남긴다 | P0 | Selector Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-018 | production `PushDeviceService` | injection 감사 | `QELLO-JAVA-INJECTION-001` inventory에 이 class가 없다 | P0 | Audit Executor |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-019 | 기존 baseline lifecycle fixture | 새 `LEGACY` 추가와 hash-only 갱신 | 계속 실패한다 | P0 | Regression Executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-001 | ratchet, `@TempDir` Git repo | violating Service가 `origin/main`과 HEAD에서 동일 | production ratchet 평가 | 대상 0건, 규칙 실패 없음 | `@TempDir` 삭제 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-002 | ratchet, temp Git repo | 같은 Service를 수정하고 class write `@Transactional`을 남김 | production ratchet 평가 | `QELLO-JAVA-TX-001`과 class 이름으로 실패 | `@TempDir` 삭제 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-003 | ratchet, temp Git repo | 같은 Service를 수정하고 class `readOnly=true`, constructor injection, public TX method만 남김 | production ratchet 평가 | 통과 | `@TempDir` 삭제 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-004 | ratchet, temp Git repo | `origin/main`에 없는 새 `@Service`가 class write TX를 가짐 | production ratchet 평가 | `QELLO-JAVA-TX-001`로 실패 | `@TempDir` 삭제 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-005 | staged selector | Java A는 staged compliant, Java B는 unstaged violating | staged ratchet | A만 대상이고 B rule ID가 결과에 없다 | `@TempDir` 삭제 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-006 | `javaConventionArchitectureTest` Gradle task | 현재 테스트 클래스 이름 | `./gradlew javaConventionArchitectureTest` | fixture 테스트와 production audit/ratchet 테스트가 모두 실행된다 | Gradle report 정리 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-007 | 실제 Qello 저장소 | production Service 무변경 tooling branch | `./gradlew javaConventionCheck` | 미수정 legacy 때문에 실패하지 않고 기존 fixture 계약도 통과 | Gradle report 정리 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-008 | 실제 Qello source | tooling/test/docs만 변경 | `git diff --name-only origin/main` | `src/main/java`, `src/main/resources/db/migration`, API 계약 파일이 없다 | 없음 |
| TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-009 | 기존 unit/integration suite | production behavior 무변경 | `./gradlew test` focused convention과 승인 후 `./gradlew check` | 기존 API·DB·transaction 회귀 없음 | suite lifecycle |

## 7. Cross-cutting scenarios

### Database and transactions

- 새 schema, migration, repository query, Testcontainers fixture를 추가하지 않는다.
- ArchUnit은 bytecode annotation과 method call만 본다. 실제 PostgreSQL
  transaction을 열지 않는다.
- `DeviceTokenService` class write 탐지는 런타임 isolation 검증이 아니라
  annotation 존재 검증이다. 실제 token rotation 동시성은 이 계획 밖이다.
- production `src/main/java`와 `src/main/resources/db/migration` 변경이 있으면
  범위 위반으로 FAIL한다.

### Concurrency and idempotency

- changed-file selector는 Git index와 working tree를 수정하지 않는다.
- 같은 diff 입력에 반복 실행하면 대상 type 집합이 같아야 한다.
- staged manifest와 origin/main selector는 공유 mutable 전역 상태에 의존하지 않는다.
- 감사 inventory는 실행마다 같은 production bytecode에 결정적이다.

### External APIs

- GitHub API, workflow dispatch, Project mutation을 테스트하지 않는다.
- Gradle은 공개 repository에서 이미 pin된 ArchUnit/JUnit을 쓰며 새 credential을
  사용하지 않는다.
- fixture와 temp Git repo는 애플리케이션 FCM, OAuth, S3, moderation client를
  생성하지 않는다.

### Failure recovery and reconciliation

- ratchet 실패 뒤 working tree와 Git index가 그대로여야 한다.
- 실패 메시지는 새 `LEGACY` 추가나 hash 갱신이 아니라 해당 Service 수정을 안내한다.
- `origin/main` 누락, 잘못된 manifest, Git 실행 실패는 expected rule violation으로
  위장하지 않고 configuration/environment failure다.
- temp Git repo와 `@TempDir`는 실패해도 실제 저장소 index를 건드리지 않는다.

## 8. Test data and isolation

- Fixtures: 기존 `src/test/java/com/dnd/qello/architecture/fixture/`에 setter·
  constructor `@Autowired` Service를 추가한다. production 감사는 실제
  `src/main/java` bytecode를 읽되 수정하지 않는다. ratchet 통합은 `@TempDir`
  최소 Git repo와 합성 `@Service` source만 사용한다.
- Database isolation: DB fixture를 만들지 않는다. 전체 existing integration
  suite가 기존 container lifecycle을 소유한다.
- Clock/randomness: Git 경로와 type 이름만 assertion한다. UUID를 기대값으로
  쓰지 않는다.
- External API doubles: Process/`@TempDir` Git만 사용한다.
- Cleanup: `@TempDir`와 Gradle report. 실제 repository hook, index, working
  tree를 test fixture로 바꾸지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Fixture Executor | `src/test/java/com/dnd/qello/architecture/ProductionConventionRules.java`, `src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureTest.java`, `src/test/java/com/dnd/qello/architecture/fixture/SetterInjectedService.java`, `src/test/java/com/dnd/qello/architecture/fixture/ConstructorAutowiredService.java` | UNIT-005~012 | 공유 rules, setter/constructor/field/TX fixture |
| 2 | Selector Executor | `src/test/java/com/dnd/qello/architecture/ChangedJavaTypes.java`, `src/test/java/com/dnd/qello/architecture/ChangedJavaTypesTest.java` | UNIT-013~017 | origin/main·staged·new file·missing-ref selector |
| 3 | Audit Executor | `src/test/java/com/dnd/qello/architecture/ProductionConventionAuditTest.java` | UNIT-001~004, UNIT-018 | production inventory, DeviceTokenService canary, baseline 불변, constructor `@Autowired` 비승격 |
| 4 | Ratchet Executor | `src/test/java/com/dnd/qello/architecture/ProductionConventionRatchetTest.java`, `build.gradle`의 `javaConventionArchitectureTest` filter | INT-001~006 | untouched/changed/new Service ratchet과 Gradle task 실행 범위 |
| 5 | Regression Executor | 기존 convention 테스트 파일은 수정하지 않음. `src/test/java/com/dnd/qello/convention/JavaConventionBaselineTest.java`를 읽기만 함 | UNIT-019, INT-007~009 | 기존 LEGACY 금지, 실제 repo `javaConventionCheck`, path diff, 회귀 suite |
| 6 | Report Owner | `docs/test-reports/gh-210-TEST-REPORT-GH-210-PRODUCTION-CONVENTION-RATCHET.md` | 전체 | 실행 결과와 잠재 문제 분석 |

각 executor는 표에 적힌 파일만 수정한다. production source와
`config/java-conventions/baseline.json`은 어떤 executor도 수정하지 않는다.
`ChangedJavaTypes`와 `ProductionConventionRules`는 앞선 executor가 만든 뒤에만
다음 executor가 사용한다. 같은 파일 변경이 필요하면 오케스트레이터가 순서를
직렬화하고 이 계획을 먼저 갱신한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] P1 미구현 시 이유, 영향과 후속 검증을 `BLOCKED`로 기록
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 신규 테스트 클래스 헤더에 정확한 ISO 8601 timestamp와 source scenario 기록
- [ ] fixture ArchUnit이 setter injection을 `QELLO-JAVA-INJECTION-001`로 실패
- [ ] production audit이 `DeviceTokenService` class write를 보고하고 baseline을 변경하지 않음
- [ ] 미수정 violating Service는 ratchet 통과, 변경된 violating Service는 해당 rule ID로 실패
- [ ] 새 Service 위반은 실패, 변경 후 규칙 충족은 통과
- [ ] staged와 origin/main selector가 같은 경로 집합에서 동일
- [ ] `origin/main` 누락은 configuration failure
- [ ] `./gradlew javaConventionArchitectureTest`가 새 테스트를 실행
- [ ] 실제 저장소 `./gradlew javaConventionCheck` 통과
- [ ] production source, migration, API 무변경
- [ ] `./harness test-run --id TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET` 실행
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` 통과
- [ ] application, DB, concurrency, transaction, external API, failure recovery 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성

## 11. Human approval

- Reviewer: human partner
- Decision: `APPROVED_FOR_IMPLEMENTATION_PLAN`
- Approved at: `2026-09-02T17:38:38+09:00`
