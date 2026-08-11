# GitHub Issue #115 Task Contract

> Generated at: `2026-08-11T16:37:27+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[Foundation] 방향 매칭 비동기 계약·스키마 동기화`
- GitHub Issue: `#115`
- Branch: `feat/gh-115-direction-matching-contract`
- Base branch: `main`

## Objective

- 비동기 방향 매칭 전환에 필요한 요청 fingerprint, 매칭 라운드 중복 방지,
  Outbox 임대·재처리 계약을 데이터베이스와 영속성 계층에 고정한다.
- 같은 Idempotency-Key의 동일 요청은 기존 결과를 반환하고, 다른 요청은 충돌로
  거절한다.

## Scope

- `direction_post.request_fingerprint` 저장 필드와 Flyway migration 추가
- 정규화된 발송 요청 입력 기반 fingerprint 생성과 동일성 검증
- `(post_id, match_round, event_type)` 기준 매칭 작업 중복 방지
- Outbox 배치 점유·임대·회수를 위한 컬럼과 인덱스 추가
- 도메인/JDBC 매핑 및 PostgreSQL 통합 테스트 추가
- ERD·API·테스트 계약 문서의 비동기 제출 구조 동기화

## Explicit exclusions

- 매칭 워커 구현
- REST Controller 구현
- 외부 푸시 연동
- 단계적 추가 수신자 정책
- P02/P03의 미확정 제품 숫자 결정
- H3·Redis·Kafka 도입
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `direction` persistence·Outbox 계약·통합 테스트 | Feature executor | 멱등성 충돌, migration 정합성, 좌표 비노출, 임대 재처리 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`를 확인했으며 기존 변경은 없었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 같은 멱등 키의 동일 요청 재시도가 기존 결과를 반환한다.
- 같은 멱등 키의 다른 요청이 `IDEMPOTENCY_KEY_REUSED` 충돌로 거절된다.
- 매칭 라운드 작업이 중복 생성되지 않는다.
- Outbox 작업의 임대 만료 후 재처리할 수 있다.
- 정확 좌표가 Outbox payload에 저장되지 않는다.
- PostgreSQL/PostGIS 통합 테스트가 migration과 제약조건을 검증한다.
