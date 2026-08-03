# GitHub Issue #40 Task Contract

> Generated at: `2026-08-03T20:39:44+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[F] Answer/Safety/Notification persistence`
- GitHub Issue: `#40`
- Parent: `#34`
- Branch: `feat/gh-40-answer-safety-notification`
- Status: Planning — implementation blocked until human approval of the test plan
- Origin/PR: 이 단계에서는 push와 PR 생성을 하지 않는다.

## Objective

V1 schema에 이미 정의된 Answer, Safety, Notification 관련 영속성 경계를 구현한다.
답변의 참조 무결성, 신고·차단의 중복/접근 제약, 알림 outbox와 전달 상태 전이,
도메인 변경과 outbox 기록의 동일 transaction 및 실패 후 재시도 가능성을
PostgreSQL/Testcontainers 증거로 검증한다.

## Scope

### Answer

- `answer`, `media_attachment`의 scalar-ID domain model, repository port/adapter 및
  필요한 JPA/JDBC mapping을 구현한다.
- `(post_recipient_id, author_id)` 참조 무결성, `(author_id, idempotency_key)`
  멱등성, 상태·moderation·published/deleted timestamp와 body/media content
  invariant를 V1과 일치시킨다.
- `post_recipient`의 recipient만 답변 author가 될 수 있도록 권한 경계를
  persistence/application에서 검증한다.

### Safety

- `user_block`, `report`, `moderation_review`의 persistence adapter와 상태 전이를
  구현한다.
- 자기 차단, target XOR, reporter/target FK, open report 중복 partial unique index,
  review decision 제약을 DB 제약과 application 검증으로 구분한다.
- 신고·차단의 개인정보/보관 기간은 정책 결정 전까지 임의 상수로 고정하지 않는다.

### Notification / Outbox

- `push_device`, `notification_preference`, `outbox_event`, `notification`,
  `notification_delivery` persistence와 repository port를 구현한다.
- 도메인 변경과 `outbox_event` 기록을 동일 transaction에 묶고, `dedup_key` 및
  `(notification_id, push_device_id)` unique 방어선을 사용한다.
- outbox/delivery의 `PENDING → PROCESSING → PROCESSED/SENT` 및 실패·재시도·dead
  전이를 조건부 갱신 또는 row lock으로 멱등하게 구현한다. 실제 push provider 호출은
  포함하지 않는다.

### Boundary and verification

- 일반 aggregate CRUD는 JPA, 조건부 갱신·락·bulk·outbox dispatch query는 JDBC를
  사용하며 feature 간 Entity/Repository 직접 참조를 금지한다.
- 빈 PostgreSQL/PostGIS DB에서 Flyway V1 전체 schema와 repository 통합 테스트를
  실행하고, transaction rollback·동시성·재시도·복구 증거를 남긴다.

## Explicit exclusions

- V1 migration, DBML/ERD/schema manifest 및 기존 ADR 변경
- REST controller/API, 모바일 UI, 인증 provider 또는 권한 정책의 신규 설계
- 실제 push provider 연동·메시지 전송, 운영 모니터링·배포·AWS/RDS apply
- 승인되지 않은 개인정보 보관/삭제 기간, 알림 SLA, backoff/retention 기간의 신규
  상수화
- `sql/001~004` 또는 `old` 계보를 구현 입력으로 사용
- 사용자 검토 전 구현, origin push, PR 생성

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Answer domain, mapping, repository adapter | Answer executor | FK/idempotency/content invariant review |
| Block/report/moderation persistence | Safety executor | target/permission/duplicate review |
| Notification/outbox/delivery persistence | Notification executor | status transition/dedup/conditional update review |
| Transaction orchestration and failure recovery | Persistence transaction executor | same-transaction and rollback review |
| Unit/integration tests and report | Test orchestrator | TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION |

각 executor는 테스트 계획에 지정된 소유 경로 밖의 파일을 수정하지 않는다. 공유
fixture 또는 V1 schema 변경이 필요하면 구현 전에 별도 검토 대상으로 올린다.

## Source-of-truth constraints

- Flyway `src/main/resources/db/migration/V1__create_direction_communication_schema.sql`
  이 table/constraint/index/trigger 계약의 기준이다.
- `docs/adr/0001-database-schema-ownership.md`에 따라 schema 변경은 Flyway가
  소유하고 Hibernate 자동 DDL은 사용하지 않는다.
- `docs/adr/0002-jpa-jdbc-boundary.md`에 따라 JPA/JDBC 책임을 지키고 외부 aggregate는
  scalar ID로만 참조한다.
- #39 Direction/PostGIS 결과를 직접 Entity/Repository 의존성으로 가져오지 않고
  `post_recipient_id`, `direction_post_id`, `answer_id` 등 scalar FK로 연결한다.
- V1에 없는 만료·보관·삭제·재시도 기간은 `미정`으로 남기고 구현에서 결정하지 않는다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

Java/Gradle 검증은 저장소가 요구하는 Java 21 toolchain으로 실행한다. Testcontainers가
필요한 검증이 환경 문제로 실패하면 원인과 미검증 범위를 보고하며 성공으로 표현하지
않는다.

## Completion criteria

- Answer 생성/조회와 recipient-author 참조 무결성이 빈 DB에서 검증된다.
- report target XOR, open duplicate, block self/duplicate 및 moderation decision이
  DB/application 계약과 일치한다.
- 도메인 변경과 outbox insert가 같은 transaction에서 함께 commit/rollback된다.
- outbox/delivery 상태 전이가 중복 실행에 안전하고 실패 후 재시도 가능하다.
- exact token/payload/개인정보가 로그·테스트 계획·DTO에 노출되지 않는다.
- 모든 테스트 클래스는 정확한 ISO 8601 생성 시각과 `TEST-PLAN-GH-40-...` 원본
  시나리오를 기록하고, 모든 테스트 메서드는 `@DisplayName`을 가진다.
- 테스트 보고서와 Harness/Gradle/Hook 검증을 완료한다.
- 구현은 로컬 commit까지만 만들고 origin push/PR 생성 전 사용자 검토를 기다린다.
