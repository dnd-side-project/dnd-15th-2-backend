# Test Plan: TEST-PLAN-GH-208-JAVA-CONVENTION-GATES

> Created at: `2026-09-02T00:13:48+09:00`
> GitHub Issue: `#208`
> Status: Approved

## 1. Objective

개발자와 에이전트가 변경한 Java 코드만 빠르게 검사하면서 formatter, constructor
injection, transaction annotation, complexity와 baseline lifecycle 위반을 같은 rule
ID로 재현 가능하게 차단하는지 검증한다. 로컬 pre-commit을 우회하거나 실행 환경이
달라도 Gradle `check`와 GitHub Actions가 같은 계약을 최종 강제해야 한다.

가장 큰 실패 위험은 ratchet 오구성으로 기존 Java 전체를 다시 포맷하는 것, partial
staging에서 working tree와 index 중 한쪽만 검사해 잘못된 commit을 허용하는 것,
baseline이 wildcard suppression이나 hash 갱신으로 새 위반을 숨기는 것, 의미 있는
constructor·programmatic transaction을 오탐하는 것, 그리고 CI가 `origin/main`을
가져오지 않아 convention gate를 건너뛰는 것이다.

## 2. Scope

### Included

- Spotless Eclipse JDT profile의 탭 indentation, 120자 line length, import와 newline 계약
- `origin/main` ratchet의 changed-file 범위와 missing-ref failure
- JavaParser constructor source-contract, Checkstyle import, method length 50과 cyclomatic complexity 15 rule
- ArchUnit field injection, `private final` state, class read-only transaction,
  private/protected transaction method와 self-invocation rule
- `LEGACY`와 `JUSTIFIED_EXCEPTION` baseline schema, exact target, canonical Git blob
  hash와 lifecycle
- staged Java manifest, partial staging 차단과 working tree·Git index 불변
- `javaConventionStagedCheck`, `javaConventionCheck`와 Gradle `check` dependency
- Husky pre-commit 조건부 호출과 기존 hook contract 회귀
- GitHub Actions checkout, Java 21, `origin/main` fetch와 convention task 계약
- positive·negative fixture의 expected rule ID와 actionable failure message
- Java 21, Gradle 8.14.3과 고정 tool version의 실제 resolution
- 기존 production source, API, DB, transaction runtime과 전체 unit/integration 회귀

### Excluded

