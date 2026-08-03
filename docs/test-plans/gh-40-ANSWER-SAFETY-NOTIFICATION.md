# Test Plan: TEST-PLAN-GH-40-ANSWER-SAFETY-NOTIFICATION

> Created at: `2026-08-03T20:40:25+09:00`
> GitHub Issue: `#40`
> Status: Draft — implementation is blocked until human approval

## 1. Objective

답변, 신고·차단, 알림 outbox를 V1 schema의 제약과 기능 경계에 맞게 영속화하고,
도메인 변경과 outbox 기록이 하나의 transaction에서 원자적으로 처리되는지 검증한다.
실패하면 답변 권한·참조 무결성 위반, 중복 신고/알림, 유실 또는 중복 전달, 실패 후
재처리 불가가 발생할 수 있으므로 PostgreSQL의 FK/unique/check/deferred trigger와
JPA/JDBC 책임 경계, 조건부 상태 갱신을 실제 통합 테스트로 증명한다.

## 2. Scope

### Included

- `answer`, `media_attachment`의 scalar-ID persistence, idempotency, content/status invariant
- `user_block`, `report`, `moderation_review`의 target/permission/duplicate 제약과 상태 전이
- `push_device`, `notification_preference`, `outbox_event`, `notification`, `notification_delivery`
- 도메인 변경 + outbox insert 동일 transaction, rollback 및 재시도/복구
- V1 전체 migration을 빈 PostgreSQL/PostGIS Testcontainers DB에 적용하는 schema 회귀
- 일반 CRUD의 JPA와 lock/conditional update/outbox dispatch의 JDBC 경계 검증

### Excluded

