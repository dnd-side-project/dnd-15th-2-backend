# GitHub Issue #35 Task Contract

> Generated at: `2026-08-03T17:18:17+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `DB 스키마 계약 동기화와 Persistence ADR`
- GitHub Issue: `#35`
- Branch: `docs/gh-35-persistence-schema-contract`

## Objective

- 방향 소통 DBML·ERD·기준 DDL을 저장소 안의 검토 가능한 schema 계약으로
  동기화하고, 후속 Flyway/JPA 구현이 따라야 할 소유권과 기술 경계를 ADR로
  고정한다.

## Scope

- 기준 DBML과 ERD 설명을 원본과 동일한 내용으로 저장소에 보관한다.
- DBML·ERD·독립 실행형 DDL의 원본 경로, SHA-256, 오브젝트 목록을 schema
  manifest에 기록한다.
- `sql/001~004` 계보가 후속 migration의 입력이 아님을 명시한다.
- Flyway schema 소유권과 JPA/JDBC 책임 경계를 ADR로 기록한다.
- 인증, 만료, 보관·삭제, `updated_at`, FK 삭제, 지역 seed, 방향 coverage,
  PostGIS extension 권한의 결정 또는 명시적 제외를 기록한다.

## Explicit exclusions

- JPA/Flyway 의존성, migration SQL, 제품 Entity/Repository/API는 변경하지 않는다.
- 운영 DB를 조회하거나 변경하지 않는다.
- 폐기된 `sql/001~004`를 복사하거나 migration 입력으로 사용하지 않는다.
- 인증·개인정보·보관 기간 등 미승인 제품 정책을 임의 상수로 확정하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Schema source and manifest | Issue #35 | Backend + product policy review |
| Persistence ADR | Issue #35 | Backend review |
| Product policy pending values | Product/Security | Human approval before enforcement |
| Flyway/JPA implementation | Issues #36+ | Issue-by-issue user review |

## Existing user-owned changes

- 작업 시작 시 `main`은 clean 상태였다.
- `.harness-local/`의 로컬 계획 파일은 ignore 상태이며 변경하지 않는다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- DBML/ERD/DDL source와 SHA-256이 기록되어 있다.
- baseline의 테이블, FK, unique/check, index, trigger 목록과 수가 manifest에
  기록되어 있다.
- 인증·만료·보관·삭제·timestamp·seed·extension 권한이 결정 또는 명시적
  제외 상태다.
- Flyway/JPA/JDBC 책임 경계 ADR이 후속 Issue에서 바로 사용할 수 있다.
- 제품 코드와 migration은 변경하지 않았다.
- Validation 명령이 통과하고 변경은 Issue #35 브랜치에 로컬 커밋된다.
- origin push와 PR 생성 없이 사용자 검토를 기다린다.