- production Service constructor, annotation과 method body 리팩터링
- 실제 package write transaction의 commit·rollback semantics 변경 검증
- Java source 717개 전체 formatter 적용
- DB schema, migration, query와 Testcontainers fixture 추가
- 실제 GitHub Actions workflow dispatch와 branch protection 설정 변경
- 실제 GitHub Project draft item 생성 API의 end-to-end 검증
- Gradle Plugin Portal 장애 자체와 외부 repository 가용성 보장
- 실제 credential, `.env`, token, 계정·서버 식별자
- 배포, 인프라와 production 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #208 | Spotless·Checkstyle·ArchUnit version 고정, staged·전체 Gradle task와 Husky·CI 연결 |
| GitHub Issue #208 | 기존 위반은 exact baseline으로 동결하고 새 legacy·근거 없는 exception·stale hash를 차단 |
| GitHub Issue #208 | field injection, 단순 명시적 constructor, class write transaction, private transaction과 self-invocation 차단 |
| GitHub Issue #208 | method length 50, cyclomatic complexity 15, formatter remediation과 partial staging 오류 제공 |
| `APP-DESIGN-GH-208-001` | Gradle single executable contract, Eclipse formatter ratchet, Checkstyle와 ArchUnit 책임 분리 |
| `APP-DESIGN-GH-208-001` | `LEGACY`/`JUSTIFIED_EXCEPTION`, canonical Git blob hash, exact target와 Project draft tracking reference |
| `APP-DESIGN-GH-208-001` | hook은 source/index를 자동 수정하지 않고 staged Java만 검사하며 CI는 같은 전체 gate 실행 |
| `scripts/run-hook.py` | staged index 기반 기존 secret·JUnit·workflow·Husky 검증을 보존하고 Java가 있을 때만 새 gate 호출 |
| `.github/workflows/harness-policy.yml` | 각 job은 독립 checkout과 Java 21을 준비하고 `./gradlew check`를 실행 |
| `AGENTS.md` §3 | JUnit 5, 모든 method `@DisplayName`, 정확한 ISO 8601 class header와 source scenario, unit/integration 분리 |
| `TASK.md` | production source, schema와 runtime 동작은 비범위이고 테스트·구현 계획 승인 전 구현 금지 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| formatter target이 전체 source로 확장됨 | 717개 file churn·history 오염 | 중간 | P0 | unchanged legacy fixture와 실제 repository source diff 0 |
| formatter가 같은 입력에서 비멱등 | commit마다 diff 반복·hook loop | 낮음~중간 | P0 | apply 2회째 diff 0 |
| partial staging에서 working tree만 검사 | 위반 index blob commit 또는 사용자 변경 덮어쓰기 | 높음 | P0 | staged/unstaged 양쪽 fixture, index·working hash 불변 |
| staged task가 unrelated unstaged Java도 검사 | 사용자 작업 차단·범위 외 수정 압박 | 중간 | P0 | staged manifest와 unrelated file isolation |
| baseline wildcard·unknown rule 허용 | 새 위반 전체 은폐 | 중간 | P0 | schema negative fixture와 exact rule ID |
| legacy hash 갱신만으로 위반 연장 | baseline 영구화 | 중간 | P0 | base/head comparison과 changed target failure |
| justified exception에 승인 근거가 없음 | transaction·constructor 오용 상시 허용 | 중간 | P0 | TASK decision·design reference 필수 fixture |
| constructor rule이 Lombok 정상 code를 거절하거나 명시적 단순 생성자를 놓침 | 오탐 또는 convention 누락 | 중간 | P0 | positive/negative source pair와 `QELLO-JAVA-CTOR-001` |
| transaction-aware 분류가 external I/O·TransactionTemplate 예외를 오탐 | 잘못된 긴 transaction 유도 | 중간 | P0 | repository, method transaction과 exact exception matrix |
| private transaction·self-invocation 탐지 누락 | annotation이 실행된다는 거짓 확신 | 중간 | P0 | ArchUnit negative fixture와 call-site line evidence |
| length·complexity boundary off-by-one | 허용 코드 실패 또는 초과 코드 통과 | 중간 | P1 | 50/51줄, 15/16 decision boundary pair |
| `origin/main` missing을 조용히 skip | CI convention 무검증 | 중간 | P0 | missing-ref fixture가 configuration failure로 종료 |
| convention task가 `check`에 연결되지 않음 | 선택 job 우회·로컬/CI 불일치 | 중간 | P0 | Gradle task graph와 controlled violation `check` failure |
| failure message가 file·rule·remediation을 숨김 | agent가 suppression으로 우회할 가능성 | 중간 | P1 | output contract assertion |
| Java 21·Gradle 8.14.3과 tool version 비호환 | 모든 build·push 차단 | 중간 | P0 | clean dependency resolution과 focused/full task pass |
| hook이 temporary manifest를 남기거나 동시 실행에서 충돌 | 다음 commit 오염·flaky failure | 낮음~중간 | P1 | 반복·동시 read-only 실행과 temp cleanup |
| tooling 작업이 production source/schema를 변경 | Issue 범위 이탈·업무 회귀 | 낮음 | P0 | path diff gate와 전체 `check` |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-001 | exact class/member, known rule, canonical hash와 required metadata를 가진 baseline | schema validation | entry가 보존되고 violation 0 | P0 | Baseline Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-002 | duplicate ID, unknown field/rule, malformed hash/date | schema validation | 각 입력이 baseline rule ID와 field를 포함해 실패 | P0 | Baseline Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-003 | package wildcard, glob, 존재하지 않는 class/member target | target validation | exact target가 아닌 entry를 모두 거절 | P0 | Baseline Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-004 | `LEGACY` entry | tracking reference 또는 review date가 없음 | 누락 field를 특정해 실패 | P0 | Baseline Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-005 | `JUSTIFIED_EXCEPTION` entry | design reference 또는 `TASK.md` approved decision ID가 없음 | 근거 없는 exception을 거절 | P0 | Baseline Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-006 | 같은 canonical Git blob과 한 byte 변경 blob | SHA-256 검증 | unchanged는 통과하고 changed는 stale hash로 실패 | P0 | Baseline Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-007 | baseline이 없는 Issue #208 bootstrap과 baseline이 이미 있는 후속 branch | 최초 inventory, legacy 삭제·추가·hash-only 갱신을 각각 비교 | #208 bootstrap만 추가를 허용하고 이후에는 삭제만 통과하며 추가·연장은 실패 | P0 | Baseline Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-008 | `@RequiredArgsConstructor`와 private final dependency를 가진 Service | constructor injection rule | violation 0 | P0 | Architecture Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-009 | field/setter injection 또는 non-final injected state | constructor injection rule | 정확한 class/field를 포함해 실패 | P0 | Architecture Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-010 | repository dependency와 class `readOnly=true` Service | transaction-aware rule | 정상 read/write annotation shape로 통과 | P0 | Architecture Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-011 | class write `@Transactional` 또는 transaction-aware Service의 class annotation 누락 | class transaction rule | class target와 기대 read-only를 포함해 실패 | P0 | Architecture Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-012 | private·protected transaction method와 public transaction method | method visibility rule | 비공개 method만 실패하고 public method는 통과 | P0 | Architecture Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-013 | 같은 class가 자신의 transaction method를 호출 | self-invocation rule | origin·target method를 포함해 실패 | P0 | Architecture Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-014 | `TransactionTemplate`, `NOT_SUPPORTED`, external I/O Service의 exact exception | exception rule | 승인 target만 통과하고 인접 class에는 전파되지 않음 | P0 | Architecture Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-015 | Lombok 정상 Service와 단순 explicit constructor Service | JavaParser constructor source-contract | 정상은 통과, explicit은 `QELLO-JAVA-CTOR-001`로 실패 | P0 | Static Analysis Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-016 | comment/blank 제외 50줄과 51줄 method | Checkstyle size rule | 50은 통과하고 51만 `QELLO-JAVA-SIZE-001`로 실패 | P1 | Static Analysis Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-017 | complexity 15와 16 method | Checkstyle complexity rule | 15는 통과하고 16만 `QELLO-JAVA-CPLX-001`로 실패 | P1 | Static Analysis Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-018 | wildcard import와 inline suppression/formatter bypass | source lint | 각각 IMPORT/BYPASS rule ID로 실패 | P0 | Static Analysis Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-019 | expected convention violation | message rendering | rule ID, file, target/line, reason, remediation와 exception 절차가 존재 | P1 | Static Analysis Executor |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-020 | Java/non-Java staged file 목록 | pre-commit dispatch decision | Java가 있을 때만 staged Gradle task를 한 번 호출 | P0 | Hook Executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-001 | Spotless Eclipse profile, Gradle TestKit fixture | 일부러 space/import/line-wrap 위반 Java | `spotlessApply` 2회와 `spotlessCheck` | 첫 apply가 승인 포맷을 만들고 두 번째 diff 0, check 통과 | `@TempDir` fixture 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-002 | Spotless ratchet, temporary Git repo | unchanged legacy, changed Java, new Java와 `origin/main` ref | `spotlessJavaCheck` | changed/new만 검사하고 unchanged legacy 위반은 이번 bootstrap baseline에서 차단하지 않음 | temp repo 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-003 | staged manifest, Checkstyle/Spotless | Java A는 staged, Java B는 unstaged 위반 | `javaConventionStagedCheck` | A만 검사하고 B는 결과에 나타나지 않음 | temp repo 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-004 | pre-commit runner, temporary Git index | 같은 Java의 compliant working tree와 violating staged blob, 반대 조합 | pre-commit | 두 조합 모두 partial staging으로 실패하고 index·working blob hash 불변 | temp repo와 manifest 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-005 | pre-commit runner | non-Java만 staged | pre-commit | 기존 검증은 실행하되 Java Gradle task는 호출하지 않음 | temp repo 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-006 | baseline validator, temporary base/head refs | unchanged legacy, changed target, entry 삭제와 hash-only 갱신 branch | full validation | unchanged·삭제는 통과, changed·hash 연장은 stable baseline rule ID로 실패 | temp repo 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-007 | `javaConventionCheck` aggregate | formatter, Checkstyle, ArchUnit, baseline 위반을 하나씩 가진 fixture | task별 실행 | 각 위반은 소유 task와 rule ID로 실패하고 compliant fixture는 통과 | TestKit project 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-008 | Gradle task graph | compliant fixture와 한 controlled convention violation | `check` 실행 | compliant는 통과하고 violation이 있으면 `check`도 실패 | TestKit project 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-009 | Spotless ratchet | `origin/main` ref 없는 shallow-like fixture | `javaConventionCheck` | formatter skip 없이 missing ref remediation을 포함해 configuration failure | temp repo 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-010 | `harness-policy.yml`, workflow validator | PR/push workflow source | contract test와 `validate-workflows.py` | convention job은 checkout history, Java 21, Gradle task를 가지며 test job에도 checkout 존재 | process 종료 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-011 | 실제 Qello source와 bootstrap baseline | production source 수정 없이 tooling branch | `javaConventionCheck` | current legacy는 baseline으로 통과하고 새 문서·tooling 범위만 diff에 존재 | Gradle report 정리 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-012 | hook runner와 temporary manifest | 같은 staged fixture에 순차 2회 및 동시 2회 검사 | staged check 반복 | 결과가 같고 index/source mutation과 repository temp residue가 없음 | executor 종료·temp 삭제 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-013 | Java 21, Gradle 8.14.3, pinned tools | clean Gradle cache 또는 CI equivalent | dependency resolution과 focused task | 동적 version 없이 resolve되고 classloading/plugin incompatibility 없음 | Gradle cache는 환경 소유 |
| TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-INT-014 | 전체 unit/integration suite | production behavior 변경 없음 | `./gradlew check`와 harness final checks | 기존 API, DB, transaction, external API 회귀가 없고 source/schema diff gate 통과 | suite lifecycle |

