# GitHub Issue #96 Task Contract

> Generated at: `2026-08-10T21:30:08+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신함 상세 조회 스코프 적용`
- GitHub Issue: `#96`
- Branch: `test/gh-96-inbox-detail-scope`
- Base branch: `main`

## Objective

- `JdbcInboxQueryRepository.findDetail()`에 수신함 목록과 답변 열람 자격에
  일치하는 질문글 스코프를 적용해, 넘김·무답변 만료·차단 발신자 항목이
  상세에서 다시 노출되지 않도록 한다.
- `ANSWERED`의 만료 후 열람과 `SKIP_PENDING`의 유예 중 열람은 유지하고,
  존재하지 않는 항목과 자격 없는 항목은 같은 빈 응답으로 처리한다.

## Scope

- 상세 조회의 소유권·질문글 ACTIVE·삭제 여부·차단 발신자·수신자 상태·만료
  스코프를 목록 및 `CAN_VIEW_ANSWERS_SQL` 규칙과 동기화한다.
- 조회 시각을 명시적으로 전달해 만료 경계를 결정한다.
- `SKIPPED`, 답변 없이 만료된 `EXPIRED`, 차단 발신자는 상세에서 제외한다.
- 만료 후 `ANSWERED`와 유예 중 `SKIP_PENDING`은 상세에 유지한다.
- 위 행위와 공통 자격 규칙의 재발 방지 통합 테스트를 추가한다.

## Explicit exclusions

- 스키마 변경, Flyway migration, 운영 행 백필
- `CAN_VIEW_ANSWERS_SQL`의 차단 발신자 정책 변경
- 컨트롤러·인증·마이탭 내 답변 조회 경로 구현
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 수신함 상세 조회 권한 스코프·SQL·통합 테스트 | Feature executor | 상태·만료·차단 조합과 정보 노출 경계 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과를 확인하고 여기에 기록한다.
- 확인 결과: 기존 사용자 변경 없음(clean).

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE` 승인 계획에 따라 테스트가 추가된다.
- [x] 상세 조회의 SKIPPED·무답변 EXPIRED·차단 발신자 차단이 검증된다.
- [x] ANSWERED 만료 후·SKIP_PENDING 유예 중 상세 조회가 유지된다.
- [x] 존재하지 않는 항목과 자격 없는 항목의 응답이 구분되지 않는다.
- [x] 테스트 클래스에 `@DisplayName`, ISO 8601 생성 시각과 source scenario가 있다.
- [x] 실행 결과·미검증 범위·잔여 위험이 테스트 보고서에 기록된다.
