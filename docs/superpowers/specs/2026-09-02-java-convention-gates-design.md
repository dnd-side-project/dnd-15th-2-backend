# Java 코드 컨벤션 자동 검증 설계

> Design ID: `APP-DESIGN-GH-208-001`
> GitHub Issue: `#208`
> Task ID: `GH-208-JAVA-CONVENTION-GATES`
> Status: `APPROVED_FOR_PLAN`
> Approved by: human partner at `2026-09-02T00:08:29+09:00`

## 1. 목적

Qello Java 코드의 포맷, Spring bean 생성자 주입, Lombok 사용, Service
트랜잭션 경계와 메서드 복잡도 규칙을 사람이 읽는 문서와 실행 가능한 검증으로
일치시킨다. 개발자와 에이전트가 변경한 코드는 commit 전에 빠르게 검증하고,
같은 계약을 Gradle `check`와 GitHub Actions에서 최종 강제한다.

기존 위반을 숨기거나 source 전체를 한 번에 다시 쓰지 않는다. 기존 위반은 정확한
target과 근거를 가진 baseline으로 동결하고, 새 위반과 변경된 legacy target의 위반
연장을 차단한다.

## 2. 현재 상태와 문제

2026-09-01 `main`을 기준으로 확인한 production source는 다음과 같다.

- Java source 717개
- `@Service` 55개
- `@RequiredArgsConstructor`를 쓰는 Service 22개
- 명시적 생성자가 있는 Service 29개
- 클래스 `@Transactional(readOnly = true)` Service 9개
- 클래스 write `@Transactional` Service 1개
- 탭 들여쓰기가 존재하는 Java file 632개
- 4-space 들여쓰기가 존재하는 Java file 11개

현재 Husky는 branch, commit, secret, JUnit metadata, workflow와 전체 Gradle
검증을 실행하지만 Java formatter와 Service convention을 직접 검증하지 않는다.
같은 목적의 코드가 서로 다른 생성자와 트랜잭션 형태를 사용하고, 긴 method를
일관되게 드러내는 자동 신호가 없다.

## 3. 범위와 비범위

### 포함

- Java convention 문서
- Spotless Eclipse JDT formatter
- Checkstyle production source lint
- ArchUnit constructor injection, transaction과 dependency rule
- 중앙 baseline과 exception lifecycle
- staged Java convention task
- Husky pre-commit·pre-push 연결
- GitHub Actions convention job
- positive·negative fixture와 validator self-test

### 제외

- production Service 생성자와 method body 변경
- production transaction 경계 변경
- 전체 Java source 일괄 formatter 적용
- API, domain, DB schema, migration과 query 변경
- package별 후속 Repository Issue 일괄 생성
- formatter나 validator가 source를 commit 중 자동 수정하는 동작
- hook, baseline 또는 CI 우회 기능

## 4. 설계 결정

### DEC-208-001: Gradle task가 실행 가능한 단일 기준이다

formatter, source lint, architecture rule과 baseline validation은 Gradle task로
조합한다. Husky와 GitHub Actions는 같은 task를 호출하고 Java 규칙을 별도로
재구현하지 않는다. Python hook code는 Git index와 partial staging을 안전하게
읽고 Gradle에 정확한 file manifest를 전달하는 glue만 소유한다.

### DEC-208-002: Spotless와 Eclipse JDT formatter를 사용한다

formatter는 현재 source 다수의 탭 들여쓰기를 보존하는 고정 Eclipse JDT profile을
사용한다. 다음 포맷을 한 설정에서 관리한다.

- 탭 기반 indentation
- continuation indentation
- 최대 line length 120
- import group `java` → `javax`/`jakarta` → `org` → `com` → `lombok`
- wildcard import 금지
- trailing whitespace 제거
- file 끝 newline
- single-statement control block의 brace

Spotless는 `origin/main` ratchet을 사용한다. branch에서 새로 추가하거나 변경한 Java
file 전체만 formatter 대상으로 삼고 기존 717개 file을 한 번에 변경하지 않는다.
CI에 기준 ref가 없으면 formatter 검증을 비활성화하지 않고 fetch 구성이 잘못됐다는
명확한 오류로 실패한다.

### DEC-208-003: Checkstyle은 source 형태와 복잡도를 검사한다

Checkstyle은 production source에 다음 안정적인 rule ID를 제공한다.

```text
QELLO-JAVA-IMPORT-001 wildcard 또는 잘못된 import 순서
QELLO-JAVA-SIZE-001   method length 50 초과
QELLO-JAVA-CPLX-001   cyclomatic complexity 15 초과
QELLO-JAVA-BYPASS-001 inline suppression 또는 formatter bypass
```

