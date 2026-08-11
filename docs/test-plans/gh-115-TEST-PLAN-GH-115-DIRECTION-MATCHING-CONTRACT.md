# Test Plan: TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT

> Created at: `2026-08-11T18:58:06+09:00`
> GitHub Issue: `#115`
> Status: Approved for implementation and test execution

## 1. Objective

비동기 매칭 제출을 재시도해도 동일한 논리 요청이 한 번만 매칭 작업으로
전환되고, 다른 요청을 같은 `Idempotency-Key`로 덮어쓰지 않는지 검증한다.
매칭 라운드 중복과 Outbox lease 경쟁이 허용되면 중복 수신·중복 알림·매칭
누락이 발생할 수 있다. 요청 fingerprint, `(post_id, match_round, event_type)`
유일성, batch claim/reclaim, 트랜잭션 원자성, payload의 정확 좌표 비노출을
실제 PostgreSQL/PostGIS 경계에서 증명한다.

## 2. Scope

### Included

- 정규화된 발송 요청에서의 deterministic request fingerprint 생성과 저장·비교
- 동일 Idempotency-Key + 동일 fingerprint의 기존 결과 반환
- 동일 Idempotency-Key + 다른 fingerprint의 `IDEMPOTENCY_KEY_REUSED` 충돌
- 매칭 라운드 작업의 `(post_id, match_round, event_type)` 중복 방지
- Outbox batch 점유, lease 만료 회수, 재시도 횟수와 상태 전이
- `direction_post`·매칭 작업·Outbox의 domain/JDBC 매핑과 Flyway 제약·인덱스
- 제출 transaction의 commit/rollback, 동시성 및 장애 후 재처리
- Outbox JSON payload에 정확 위도·경도를 저장하지 않는 안전성 계약
- 비동기 제출 구조에 맞춘 ERD·API·테스트 계약 문서의 검증 항목

### Excluded

- 매칭 worker의 실제 후보 선정·정렬·수신자 확정 알고리즘
- REST Controller와 실제 외부 push provider 연동
- 단계적 추가 수신자 정책, P02/P03의 미확정 숫자
- H3·Redis·Kafka 도입, 인프라 apply·배포·운영 DB 변경
- 실제 좌표, URL, 토큰, 계정 식별자와 같은 운영 민감정보

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #115 / 현재 브랜치 `TASK.md` 계약 | `direction_post.request_fingerprint` 저장, 정규화 입력 fingerprint 동일성 검증, 매칭 라운드 중복 방지, Outbox batch 점유·임대·회수 필드/인덱스, domain/JDBC 및 PostgreSQL 통합 검증을 수행한다. 동일 요청은 기존 결과를 반환하고 다른 요청은 `IDEMPOTENCY_KEY_REUSED`로 거절한다. 아래 결정 기록의 v1 계약을 테스트 입력으로 사용한다. |
| `AGENTS.md` §3, §11 | JUnit 5, 단위/통합 분리, 모든 메서드 `@DisplayName`, 테스트 클래스의 정확한 ISO 8601 생성 시각과 source scenario, 실패 원인·환경 구분, PASS/FAIL/BLOCKED 보고를 따른다. |
| `docs/adr/0001-database-schema-ownership.md` | Flyway가 schema source of truth이며 적용된 migration을 수정하지 않는다. 새 migration과 DBML/ERD/manifest 정합성을 함께 검증한다. |
| `docs/product/data-model/schema-manifest.md`, `direction_communication.dbml`, `DIRECTION_COMMUNICATION_ERD.md` | 현재 `direction_post`·`outbox_event`·partial dispatch index와 transactional outbox 관계를 기준으로 새 필드·제약·인덱스가 세 산출물과 동기화되는지 확인한다. |
| 현재 코드 baseline | `DirectionPostService.send()`는 현재 idempotency key로 기존 post를 조회하고, `OutboxEvent`/`JdbcNotificationRepository`는 PENDING/FAILED claim만 제공한다. #115 테스트는 이 baseline을 회귀 기준으로 삼되, 아래 결정된 lease·matching job 동작은 신규 계약으로 검증한다. |

## 3.1 Decision record — implementation/test contract

### A. Request fingerprint

- 범위는 기존 DB 유일성 범위와 동일한 `(sender_id, idempotency_key)`다. `sender_id`와
  `idempotency_key`는 lookup namespace이므로 fingerprint 입력에는 넣지 않는다.