## 7. Cross-cutting scenarios

### Database and transactions

- 새 schema, migration, repository query와 Testcontainers fixture를 추가하지 않는다.
- transaction annotation fixture는 ArchUnit bytecode 분석만 수행하고 실제 DB transaction을 열지 않는다.
- production `src/main/java`, `src/main/resources/db/migration` 변경이 있으면 범위 위반으로 FAIL한다.
- 기존 전체 integration suite를 실행해 tooling dependency가 Spring context와 transaction runtime을
  깨뜨리지 않았다는 회귀 증거만 수집한다.

### Concurrency and idempotency

- staged convention check는 source와 Git index를 수정하지 않는 read-only 동작이어야 한다.
- 같은 fixture에 반복 실행해 output과 exit code가 안정적인지 검증한다.
- 두 process가 동시에 실행돼도 고정 temporary path나 shared manifest를 덮어쓰지 않아야 한다.
- baseline validation은 같은 base/head blob에 결정적 결과를 반환해야 한다.

### External APIs

- 실제 GitHub API, workflow dispatch, branch protection, Project mutation을 테스트하지 않는다.
- GitHub Actions YAML과 repository validator를 정적으로 검증한다.
- Gradle tool dependency는 공개 repository에서 resolve하지만 credential을 사용하지 않고 version을 고정한다.
- fixture test는 실제 애플리케이션 external API client, FCM, OAuth, S3와 moderation provider를 호출하지 않는다.