Spring stereotype과 명시적 생성자의 관계는 JavaParser source-contract test가
`QELLO-JAVA-CTOR-001`로 검사한다. 의미 있는 명시적 생성자는 source 안의 임의
suppression이 아니라 중앙 `JUSTIFIED_EXCEPTION`으로만 허용한다. formatter가 완전히
고칠 수 있는 whitespace 규칙은 Checkstyle에서 중복 검사하지 않는다.

method length와 complexity는 코드 품질을 단정하는 점수가 아니라 review signal이다.
새 file과 baseline이 해제된 file은 상한을 넘으면 실패한다. SQL 상수, 명시적 state
machine 또는 분리가 응집도를 훼손하는 method는 exact target exception과 설계 근거가
있을 때만 허용한다.

### DEC-208-004: constructor injection 결과는 ArchUnit으로 검사한다

Spring bean은 다음 조건을 만족한다.

- injected state는 `private final`
- field·setter injection 금지
- 단순 constructor assignment는 Lombok `@RequiredArgsConstructor` 사용
- JPA entity의 protected no-arg constructor는 이 rule 대상이 아님

명시적 생성자는 다음 경우에만 `JUSTIFIED_EXCEPTION`으로 허용한다.

- `TransactionTemplate` 등 collaborator를 생성·설정
- `@Qualifier` 등 parameter-level injection metadata 필요
- constructor validation 또는 transformation
- framework가 요구하는 여러 constructor 중 하나를 명시적으로 선택

Lombok 사용 범위는 constructor boilerplate 제거로 제한한다. 이 설계는 `@Data`,
setter, builder 또는 domain entity Lombok 확대를 요구하지 않는다.

### DEC-208-005: DB Service는 class read-only를 기본으로 한다

transaction-aware Service는 다음 중 하나를 만족하는 `@Service`다.

- `..repository..` type에 직접 의존
- `EntityManager`, JDBC template 또는 transaction manager에 직접 의존
- method-level `@Transactional` 선언

transaction-aware Service는 기본적으로 class에
`@Transactional(readOnly = true)`를 선언한다. 실제 write entry method는
method-level `@Transactional`로 override한다. 다음을 자동 차단한다.

- class write `@Transactional`
- private 또는 protected transaction method
- 같은 class 내부의 transaction method self-invocation
- class read-only와 완전히 같은 method annotation의 불필요한 반복

다음은 exact target과 근거를 가진 예외다.

- `TransactionTemplate`로 짧은 state-change 구간만 여는 Service
- `Propagation.NOT_SUPPORTED`로 external storage I/O를 분리하는 method
- 외부 API 호출을 DB transaction 밖에 두는 Service
- worker가 claim, external I/O와 terminal update를 서로 다른 transaction으로 나누는 경우

static rule은 method의 업무 의미가 read인지 write인지 완전히 추론하지 않는다.
class read-only 기본값을 안전장치로 사용하고 실제 write 성공·rollback은 package별
integration test가 검증한다.

### DEC-208-006: baseline은 suppression 목록이 아니라 부채 원장이다

baseline entry는 `LEGACY`와 `JUSTIFIED_EXCEPTION`만 허용한다.

- `LEGACY`: 도구 도입 전에 존재했고 package별 후속 정리 대상
- `JUSTIFIED_EXCEPTION`: 승인된 설계상 유지해야 하는 예외

package wildcard, rule 전체 suppression과 source inline suppression은 금지한다.
entry는 exact class 또는 member target만 가리킨다.

### DEC-208-007: canonical Git blob hash로 변경을 감지한다

baseline hash는 working tree bytes가 아니라 Git index 또는 commit의 canonical blob을
UTF-8/LF bytes로 읽어 SHA-256으로 계산한다.

- pre-commit은 staged blob을 검사한다.
- CI는 PR head commit blob을 검사한다.
- unchanged legacy target은 기존 hash로 통과한다.
- changed legacy target은 hash mismatch로 실패하며 violation을 고치고 entry를 삭제한다.
- legacy entry의 hash만 갱신해 수명을 연장할 수 없다.
- justified target 변경은 기존 exception을 무효화하고 승인 근거를 다시 요구한다.

### DEC-208-008: partial staging Java는 차단한다

formatter가 working tree를 검사한 내용과 Git index에 commit되는 내용이 다르면 gate가
거짓으로 통과할 수 있다. staged Java file에 unstaged diff가 함께 있으면 pre-commit은
file 목록과 해결 방법을 출력하고 실패한다. 다른 unstaged Java file은 staged check의
대상이 아니며 commit을 막지 않는다.