- fingerprint 대상은 사용자 의도인 `approvedQuestionId`, `schemeId`, `segmentKey`,
  `minDistanceMeters`, `maxDistanceMeters`, `coarseRegionCode`, `bodyText`다.
  현재 `SendCommand`의 `submittedAt`·`expiresAt`은 서버 소유 시각이며 제외한다.
  향후 client-controlled expiry 또는 media 입력을 추가할 때는 별도 fingerprint
  version 계약이 필요하다.
- 문자열은 Unicode NFC 정규화 후 바깥 Unicode whitespace만 trim한다. body 내부
  whitespace와 식별자의 대소문자는 변경하지 않는다. `bodyText = null`은 null로
  canonicalize하며 blank body는 기존 validation에서 거절한다.
- 숫자는 정수 decimal 표기로 canonicalize하고, field name이 고정된 정렬 JSON object를
  UTF-8로 직렬화한 뒤 SHA-256 lowercase hex를 계산한다. 저장값은 `v1:` 접두어를
  포함한 67자 문자열이며 `direction_post.request_fingerprint VARCHAR(80)`에 저장한다.
- 기존 행은 migration에서 즉시 NOT NULL로 강제하지 않는다. 새 제출은 fingerprint를
  반드시 저장하고, 기존 행의 null fingerprint는 첫 idempotency 재시도 시
  `direction_post + post_audience`의 저장된 의도로 lazy backfill한다. audience를
  복원할 수 없는 legacy 행은 기존 결과를 반환하되 새 fingerprint 충돌 판정은 하지
  않고 reconciliation 대상으로 기록한다.

### B. Matching job schema

- 별도 `matching_job` 테이블을 만들지 않는다. transactional outbox row 자체를
  matching job으로 취급해 post와 작업 요청의 dual-write 상태를 제거한다.
- `outbox_event.match_round INTEGER`를 추가한다. 초기 매칭 요청은 `1`이며,
  worker retry/reclaim에서는 증가하지 않는다. 제품 판단으로 새 matching round를
  예약할 때만 1씩 증가한다.
- `match_round`는 `aggregate_type = 'DIRECTION_POST'`이고
  `event_type = 'RECIPIENT_MATCH_REQUESTED'`인 행에만 필수다. 그 외 Outbox event는
  null이다. 이 조합에 대해 `(aggregate_id, match_round, event_type)` partial unique
  index를 추가한다. 여기서 `aggregate_id`는 post id다.
- matching event의 `dedup_key`는
  `direction-match:{postId}:{matchRound}:{eventType}`로 서버가 파생한다. 호출자가
  임의 dedup key를 주입하지 않는다.
- matching payload는 `postId`, `matchRound`, `eventType`, `requestFingerprint`와
  재조회에 필요한 coarse 식별자만 포함한다. `post_audience.origin_position`이나
  위도·경도는 payload에 복사하지 않는다.

### C. Lease fencing

- `outbox_event`에 `lease_owner VARCHAR(100)`, `lease_expires_at TIMESTAMPTZ`,
  `lease_generation BIGINT NOT NULL DEFAULT 0`을 추가한다. `PROCESSING`일 때만
  owner와 expiry가 함께 존재하고, 그 외 상태에서는 둘 다 null이다.
- owner는 worker instance의 opaque logical id이며 사용자·계정·비밀값이 아니다.
  lease generation은 claim/reclaim 성공 때마다 원자적으로 1 증가하는 monotonic
  fencing token이다.
- batch claim 대상은 due `PENDING`/`FAILED`와 lease가 만료된 `PROCESSING`이다.
  한 번의 claim에서 status, owner, expiry, generation, attempt_count를 함께 갱신한다.
  due 판정과 row 점유는 한 SQL transaction에서 처리한다.
- processed/failed/dead 갱신은 `id + status = PROCESSING + lease_owner +
  lease_generation + lease_expires_at > now` 조건을 모두 사용한다. 조건에 맞지 않는
  stale worker의 완료는 0 row 처리로 거절한다.
- 성공·실패·dead 전환 시 lease owner/expiry를 null로 해제한다. 실패 시
  `attempt_count`와 승인된 backoff에 따른 `next_attempt_at`을 함께 저장한다.
  lease duration과 backoff의 숫자값은 DB schema에 고정하지 않고 application
  configuration으로 주입한다. #115는 fencing semantics를 고정하며 운영 기본값은
  배포 설정 검토 항목이다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| canonicalization이 요청 필드 순서·공백·nullable 표현에 따라 달라진다. | 같은 요청 재시도가 다른 요청으로 판정되어 중복 post/매칭 발생 | Medium | P0 | 고정 fixture의 동일·동등·상이 입력 fingerprint unit 결과 |
