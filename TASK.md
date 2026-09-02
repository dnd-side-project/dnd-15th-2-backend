# GitHub Issue #208 Task Contract

> Generated at: `2026-09-02T00:05:41+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Java 코드 컨벤션 자동 검증 기반 구축`
- GitHub Issue: `#208`
- Branch: `chore/gh-208-java-convention-gates`
- Base branch: `main`
- Task ID: `GH-208-JAVA-CONVENTION-GATES`
- Design ID: `APP-DESIGN-GH-208-001`
- Design path:
  `docs/superpowers/specs/2026-09-02-java-convention-gates-design.md`
- Design status: `APPROVED_FOR_PLAN`
- Design approval evidence: `2026-09-02T00:08:29+09:00` 사용자가 네 개 설계
  섹션 전체를 승인하고 Issue #208 작업 진행을 요청함
- Test plan: `TEST-PLAN-GH-208-JAVA-CONVENTION-GATES`
- Test plan path:
  `docs/test-plans/gh-208-TEST-PLAN-GH-208-JAVA-CONVENTION-GATES.md`
- Test plan status: `APPROVED_FOR_IMPLEMENTATION_PLAN`
- Test plan approval evidence: `2026-09-02T00:21:29+09:00` 사용자가
  `TEST-PLAN-GH-208-JAVA-CONVENTION-GATES`를 승인함
- Implementation plan:
  `docs/superpowers/plans/2026-09-02-java-convention-gates.md`
- Implementation plan status: `APPROVED_FOR_BUILD`
- Implementation plan approval evidence: `2026-09-02T00:38:56+09:00` 사용자가
  구현 계획을 승인하고 현재 #208 checkout에서 구현 시작을 요청함
- Implementation gate: `APPROVED_FOR_BUILD`

## Objective

- Java 포맷, Spring bean 생성자 주입, Lombok 사용, Service 트랜잭션 경계와
  메서드 복잡도 규칙을 사람이 읽는 문서와 실행 가능한 검증으로 일치시킨다.
- 개발자와 에이전트가 변경한 Java 코드가 commit 전에 빠르게 검증되고,
  동일한 계약이 Gradle `check`와 GitHub Actions에서 최종 강제되게 한다.
- 기존 위반은 일괄 소스 변경 없이 중앙 baseline으로 동결하고, 새 위반과
  변경된 legacy target의 위반 연장을 차단한다.

## Scope

- `docs/harness/JAVA_CONVENTIONS.md`에 Java·Lombok·트랜잭션·복잡도 정책 기록
- Spotless와 고정된 Eclipse JDT formatter 설정
- 탭 들여쓰기, 120자 줄 길이, import 순서와 기본 공백·개행 규칙
- `origin/main` 기준 변경 파일 formatter ratchet
- Checkstyle 기반 production source 구조·길이·복잡도 검사
- ArchUnit 기반 field injection, transaction annotation과 self-invocation 검사
- 단순 Spring bean 생성자의 `@RequiredArgsConstructor` 전환 규칙
- DB Service의 클래스 `@Transactional(readOnly = true)` 기본값과 method write override 규칙
- `LEGACY`와 `JUSTIFIED_EXCEPTION`을 분리한 중앙 baseline과 canonical Git blob hash 검증
- staged Java만 검사하는 `javaConventionStagedCheck`
- 전체 convention을 묶는 `javaConventionCheck`와 Gradle `check` 연결
- 부분 staging Java 차단과 기존 Husky pre-commit·pre-push 연결
- GitHub Actions `java-conventions` job과 필요한 `origin/main` fetch
- positive·negative fixture 기반 검증기 자체 테스트
- package별 후속 정리를 위한 GitHub Project draft tracking reference

## Explicit exclusions

- production Service의 생성자, 트랜잭션 또는 메서드 본문 리팩터링
- 기존 Java source 전체의 일괄 formatter 적용
- API, domain, persistence, schema, migration과 query 동작 변경
- package별 후속 Repository Issue 일괄 생성
- 새 제품 기능, 배포, 인프라와 production 변경
- hook, baseline 또는 CI를 우회하는 기능
- 테스트 계획과 구현 계획 승인 전 build script, hook, workflow와 test source 수정
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Approved decisions

