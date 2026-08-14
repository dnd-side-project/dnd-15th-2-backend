# Test Plan: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API

> Created at: `2026-08-13T22:22:17+09:00`
> GitHub Issue: `#121`
> Status: Approved

## 1. Objective

인증된 앱 사용자가 자신의 최신 위치와 수신 허용 상태만 안전하게 갱신하고,
위치가 만료되거나 유효하지 않으면 preview·매칭 후보에서 제외되는지 검증한다.

가장 큰 위험은 다른 사용자의 presence 변경, 늦게 도착한 요청의 최신 위치 덮어쓰기,
클라이언트가 임의로 유효기간을 늘리는 것, 부정확한 위치의 후보 포함, 정확 좌표의
응답·오류·로그 유출이다.

## 2. Scope

### Included

- JWT `sub` 기반 본인 식별과 ACTIVE USER 재검증
- 좌표·정확도·관측 시각·수신 허용값 validation
- 서버 권위 지역과 서버 계산 `expiresAt`
- 최신 관측만 반영하는 사용자별 조건부 UPSERT
- stale/equal 요청의 멱등 no-op과 동시 갱신
- 좌표 없는 API response/error/log/OpenAPI example
- 실제 PostgreSQL/PostGIS에서의 저장·rollback·만료 후보 제외
- 기존 preview·matching candidate SQL 회귀

### Excluded

- TTL·정확도·관측 시각 허용 범위의 최종 제품 정책 결정
- 위치 이력, 지도 집계, reverse geocoding, 외부 위치 API
- region polygon과 좌표의 실제 포함 관계 검증
- #122 이후 API, Outbox, 알림, 배포와 인프라 변경
- 새 Flyway migration 또는 인덱스 추가

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #121 | 본인의 presence만 갱신하고 만료·잘못된 위치를 후보에서 제외하며 정확 좌표를 응답·로그·오류에서 숨긴다. 기존 `active_user_presence` UPSERT를 사용한다. |
| Parent #116 | 위치 API는 preview·제출·matching보다 앞선 수직 흐름이며 정확 좌표 비노출이 공통 기준이다. |
| Predecessor #115 | 정확 좌표를 Outbox에 넣지 않는 기존 비동기 계약을 보존한다. 이번 Issue는 Outbox를 만들지 않는다. |
| `ActiveUserPresence` | 위·경도 동시 존재, 위도 `[-90, 90]`, 경도 `[-180, 180]`, 정확도 비음수, `expiresAt > locationAt`을 보장한다. |
| V1 schema | 사용자별 PK, geography point, region FK, NUMERIC(10,2), `expires_at > location_at` 제약을 보존한다. |
| `ActiveUserPresenceSql` | 기존 candidate SQL은 `position IS NOT NULL`, `receive_allowed`, `location_at <= at`, `expires_at > at`을 요구한다. |
| Security/Auth | `/api/**`는 JWT 인증 대상이고 access token `sub`는 서버 발급 사용자 ID다. 토큰 수명 중 계정 상태가 바뀔 수 있어 service에서 ACTIVE USER를 재검증한다. |
| API response contract | 성공은 `ApiResponse`, 실패는 `ApiErrorResponse`를 사용하고 OpenAPI 산출물을 동기화한다. |
| `auth/web` ApiSpec convention | 메서드 매핑과 OpenAPI 애노테이션은 `*ApiSpec`에, Controller에는 `@RestController`·클래스 수준 `@RequestMapping`·`@Override` 구현만 둔다. |
| Web DTO package convention | request DTO는 `direction.web.request`, response DTO는 `direction.web.response`에 두고 Controller/ApiSpec과 분리한다. |

## 4. Decision gates

| Decision | State | Recommended contract |
| --- | --- | --- |
| Endpoint | CONFIRMED | `PUT /api/v1/direction/presence` |
| Request ownership | CONFIRMED | `userId` 없이 JWT `sub`만 사용 |
| Region | CONFIRMED | Account의 서버 저장 `coarseRegionCode` 사용 |
| Expiration | CONFIRMED | client `expiresAt` 금지, `observedAt + PT24H` |
| Accuracy | CONFIRMED | `accuracyMeters` 필수, `0 <= accuracyMeters <= 100m` |
| Observation time | CONFIRMED | `observedAt` 필수, future skew `PT30S`, maximum age `PT5M` |
| Stale/equal request | CONFIRMED | DB no-op, `200`, `applied=false` |
| Response | CONFIRMED | 조건부 UPSERT의 `applied`만 반환하고 좌표·userId·region 제외 |
| Receive allowed | CONFIRMED | 수신 후보만 제어하며 현재 위치가 유효한 사용자의 발신은 허용 |
| Configuration | CONFIRMED | `DirectionPresenceProperties`와 `qello.direction.presence.*`로 모든 정책값 외부화 |