| fingerprint 저장·비교가 sender 또는 Idempotency-Key 범위를 잘못 사용한다. | 다른 사용자의 요청 충돌 또는 같은 키의 다른 요청 덮어쓰기 | Medium | P0 | 동일 key/동일·상이 fingerprint의 PostgreSQL 통합 및 unique 경계 |
| 같은 매칭 라운드 작업을 두 번 생성한다. | 후보 계산·수신자·알림 중복 | High | P0 | DB unique constraint와 동시 insert에서 정확히 한 성공 |
| batch claim이 같은 Outbox를 두 worker에 배정한다. | 중복 처리·외부 전달 재시도 폭증 | High | P0 | `FOR UPDATE SKIP LOCKED` 또는 승인된 equivalent contract의 동시성 증거 |
| lease 만료 후 회수하지 못하거나 만료 전 작업을 빼앗는다. | 장애 후 매칭 영구 정지 또는 중복 처리 | High | P0 | fixed clock의 expired/non-expired reclaim 및 attempt 증가 검증 |
| 이전 lease 소유자의 늦은 완료가 새 lease 소유자의 상태를 덮어쓴다. | 재처리 결과 유실·상태 역전 | Medium | P0 | lease token/version 또는 승인된 stale-owner guard 통합 테스트 |
| 제출·매칭 작업·Outbox가 부분 커밋된다. | post만 존재하거나 이벤트가 유실되어 비동기 흐름 중단 | High | P0 | 제약 위반/강제 예외 후 관련 행 0건과 새 transaction retry |
| payload에 정확 좌표가 들어간다. | 개인정보·위치정보 노출 | Medium | P0 | JSONB 키/값 assertion과 serializer/source boundary 검사 |
| lease 필드·인덱스·유일성 제약이 DBML/ERD/migration과 불일치한다. | 신규 DB와 기존 DB의 동작 차이·배포 실패 | Medium | P0 | 빈 PostgreSQL/PostGIS migration catalog 및 문서 계약 검사 |
| 기존 Outbox notification claim/processed 흐름이 회귀한다. | 기존 답변·알림 처리 중단 | Medium | P1 | 기존 `AnswerSafetyNotificationPersistenceIntegrationTest` 대상 회귀 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-001 | Decision record A의 7개 의도 필드가 같고 필드 순서·허용된 바깥 공백·Unicode 표현만 다른 발송 요청 | NFC/trim/canonical JSON 후 fingerprint를 반복 생성 | 결과가 deterministic하고 동일한 `v1:SHA-256` fingerprint가 된다. `submittedAt`, `expiresAt`, sender, Idempotency-Key 변경은 결과를 바꾸지 않는다. | P0 | Fingerprint executor |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-002 | 7개 fingerprint 대상 중 하나가 의미 있게 다른 요청 | fingerprint를 생성·비교 | fingerprint가 달라지고, body 내부 whitespace·식별자 대소문자 변경도 의미 변경으로 판정된다. | P0 | Fingerprint executor |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-003 | 같은 Idempotency-Key에 저장된 fingerprint와 결과 snapshot | 동일 fingerprint 재시도 및 다른 fingerprint 재시도를 각각 수행 | 동일 요청은 기존 결과 참조를 반환하고 새 post/job/outbox를 만들지 않는다. 다른 요청은 `IDEMPOTENCY_KEY_REUSED` 충돌을 반환하며 새 결과를 만들지 않는다. | P0 | Matching contract executor |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-004 | `(postId, matchRound, eventType)` 작업 식별자 조합 | 동일 조합·다른 round·다른 event type을 비교 | 동일 조합만 duplicate로 판정하고 round 또는 event type이 다르면 독립 작업으로 판정한다. null·0 이하 식별자는 거절한다. | P0 | Matching contract executor |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-005 | PENDING/PROCESSING/FAILED/PROCESSED/DEAD Outbox와 승인된 lease owner/expiry 값 | claim, processed, failed, reclaim 전이를 fixed clock으로 수행 | claim마다 attempt가 정확히 1 증가하고, lease가 유효한 행은 재점유되지 않으며, 만료된 행만 재처리 가능하다. 상태·lease·processed 시각 불변식이 보존된다. | P0 | Outbox executor |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-006 | 정확 좌표와 coarse region/cell을 포함할 수 있는 매칭 event 입력 | Outbox payload를 생성 | payload는 JSON object이고 post/round/event 및 허용된 coarse 정보만 포함하며 위도·경도·PostGIS point의 정확 값과 동등한 필드를 포함하지 않는다. | P0 | Outbox executor |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-UNIT-007 | 새 `request_fingerprint`, matching job, Outbox lease 필드를 가진 domain/JDBC row | 저장 후 restore/map round-trip | nullable lease와 시각 정밀도, enum, fingerprint, round, event type이 손실 없이 왕복되고 기존 notification mapping과 분리된다. | P1 | Persistence executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-001 | Flyway, PostgreSQL/PostGIS catalog, JDBC | 빈 Testcontainers DB에 승인된 신규 migration 적용 | `direction_post.request_fingerprint`, `outbox_event.match_round`, lease columns, matching partial unique index, claim index를 조회 | migration이 성공하고 legacy fingerprint null 허용, 신규 matching event의 round 필수 check, `(aggregate_id, match_round, event_type)` 유일성, due/lease dispatch index가 실제 catalog에 존재한다. 기존 V1~V10 migration은 수정되지 않는다. | container 종료 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-002 | Direction submit persistence, fingerprint verifier, matching/outbox writer | 같은 sender의 동일 key 요청 1건을 저장하고 동일/상이 fingerprint fixture 준비 | 동일 요청을 재시도한 뒤 상이 요청을 같은 key로 제출 | 동일 요청은 첫 post/result와 동일한 fingerprint를 반환하고 post·matching job·outbox count가 증가하지 않는다. 상이 요청은 `IDEMPOTENCY_KEY_REUSED`로 거절되고 기존 row가 보존된다. | scenario marker 기준 역순 삭제 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-003 | 두 application transaction, direction post unique/fingerprint lookup | 동일 key의 동일 요청 2개와 동일 key의 상이 요청 2개를 barrier로 동시 실행 | 두 경쟁을 각각 commit | 동일 요청 경쟁은 정확히 하나의 logical result와 하나의 matching event만 남기고 두 호출이 같은 결과 계약을 따른다. 상이 요청 경쟁은 한 요청만 최초 결과를 만들며 다른 요청은 conflict이고 partial row가 없다. | executor 종료 및 marker 정리 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-004 | Outbox repository, DB partial unique constraint | 같은 post의 동일 round/event와 다른 round/event fixture | 동일 `RECIPIENT_MATCH_REQUESTED` 조합을 순차·동시 insert하고 다른 round도 insert | 동일 조합은 한 Outbox row만 성공하며 duplicate는 명시된 idempotent result 또는 constraint-mapped conflict가 된다. 다른 round는 각각 저장되고, non-matching event에는 round를 저장할 수 없다. | outbox marker 삭제 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-005 | Outbox batch claim repository, 두 worker transaction | due PENDING/FAILED, future due, PROCESSING(유효 lease), PROCESSED, DEAD 행을 준비 | 두 worker가 batch claim을 동시에 수행 | 한 event는 최대 한 worker에만 lease되고 future/terminal/유효 lease 행은 제외된다. claim된 행의 owner/expiry/status/attempt가 원자적으로 함께 갱신된다. | outbox marker 삭제 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-006 | Outbox lease reclaim/update repository | PROCESSING 행을 expired lease와 유효 lease로 각각 준비 | expired 행을 reclaim하고, 유효 lease에서 다른 worker reclaim 및 이전 worker completion을 시도 | expired 행만 재점유되고 attempt가 증가한다. 유효 lease는 유지된다. stale owner의 완료가 새 lease 결과를 덮어쓰지 않도록 승인된 fencing 조건이 적용되며, 그 조건이 없으면 BLOCKED로 보고한다. | outbox marker 삭제 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-007 | Direction post, request fingerprint, matching job, outbox, transaction boundary | 정상 제출 fixture와 의도적 unique/check/serialization failure 지점 준비 | transaction 중간에 실패시키고 새 transaction으로 같은 요청을 재시도 | 실패 transaction에는 post/fingerprint/job/outbox 부분 행이 남지 않는다. 유효 재시도는 새 logical result를 한 번 만들고, duplicate 재시도는 기존 결과를 반환한다. | FK 역순 정리 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-008 | Outbox JSONB persistence 및 approved payload mapper | synthetic location 입력과 coarse-only payload fixture | event 저장 후 JSONB를 조회·파싱하고 허용/금지 key를 검사 | payload에 정확 좌표, geography WKB/GeoJSON 또는 좌표로 복원 가능한 필드가 없고, 매칭에 필요한 postId/round/event/fingerprint reference만 보존된다. | outbox marker 삭제 |
| TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-009 | Existing notification Outbox repository and new lease contract | 기존 `AnswerSafetyNotificationPersistenceIntegrationTest` fixture와 신규 matching event fixture | 기존 claim/save/update/processed 흐름과 신규 batch/reclaim 흐름을 함께 실행 | 기존 answer notification의 dedup·atomic rollback·single claim이 회귀하지 않고, 신규 contract가 notification delivery API를 변경하지 않는다. | 기존 fixture cleanup |

