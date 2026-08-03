# GitHub Issue #36 Task Contract

> Generated at: `2026-08-03T17:39:09+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Flyway와 최초 schema migration`
- GitHub Issue: `#36`
- Branch: `chore/gh-36-flyway-baseline`

## Objective

- 승인된 schema 계약을 Flyway 최초 versioned migration으로 구현하고, 빈
  PostgreSQL/PostGIS에서 전체 schema가 재현되고 재실행 가능한지 검증한다.

## Scope

- Spring Boot가 관리하는 Flyway core/PostgreSQL 의존성을 추가한다.
- Flyway가 startup 시 `db/migration`의 schema를 적용하도록 안전 설정을 추가한다.
- 승인된 독립 DDL을 빈 DB 전용 `V1` migration으로 고정한다.
- PostgreSQL/PostGIS Testcontainers에서 migration 성공, 재실행, checksum,
  schema history, 오브젝트 inventory를 검증한다.
- 실패 migration이 성공 상태로 남지 않는 복구 시나리오를 검증한다.
- 실행 증거와 잠재 위험을 Issue #36 테스트 보고서에 기록한다.

## Explicit exclusions

- JPA 의존성, Entity, Spring Data Repository, 제품 API를 추가하지 않는다.
- 기존 운영 DB baseline, Flyway repair/clean, 과거 migration 수정은 하지 않는다.
- 폐기된 `sql/001~004`를 migration 입력으로 사용하지 않는다.
- `region_code` 운영 seed와 미승인 보관·만료 정책 값을 넣지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Flyway dependency and configuration | Issue #36 | Backend review |
| V1 schema migration | Issue #36 | Schema contract review |
| Migration integration tests | TEST-PLAN-GH-36-FLYWAY-BASELINE | Test-plan approval |
| Production extension/role provisioning | Future infrastructure issue | Explicit human approval |

## Existing user-owned changes

- 작업 시작 시 Issue #35 승인 커밋 위에서 clean 상태였다.
- Issue #35의 accepted ADR과 schema manifest는 선행 계약으로 보존한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 빈 PostgreSQL/PostGIS에서 V1 migration이 성공한다.
- 같은 DB에서 두 번째 migrate가 신규 migration 없이 성공한다.
- `flyway_schema_history`에 V1이 성공 상태와 고정 checksum으로 기록된다.
- DBML/manifest의 26 tables, 45 FK, 18 unique constraint, 95 check,
  47 index, 9 trigger가 실제 schema와 일치한다.
- Hibernate/JPA 자동 DDL이 도입되지 않는다.
- 실패 migration 복구 동작과 남은 위험이 테스트 보고서에 기록된다.
- 모든 검증이 통과하고 로컬 커밋만 생성한 뒤 사용자 검토를 기다린다.