- V1 migration, DBML/ERD/schema manifest, ADR 수정 및 운영 DB 변경
- REST controller/API, 모바일 UI, 신규 인증·권한 정책
- 실제 push provider 호출·메시지 전송, 운영 모니터링·배포·AWS/RDS apply
- 승인되지 않은 개인정보 보관·삭제 기간, retry backoff, notification SLA의 신규 상수
- `sql/001~004` 또는 `old` 폴더의 폐기 설계

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #40 | 답변 참조 무결성, 신고·차단 중복/접근 제약, outbox 동일 transaction, 멱등 상태 전이, 빈 DB schema/repository 통합 검증 |
| `TASK.md` | Answer/Safety/Notification 범위, scalar FK, JPA/JDBC 경계, 미정 정책값 비고정, 로컬 검토 게이트 |
| `docs/adr/0001-database-schema-ownership.md` | Flyway V1이 schema source of truth이며 Hibernate 자동 DDL을 사용하지 않음 |
| `docs/adr/0002-jpa-jdbc-boundary.md` | 단순 CRUD는 JPA, lock·조건부 갱신·bulk·dispatch는 JDBC; feature 간 Entity/Repository 직접 참조 금지 |
| `V1__create_direction_communication_schema.sql` | Answer FK/unique/check/deferred content trigger, Safety target/open-report index, Notification/outbox status/dedup/index 계약 |
| #38/#39 결과 | 외부 aggregate는 구현체가 아닌 scalar `post_recipient_id`, `direction_post_id`, `answer_id`로 참조 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 답변 author가 recipient가 아니거나 존재하지 않는 FK를 사용한다. | 타인 답변·참조 무결성 위반 | Medium | P0 | composite FK integration + application authorization unit |
| idempotency key 또는 media attachment 중복이 race에서 허용된다. | 답변/콘텐츠 중복 | Medium | P0 | unique conflict and retry integration |
| published/deleted 상태와 timestamp/content가 어긋난다. | 빈 공개 콘텐츠·복구 불가 상태 | Medium | P0 | check/deferred trigger integration |
| block/report target XOR·self/중복 규칙이 누락된다. | 안전 기능 우회·신고 큐 중복 | High | P0 | DB constraint and permission unit/integration |
| 도메인 변경만 commit되거나 outbox만 남는다. | 알림 유실 또는 유령 알림 | High | P0 | forced failure rollback transaction test |
| outbox/delivery claim이 중복 처리 또는 영구 정체된다. | 중복 전달·재시도 불가 | High | P0 | conditional update/row-lock concurrency and failure recovery |
| notification/delivery dedup이 recipient/device 범위에서 맞지 않는다. | 사용자별 중복 알림 | Medium | P0 | duplicate insert/retry integration |
| JPA가 V1 schema를 변경하거나 JDBC 경계가 무너진다. | migration drift·잠금 오류 | Medium | P1 | `ddl-auto=validate`, architecture/source scan |
| token/payload/detail 등 민감값이 로그·DTO·계획에 노출된다. | 개인정보·보안 사고 | Low | P0 | source scan and log/DTO boundary test |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| ANSWER-SAFETY-NOTIFICATION-UNIT-001 | recipient ID, author ID, body/media, status/timestamp 조합 | Answer command 검증·상태 전이 | recipient-author 관계, body/media content, published/deleted timestamp, 허용 전이가 V1 계약과 일치하고 invalid transition은 거절된다. | P0 | Answer executor |
| ANSWER-SAFETY-NOTIFICATION-UNIT-002 | 동일 author와 idempotency key, media attachment 대상 | create/retry mapping | 동일 요청은 멱등 결과로 처리되고 다른 answer/media 대상 충돌은 명시적 conflict가 된다. | P0 | Answer executor |
| ANSWER-SAFETY-NOTIFICATION-UNIT-003 | blocker/blocked 계정과 report target 후보 | block/report command 검증 | 자기 차단, 다중 target, 빈 reason, 권한 밖 target을 거절하고 open report 중복 키를 계산한다. | P0 | Safety executor |
| ANSWER-SAFETY-NOTIFICATION-UNIT-004 | report 상태와 moderation decision | review transition | RECEIVED/UNDER_REVIEW/ACTIONED 계열 전이와 decision/action mapping이 허용 목록을 따르며 임의 상태는 거절된다. | P0 | Safety executor |
| ANSWER-SAFETY-NOTIFICATION-UNIT-005 | outbox/delivery status, attempt count, nextAttemptAt, processed/sent time | claim/success/failure/retry 명령 | 조건부 갱신 대상과 affected-row 판정을 사용하고 terminal 상태 재처리는 멱등이며 임의 retry 기간을 계산하지 않는다. | P0 | Notification executor |
| ANSWER-SAFETY-NOTIFICATION-UNIT-006 | domain event와 dedup key/notification target | notification command 생성 | aggregate/event type과 target cardinality가 V1 목록을 따르고 payload는 object이며 provider 호출 없이 persistence port만 사용한다. | P0 | Notification executor |
| ANSWER-SAFETY-NOTIFICATION-UNIT-007 | feature source set와 repository imports | architecture scan | Answer/Safety/Notification이 다른 feature Entity/Spring Data Repository를 직접 참조하지 않고 scalar IDs와 승인된 port만 사용한다. | P1 | Test orchestrator |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| ANSWER-SAFETY-NOTIFICATION-INT-001 | Flyway, Hibernate validation, JDBC | 빈 PostgreSQL/PostGIS Testcontainer | V1 적용, extension/table/index/trigger 및 mapping 확인 | 전체 migration이 재현되고 `ddl-auto=validate`가 통과하며 schema 변경은 발생하지 않는다. | container 종료 |
| ANSWER-SAFETY-NOTIFICATION-INT-002 | Answer repository + `post_recipient` FK | recipient-author 일치/불일치 fixture | answer 생성·조회·retry 실행 | 일치하는 답변만 저장되고 composite FK/unique conflict는 rollback되며 조회가 scalar 참조를 보존한다. | answer/media/recipient 역순 삭제 |
| ANSWER-SAFETY-NOTIFICATION-INT-003 | Answer + media attachment + deferred content trigger | body-only, READY media, empty content fixture | publish, attach/detach, media status 변경 | 공개 답변은 body 또는 READY media가 필요하고 잘못된 변경은 commit 시 rollback된다. | attachment → answer |
| ANSWER-SAFETY-NOTIFICATION-INT-004 | Block repository | 두 계정, active/released block fixture | 동일 block 재시도, self block, release 후 재생성 | PK와 self CHECK가 중복/자기 차단을 막고 release 상태 재활성화 동작은 명시된 port 계약대로만 수행된다. | block rows |
| ANSWER-SAFETY-NOTIFICATION-INT-005 | Report + moderation review | user/post/answer target 및 상태 fixture | target별 신고, open duplicate, review 처리 | target XOR, reporter/target FK, open partial unique index, decision/review FK가 DB와 application에서 일치한다. | review → report |
| ANSWER-SAFETY-NOTIFICATION-INT-006 | Answer/Safety transaction service + outbox | 유효 command, forced repository failure fixture | 도메인 변경 후 outbox insert를 같은 transaction에서 실행 | 모두 commit되거나 모두 rollback되고 중간 실패 후 orphan outbox/domain row가 없다. | transaction rollback/container |
| ANSWER-SAFETY-NOTIFICATION-INT-007 | Outbox repository | PENDING/FAILED/PROCESSING/PROCESSED/DEAD fixture | claim, success, fail, retry, terminal retry 실행 | `dedup_key`, status, attempt, nextAttemptAt, processedAt 불변식과 조건부 갱신이 멱등이며 stale worker는 갱신하지 못한다. | outbox rows |
| ANSWER-SAFETY-NOTIFICATION-INT-008 | Notification + delivery repository | recipient, outbox, active/revoked devices, duplicate keys | notification 생성/읽음 전환/delivery retry 실행 | recipient dedup, target XOR, device unique, SENT/sentAt 및 revoked device FK/상태 제약이 보장된다. | delivery → notification |
| ANSWER-SAFETY-NOTIFICATION-INT-009 | Concurrent outbox/delivery workers | 같은 PENDING row와 두 worker transaction | 동시에 claim 및 결과 반영 | 한 worker만 claim하고 다른 worker는 affected-row 0으로 재조회하며 중복 처리/attempt 손실이 없다. | container rollback |
| ANSWER-SAFETY-NOTIFICATION-INT-010 | Full V1 schema regression + repository adapters | 모든 관련 parent fixture와 빈 schema | 전체 repository test suite 및 migration 재실행 | FK 삭제 순서, index/trigger, 기존 Account/Question/Direction 테스트가 회귀 없이 통과한다. | container 폐기 |