## 7. Cross-cutting scenarios

### Database and transactions

- 실제 PostgreSQL/PostGIS Testcontainers에서 migration을 실행한다. H2나 정적 SQL
  문자열 검사만으로 unique, partial index, JSONB, transaction, lock을 통과시키지
  않는다.
- 이미 적용된 V1~V10은 수정하지 않고 새 versioned migration으로만 변경한다. 빈 DB
  startup과 기존 schema upgrade 양쪽에서 catalog·constraint·index를 확인한다.
- 제출 transaction은 post와 fingerprint, matching job/event, Outbox 기록을 하나의
  원자적 경계로 묶는다. unique/check/serialization 실패 후 관련 행이 남지 않는지
  재조회한다.
- Decision record A의 legacy null/lazy backfill과 Decision record B의 outbox-as-job
  구조를 migration·통합 테스트에 고정한다. legacy audience를 복원할 수 없는 행은
  충돌 판정 대상이 아니라 reconciliation 대상으로 남긴다.

### Concurrency and idempotency

- 동일 key·동일 fingerprint 경쟁은 한 logical result만 만들고, 동일 key·상이
  fingerprint 경쟁은 한 요청만 성공하며 나머지는 `IDEMPOTENCY_KEY_REUSED`가 된다.
- matching job은 `outbox_event` 자체이며, application pre-check만 믿지 않고
  `aggregate_id + match_round + event_type` partial unique를 최종 방어선으로 검증한다.
  worker retry/reclaim은 round를 바꾸지 않고 명시적 새 round만 새 작업을 허용한다.
