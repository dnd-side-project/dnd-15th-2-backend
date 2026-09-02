# GitHub Issue #210 Task Contract

> Generated at: `2026-09-02T17:26:45+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Java convention production 감사와 changed-file ratchet`
- GitHub Issue: `#210`
- Branch: `chore/gh-210-production-convention-ratchet`
- Base branch: `main`
- Task ID: `GH-210-PRODUCTION-CONVENTION-RATCHET`
- Design ID: `APP-DESIGN-GH-210-001`
- Design path:
  `docs/superpowers/specs/2026-09-02-production-convention-ratchet-design.md`
- Design status: `APPROVED_FOR_PLAN`
- Design approval evidence: `2026-09-02` Codex 세션에서 Wave 0A·0B를 승인하고,
  이어서 Issue #210·Project draft·브랜치 생성을 요청함. 동일 결정을
  `APP-DESIGN-GH-210-001`로 기록함
- Test plan: `TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET`
- Test plan path:
  `docs/test-plans/gh-210-TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET.md`
- Test plan status: `APPROVED_FOR_IMPLEMENTATION_PLAN`
- Test plan approval evidence: `2026-09-02T17:38:38+09:00` 사용자가
  `TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET`를 승인함
- Implementation plan:
  `docs/superpowers/plans/2026-09-02-production-convention-ratchet.md`
- Implementation plan status: `APPROVED_FOR_BUILD`
- Implementation plan approval evidence: `2026-09-02T17:42:43+09:00` 사용자가
  구현 계획을 승인하고 현재 #210 checkout에서 구현 시작을 요청함
- Implementation gate: `APPROVED_FOR_BUILD`

## Objective

- PR #209 ArchUnit 검사가 테스트 fixture만 스캔하는 공백을 메운다.
- production `@Service`의 transaction·injection 위반 inventory를 확정한다.
- 기존 코드를 즉시 깨뜨리지 않는 changed-file ratchet을 활성화한다.

## Scope

- production `@Service` 전체를 `QELLO-JAVA-TX-001`·`QELLO-JAVA-TX-002`·
  `QELLO-JAVA-TX-003`·`QELLO-JAVA-INJECTION-001`로 읽기 전용 감사
- 누락 위반을 package·class·rule별로 inventory화하고 기존 baseline 51건과 구분
- 새 `LEGACY` 추가 금지는 유지하고, 기존 위반을 baseline에 억지로 추가하지 않음
- `origin/main` 대비 변경된 production Service에만 ArchUnit 규칙을 강제
- 수정하지 않은 legacy는 감사 inventory와 Project draft로 추적

## Explicit exclusions

- production Service 생성자·트랜잭션·메서드 본문 리팩터링
- 수신함 `REPEATABLE_READ` isolation 결함 수정
- `feed` application seam 분리
- 기존 baseline 51건 제거
- 전체 production scan 전환
- API·도메인·DB schema·migration·query 동작 변경
- 패키지별 후속 Repository Issue 일괄 생성
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Approved decisions

- `DEC-210-001`: production transaction/injection 규칙은 fixture가 아니라
  production `@Service`를 대상으로 감사한다.
- `DEC-210-002`: 기존 위반을 baseline에 일괄 추가하지 않고, `origin/main`
  대비 변경된 production Service에만 ratchet을 적용한다.
- `DEC-210-003`: 새 `LEGACY` 추가와 hash-only 갱신 금지는 유지한다.
- `DEC-210-004`: Inbox isolation 결함과 feed 구조 분리는 이 Issue에 섞지 않는다.
- `DEC-210-005`: fixture rule과 production rule은 `ProductionConventionRules` 하나다.
- `DEC-210-006`: setter injection은 `QELLO-JAVA-INJECTION-001`에 포함하고,
  constructor `@Autowired`는 이 Issue에서 승격하지 않는다.