- `DEC-208-001`: Gradle task를 실행 가능한 단일 기준으로 두고 Husky와 CI는 같은 task를 호출한다.
- `DEC-208-002`: Spotless Eclipse JDT formatter와 `origin/main` ratchet으로 변경 파일부터 적용한다.
- `DEC-208-003`: 단순 주입 생성자는 `@RequiredArgsConstructor`를 사용하고 의미 있는 생성자는 근거 있는 예외로 남긴다.
- `DEC-208-004`: DB Service는 클래스 read-only를 기본으로 하고 쓰기 method만 `@Transactional`을 재정의한다.
- `DEC-208-005`: 외부 I/O 경계, `TransactionTemplate`, `NOT_SUPPORTED`와 주입 metadata는 명시적 예외로 허용한다.
- `DEC-208-006`: 새 코드의 method length는 50줄, cyclomatic complexity는 15를 상한으로 둔다.
- `DEC-208-007`: 기존 위반은 `LEGACY`, 의도적인 예외는 `JUSTIFIED_EXCEPTION`으로 분리하고 정확한 target만 등록한다.
- `DEC-208-008`: 변경된 legacy target은 baseline hash 갱신으로 연장하지 않고 해당 위반을 해소한다.
- `DEC-208-009`: pre-commit은 staged Java만 검사하고 같은 파일의 partial staging은 차단한다.
- `DEC-208-010`: 첫 package pilot은 별도 작업에서 `feed`를 우선 검토하며 이 Issue에서는 production refactor를 하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·설계·Issue 계약 통합 | Orchestrator | Human partner |
| Gradle·formatter·Checkstyle·ArchUnit 구현 | Execution agent | Independent verifier |
| baseline inventory와 exception 근거 | Execution agent | PM reviewer |
| Husky·GitHub Actions 연결 | Execution agent | Independent verifier |
| positive·negative fixture 및 회귀 검증 | Test executor | Independent verifier |
| 최종 범위·위험·증거 승인 | PM reviewer | Human partner |

## Existing user-owned changes

- Issue intake 시작 시 `main`의 `git status --short`는 비어 있었고 기존 사용자 변경은 없었다.
- `./harness start`가 최신 `origin/main`에서
  `chore/gh-208-java-convention-gates`를 생성했다.
- 현재 변경은 `./harness task-init --replace`가 만든 `TASK.md`, 이 계약 구체화,
  승인된 설계 문서, 승인된 테스트 계획과 draft 구현 계획뿐이다.
- 범위 밖 기존 파일과 다른 사용자의 변경을 자동 정리하거나 되돌리지 않는다.

## Validation

Planning checks:

```bash
rg -n "TODO|TBD|PLACEHOLDER" TASK.md \
  docs/superpowers/specs/2026-09-02-java-convention-gates-design.md
git diff --check
```

Implementation checks after separate plan approval:

```bash
./gradlew javaConventionCheck
./gradlew check
npm run hooks:validate
npm run hooks:pre-commit
./harness check
./harness pr-ready --project-tests
git diff --check
```

검증기 자체 테스트는 positive fixture가 통과하고 각 negative fixture가 기대한
rule ID 하나로 실패하는지 확인한다. 실제 staged hook 검증은 별도 임시 Git index
fixture에서 실행해 현재 작업 index를 훼손하지 않는다.

## Completion criteria

- [ ] 사람이 risk-based 테스트 계획과 구현 계획을 별도로 승인했다.
- [ ] Spotless, Eclipse JDT formatter, Checkstyle과 ArchUnit 버전이 고정됐다.
- [ ] formatter 설정과 `JAVA_CONVENTIONS.md`가 같은 포맷 계약을 표현한다.
- [ ] `spotlessApply`, `javaConventionStagedCheck`, `javaConventionCheck`가 동작한다.
- [ ] 전체 convention 검증이 Gradle `check`에 연결됐다.
- [ ] 기존 위반 inventory가 정확한 target, 분류, 근거와 tracking reference를 가진다.
- [ ] 새 `LEGACY`, wildcard suppression과 근거 없는 예외가 차단된다.
- [ ] canonical Git blob hash가 달라진 legacy target은 실패한다.
- [ ] field injection, 단순 명시적 생성자, class write transaction이 차단된다.
- [ ] private transaction method와 transaction self-invocation이 차단된다.
- [ ] method length 50과 cyclomatic complexity 15 상한이 변경 코드에 적용된다.
- [ ] partial staging Java가 명확한 메시지로 차단된다.
- [ ] formatter 위반 메시지가 `./gradlew spotlessApply`를 안내한다.
- [ ] positive·negative fixture가 기대한 원인으로 통과·실패한다.
- [ ] 기존 production source와 runtime 동작을 변경하지 않는다.
- [ ] 전체 Java source를 일괄 포맷하지 않는다.
- [ ] 필수 local check와 GitHub Actions 결과, 실행 시간과 미검증 범위가 보고된다.
