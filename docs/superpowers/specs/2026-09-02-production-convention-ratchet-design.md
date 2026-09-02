# Java convention production 감사와 changed-file ratchet 설계

> Design ID: `APP-DESIGN-GH-210-001`
> GitHub Issue: `#210`
> Task ID: `GH-210-PRODUCTION-CONVENTION-RATCHET`
> Status: `APPROVED_FOR_PLAN`
> Approved by: human partner at `2026-09-02`
> Approval evidence: Codex 세션에서 Wave 0A·0B 설계를 승인하고, 이어서
> Issue #210·Project draft·브랜치 생성을 요청함

선행 설계: `APP-DESIGN-GH-208-001` (`docs/superpowers/specs/2026-09-02-java-convention-gates-design.md`)

## 1. 목적

PR #209가 도입한 ArchUnit 규칙은 테스트 fixture만 스캔한다. production
`@Service`의 transaction·injection 위반은 `javaConventionCheck`를 통과한 채로
남아 있다. 이 작업은 production을 읽기 전용으로 감사하고, 기존 코드를 즉시
깨뜨리지 않으면서 변경된 production Service에만 같은 규칙을 강제한다.

## 2. 현재 상태와 문제

2026-09-02 `main`(PR #209 merge 이후) 기준으로 확인한 사실이다.

- `JavaConventionArchitectureTest`는
  `ClassFileImporter.importClasses(fixture)`만 사용한다.
  `com.dnd.qello` production package를 import하지 않는다.
- Gradle `javaConventionArchitectureTest` filter는
  `*JavaConventionArchitectureTest`만 실행한다.
- production `@Service`는 약 50개다.
- 클래스 `@Transactional(readOnly = true)`가 없는 Service가 약 41개다.
- 클래스 write `@Transactional`은 `DeviceTokenService` 1건이다.
- field `@Autowired`는 현재 탐지되지 않는다.
- `PushDeviceService`는 constructor `@Autowired`를 사용한다. 이는 현재
  `QELLO-JAVA-INJECTION-001` 구현(field `@Autowired`)의 대상이 아니다.
- baseline 51건은 생성자·복잡도·길이·import만 담고 있으며 TX/injection
  부채 목록이 아니다.
- 새 `LEGACY` 추가와 hash-only 갱신은 이미 금지되어 있다.

따라서 지금 production Service를 수정하지 않은 채 전체 scan을 켜면
`javaConventionCheck`와 pre-commit이 기존 코드 수십 개에서 실패한다.

## 3. 범위와 비범위

### 포함

- production `@Service`에 대한 `QELLO-JAVA-TX-001`·`QELLO-JAVA-TX-002`·
  `QELLO-JAVA-TX-003`·`QELLO-JAVA-INJECTION-001` 읽기 전용 감사
- package·class·rule별 inventory. 기존 baseline 51건과 구분
- fixture와 production이 같은 ArchCondition을 쓰도록 rule 추출
- setter injection fixture (`JAVA_CONVENTIONS.md`의 field/setter 금지와 일치)
- `origin/main` 대비 변경된 production Service에만 ArchUnit 강제
- staged manifest가 있으면 그 경로 집합을 ratchet 대상으로 사용
- 새 production Service 파일은 항상 ratchet 대상
- `origin/main` 누락은 convention skip이 아니라 configuration failure

### 제외

- production Service 본문, 생성자, 트랜잭션 경계 변경
- 기존 위반을 `baseline.json` `LEGACY`로 일괄 등록
- Inbox `REPEATABLE_READ` isolation 수정
- `feed` application seam 분리
- 기존 baseline 51건 제거
- 전체 production scan 전환 (Wave 6 draft)
- constructor `@Autowired`를 `QELLO-JAVA-INJECTION-001`로 승격
- API, schema, migration, query 변경

## 4. 설계 결정

### DEC-210-001: production을 실제로 스캔한다

fixture-only import는 규칙이 존재한다는 증거일 뿐 production 계약을
강제하지 못한다. 감사와 ratchet 모두 `com.dnd.qello` production
`@Service` bytecode를 읽는다.

### DEC-210-002: 기존 위반을 baseline에 넣지 않는다

TX/injection 부채를 `LEGACY`로 옮기면 #208이 막은 “새 legacy 추가” 계약을
우회하고 suppression 묘지가 된다. 미수정 위반은 inventory와 Project draft로만
추적한다.

### DEC-210-003: changed-file ratchet만 강제한다

`javaConventionCheck`는 `origin/main` diff의 production Java가 가리키는
`@Service`에만 TX/injection 규칙을 적용한다. 새 파일은 diff에 나타나므로
즉시 강제된다. 미수정 legacy는 통과한다.

`javaConventionStagedCheck`는 `qelloConventionManifest` 경로 집합을 같은
방식으로 사용한다. 같은 경로 집합이면 origin/main 판정과 staged 판정이
같아야 한다.

### DEC-210-004: 기능 결함과 구조 리팩터링을 이 Issue에 섞지 않는다

수신함 isolation과 feed seam 분리는 별도 Project draft다. 이 작업은
tooling과 테스트만 변경한다.

### DEC-210-005: fixture rule과 production rule은 하나의 구현이다

`JavaConventionArchitectureTest`의 private `ArchCondition`을 production
ratchet이 복사하면 규칙이 갈라진다. 공유 `ProductionConventionRules`가
rule ID 문자열과 조건을 소유하고, fixture 테스트와 production ratchet이
그 구현만 호출한다.

### DEC-210-006: setter injection은 이번 규칙 완성 범위다

`JAVA_CONVENTIONS.md`는 field/setter injection을 금지한다. 현재 구현은
field `@Autowired`만 본다. Wave 0는 setter `@Autowired`를 같은
`QELLO-JAVA-INJECTION-001`로 거절한다. constructor `@Autowired`는
주입 metadata로 보고 이 Issue에서 승격하지 않는다. `PushDeviceService`는
notification package draft에서 검토한다.

### DEC-210-007: 감사 inventory는 gate가 아니다

전체 production scan 결과는 테스트가 구조화해 기록한다. inventory 누락이나
scanner 오동작은 테스트 실패다. inventory에 있는 위반 자체는
`javaConventionCheck`를 실패시키지 않는다. 실패 조건은 ratchet 대상이
규칙을 어기는 경우뿐이다.

## 5. 구성 요소

```text
src/test/java/com/dnd/qello/architecture/ProductionConventionRules.java
src/test/java/com/dnd/qello/architecture/ChangedJavaTypes.java
src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureTest.java
src/test/java/com/dnd/qello/architecture/ProductionConventionAuditTest.java
src/test/java/com/dnd/qello/architecture/ProductionConventionRatchetTest.java
src/test/java/com/dnd/qello/architecture/fixture/SetterInjectedService.java
docs/harness/JAVA_CONVENTIONS.md
build.gradle   # javaConventionArchitectureTest filter
```

production source와 `baseline.json`은 수정하지 않는다.

### 감사

`ProductionConventionAuditTest`는 production `@Service` 전체에 공유 규칙을
평가하고 package·class·rule 목록을 assertion 가능한 구조로 만든다. 최소
canary로 `DeviceTokenService`의 class write `QELLO-JAVA-TX-001`을 포함해야
scanner가 production을 읽고 있음을 증명한다.

### ratchet 대상 계산

1. staged manifest가 있으면 그 Java 경로만 사용한다.
2. 없으면 `git diff --name-only origin/main`의 `src/main/java/**/*.java`를
   사용한다.
3. 경로를 fully-qualified type으로 변환하고 `@Service`인 class만 남긴다.
4. `origin/main`이 없으면 빈 대상으로 통과시키지 않고 configuration
   failure로 종료한다.

### Gradle 연결

`javaConventionArchitectureTest`는 fixture 테스트와 production ratchet
테스트를 함께 실행해야 한다. 현재 `*JavaConventionArchitectureTest` filter는
새 테스트 클래스를 놓친다. filter를 architecture convention 테스트 집합으로
넓히거나, ratchet 테스트 이름을 기존 filter에 맞춘다. `javaConventionCheck`와
`javaConventionStagedCheck`는 이 task에 계속 의존한다.

## 6. 오류 메시지

ratchet 실패는 기존 convention 오류 계약을 따른다.

- rule ID
- class/member target
- 변경된 file 경로
- 이유: 변경된 production Service는 TX/injection 규칙을 만족해야 한다
- remediation: 해당 파일의 위반을 고친다. baseline hash 갱신이나 새
  `LEGACY` 추가는 허용되지 않는다
- exception 절차: 승인된 programmatic transaction·injection metadata만
  후속 Issue에서 `JUSTIFIED_EXCEPTION`으로 전환

## 7. 위험

| 위험 | 완화 |
| --- | --- |
| 전체 scan을 켜 기존 Service 41개가 CI를 막음 | changed-file ratchet, 미수정은 inventory만 |
| 기존 위반을 baseline에 넣어 영구화 | `LEGACY` 추가 금지 유지 |
| fixture rule과 production rule 분기 | 공유 `ProductionConventionRules` |
| staged와 origin/main 대상 불일치 | 같은 selector, 입력만 다름 |
| `origin/main` 없는 CI가 검사를 skip | configuration failure |
| constructor `@Autowired`를 갑자기 차단 | Wave 0 비범위, notification draft로 추적 |
| tooling 작업이 production source를 변경 | path diff gate |

## 8. 구현 게이트

이 문서는 구현 계획을 작성할 수 있도록 승인된 설계다. build script, test
source, hook과 문서 변경은 risk-based 테스트 계획과 implementation plan을
사람이 승인한 뒤에 시작한다. production source와 baseline은 이 Issue에서
수정하지 않는다.