### Failure recovery and reconciliation

- formatter·lint failure 뒤 working tree와 Git index가 그대로여야 하며 사용자가 명시적으로
  `spotlessApply`를 실행한 뒤 재검증할 수 있어야 한다.
- invalid baseline, missing `origin/main`, tool classloading과 dependency resolution failure를
  code violation으로 위장하지 않고 configuration/environment failure로 구분한다.
- 실패·interruption 뒤 temporary manifest와 TestKit project가 정리되는지 검증한다.
- hook을 우회한 commit도 CI `javaConventionCheck`와 Gradle `check`에서 같은 위반으로 실패해야 한다.

## 8. Test data and isolation

- Fixtures: `src/test/resources/java-conventions/`에 baseline JSON, Java source와 expected
  rule ID를 rule별 작은 fixture로 둔다. integration은 `@TempDir`에 최소 Gradle/Git
  project를 복사하고 `refs/remotes/origin/main`을 명시적으로 만든다.
- Database isolation: DB fixture를 만들지 않는다. 전체 existing integration suite가 기존
  container lifecycle을 소유한다.
- Clock/randomness: baseline review date 검증은 주입한 `LocalDate` fixture를 사용한다.
  UUID나 random path를 assertion 값으로 사용하지 않고 `@TempDir` path만 격리에 사용한다.