설정 키는 `ttl`, `max-accuracy-meters`, `max-future-skew`,
`max-observation-age`로 고정한다. 시간은 Spring `Duration`, 정확도는 decimal meters로
binding한다. MVP 기본값은 `PT24H`, `100`, `PT30S`, `PT5M`이다.

## 5. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 요청 user ID를 믿어 다른 사용자의 위치를 변경 | 위치 조작·권한 침해 | Medium | P0 | token A 호출 뒤 A만 변경되고 B는 불변인 MockMvc+DB 증거 |
| 유효 JWT지만 BLOCKED/DELETED/없는 계정이 갱신 | 차단 우회·후보 오염 | Medium | P0 | 상태별 무변경 통합 테스트 |
| client가 expiresAt을 선택하거나 TTL을 임의 상향 | 오래된 위치로 지속 매칭 | High | P0 | request schema 부재와 서버 계산 시각 검증 |
| 늦은 요청 또는 동시 요청이 최신 위치를 덮어씀 | 잘못된 수신 후보 | High | High | 조건부 UPSERT 단일 SQL과 동시성 테스트 |
| 정확도 상한 밖 위치가 저장됨 | 잘못된 거리·방위 후보 | Medium | High | 경계값 단위/API 테스트와 DB 무변경 |
| 만료 경계가 `>=`로 바뀜 | 만료 위치의 후보 노출 | High | Medium | `expiresAt == at`과 `< at` PostGIS 후보 테스트 |
| 누락된 Boolean이 false로 해석됨 | 사용자 의도 없는 수신 중단 | Medium | Medium | nullable request field/Bean Validation 테스트 |
| 좌표가 DTO 문자열·오류 reason·응답에 포함 | 민감 위치 유출 | High | Medium | sentinel 좌표 기반 response/log/toString 부재 검증 |
| DB FK/NUMERIC 오류가 500 또는 내부 메시지로 노출 | 계약 위반·정보 유출 | Medium | Medium | feature validation 및 안전 오류 통합 테스트 |
| OpenAPI에 인증 또는 privacy 계약 누락 | 잘못된 클라이언트 구현 | Medium | Medium | generated spec assertion과 artifact diff |
| 설정값이 코드·SQL·테스트에 흩어져 서로 달라짐 | 환경별 후보 판정 불일치 | Medium | Medium | 단일 properties binding과 설정 변경 회귀 테스트 |

## 6. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-001 | 위·경도 정상값과 각 경계/범위 밖/한쪽 누락 입력 | `ActiveUserPresence` 또는 승인된 update value를 생성 | 정상 경계는 허용하고 잘못된 좌표는 `DIR-VAL-006`, 값이 포함되지 않은 reason으로 거절 | P0 | Domain executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-002 | 승인된 max accuracy의 `0`, 경계, 초과, 음수와 scale/DB 상한 입력 | validation 실행 | 허용 범위만 통과하고 저장 계층 오류 전 `DIR-VAL-008`로 종료 | P0 | Domain executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-003 | 고정 Clock과 `PT24H` TTL, 승인된 future skew/max age의 경계값 | update command와 policy properties 생성 | client는 expiresAt을 지정하지 못하고 서버가 `observedAt + PT24H`로 계산하며 미래·과거 경계의 포함/제외가 명시적이다. TTL은 양수이고 max age는 TTL보다 짧아 승인된 요청이 이미 만료되지 않는다 | P0 | Application executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-004 | `receiveAllowed`가 true/false/null | request validation과 command 변환 | true/false는 보존되고 null은 false 기본값으로 바뀌지 않고 400 | P0 | Web executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-005 | JWT subject가 양의 숫자/누락/0/음수/비숫자 | 인증 사용자 ID 변환 | 양의 ID만 service로 전달되고 나머지는 인증 오류로 종료 | P0 | Web executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-006 | Account ACTIVE USER/BLOCKED/DELETED/OPERATOR/없음 | service update 호출 | ACTIVE USER만 repository를 호출하고 region은 Account 값만 사용 | P0 | Application executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-007 | sentinel 위·경도와 정확도를 가진 request/response | `toString()`과 response mapping | 문자열과 response에 입력 숫자, userId, cell ID가 없음 | P0 | Web executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-008 | 조건부 UPSERT source와 repository result | contract test 실행 | `location_at` 비교가 SQL 한 문장에 있고 affected row가 `applied` 의미로 전달됨 | P1 | Persistence executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-009 | 네 설정값의 정상·0·음수·관계 역전 조합과 Spring property binding | `DirectionPresenceProperties` 생성 및 context binding | `PT24H` 기본값과 각 override가 독립 반영되고, 잘못된 값 또는 `maxObservationAge >= ttl`은 시작 시 fail-fast | P0 | Application executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-010 | `ActiveUserPresenceApiSpec`, `ActiveUserPresenceController`, request/response DTO source | API boundary contract test 실행 | Controller가 ApiSpec을 구현하고 메서드 수준 mapping·springdoc 애노테이션은 ApiSpec에만 있다. request DTO는 `web.request`, response DTO는 `web.response`에 있으며 Controller에는 중복 애노테이션이 없음 | P1 | Web executor |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-011 | 유효한 위치와 `receiveAllowed=false` presence | 현재 위치 판정과 수신 가능 판정을 호출 | 현재 위치는 유효하지만 수신 가능 판정만 false여서 발신과 수신 의미가 분리됨 | P0 | Domain executor |