hook은 source를 자동 수정하거나 index를 다시 stage하지 않는다. 위반 시
`./gradlew spotlessApply`를 안내하고 개발자 또는 agent가 diff를 검토한 뒤 다시 stage한다.

### DEC-208-009: local과 CI의 빠른 gate와 전체 gate를 분리한다

```text
javaConventionStagedCheck
├── staged Spotless check
├── changed-file Checkstyle
└── staged baseline validation

javaConventionCheck
├── ratcheted spotlessJavaCheck
├── checkstyleMain + baseline
├── JavaConventionArchitectureTest + baseline
└── baseline schema/lifecycle validation
```

`javaConventionStagedCheck`는 pre-commit의 exact staged file manifest만 읽는다.
`javaConventionCheck`는 Gradle `check`에 연결한다. pre-push의 기존
`harness pr-ready --project-tests`가 `check`를 실행하므로 같은 전체 task를 다시
호출하지 않는다.

### DEC-208-010: CI는 독립 job에서도 같은 task를 실행한다

`java-conventions` job은 checkout 전체 history, Java 21과 Gradle cache를 준비하고
`./gradlew javaConventionCheck`를 실행한다. 기존 test job과 병렬로 빠른 feedback을
제공하되 `./gradlew check`에도 convention dependency를 남겨 선택적 job 실행으로
우회되지 않게 한다.

GitHub Actions job은 workspace를 공유하지 않으므로 Gradle을 실행하는 각 job에 명시적
checkout을 둔다. `origin/main` ratchet을 위해 `fetch-depth: 0` 또는 동등한 explicit
fetch를 사용한다.

## 5. 구성 요소와 파일

```text
docs/harness/JAVA_CONVENTIONS.md
config/spotless/eclipse-java-formatter.xml
config/checkstyle/checkstyle.xml
config/checkstyle/suppressions.xml
config/java-conventions/baseline.json
src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureTest.java
src/test/java/com/dnd/qello/architecture/fixture/...
scripts/run-hook.py
scripts/validate-husky.py
build.gradle
.husky/pre-commit
.github/workflows/harness-policy.yml
```

`JAVA_CONVENTIONS.md`는 사람이 읽는 설명, 예시와 exception 절차를 소유한다.
Gradle config와 architecture test가 실행 가능한 계약을 소유한다. baseline JSON이
legacy와 justified target의 유일한 원장이다.

`config/checkstyle/suppressions.xml`에는 tool이나 generated-source 차원의 고정 제외만
허용하고 legacy target을 중복 기록하지 않는다. build task는 baseline JSON을 읽어
Checkstyle과 ArchUnit에 exact target exception을 제공한다.

## 6. Baseline schema와 lifecycle

```json
{
  "schemaVersion": 1,
  "entries": [
    {
      "id": "JAVA-CONV-0001",
      "rule": "QELLO-JAVA-CTOR-001",
      "target": "com.dnd.qello.question.service.QuestionAssignmentService#<init>",
      "sourceSha256": "<canonical-git-blob-sha256>",
      "classification": "LEGACY",
      "trackingReference": "<project-draft-item-reference>",
      "reviewBy": "2026-12-31",
      "reason": "도구 도입 전에 존재한 단순 명시적 생성자"
    }
  ]
}
```

angle bracket 값은 schema 설명용이며 실제 baseline에는 실제 hash와 tracking reference만
기록한다. validator는 다음을 검사한다.

- schema version과 허용 field
- stable ID 중복
- 알려진 rule ID
- exact target 존재
- wildcard·package-level target 금지
- 비어 있거나 일반적인 reason 금지
- legacy tracking reference와 review date
- justified design reference와 `TASK.md` approved decision ID
- expired review date
- canonical blob hash
- `origin/main` 대비 새 legacy entry 금지

Issue #208은 최초 baseline을 만드는 유일한 bootstrap 작업이다. 이후 legacy entry는
삭제만 허용한다. 아직 구현하지 않을 package 정리는 GitHub Project draft item으로
추적하고, 실제 착수할 때만 Repository Issue로 변환한다.

## 7. Hook 동작

pre-commit은 기존 branch, staged whitespace, secret, JUnit과 workflow 검증을 보존하고
staged Java가 있을 때만 다음 단계를 추가한다.

1. staged file과 unstaged file 교집합을 계산한다.
2. 교집합에 Java가 있으면 partial staging 오류로 종료한다.
3. staged Java manifest를 temporary file로 만들고 Gradle에 전달한다.
4. `javaConventionStagedCheck`를 실행한다.
5. 성공 시 temporary manifest를 제거하고 기존 commit 흐름을 계속한다.

temporary file은 repository에 남기지 않고 재실행에 안전하게 정리한다. file path는
NUL-safe Git output에서 읽고 repository root 밖 target이나 symlink escape를 거절한다.

