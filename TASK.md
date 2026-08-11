# GitHub Issue #97 Task Contract

> Generated at: `2026-08-10T22:48:08+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신자 선정 필터·상한·분산`
- GitHub Issue: `#97`
- Branch: `feat/gh-97-recipient-filter-limit-distribution`
- Base branch: `main`

## Objective

- 후보 선정 시 차단 관계와 계정 상태를 반영하고, 발송별 수신자 인원 상한과
  최근 수신 횟수 기반 분산 정렬을 적용한다.
- 답변 열람 권한에도 양방향 활성 차단 관계를 적용해 후보 선정과 열람 정책의
  차단 전파 범위를 일치시킨다.

## Scope

- `ActiveUserPresenceSql.FIND_CANDIDATES_SQL`에 양방향 활성 차단 제외,
  `user_account.status = 'ACTIVE'` 필터, `recipient_receive_state` 기반
  분산 정렬을 적용한다.
- 후보가 발송별 인원 상한을 초과할 때 설정값
  `qello.direction.max-recipients-per-post`(MVP 기본값 10)까지만 확정되도록
  한다.
- 답변 열람 자격 SQL에도 양방향 활성 차단 조건을 적용한다.
- 실제 PostgreSQL/PostGIS 통합 테스트로 규칙별 선정·열람 결과를 검증한다.
- 인덱스 영향, transaction 경계, 동시 발송 시 상한·중복·카운터 정합성을
  테스트 계획에서 다룬다.

## Explicit exclusions

- 단계적 추가 선정 대기 시간(D18)과 후속 선정 워커는 포함하지 않는다.
- 발송별 수신자 상한의 기본값은 10으로 확정하지만 설정값으로 조정 가능하게
  하며, 운영 중 값을 변경하는 별도 배포 절차는 이 Issue 범위에 포함하지 않는다.
- 단계적 추가 수신자 선정 로직과 지도 마커 집계의 차단 제외는 포함하지 않는다.
- 스키마/Flyway migration은 추가하지 않는다. 기존 인덱스가 충분하지 않다면
  별도 Issue로 분리한다.
- API·인증·푸시·외부 연동·인프라 apply·배포는 포함하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 후보 SQL·발송별 상한·분산 정렬·통합 테스트 | Feature executor | 양방향 차단, ACTIVE 계정, 상한 단위, tie-break, 동시성 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과를 확인하고 여기에 기록한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 차단한 쪽과 차단당한 쪽 모두 후보에서 제외된다.
- `ACTIVE`가 아닌 계정은 후보에서 제외된다.
- 후보가 상한보다 많을 때 상한만큼만 수신자로 확정된다.
- 최근 수신 횟수가 적은 후보가 우선 선정되고 동률 순서가 결정론적이다.
- 인원 상한이 설정값으로 변경 가능하다.
- 차단 관계인 사용자는 답변을 열람할 수 없다.
- 규칙별 PostgreSQL/PostGIS 통합 테스트가 추가된다.
- 테스트 계획 승인 후에만 테스트·production 구현을 시작한다.