## 7. Cross-cutting scenarios

### Database and transactions

- H2가 아닌 PostgreSQL/PostGIS Testcontainers에서 BYTEA, JSONB, partial unique index,
  deferred trigger, FK `ON DELETE` 정책을 실제로 실행한다.
- Flyway가 migration을 단독 소유하며 Hibernate는 `validate`만 사용한다. V1 파일과
  schema manifest checksum을 수정하지 않는다.
- domain row, related notification row, outbox row, delivery claim의 commit/rollback
  경계를 테스트 증거와 보고서에서 분리한다.

### Concurrency and idempotency

- 동일 answer idempotency, open report, notification dedup, delivery device unique를
  동시 요청으로 실행해 DB unique를 최종 방어선으로 확인한다.
- outbox/delivery claim은 status와 `next_attempt_at` 조건을 포함한 조건부 갱신 또는
  row lock과 affected-row 판정을 사용한다. 승인된 schema에 없는 version column이나
  retry deadline을 추가하지 않는다.
- serialization/deadlock/unique conflict 후 새 transaction retry가 중복 row 없이
  동작하는지 확인한다.

### External APIs

- 실제 push provider 호출은 없다. 외부 경계는 persistence port와 fake dispatcher로
  대체하고, provider message ID는 선택적 문자열 mapping만 검증한다.
- Testcontainers의 PostgreSQL/PostGIS만 실제 infrastructure dependency로 사용한다.

### Failure recovery and reconciliation

- 도메인 insert 성공 직후 outbox insert 실패, outbox claim 후 worker 실패, provider
  결과 반영 전 timeout을 각각 유도한다.
- 재시도 후 aggregate/outbox/delivery count, dedup key, attempt count를 재조회해
  orphan/duplicate가 없는지 확인한다.
- token ciphertext, fingerprint, notification payload/detail이 로그·예외·테스트
  fixture·DTO에 그대로 노출되지 않는지 source scan으로 확인한다.

## 8. Test data and isolation

- Fixtures: region, account, direction post/recipient, answer body/READY media,
  blocker/blocked users, report targets, reviewer, outbox event, notification,
  active/revoked push devices.
- Database isolation: 일반 시나리오는 transaction rollback 또는 scenario-prefix로
  격리하고, deferred trigger/lock/EXPLAIN은 명시적 commit 후 container lifecycle로
  정리한다.
- Clock/randomness: UTC fixed `Clock`; `createdAt`, `nextAttemptAt`, `processedAt`,
  `sentAt`은 fixture에서 주입하며 미정 보관·retry 기간을 계산하지 않는다.
- External API doubles: provider 없음; dispatcher port fake만 사용한다.
- Cleanup: delivery → notification → outbox, moderation → report, attachment → answer,
  block/device → account 순서 또는 container 폐기로 FK 역순 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Answer executor | `src/main/java/com/dnd/qello/answer/**`, `src/test/java/com/dnd/qello/answer/**` | UNIT-001~002, INT-002~003 | Answer unit + PostgreSQL FK/content tests |
| 2 | Safety executor | `src/main/java/com/dnd/qello/safety/**`, `src/test/java/com/dnd/qello/safety/**` | UNIT-003~004, INT-004~005 | block/report/review constraint and permission tests |
| 3 | Notification executor | `src/main/java/com/dnd/qello/notification/**`, `src/test/java/com/dnd/qello/notification/**` | UNIT-005~006, INT-007~009 | outbox/delivery status, dedup, concurrency tests |
| 4 | Transaction executor | `src/main/java/com/dnd/qello/*/service/**`, `src/test/java/com/dnd/qello/*/service/**` | INT-006, cross-cutting transaction/recovery | forced failure rollback and retry evidence |
| 5 | Test orchestrator | `src/test/java/com/dnd/qello/architecture/**`, `docs/reports/tests/gh-40-ANSWER-SAFETY-NOTIFICATION.md` | UNIT-007, INT-001/010, all P0 | full Gradle/Testcontainers + report + Harness gates |

각 executor는 소유 경로 밖의 파일을 수정하지 않는다. 새 dependency, V1 migration 변경,
공유 fixture 변경은 별도 검토 대상으로 중단하고 상위 작업에 보고한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현 및 결과 증거 확보
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 클래스 헤더에 정확한 ISO 8601 timestamp와 source scenario ID
- [ ] 단위 및 실제 PostgreSQL/PostGIS 통합 테스트 통과
- [ ] 동일 transaction rollback, 조건부 claim, 중복 retry, 실패 복구 증거 확보
- [ ] DB 제약과 application 보완 규칙, P1 잔여 위험을 테스트 보고서에서 분리
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] `./harness check`, `./harness pr-ready --project-tests`,
  `npm run hooks:validate`, `git diff --check` 통과
- [ ] 구현 결과를 origin에 push하지 않고 사용자 검토 대기

## 11. Human approval

- Reviewer: User
- Decision: Pending
- Approved at:
