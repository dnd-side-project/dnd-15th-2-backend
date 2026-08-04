# GitHub Issue #53 Task Contract

> Generated at: `2026-08-05T02:49:46+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[A] 스키마 계약 재동기화`
- GitHub Issue: `#53`
- Branch: `docs/gh-53-sync-schema-contract`

## Objective

- 2026-08-04 개정된 DBML과 ERD를 저장소의 스키마 계약으로 동기화한다.
- V2 migration 작성 전에 오브젝트 카운트와 출처 checksum을 manifest에 고정한다.

## Scope

- 개정 DBML·ERD를 원본과 byte 동일하게 복사한다.
- manifest `§3` Source snapshot을 갱신한다. 기존 standalone DDL 행은 "V1 원본"으로
  이력을 남기고, 2026-08-04 target DDL을 새 행으로 추가한다.
- manifest `§5` 카운트와 `§6`~`§9` 인벤토리를 갱신한다.

## Explicit exclusions

- Flyway migration 작성은 #54에서 한다. 이 브랜치에서 `db/migration`을 건드리지 않는다.
- Entity, Repository, 제품 코드는 변경하지 않는다.
- 적용 완료된 `V1__create_direction_communication_schema.sql`은 수정하지 않는다.
- 카운트가 실제 schema와 맞는지는 #54의 통합 테스트가 증명한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `docs/product/data-model/` | @Byuntil | @Byuntil |

## Existing user-owned changes

- 브랜치 생성 시점(2026-08-05)에 `git status --short`가 비어 있었다. 정리한 타인의
  변경은 없다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] DBML·ERD가 원본 checksum과 일치한다.
- [ ] V1의 sha256 잠금과 모순되지 않는 형태로 target DDL 출처가 기록됐다.
- [ ] 테이블 28 / FK 48 / CHECK 97 / 인덱스 50 / 함수 11 / 트리거 10이 기록됐다.
- [ ] Flyway migration과 제품 코드가 diff에 없다.
- [ ] `git diff --check`, `./harness check`, `npm run hooks:validate`가 통과한다.
