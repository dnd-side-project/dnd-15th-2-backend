# Test Plan: TEST-PLAN-GH-39-DIRECTION-POSTGIS-PERSISTENCE

> Created at: `2026-08-03T19:48:55+09:00`
> GitHub Issue: `#39`
> Status: Approved — implementation executed after user approval

## 1. Objective

사용자가 선택한 방향과 발신 위치를 기준으로 수신 후보를 계산하고, 실제 전송 시점의
서버 권위 값과 수신자 snapshot을 잃지 않고 저장하는지 검증한다. 실패하면 정확 위치
노출, 잘못된 sector 경계, 만료 후보 선택, 용량 초과 또는 중복 수신이 발생할 수
있으므로 일반 JPA CRUD가 아니라 실제 PostgreSQL/PostGIS의 공간 연산·제약·잠금과
transaction 증거를 남긴다.

## 2. Scope

### Included

- `direction_scheme`, `direction_segment`의 8×45° half-open sector/coverage 규칙
- `active_user_presence` geography point, coarse region, TTL 및 receive eligibility
- `recipient_receive_state`의 활성 미처리 용량 예약·해제
- `direction_post`, `post_audience`, `post_recipient` scalar-ID persistence
- 발송 시점 direction/거리 재계산, audience 및 recipient snapshot transaction
- PostGIS distance/bearing 후보 query, spatial index와 `EXPLAIN` 근거
- idempotency, question ACTIVE deferred trigger, 자기수신 방지와 status/timestamp 제약
- PostgreSQL 16/PostGIS Testcontainers 단위·통합 테스트와 잠재 문제 보고서

### Excluded

