# Java 코드 컨벤션

## 적용 명령

Gradle은 Java 21로 실행한다.

```bash
./gradlew spotlessApply
./gradlew javaConventionStagedCheck -PqelloConventionManifest=<manifest>
./gradlew javaConventionCheck
```

`spotlessApply`는 변경 파일을 formatter profile에 맞게 정리한다. commit hook은 파일을
자동 수정하지 않으며, 위반 시 apply 명령만 안내한다.

## 포맷

- Eclipse JDT profile을 사용한다.
- 들여쓰기는 탭, 줄 길이는 120자다.
- import 그룹은 `java`, `javax`/`jakarta`, `org`, `com`, `lombok`, static 순서다.
- wildcard import, trailing whitespace와 파일 마지막 개행 누락을 금지한다.
- `origin/main` ratchet 기준으로 변경된 Java 파일부터 적용한다.

## 생성자와 Lombok

Spring stereotype bean의 의존성 필드는 `private final`이어야 한다.

필드 대입만 수행하는 생성자는 `@RequiredArgsConstructor`로 대체한다. 다음 경우에는
명시적 생성자를 유지할 수 있다.

- `TransactionTemplate` 등 collaborator를 생성하거나 설정하는 경우
- `@Qualifier` 등 parameter-level metadata가 필요한 경우
- constructor validation 또는 transformation이 필요한 경우
- framework 호환성을 위해 생성자 형태를 직접 제어해야 하는 경우

field/setter injection은 금지한다. JPA entity의 protected no-arg constructor는 이 규칙의
대상이 아니다.

## Service 트랜잭션

DB를 직접 사용하는 Service는 다음 형태를 기본으로 한다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExampleService {

    public Example find(...) {
        // 조회
    }

    @Transactional
    public Example create(...) {
        // 변경
    }
}
```

- 클래스 단위 write `@Transactional`은 금지한다.
- 실제 변경 entry method에만 method-level `@Transactional`을 붙인다.
- private/protected transaction method와 self-invocation을 금지한다.
  self-invocation은 같은 클래스 내부에서 `@Transactional` 메서드를 직접 호출하는
  경우다. 다른 Spring bean의 트랜잭션 메서드 호출은 해당하지 않는다.
- `TransactionTemplate`, `Propagation.NOT_SUPPORTED`, external I/O boundary와 특별한
  propagation/isolation은 중앙 baseline의 `JUSTIFIED_EXCEPTION`으로 등록한다.
- 정적 규칙은 업무 의미상 read/write를 완전히 추론하지 않는다. class read-only 기본값과
  package별 integration test로 실제 transaction behavior를 확인한다.

## production scan과 changed-file ratchet

ArchUnit TX/injection 규칙은 production `@Service`를 대상으로 한다.

- 감사 inventory는 전체 production Service를 읽지만 `javaConventionCheck`를 실패시키지 않는다.
- `javaConventionCheck`는 `origin/main` 대비 변경된 production Java의 `@Service`에만 규칙을 강제한다.
- `javaConventionStagedCheck`는 `qelloConventionManifest` 경로 집합에 같은 규칙을 적용한다.
- 새 production Service 파일은 즉시 강제된다. 미수정 legacy는 Project draft로 추적한다.
- 기존 위반을 `LEGACY`로 추가하거나 hash만 갱신하지 않는다.
- field와 setter `@Autowired`는 `QELLO-JAVA-INJECTION-001`이다. constructor `@Autowired`는
  이 규칙의 대상이 아니다.
- `origin/main`이 없으면 검사를 건너뛰지 않고 configuration failure로 종료한다.

## 메서드 복잡도

- method length 50줄 초과는 `QELLO-JAVA-SIZE-001`이다.
- cyclomatic complexity 15 초과는 `QELLO-JAVA-CPLX-001`이다.
- 임계값 초과는 무조건 기계적으로 분할하지 않는다. 응집도와 transaction 경계를 검토하고,
  불가피하면 exact target 예외와 근거를 남긴다.

## Rule ID와 오류 처리

| Rule ID | 의미 |
| --- | --- |
| `QELLO-JAVA-CTOR-001` | 단순 Spring bean 명시적 생성자 |
| `QELLO-JAVA-IMPORT-001` | wildcard 또는 import 규칙 위반 |
| `QELLO-JAVA-SIZE-001` | method length 초과 |
| `QELLO-JAVA-CPLX-001` | cyclomatic complexity 초과 |
| `QELLO-JAVA-BYPASS-001` | inline formatter/lint 우회 |
| `QELLO-JAVA-INJECTION-001` | field/setter injection 또는 mutable dependency |
| `QELLO-JAVA-TX-001` | class-level write 또는 read-only 기본값 누락 |
| `QELLO-JAVA-TX-002` | private/protected transaction method |
| `QELLO-JAVA-TX-003` | transaction method self-invocation |

오류 메시지는 rule ID, file/target, reason, remediation command와 exception 절차를 포함해야
한다. 예상된 violation을 configuration/environment failure로 위장하지 않는다.

## Baseline과 예외

baseline은 `config/java-conventions/baseline.json` 하나만 source of truth로 사용한다.

- `LEGACY`: 도구 도입 전 위반. exact target, canonical Git blob SHA-256, tracking reference,
  review date와 사유가 필요하다.
- `JUSTIFIED_EXCEPTION`: 승인된 설계상 예외. exact target, design reference, `TASK.md`의
  decision ID와 SHA-256이 필요하다.
- package wildcard, rule 전체 suppression과 source inline suppression은 금지한다.
- `origin/main`에 baseline이 생긴 뒤 새 `LEGACY` 추가와 hash-only 연장은 허용하지 않는다.
- legacy target을 수정할 때는 위반을 해결하고 baseline 항목을 삭제한다.

아직 시작하지 않은 package 정리는 Project draft item으로 추적한다. 실제 구현 Issue로
변환하기 전까지 Repository Issue를 대량 생성하지 않는다.

## Hook과 CI

pre-commit은 staged Java에 unstaged diff가 있으면 차단하고, temporary manifest를 만들어
`javaConventionStagedCheck`에 전달한다. manifest와 source/index는 hook이 수정하지 않는다.

GitHub Actions의 `java-conventions` job과 기존 `check`는 `javaConventionCheck`를 실행한다.
CI checkout은 `origin/main` ratchet을 위해 full history를 가져온다. hook을 우회한 commit도
CI에서 같은 rule ID로 실패해야 한다.