- batch claim은 승인된 lease 방식(예: row lock + `SKIP LOCKED` 또는 조건부 update)을
  실제 affected-row와 함께 검증한다. 유효 lease와 만료 lease를 구분하고 attempt
  증가를 원자적으로 확인한다.
- stale owner 완료 차단은 `lease_owner + lease_generation + lease_expires_at > now`
  조건으로 고정한다. generation이 증가한 뒤 이전 worker가 갱신을 시도하면 affected
  row가 0이어야 한다.

### External APIs

- #115 범위에는 REST Controller와 외부 push provider가 없다. 외부 API double을
  만들지 않고, PostgreSQL/PostGIS만 실제 의존성으로 사용한다.
- event payload는 worker가 나중에 사용할 내부 계약이므로 외부 provider request나
  push token을 fixture·로그·보고서에 기록하지 않는다.

### Failure recovery and reconciliation

- worker crash를 PROCESSING + expired lease fixture로 재현하고 reclaim 후 attempt,
  next attempt, owner/expiry, status를 다시 검증한다.
- reclaim 이후 old worker가 늦게 완료하는 경우 새 owner의 상태를 보존해야 한다.
  fencing 계약이 확정되지 않으면 이 시나리오는 BLOCKED이며 잔여 위험으로 보고한다.
- 실패 transaction과 재시도 후 `direction_post`, matching job, outbox 수를 대조해
  1 logical request : 1 matching event 불변식을 확인한다.
- `PROCESSED`/`DEAD` 행을 batch 대상에서 제외하고, FAILED 재시도가 승인된 backoff/
  attempt 상한 계약과 일치하는지 확인한다. 상한이 정해지지 않은 값은 임의로 만들지
  않는다.

## 8. Test data and isolation

- Fixtures: scenario별 고유 marker를 가진 sender, approved ACTIVE question, direction
  post, canonical request variants, matching round/event variants, PENDING/FAILED/
  PROCESSING/terminal Outbox 행을 생성한다. 위치는 synthetic Testcontainers fixture로
  제한하고 payload에는 coarse 값만 허용한다.
- Database isolation: 기본은 Testcontainers PostgreSQL/PostGIS와 transaction rollback;
  unique race, lease race, migration catalog 검증은 명시적 commit과 scenario marker를
  사용한다. 공유 application context는 사용하되 fixture key는 공유하지 않는다.