- V1 migration, DBML/ERD/schema manifest/region seed 변경
- 모바일 위치 수집 UI, API controller, auth/permission, 외부 메시지 전송
- 추천·인접 sector fallback·질문 선정 정책, Answer/Safety/Notification persistence
- 정확 좌표의 API/log/event/outbox 노출, 운영 DB/deploy/apply
- V1에 없는 만료 기간·거리·수신량 정책의 신규 상수
- version column 또는 승인되지 않은 별도 동시성 정책

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #39 | 8×45° 매핑, PostGIS 후보 조회, send-time 재계산, recipient snapshot, 공간 인덱스, JPA/JDBC 경계와 Testcontainers를 검증한다. |
| `TASK.md` | 7개 방향 관련 table 경계, scalar ID, row lock/conditional update, 절대 expiresAt, 로컬 검토 게이트를 구현 계약으로 사용한다. |
| ADR-0001 | Flyway V1이 schema source of truth이고 Hibernate 자동 DDL을 사용하지 않는다. |
| ADR-0002 | PostGIS 거리/방향, lock, conditional update, bulk는 JDBC 책임이며 domain/application은 port에만 의존한다. |
| Flyway V1 / schema manifest | geography/check/FK/unique/deferred trigger/index와 정확 위치 비노출 주석을 그대로 따른다. |
| Issue #38 | `approved_question_id`는 ACTIVE 질문만 참조하고 Question 구현체가 Direction을 직접 참조하지 않는다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 각도 0/360 또는 45° 경계가 두 sector/누락으로 매핑된다. | 잘못된 수신 후보 | Medium | P0 | domain boundary/property unit |
| direction scheme segment가 overlap/gap 또는 잘못된 coverage를 갖는다. | 정책 버전 재현 불가 | Medium | P0 | validation unit + DB integration |
| expired/receive 불가 presence가 후보에 포함된다. | 만료·차단 사용자 노출 | High | P0 | fixed Clock PostGIS query |
| geography 좌표 축/순서 또는 거리 단위가 틀린다. | 거리·방향 오판 및 개인정보 위험 | Medium | P0 | known coordinate fixture + SQL result |
| preview 값이 전송 시 재계산되지 않는다. | 이동 후 잘못된 수신자 snapshot | High | P0 | changed presence between preview/send integration |
| audience/recipient snapshot이 부분 저장된다. | 권한과 감사 이력 불일치 | Medium | P0 | forced failure rollback |
| active unhandled capacity가 동시 요청에서 5를 초과한다. | 수신 상한 정책 위반 | Medium | P0 | concurrent row-lock/conditional update test |
| sender 자기수신 또는 inactive question post가 커밋된다. | 자기 노출·정책 위반 | Medium | P0 | deferred trigger integration |
| idempotency/recipient unique가 race에서 중복을 허용한다. | 중복 알림·응답 창구 | Medium | P0 | unique conflict + retry test |
| post status와 timestamp/capacity release가 어긋난다. | inbox 상태와 용량 투영 불일치 | Medium | P0 | check/deferred trigger integration |
| 동시 send가 stale presence를 snapshot한다. | 서버 권위 값 위반 | Low | P1 | residual analysis; transaction lock boundary |
| 정확 좌표가 SQL 결과·로그·DTO로 새어 나간다. | 개인정보 침해 | Medium | P0 | source/response/log boundary test |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-001 | 0°~360° bearing과 8개 equal segment scheme | normalize 및 sector 선택 | 0°/360°가 동일하고 각 half-open 구간이 정확히 하나에 속하며 45° 경계가 다음 구간으로 일관되게 매핑된다. | P0 | Domain executor |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-002 | scheme/segment 후보와 center/width/order | coverage 검증 | 중복·gap·범위 밖 center/width/order·잘못된 scheme status를 거절한다. | P0 | Domain executor |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-003 | presence와 caller 절대 `Instant` | 유효성/만료 판정 | `expiresAt > locationAt`, receive 허용, region 및 fixed Clock 기준 current 판정이 보장되고 기본 기간을 계산하지 않는다. | P0 | Presence executor |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-004 | WGS84 origin/target point와 bearing sector | 거리·방위 계산 | PostGIS adapter에 전달할 longitude/latitude 순서와 meter 단위가 명시되고 exact coordinate가 외부 DTO로 변환되지 않는다. | P0 | JDBC executor |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-005 | post/audience/recipient 상태 조합 | state transition 및 mapper round-trip | status별 timestamp invariant, scalar IDs, enum/Instant/decimal 정밀도를 보존하고 invalid transition을 거절한다. | P0 | Direction executor |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-006 | capacity count와 concurrent command | reserve/release 결정 | active unhandled 5 상한을 넘기는 예약을 거절하고 종결 상태에서만 release가 허용된다. | P0 | Transaction executor |
| DIRECTION-POSTGIS-PERSISTENCE-UNIT-007 | direction package source set | architecture scan | JPA Entity/Spring Data와 Account/Question 구현 직접 참조가 없고 JDBC·port 방향이 ADR과 일치한다. | P0 | Test executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| DIRECTION-POSTGIS-PERSISTENCE-INT-001 | Flyway, Hibernate, JDBC schema | 빈 PostgreSQL/PostGIS container | V1 적용과 application context metadata validation | PostGIS extension, 관련 table/index/trigger가 존재하고 migration/DDL 변경 없이 mapping이 검증된다. | container 종료 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-002 | Scheme/segment repository | 8개 segment fixture 및 잘못된 fixture | 저장·조회·coverage 검증 | 정상 scheme은 8개 half-open segment로 round-trip되고 overlap/gap/duplicate order는 DB/domain에서 거절된다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-003 | Presence JDBC adapter | 알려진 WGS84 points, region, fixed Clock | upsert 후 만료·receive·range/sector 후보 query 실행 | 현재이고 수신 허용인 후보만 반환되며 거리/방위와 region 조건이 맞고 exact position은 반환하지 않는다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-004 | Direction post + approved question | ACTIVE/PENDING question, sender, region fixture | post insert/update/commit | ACTIVE 질문만 commit되고 PENDING 질문은 deferrable trigger로 rollback되며 expiresAt > submittedAt이 유지된다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-005 | Audience/recipient repository + trigger | post와 다른 recipient fixture | sender 자기수신, duplicate recipient, invalid bearing/timestamps 저장 | 자기수신·중복·범위/시각 오류가 constraint trigger/check/unique로 거절된다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-006 | Send transaction service, presence query, audience/recipient writer | preview 후 sender presence를 변경 | send 실행 | preview 결과를 재사용하지 않고 최신 위치/선택 방향을 재계산해 audience와 recipient snapshot을 한 transaction에 기록한다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-007 | Capacity state + conditional update/row lock | 같은 recipient의 active count=4/5 fixture | 동시에 두 reserve와 종료 release 실행 | 한도 5를 초과하는 reserve가 없고 성공한 reserve만 count를 증가시키며 release/재시도는 멱등이다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-008 | Idempotency and recipient unique constraints | 동일 sender/key와 post/recipient fixture | 같은 send 및 recipient insert를 재시도 | 첫 요청만 snapshot을 만들고 duplicate는 기존 결과 또는 명시적 conflict로 처리하며 partial row가 없다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-009 | Post recipient state/capacity deferred triggers | AVAILABLE/DISCOVERED/OPENED/terminal fixtures | 상태·timestamp·capacity release를 각각 유효/무효 조합으로 갱신 | status timestamp와 `capacity_released_at` 동치가 커밋 시점에 보장된다. | row 정리 |
| DIRECTION-POSTGIS-PERSISTENCE-INT-010 | PostgreSQL planner and spatial index | 충분한 presence/post rows | candidate SQL에 `EXPLAIN (FORMAT JSON)` 실행 | query가 필요한 spatial/region/expiry/index 조건을 사용하고 full scan 또는 임의 좌표 노출이 계획/결과에 없다. | container 종료 |

## 7. Cross-cutting scenarios

### Database and transactions

- H2 대신 PostgreSQL 16/PostGIS 3.x Testcontainers에서 실제 geography, GiST/partial
  index, deferred constraint trigger를 실행한다.
