# GitHub Issue #212 Task Contract

> Generated at: `2026-09-02T19:05:27+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신함 목록 REPEATABLE_READ isolation 결함 수정`
- GitHub Issue: `#212`
- Branch: `fix/gh-212-inbox-list-isolation`
- Base branch: `main`
- Task ID: `GH-212-INBOX-LIST-ISOLATION`
- Design ID: `APP-DESIGN-GH-212-001`
- Test plan: `TEST-PLAN-GH-212-INBOX-LIST-ISOLATION`
- Test plan path:
  `docs/test-plans/gh-212-TEST-PLAN-GH-212-INBOX-LIST-ISOLATION.md`
- Test plan status: `APPROVED_FOR_IMPLEMENTATION_PLAN`
- Test plan approval evidence: `2026-09-02T19:20:00+09:00` 사용자가
  `TEST-PLAN-GH-212-INBOX-LIST-ISOLATION`를 승인함
- Implementation plan:
  `docs/superpowers/plans/2026-09-02-inbox-list-isolation.md`
- Implementation plan status: `APPROVED_FOR_BUILD`
- Implementation plan approval evidence: `2026-09-02T19:28:00+09:00` 사용자가
  구현 계획을 승인하고 현재 #212 checkout에서 구현 시작을 요청함
- Implementation gate: `APPROVED_FOR_BUILD`

## Objective

- `InboxApplicationService.list()`가 바깥 `READ_COMMITTED`에 묶여
  `InboxQueryService.list()`의 `REPEATABLE_READ`가 무시되는 결함을 고친다.
- 같은 `list()` 호출의 목록과 direction chip이 하나의 snapshot을 보게 한다.

## Scope

- `InboxApplicationService.list()`에서 실제 `REPEATABLE_READ`가 시작되게 조정
- 목록 SELECT 직후 다른 transaction이 새 항목을 commit해도 chip 집계가
  새 항목을 보지 않는 concurrency integration test
- 목록과 chip count가 하나의 snapshot에서 일치하는지 검증
- 상세 열람에서 `OPENED` 전이 후 projection 조회 실패 시 전체 rollback 회귀 유지
- 손대는 production `@Service`는 Wave 0 ratchet
  (`QELLO-JAVA-TX-001`·`TX-002`·`TX-003`·`QELLO-JAVA-INJECTION-001`)을 맞춘다
- `REPEATABLE_READ` isolation이 남으면 `JUSTIFIED_EXCEPTION`으로 등록한다

## Approved decisions

- `DEC-212-001`: `REPEATABLE_READ`는 `InboxApplicationService.list()`에서 시작한다.
  `InboxQueryService.list()`에 `REQUIRES_NEW`를 두지 않는다.
- `DEC-212-002`: 이 파일을 고치므로 class-level `@Transactional(readOnly = true)`를
  추가한다. write method는 기존 method-level `@Transactional`을 유지한다.
- `DEC-212-003`: isolation을 검사하지 않는 ArchUnit 규칙에 dummy
  `JUSTIFIED_EXCEPTION`을 넣지 않는다.
- `DEC-212-004`: feed seam 분리, HTTP/DB 계약 변경, `InboxQueryService` 구조
  변경은 제외한다.
- `DEC-212-005`: `QELLO-JAVA-TX-003`은 같은 클래스 self-invocation만 실패한다.
  다른 Spring bean의 `@Transactional` 호출은 오탐이다.

## Explicit exclusions

- feed application seam 분리 (Wave 1B draft)
- baseline 51건 제거
- 전체 production scan 전환
- HTTP 경로, 오류 코드, DB schema, migration 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·설계·Issue 계약 통합 | Orchestrator | Human partner |
| isolation 경계 수정 | Execution agent | Independent verifier |
| concurrency integration test | Test executor | Independent verifier |
| production convention ratchet | Execution agent | Independent verifier |
| 최종 범위·위험·증거 승인 | PM reviewer | Human partner |

## Existing user-owned changes

- Issue intake 시작 시 작업 트리는 깨끗했고, 기존 사용자 변경은 없었다.
- `./harness start`가 최신 `origin/main`(`46377d8`, PR #211)에서
  `fix/gh-212-inbox-list-isolation`를 생성했다.
- 현재 변경은 이 `TASK.md`뿐이다.
- 범위 밖 기존 파일과 다른 사용자의 변경을 자동 정리하거나 되돌리지 않는다.

## Validation

Planning checks:

```bash
rg -n "TODO|TBD|PLACEHOLDER" TASK.md
git diff --check
```

Implementation checks after separate test and implementation plan approval:

```bash
./gradlew javaConventionCheck
./gradlew test --tests '*Inbox*'
./gradlew check
npm run hooks:validate
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 사람이 risk-based 테스트 계획과 구현 계획을 별도로 승인했다.
- [x] 같은 `list()` 호출의 목록과 chip 집계가 하나의 `REPEATABLE_READ` snapshot을 사용한다.
- [x] 첫 SELECT 이후 커밋된 새 항목이 같은 호출의 chip count에 섞이지 않는다.
- [x] 상세 열람 `OPENED` 전이 후 projection 조회 실패 시 전체 rollback이 유지된다.
- [x] HTTP 경로, 오류 코드, DB schema는 변경하지 않는다.
- [x] feed 모듈 분리와 같은 PR에 섞지 않는다.
- [x] 변경된 production Service가 `javaConventionCheck`를 통과한다.