## 7. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-001 | MockMvc, JWT resource server, Controller, service, JDBC, PostgreSQL/PostGIS | ACTIVE USER A/B와 region, A subject JWT | 정상 PUT 호출 | 200 성공, A row의 geography/정확도/flag/서버 계산 만료 저장, B row 불변, 응답에 좌표·userId 없음 | presence → account → region 삭제 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-002 | Security filter, Controller | DB baseline | token 없음/invalid token으로 호출 | 401 공통 오류, presence 0행 | marker row count 확인 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-003 | JWT subject parsing, Account lookup | 없는 ID, BLOCKED/DELETED/OPERATOR 계정의 유효 서명 JWT | 각 subject로 호출 | 승인된 401/403/404 계약으로 거절되고 presence 불변 | 계정 정리 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-004 | Bean Validation, domain validation, GlobalExceptionHandler | 기존 정상 presence와 log capture | 좌표 경계 밖, 한쪽 누락, 정확도 음수/초과, observedAt future/old, receiveAllowed 누락 호출 | 모두 400, 기존 row 불변, response/error/log에 sentinel 좌표 없음 | appender 제거·row 정리 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-005 | Conditional UPSERT | A의 이전 presence | 더 최신 observedAt의 요청 후 같은/더 오래된 요청 | 최신 요청만 반영되고 equal/older는 `applied=false`; 최신 좌표·flag·expiry가 보존 | row 정리 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-006 | Conditional UPSERT, 두 transaction/thread | 같은 사용자의 newer/older update와 latch | 두 요청을 경합시킴 | 완료 순서와 무관하게 최대 observedAt 행만 남고 partial state 없음 | executor 종료·row 정리 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-007 | API write, `findCandidates`, `findCandidateCountsBySegment`, PostGIS | API로 저장한 후보와 고정 `at`, direction scheme | `expiresAt` 직전/같음/이후에 후보 조회 | 직전만 포함하고 `expiresAt <= at`는 두 후보 경로 모두 제외 | scheme/presence/account/region 역순 정리 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-008 | API write, candidate SQL | 정상 좌표지만 `receiveAllowed=false` | 저장 후 preview/matching 후보 조회 | row는 갱신되지만 후보에는 포함되지 않음 | 동일 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-009 | Account region, request deserialization, JDBC FK | A/B가 서로 다른 region | A token으로 요청에 target/region 필드를 주입 시도 | 다른 user/region을 선택할 수 없고 승인된 unknown-field 계약을 따르며 DB에는 A의 서버 region만 저장 | row 정리 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-010 | Repository transaction, PostgreSQL constraint/failure | 기존 정상 row와 강제 DB 실패 경계 | update가 실패 | 기존 row가 부분 갱신되지 않고 안전한 feature/common 오류만 외부 노출 | transaction rollback·row 정리 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-011 | springdoc, `ActiveUserPresenceApiSpec`, artifact generator | test profile | `/v3/api-docs` 생성 | PUT path, `appAccessToken`, request validation, 근거가 확인된 200/400/401/403이 있고 response/example에 정확 좌표가 없음. Controller 애노테이션 중복으로 operation이 이중 생성되지 않음 | 결정적 artifact 재생성 |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-012 | Spring Boot config binding, service Clock | TTL만 `PT24H`와 별도 테스트값으로 바꾼 두 격리 context | 동일 observedAt 갱신 | 코드 변경 없이 각 TTL에 맞는 expiresAt이 저장되고 정확도·관측 시각 설정은 서로 영향을 주지 않음 | context/container lifecycle |
| TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-013 | API write, `DirectionPostService.previewAll`, candidate SQL | sender는 유효 위치와 `receiveAllowed=false`, 다른 candidate는 수신 허용 | sender preview와 candidate 조회 | sender는 자신의 위치를 원점으로 preview할 수 있지만 다른 발신자의 수신 후보에는 포함되지 않음 | scheme/presence/account/region 정리 |

## 8. Cross-cutting scenarios

### Database and transactions