warm Gradle daemon 기준 목표는 10초 이내다. 시간 자체를 flaky pass/fail threshold로
사용하지 않고 test report에 cold/warm 시간을 구분해 기록한다.

## 8. 오류 메시지 계약

예상된 convention failure는 stack trace 대신 다음 정보를 출력한다.

```text
rule_id
file
line_or_target
reason
remediation_command
exception_process
```

formatter violation은 `./gradlew spotlessApply`를 안내한다. partial staging은 file을
모두 stage하거나 unstaged change를 분리하라고 안내한다. baseline failure는 entry ID,
classification과 invalid field를 노출하되 source 전체나 민감한 값을 출력하지 않는다.

tool crash, missing `origin/main`, invalid config와 dependency resolution failure는 expected
violation으로 위장하지 않고 환경·구성 실패로 구분한다.

## 9. 테스트 전략

### Positive fixture

- `@RequiredArgsConstructor` Spring Service
- class read-only와 method write transaction
- isolation을 override하는 read method
- 승인된 `TransactionTemplate` exception
- method length와 complexity 상한 이하
- valid legacy와 justified baseline entry

### Negative fixture

- field injection
- 단순 명시적 constructor
- class write transaction
- private transaction method
- transaction self-invocation
- method length 50 초과
- cyclomatic complexity 15 초과
- wildcard target
- duplicate baseline ID
- stale source hash
- tracking reference 없는 legacy
- approved decision 없는 justified exception
- partial staging Java

각 negative fixture는 단순히 non-zero exit가 아니라 예상 rule ID 하나로 실패해야 한다.
fixture는 production package에 포함하지 않고 실제 Spring context, DB나 external API를
호출하지 않는다.

### Regression과 final checks

```bash
./gradlew javaConventionCheck
./gradlew check
npm run hooks:validate
npm run hooks:pre-commit
./harness check
./harness pr-ready --project-tests
git diff --check
```

staged hook behavior는 임시 Git index 또는 격리 fixture에서 검증한다. 현재 사용자의
index와 working tree를 test fixture로 바꾸지 않는다.

## 10. Rollout

1. Issue #208에서 도구, 문서, bootstrap baseline과 gate만 도입한다.
2. `feed` package를 첫 별도 pilot 후보로 검토한다.
3. 작은 package와 transaction risk가 낮은 target부터 legacy를 제거한다.
4. `answer`, `direction`, `notification`, `filtering`처럼 programmatic transaction과
   external I/O 경계가 많은 package는 별도 risk-based test plan 뒤 정리한다.

한 package의 legacy file을 수정하면 formatter, constructor, transaction과 complexity
위반을 함께 해소하고 해당 baseline entry를 삭제한다. 후속 작업은 각각 Project draft로
계획하고 실제 구현할 때만 Issue로 변환한다.

## 11. 위험과 완화

### Formatter churn

변경 file 전체를 formatter가 다시 쓰므로 기능 diff가 커질 수 있다. ratchet과 package별
작업으로 범위를 제한하고 formatter-only 변경과 semantic 변경을 commit 목적에서 분리한다.

### Static analysis overreach

명시적 state machine이나 긴 SQL처럼 숫자만으로 분리하면 가독성이 나빠질 수 있다.
exact justified exception과 설계 근거를 허용하되 wildcard suppression은 금지한다.

### Transaction false confidence

static rule은 업무상 write를 완전히 식별하지 못한다. read-only class default와 package별
PostgreSQL integration test를 함께 사용하고, passing lint를 transaction 검증 완료로
보고하지 않는다.

### Hook latency

cold Gradle 시작은 느릴 수 있다. staged file target, daemon과 build cache를 사용하고
실측 cold/warm 시간을 보고한다. 속도를 위해 rule이나 full pre-push check를 건너뛰지 않는다.

### Baseline permanence

baseline이 suppression 묘지로 남을 수 있다. legacy addition 금지, canonical hash,
review date와 Project tracking reference로 위반 연장을 차단한다.

## 12. 구현 게이트

이 문서는 구현 계획을 작성할 수 있도록 승인된 설계다. production source, build script,
hook, workflow와 test source 변경은 별도 risk-based 테스트 계획과 implementation plan을
사람이 승인한 뒤 시작한다.

구현 계획은 다음을 확정해야 한다.

- Java 21과 Gradle 8.14.3에서 실제 resolve되는 고정 tool version
- Checkstyle rule 구현 방식과 rule ID fixture
- baseline bootstrap inventory와 Project draft mapping
- staged file manifest의 exact interface
- RED/GREEN command와 commit 목적 분리
- independent verifier와 PM review evidence