- Clock/randomness: UTC fixed `Clock`과 고정 UUID/key를 사용한다. lease duration과
  backoff는 application configuration으로 주입하고 테스트에서 명시적 test value를
  사용한다. fingerprint algorithm은 Decision record A의 `v1:SHA-256`으로 고정한다.
- External API doubles: 없음. 외부 provider는 범위 밖이며 outbox row와 payload만
  검증한다.
- Cleanup: notification/delivery가 참조하는 기존 outbox는 먼저 정리하고, matching
  job/event → direction post → account/question/region 순으로 FK 역순 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Fingerprint executor | `src/test/java/com/dnd/qello/direction/matching/**` | UNIT-001~003 | canonicalization, fingerprint conflict, same-result contract unit tests |
| 2 | Matching contract executor | `src/test/java/com/dnd/qello/direction/matching/**` 중 1번이 소유하지 않은 파일 | UNIT-004, UNIT-007 | matching identity와 domain/JDBC mapping unit tests |
| 3 | Outbox executor | `src/test/java/com/dnd/qello/notification/**` 신규 lease test 파일 | UNIT-005~006 | lease state machine, payload safety unit tests |
| 4 | Migration executor | `src/test/java/com/dnd/qello/FlywayMigrationContractTest.java`, `src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java` | INT-001 | migration history, catalog column/index/constraint, document manifest contract |
| 5 | Matching integration executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingContractIntegrationTest.java` | INT-002~004, INT-007~008 | fingerprint retry/race, round unique, atomic rollback, payload JSONB |
| 6 | Outbox integration executor | `src/integrationTest/java/com/dnd/qello/OutboxLeaseIntegrationTest.java` | INT-005~006, INT-009 | batch lease/reclaim concurrency and existing notification regression |

각 executor는 위 소유 경로 밖의 파일을 수정하지 않는다. `matching/**`는 1번과
2번이 서로 다른 구체 파일을 승인 메시지에서 먼저 확정해야 하며, migration 기존
테스트 파일은 4번만 수정한다. production 구현 파일·migration 파일·ERD/API 문서의
소유권은 테스트 계획 승인 후 별도 implementation plan에서 정한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오가 승인된 구현과 대응되고, 각 테스트 파일 소유권이 겹치지 않는다.
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 클래스 헤더에 정확한 ISO 8601 생성 시각과 해당 `TEST-PLAN-GH-115...` scenario ID 기록
- [ ] 단위 테스트 통과: `./gradlew test --tests ...`
- [x] 통합 테스트 통과: `./gradlew integrationTest --tests ...` (PostgreSQL/PostGIS container 필요)
- [x] migration catalog, unique/index, same-key race, lease reclaim, stale-owner guard,
      payload coordinate exclusion 증거 확보
- [x] 구현 문제와 테스트 환경 문제를 분리한 잠재 문제 분석 및 `templates/test-report.md`
      기반 보고서 생성
- [x] 완료 전 저장소 검증: `./harness check`, `./harness pr-ready --project-tests`,
      `npm run hooks:validate`, `git diff --check`
- [ ] INT-007 transaction rollback failure-injection evidence (safe production seam is absent)
- [ ] 테스트 구현·실행·push·PR 생성은 사람 승인 이후에만 수행

## 11. Human approval

- Reviewer: User
- Decision: Approved — Decision record A~C를 구현·테스트 계약으로 확정
- Approved at: `2026-08-11T19:16:00+09:00`

이번 계획에서 확정 제안한 정책은 다음과 같다.

- fingerprint: 7개 사용자 의도 필드, NFC/바깥 trim, canonical JSON, `v1:SHA-256`,
  서버 시각·sender·Idempotency-Key 제외, legacy lazy backfill
- matching job: 별도 테이블 없이 `outbox_event`에 `match_round`를 추가하고,
  `RECIPIENT_MATCH_REQUESTED`에 대해 `(aggregate_id, match_round, event_type)`
  partial unique 적용
- lease fencing: `lease_owner`, `lease_expires_at`, monotonic `lease_generation`을
  사용하고 완료·실패 갱신 시 owner/generation/미만료 조건을 모두 확인
- 동일 fingerprint 재시도는 기존 결과 반환, 상이 fingerprint는
  `IDEMPOTENCY_KEY_REUSED` 충돌

운영 환경의 lease duration과 backoff 숫자값만 application deployment configuration
검토 항목으로 남긴다. 구현 계획 및 테스트 실행 단계에서 이 값을 임의로 하드코딩하지
않고 설정 주입으로 검증한다.