- `DEC-210-007`: 감사 inventory는 gate가 아니며, 실패 조건은 ratchet 대상 위반뿐이다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·설계·Issue 계약 통합 | Orchestrator | Human partner |
| production audit inventory | Execution agent | Independent verifier |
| ArchUnit changed-file ratchet | Execution agent | Independent verifier |
| fixture·회귀 검증 | Test executor | Independent verifier |
| 최종 범위·위험·증거 승인 | PM reviewer | Human partner |

## Existing user-owned changes

- Issue intake 시작 시 `main`의 `git status --short`는 비어 있었고 기존 사용자
  변경은 없었다.
- `./harness start`가 최신 `origin/main`에서
  `chore/gh-210-production-convention-ratchet`를 생성했다.
- 현재 변경은 `TASK.md`, 승인된 설계·테스트·구현 계획, convention test/source,
  `build.gradle` filter, `JAVA_CONVENTIONS.md`, 테스트 보고서뿐이다.
- production `src/main/java`, schema, migration, API, `baseline.json`은 수정하지 않았다.
- 범위 밖 기존 파일과 다른 사용자의 변경을 자동 정리하거나 되돌리지 않는다.

## Follow-up Project drafts

| Wave | Title | Item ID | Priority | Work type |
| --- | --- | --- | --- | --- |
| 1A | 수신함 목록 REPEATABLE_READ isolation 결함 수정 | `PVTI_lADOBD3v1M4BfKwozg5C8a8` | P1 | Bug |
| 1B | feed application seam 분리 | `PVTI_lADOBD3v1M4BfKwozg5C8eE` | P1 | Refactor |
| 2 | account package 생성자 convention 정리 | `PVTI_lADOBD3v1M4BfKwozg5C8hI` | P2 | Refactor |
| 2 | question package 생성자 convention 정리 | `PVTI_lADOBD3v1M4BfKwozg5C8kI` | P2 | Refactor |
| 3 | auth package 생성자·transaction convention 정리 | `PVTI_lADOBD3v1M4BfKwozg5C8oE` | P2 | Refactor |
| 3 | answer package TransactionTemplate 생성자 정리 | `PVTI_lADOBD3v1M4BfKwozg5C8qs` | P2 | Refactor |
| 2·4 | safety package convention 정리 | `PVTI_lADOBD3v1M4BfKwozg5C8uQ` | P2 | Refactor |
| 3·4·5 | direction package convention 정리 | `PVTI_lADOBD3v1M4BfKwozg5C8yA` | P2 | Refactor |
| 3·4·5 | filtering package convention 정리 | `PVTI_lADOBD3v1M4BfKwozg5C80g` | P2 | Refactor |
| 2·4·5 | notification package convention 정리 | `PVTI_lADOBD3v1M4BfKwozg5C83A` | P2 | Refactor |
| 6 | Java convention full production scan 전환 | `PVTI_lADOBD3v1M4BfKwozg5C85E` | P2 | Chore |

## Validation

Planning checks:

```bash
rg -n "TODO|TBD|PLACEHOLDER" TASK.md \
  docs/superpowers/specs/2026-09-02-production-convention-ratchet-design.md \
  docs/test-plans/gh-210-TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET.md
git diff --check
```

Implementation checks after separate test and implementation plan approval:

```bash
./gradlew javaConventionCheck
./gradlew test --tests '*JavaConvention*'
./gradlew check
npm run hooks:validate
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 사람이 risk-based 테스트 계획과 구현 계획을 별도로 승인했다.
- [x] 변경되지 않은 legacy Service는 `./gradlew javaConventionCheck`를 막지 않는다.
- [x] 새 Service의 transaction/injection 위반은 정확한 rule ID로 실패한다.
- [x] 기존 Service를 수정하면서 위반을 남기면 실패한다.
- [x] 수정된 Service가 모든 규칙을 만족하면 통과한다.
- [x] 새 `LEGACY` 추가나 hash-only 갱신은 계속 실패한다.
- [x] changed-file 판정이 `origin/main`과 staged manifest에서 동일하다.
- [x] production audit 결과와 실제 ratchet 실패 대상이 일치한다.
- [x] production Service 본문, API, DB schema, migration, query를 변경하지 않는다.