- send transaction의 preview 재계산, audience snapshot, recipient snapshot, capacity
  reservation이 모두 commit되거나 모두 rollback되어야 한다.
- DB가 강제하는 unique/check/FK/deferred trigger와 application이 보완하는 sector,
  candidate, transition 규칙을 테스트와 보고서에서 구분한다.
- Hibernate `ddl-auto=validate`만 허용하며 `V1__create_direction_communication_schema.sql`
  또는 schema manifest checksum을 변경하지 않는다.

### Concurrency and idempotency

- `recipient_receive_state` row lock 또는 conditional update의 실제 SQL과 affected-row
  판정으로 active unhandled 5 상한을 원자적으로 보장한다.
- 동일 sender/idempotency key 및 post/recipient 요청은 DB unique를 최종 방어선으로
  사용하고 retry 시 partial snapshot이 없어야 한다.
- send 시 presence가 변경되는 race는 transaction snapshot/lock 경계를 확인하되, V1과
  승인 계획에 없는 추가 lock 범위를 임의로 넓히지 않고 남은 위험을 기록한다.

### External APIs

- 외부 API 호출은 없다. PostGIS는 Testcontainers의 실제 database extension으로
  검증하고, Account/Question은 scalar fixture ID로만 연결한다.

### Failure recovery and reconciliation

- unique/check/trigger/serialization 실패가 발생해도 post, audience, recipient,
  capacity update가 부분 저장되지 않고 유효 command를 새 transaction에서 재시도할 수
  있어야 한다.
- retry 후 idempotency 및 count를 재조회해 snapshot 수와 active count를 reconcile한다.
- SQL/log/DTO에서 exact coordinate가 누출되지 않았는지 source scan과 테스트 보고서에서
  확인한다.

## 8. Test data and isolation

- Fixtures: region, account, ACTIVE/PENDING question, scheme/8 segments, WGS84 points,
  sender/recipient, post/audience/recipient, receive state를 scenario별 unique key로
  생성한다.
- Database isolation: scenario transaction rollback을 기본으로 하고 deferred trigger,
  lock, EXPLAIN 검증은 명시적 commit 또는 container lifecycle로 격리한다.
- Clock/randomness: UTC fixed `Clock`; location/expiry/bearing/keys를 고정하고 임의
  거리·기간 정책을 사용하지 않는다.
- External API doubles: 없음. Testcontainers PostgreSQL/PostGIS만 사용한다.
- Cleanup: recipient → audience → post → receive state/presence → segments → scheme →
  account/region 순 FK 역순 또는 container 폐기로 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Domain executor | `src/main/java/com/dnd/qello/direction/domain/**`, `src/test/java/com/dnd/qello/direction/domain/**` | UNIT-001~003 | sector/policy unit tests |
| 2 | JDBC executor | `src/main/java/com/dnd/qello/direction/repository/jdbc/**`, `src/test/java/com/dnd/qello/direction/repository/jdbc/**` | UNIT-004, INT-001~003, INT-010 | PostgreSQL/PostGIS repository and EXPLAIN tests |
| 3 | Direction executor | `src/main/java/com/dnd/qello/direction/repository/**` excluding `jdbc`, `src/test/java/com/dnd/qello/direction/repository/**` | UNIT-005, INT-004~005, INT-009 | mapping/state/trigger tests |
| 4 | Transaction executor | `src/main/java/com/dnd/qello/direction/service/**`, `src/test/java/com/dnd/qello/direction/service/**` | UNIT-006, INT-006~008 | transaction/lock/idempotency tests |
| 5 | Test orchestrator | `src/test/java/com/dnd/qello/direction/architecture/**`, `docs/reports/tests/gh-39-DIRECTION-POSTGIS-PERSISTENCE.md` | UNIT-007, cross-cutting and all P0 | Gradle check + report + Harness |

각 executor는 소유 경로 밖의 파일을 수정하지 않는다. 공유 fixture가 필요하면 test
orchestrator가 기존 support를 재사용하고, 새 dependency 도입은 별도 승인 대상으로
분리한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 클래스 헤더에 정확한 ISO 8601 timestamp와 원본 scenario ID 기록
- [ ] 단위 및 실제 PostgreSQL/PostGIS 통합 테스트 통과
- [ ] sector 경계, spatial query 결과와 EXPLAIN 근거 확보
- [ ] send transaction rollback, capacity race, idempotency 재시도 증거 확보
- [ ] DB 강제, application 보완, P1 동시성 잔여 위험을 분리한 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] `./harness check`, `./harness pr-ready --project-tests`,
  `npm run hooks:validate`, `git diff --check` 통과
- [ ] 구현 결과를 origin에 push하지 않고 사용자 검토 대기

## 11. Human approval

- Reviewer: User
- Decision: Approved
- Approved at: `2026-08-03T20:16:43+09:00`
