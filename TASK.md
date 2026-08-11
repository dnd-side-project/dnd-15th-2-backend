# GitHub Issue #117 Task Contract

> Generated at: `2026-08-12T00:57:48+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `방향 preview 전체 구간 후보 집계`
- GitHub Issue: `#117`
- Branch: `feat/gh-117-direction-preview-all-segments`
- Base branch: `main`

## Objective

- 모바일 회전 중 방향별 preview 호출을 반복하지 않도록, repository와 service 결과 모델에서
  한 번의 PostgreSQL/PostGIS 질의로 모든 활성 direction segment의 예상 후보 수를 반환한다.

## Scope

- 모든 활성 direction segment를 한 번에 집계한다.
- `ST_DWithin`으로 반경 후보를 축소하고 `ST_Azimuth`로 방향을 계산한다.
- segment 경계는 시작각 포함·종료각 제외로 처리하고 0/360도 wrap-around 경계를 보존한다.
- 후보가 없는 segment를 `0` count로 채우는 service 결과를 구성한다.
- preview 결과 모델에는 사용자 ID와 정확 좌표를 포함하지 않는다.
- 방향, 날짜 변경선, 최소·최대 거리 경계를 실제 PostgreSQL/PostGIS 통합 테스트로 검증한다.

## Explicit exclusions

- REST Controller 구현
- preview cache
- 거리 정책의 미확정 기본 숫자 결정
- migration은 #115 범위와 조정하며, #117에서 임의로 추가하지 않는다.
- 수신자 목록 노출
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Direction repository SQL | Backend | PostGIS integration evidence |
| Direction preview service/model | Backend | Privacy and zero-fill test evidence |
| Test plan and test report | Backend | Human approval before implementation |

## Existing user-owned changes

- `./harness start` 전에는 테스트 계획 초안 파일만 존재했다.
- `./harness start`가 clean worktree를 요구하여 해당 파일을 임시 stash로 보존한 뒤
  `feat/gh-117-direction-preview-all-segments` 브랜치를 생성하고 복원했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 한 질의 결과로 모든 방향 구간의 count를 반환한다.
- 구간 경계의 중복·누락이 없다.
- 미리보기 결과에 사용자 식별자와 정확 좌표가 없다.
- 실제 PostgreSQL/PostGIS 통합 테스트가 통과한다.
- 승인된 테스트 계획 없이 테스트 또는 production code 구현을 시작하지 않는다.
