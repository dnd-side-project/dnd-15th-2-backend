# GitHub Issue #37 Task Contract

> Generated at: `2026-08-03T18:01:08+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `JPA 공통 규칙과 Account 첫 수직 슬라이스`
- GitHub Issue: `#37`
- Branch: `feat/gh-37-account-persistence`

## Objective

- 승인된 Flyway schema 위에 JPA 공통 매핑 규칙을 도입하고 `user_account`를
  domain model → repository port → JPA adapter로 연결하는 첫 persistence 수직
  슬라이스를 구현한다.

## Scope

- Spring Boot가 관리하는 Spring Data JPA 의존성을 추가한다.
- Hibernate는 Flyway가 만든 schema를 `validate`만 하고 생성·수정하지 않게 한다.
- Open EntityManager in View를 끄고 PostgreSQL `TIMESTAMPTZ`를 `Instant`로 매핑한다.
- JPA write의 `created_at`, `updated_at`을 Spring Data auditing으로 관리한다.
- `account` feature 안에 Spring/JPA에 의존하지 않는 Account domain model과
  repository port를 둔다.
- `user_account` 전용 JPA Entity, Spring Data repository, mapper, adapter를 둔다.
- `role`, `status`는 ordinal이 아닌 문자열 enum으로 매핑한다.
- `coarse_region_code`는 다른 Entity 관계가 아닌 scalar FK ID로 매핑한다.
- PostgreSQL/PostGIS Testcontainers에서 저장·조회·갱신·제약 위반·rollback과
  Flyway/Hibernate schema 경계를 검증한다.

## Explicit exclusions

- 적용된 V1 migration과 DBML/ERD/schema manifest는 수정하지 않는다.
- 새 schema migration이나 Hibernate DDL 생성·수정은 추가하지 않는다.
- `region_code`, `user_private_attribute`, `active_user_presence`,
  `recipient_receive_state` Entity/Repository는 구현하지 않는다.
- schema에 version column이 없으므로 이번 Issue에서는 `@Version` 낙관적 잠금을
  도입하지 않는다. 동시 수정 lost-update 검증은 잔여 위험으로 기록한다.
- 닉네임 고유성·변경 주기, 인증·세션, API와 제품 상태 전이 정책을 만들지 않는다.
- Question, Direction, Answer, Safety, Notification persistence를 구현하지 않는다.
- 다른 feature가 Account JPA Entity 또는 Spring Data Repository를 참조하게 하지
  않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| JPA dependency, Hibernate safety config, auditing | Issue #37 configuration executor | Backend review |
| Account domain model and repository port | Issue #37 account executor | Domain boundary review |
| Account JPA Entity, mapper and adapter | Issue #37 account executor | Persistence review |
| Unit/integration tests and report | TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE | Test-plan approval |

## Existing user-owned changes

- Issue #36 승인 commit `49b1d3c`을 origin에 push한 clean 상태에서 분기했다.
- Issue #35/#36의 ADR, schema manifest, Flyway V1과 migration tests를 선행 계약으로
  보존한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- Account 저장 후 identity ID와 auditing timestamp가 생성된다.
- Account를 ID로 조회하고 domain model로 복원할 수 있다.
- enum은 `USER`/`OPERATOR`, `ACTIVE`/`BLOCKED`/`DELETED` 문자열로 저장된다.
- nickname과 상태를 순차 갱신하면 `updated_at`이 갱신되고 다시 조회된다.
- 없는 region FK, 공백 nickname, status/deleted_at 불일치가 DB 또는 domain
  경계에서 거절되고 transaction이 부분 반영되지 않는다.
- Hibernate `ddl-auto=validate`, `generate-ddl=false`, `open-in-view=false`가
  설정되고 startup 후 Flyway schema inventory가 변경되지 않는다.
- domain 계층이 Spring Data/JPA에 의존하지 않고 다른 feature가 Account Entity나
  Spring Data Repository를 직접 참조하지 않는다.
- 모든 JUnit 5 테스트와 Harness, Gradle check, Hook 검증이 통과한다.
- 구현과 테스트 보고서를 로컬 commit까지만 만들고 origin에는 push하지 않은 채
  사용자 검토를 기다린다.