- schema 변경 없이 기존 PK/FK/CHECK/partial GiST index와 `geography(Point,4326)`를 사용한다.
- 입력 검증은 FK/NUMERIC 오류 전에 수행하고 UPSERT 한 문장만 원자적 쓰기 경계로 둔다.
- DB 실패 시 기존 row의 position, accuracy, receive flag, timestamps가 함께 보존되는지 확인한다.

### Concurrency and idempotency

- `location_at`을 버전으로 사용해 newer-wins를 DB가 강제한다.
- 같은 observedAt 재시도는 값이 달라도 기존 row를 바꾸지 않는 결정적 no-op으로 계획한다.
- 두 요청의 commit/arrival 순서를 바꿔도 최종 관측 시각이 가장 큰 행만 남아야 한다.
- TTL 설정 변경은 기존 저장 행을 소급 변경하지 않고 다음 갱신부터 적용한다.

### External APIs

- 외부 연동은 없다. reverse geocoding, 지도 SDK, FCM/APNs를 호출하지 않는다.
- Spring Security JWT decoder는 test signing key 또는 `spring-security-test` JWT support로 격리한다.

### Failure recovery and reconciliation

- API 재시도는 별도 idempotency key 없이 `(user_id, observedAt)`의 monotonic contract로 안전해야 한다.
- Outbox나 보상 job은 만들지 않는다. 실패 요청은 DB 무변경이며 클라이언트가 재시도한다.
- 장기간 쌓인 만료 row 삭제/보존은 이번 Issue 밖이다. 후보 제외만 강제한다.

## 9. Test data and isolation

- Fixtures: ACTIVE/BLOCKED/DELETED/OPERATOR 계정, 두 region, 8-direction scheme,
  경계 좌표·정확도·observedAt, `receiveAllowed` true/false presence.
- Database isolation: Testcontainers PostgreSQL/PostGIS, 테스트 marker 기반 row,
  block/recipient가 생기면 의존 역순으로 정리한다.
- Clock/randomness: 단위 테스트는 `Clock.fixed`; 통합 테스트는 `@Primary` fixed Clock
  또는 명시적 기준 `Instant`를 사용한다. wall clock sleep은 쓰지 않는다.
- External API doubles: 없음. JWT는 테스트 전용 signer/support만 사용하고 실제 토큰을 기록하지 않는다.
- Cleanup: log appender와 executor를 `finally`/lifecycle에서 제거하고
  presence → account → region/scheme 순서로 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 10. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Domain/application executor | `direction/domain/ActiveUserPresence.java`, 신규 `DirectionPresenceProperties`/service, `application.properties`, 신규 domain/service/properties test | UNIT-001~003, UNIT-006, UNIT-009, UNIT-011, INT-012~013 | 대상 unit tests와 config binding integration |
| 2 | Persistence executor | `ActiveUserPresenceRepository.java`, `JdbcActiveUserPresenceRepository.java`, `ActiveUserPresenceSql.java`, 신규 persistence integration test | UNIT-008, INT-005~008, INT-010 | persistence integration + preview/matching regression |
| 3 | Web executor | 신규 `direction/web/ActiveUserPresenceController.java`, `direction/web/request/UpdateActiveUserPresenceRequest.java`, `direction/web/response/UpdateActiveUserPresenceResponse.java`, 신규 API boundary/API integration test | UNIT-004~005, UNIT-007, UNIT-010, INT-001~004, INT-009 | API boundary + integration test |
| 4 | API docs executor | 신규 `ActiveUserPresenceApiSpec.java`, `docs/api/openapi.json` | INT-011 | OpenAPI specification integration test |
| 5 | Test orchestrator | `docs/reports/tests/gh-121-*.md` | 전체 결과·잠재 문제 | repository required checks |
| 6 | Independent reviewer | read-only 전체 diff | 전체 | Issue/TASK/plan과 실제 증거 대조 |

실행자는 소유 파일을 겹치지 않는다. Web executor는 API docs executor가 고정한
`ActiveUserPresenceApiSpec` signature를 구현하고 해당 파일 자체는 수정하지 않는다.
`docs/api/openapi.json`은 직접 편집하지 않고 생성 테스트 결과만 반영한다.

## 11. Completion criteria

- [x] 모든 정책값과 API 계약이 사람에게 승인됨
- [ ] `PT24H` 기본값과 모든 presence 정책값의 외부 설정 분리 검증
- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 정확한 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] API·PostgreSQL/PostGIS 통합 테스트 통과
- [ ] 기존 preview·matching 후보 회귀 테스트 통과
- [ ] OpenAPI 산출물 재생성과 privacy assertion 통과
- [ ] ApiSpec/Controller 애노테이션 분리와 operation 비중복 검증
- [ ] DB·동시성·트랜잭션·인증·로그·장애 복구 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] 저장소 필수 검증 통과 또는 미실행 항목과 위험 기록

## 12. Human approval

- Reviewer:
- Decision: `APPROVED`
- Approved at: `2026-08-14T00:47:55+09:00`