- External API doubles: Process runner와 Gradle TestKit을 사용하며 GitHub/FCM/S3/OAuth client는
  생성하지 않는다. workflow는 YAML/validator contract로만 검사한다.
- Cleanup: temporary Git repo, custom index, manifest와 executor를 `finally`/`@TempDir` lifecycle로
  정리한다. test가 실패해도 실제 repository index, hooksPath와 working tree를 바꾸지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Baseline Executor | `src/test/java/com/dnd/qello/convention/baseline/JavaConventionBaselineTest.java`, `src/test/resources/java-conventions/baseline/**` | UNIT-001~007 | baseline schema·target·hash·lifecycle unit tests |
| 2 | Architecture Executor | `src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureRuleTest.java`, `src/test/java/com/dnd/qello/architecture/fixture/**` | UNIT-008~014 | constructor injection과 transaction ArchUnit rule tests |
| 3 | Static Analysis Executor | `src/test/java/com/dnd/qello/convention/staticanalysis/JavaStaticAnalysisRuleTest.java`, `src/test/resources/java-conventions/static-analysis/**` | UNIT-015~019 | Checkstyle boundary와 message tests |
| 4 | Hook Executor | `src/test/java/com/dnd/qello/convention/hook/JavaConventionHookDispatchTest.java`, `src/integrationTest/java/com/dnd/qello/convention/JavaConventionHookIntegrationTest.java`, `src/integrationTest/resources/java-conventions/hook/**` | UNIT-020, INT-003~005, INT-012 | staged dispatch, partial staging, read-only 반복·동시 실행 |
| 5 | Gradle Integration Executor | `src/integrationTest/java/com/dnd/qello/convention/JavaConventionGradleIntegrationTest.java`, `src/integrationTest/resources/java-conventions/gradle/**` | INT-001~002, INT-006~009, INT-013 | Spotless, baseline, aggregate task, ratchet와 version compatibility |
| 6 | Workflow Contract Executor | `src/integrationTest/java/com/dnd/qello/convention/JavaConventionWorkflowContractIntegrationTest.java` | INT-010 | GitHub Actions와 existing workflow validator contract |
| 7 | Repository Regression Verifier | 기존 source와 test file은 수정하지 않음 | INT-011, INT-014 | actual repository convention/full suite와 path diff 검사 |
| 8 | Report Owner | `docs/test-reports/gh-208-TEST-REPORT-GH-208-JAVA-CONVENTION-GATES.md` | 전체 | 실행 결과, timing과 잠재 문제 분석 기록 |

각 executor는 표에 적힌 test/fixture file만 수정한다. Production tooling file 소유권과
작업 순서는 승인된 implementation plan에서 별도로 지정한다. 같은 file 변경이 필요하면
오케스트레이터가 순서를 직렬화하고 계획을 먼저 갱신한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] P1 미구현 시 이유, 영향과 후속 검증을 `BLOCKED`로 기록
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 신규 테스트 클래스 헤더에 정확한 ISO 8601 timestamp와 source scenario 기록
- [ ] baseline unit, ArchUnit와 Checkstyle fixture test 통과
- [ ] Gradle TestKit formatter·ratchet·aggregate task test 통과
- [ ] temporary Git repo staged·partial staging·동시 실행 test 통과
- [ ] negative fixture가 기대한 단일 rule ID로 실패
- [ ] Java 21·Gradle 8.14.3에서 pinned tool dependency resolve
- [ ] actual repository `javaConventionCheck`와 전체 `check` 통과
- [ ] production source, migration, API와 runtime behavior 변경 없음 확인
- [ ] `./harness test-run --id TEST-PLAN-GH-208-JAVA-CONVENTION-GATES` 실행
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과
- [ ] cold/warm hook 실행 시간과 application, DB, concurrency, transaction, external API,
  failure recovery 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성

## 11. Human approval

- Reviewer: human partner
- Decision: `APPROVED_FOR_IMPLEMENTATION_PLAN`
- Approved at: `2026-09-02T00:21:29+09:00`
