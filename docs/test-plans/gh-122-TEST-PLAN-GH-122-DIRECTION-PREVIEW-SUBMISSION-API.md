# Test Plan: TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API

> Created at: `2026-08-14T12:08:41+09:00`
> GitHub Issue: `#122`
> Status: Approved for implementation

## 1. Objective

인증 사용자가 현재 위치를 기준으로 전 세계 방향별 예상 후보 수를 안전하게 미리보고,
텍스트와 JPEG/PNG 이미지 한 장을 조합한 질문글을 멱등하게 비동기 제출하는 흐름을
검증한다.

가장 큰 위험은 기존 `coarseRegionCode` 필터가 남아 국내 다른 지역이나 해외 후보를
누락하는 것, preview 결과를 실제 수신자로 신뢰하는 것, 미디어 첨부와 Outbox가 서로
다른 transaction에 남는 것, fingerprint가 미디어를 구분하지 못하는 것, 정확 위치·수신자·
storage 정보가 API 경계를 통해 노출되는 것이다.

## 2. Scope

### Included

- JWT `sub` 기반 preview·submit·media upload/confirm 소유권
- ACTIVE USER, 현재 발신 presence, configured active scheme과 서버 `Clock` 재검증
- GLOBAL `0..20,100,000m` 범위의 모든 활성 direction segment 후보 집계
- preview와 #120 matching worker의 국가·대략 지역 미필터 범위 동치
- 서버 계산 `submittedAt`, `expiresAt = submittedAt + PT12H`
- NFC/바깥 공백 정규화 후 본문 최대 300 Unicode code points
- 텍스트만, JPEG/PNG 이미지 한 장만, 텍스트와 이미지 한 장 제출
- 기존 media upload request·confirm service의 앱 HTTP 경계와 LocalStack 검증
- READY·본인 소유·미첨부 media asset 한 건 검증
- 현재 `v1` fingerprint에 본문·media ID를 포함하고 fingerprint가 없는 nullable 행을 저장된 audience 의도로 복원
- 질문글·media attachment·audience·MatchRequested Outbox의 단일 transaction
- 동일 요청 재시도, 다른 요청의 키 재사용과 동일 키 동시 제출
- `202 SUBMITTED`, 공통 오류 응답, ApiSpec/Controller 분리와 OpenAPI 생성
- 응답·오류·로그·Outbox의 위치·수신자·storage privacy

### Excluded

- preview cache와 preview candidate ID/좌표/목록 반환
- client가 사용자 ID·좌표·지역·거리·시각·만료를 지정하는 계약
- 이미지 2장 이상, WebP/GIF/HEIC/영상, 이미지 편집·리사이즈·모더레이션
- 단계적 거리 확대와 SAME_COUNTRY 운영 모드
- 매칭 알고리즘·수신 슬롯 상한·최대 수신자 수 정책 변경
- #123 외부 Push, #124 수신함, #125 답변 API
- H3·Redis·Kafka와 GLOBAL 쿼리 최적화 구현; 대표 실행계획과 남은 위험만 기록
- 새 S3 인프라, 배포와 프로덕션 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #122 | preview와 submit을 별도 REST 계약으로 제공하고 인증 사용자·서버 시각을 사용한다. preview를 수신자 확정값으로 신뢰하지 않으며 submit은 `202 SUBMITTED`, 동일 멱등 재시도/충돌과 공통 오류를 제공한다. |
| 사용자 승인 `2026-08-14` | 국가가 다른 사용자까지 보낼 수 있는 GLOBAL 범위, `PT12H`, 본문 300자, JPEG/JPG/PNG 이미지 최대 1장, 텍스트/이미지/둘의 조합을 지원한다. |
| Parent #116 / predecessor #117 | preview는 모든 활성 segment를 한 PostGIS 질의로 집계하고 0 count를 보존하며 사용자 ID·정확 좌표를 반환하지 않는다. |
| Predecessor #115/#118 | `(sender_id, idempotency_key)`와 request fingerprint로 동일 재시도와 다른 의도를 구분한다. 제출은 post·audience·MatchRequested Outbox만 기록하며 수신자를 만들지 않는다. |
| Predecessor #120 | worker가 post/audience를 다시 읽어 현재 자격과 capacity를 잠그고 수신자를 확정한다. #122의 GLOBAL 정책은 이 재계산에도 동일하게 적용돼야 한다. |
| Predecessor #121 | `receiveAllowed=false`여도 현재 위치 자체가 유효하면 발신할 수 있고, API 사용자 ID는 JWT `sub`, 지역은 서버 Account/presence 값이다. |
| `DirectionPostService` | `previewAll`과 `send`는 현재 caller가 scheme·거리·지역·시각을 넘긴다. API application 경계가 client 입력 대신 승인된 서버 정책으로 command를 구성해야 한다. |
| `ActiveUserPresenceSql` | candidate SQL은 nullable `regionCode`를 지원한다. GLOBAL preview·worker는 `NULL`로 호출하고 `ST_DWithin`·`ST_Azimuth`·half-open segment 규칙을 보존한다. |
| `DirectionMatchingWorker` | 현재 `post.coarseRegionCode`를 후보 필터로 전달한다. 표시 지역과 candidate 범위를 분리해 GLOBAL에서 region 필터를 사용하지 않아야 한다. |
| `DirectionRequestFingerprint` / V12 | 현재 `v1:SHA-256`과 `VARCHAR(80)` 저장 계약에 본문과 media ID를 포함하고 coarse region은 제외한다. |
| V1 media schema / services | `media_attachment.media_id` PK, post order unique, owner composite FK와 deferred content trigger를 사용한다. `MediaUploadService`는 mime/size와 실제 object metadata를 검증하고 confirm을 멱등 처리한다. |
| API conventions | 성공은 `ApiResponse`, 오류는 `ApiErrorResponse`; 메서드 매핑과 springdoc은 ApiSpec, Controller는 구현에 둔다. `docs/api/openapi.json`은 생성 테스트로 갱신한다. |
| AGENTS.md | JUnit 5, 단위/통합 분리, 모든 테스트 `@DisplayName`, 정확한 생성 시각/source scenario 헤더, DB·동시성·transaction·외부 API·복구 분석을 지킨다. |

## 4. Decision gates

| Decision | State | Contract |
| --- | --- | --- |
| Delivery scope | CONFIRMED | `GLOBAL`; country/coarse region으로 후보를 제한하지 않는다. |
| Distance | CONFIRMED | `0 <= distance <= 20,100,000m`; 서버 설정만 사용한다. |
| Post expiration | CONFIRMED | 최초 서버 제출 시각 `+ PT12H`; client 입력 금지, retry 시 최초 값 반환. |
| Body | CONFIRMED | nullable, 정규화 후 1~300 Unicode code points. |
| Media count | CONFIRMED | 0개 또는 1개; 단일 media는 `displayOrder=0`. |
| Media format | CONFIRMED | `.jpg`/`.jpeg`는 `image/jpeg`, `.png`는 `image/png`; WebP 등은 거절. |
| Media size | REUSED | 기존 `qello.media.max-byte-size=10485760`을 유지. |
| Content invariant | CONFIRMED | 정규화된 본문 또는 READY media 중 하나 이상 필수. |
| Submit response | CONFIRMED | `202 Accepted`, public `submissionStatus=SUBMITTED`; recipient 확정값 없음. |
| Idempotency key | CONFIRMED | `Idempotency-Key` 헤더, 1~200자; 같은 client intent는 최초 snapshot 결과, 다른 intent는 `409 DIR-APP-005`. |
| Media upload web boundary | RECOMMENDED / PLAN APPROVAL REQUIRED | `POST /api/v1/media-assets/upload-requests`(201)와 `POST /api/v1/media-assets/{mediaId}/confirm`(200)으로 기존 service를 노출하고 새 storage 동작은 만들지 않는다. |

## 5. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| preview만 GLOBAL이고 worker는 `post.coarseRegionCode`로 제한됨 | 해외 후보가 화면에는 보이나 실제로 받지 못함 | High | P0 | 서울-제주·서울-해외 fixture의 preview/worker 동일 후보 증거 |
| 20,100km `ST_DWithin`이 spatial index 선택성을 잃음 | 후보 증가 시 latency·DB 부하 급증 | High | P0 | 대표 데이터 `EXPLAIN (FORMAT JSON)`과 #127 후속 위험 기록 |
| client가 user/좌표/region/거리/시각을 주입 | 발신 위치·범위 조작과 권한 침해 | Medium | P0 | MockMvc request injection 후 서버 값만 저장/사용됨을 DB로 확인 |
| BLOCKED/DELETED/없는 계정 또는 만료 presence가 발신 | 차단 우회·잘못된 origin | Medium | P0 | 상태별 API 거절과 DB 무변경 |
| preview count/response/log에 user ID·좌표 노출 | 위치 개인정보 유출 | High | P0 | sentinel 값 기반 response/log/OpenAPI/Outbox 부재 assertion |
| 본문과 media가 모두 없거나 본문 300자를 초과 | 활성화 시 DB trigger 실패·남용 | Medium | P0 | web/application validation과 DB 무변경 |
| 이미지 2장·WebP·타인/UPLOADING media가 첨부 | 정책·소유권 우회 | High | P0 | 각 오류 코드와 post/audience/outbox 0행 |
| post commit 후 media를 별도 attach | media-only post가 빈 콘텐츠로 worker에 노출 | High | P0 | 강제 attach 실패 시 post/attachment/audience/outbox 전체 rollback |
| fingerprint가 media ID를 포함하지 않음 | 같은 키로 다른 이미지가 동일 요청으로 오인 | High | P0 | v1 fingerprint unit + API 409 integration |
| 정책/지역 변경 뒤 retry가 현재 값으로 fingerprint를 재계산 | 같은 HTTP 요청이 409 또는 새 만료로 응답 | Medium | P0 | 최초 snapshot 기반 retry 회귀 |
| 동일 키 동시 제출이 post·attachment·Outbox를 중복 생성 | 중복 질문·미디어 PK 충돌·작업 중복 | High | P0 | 두 transaction/latch concurrency integration |
| S3 HeadObject timeout/5xx를 media 부재로 오인 | 정상 업로드 REJECTED 또는 내부 오류 노출 | Medium | P1 | `STORAGE_UNAVAILABLE` 503와 상태 보존 service/API test |
| presigned/storage key가 post API·오류·로그에 노출 | 저장소 정보 확산 | Medium | P0 | upload endpoint 외 surface의 sentinel 부재 assertion |
| OpenAPI의 202/409/media schema 또는 Bearer 인증 누락 | 클라이언트가 동기 확정·잘못된 retry를 구현 | Medium | P1 | generated spec assertion과 artifact diff |

## 6. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-001 | GLOBAL, 0/20,100,000m, PT12H, 300자, media 1장과 각 0·음수·역전·상한 초과 조합 | post properties 생성 | 승인값만 허용하고 잘못된 값은 startup 전에 feature/config 오류로 fail-fast | P0 | Direction application executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-002 | JWT subject 정상/누락/0/음수/비숫자와 request의 임의 user/time/region/distance 필드 | API application command 변환 | 양의 subject와 서버 `Clock`·정책·presence 값만 사용하고 나머지는 인증/validation 오류 | P0 | Direction web executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-003 | null/공백/1/300/301 code point 본문과 media 0/1/2개 조합 | submit request normalization | text-only/media-only/both만 허용하고 빈 콘텐츠·301자·2장은 안전한 direction 오류로 거절 | P0 | Direction application executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-004 | JPEG/JPG/PNG/WebP/GIF mime 요청과 10 MiB 경계 | media upload policy 검증 | `image/jpeg`·`image/png`와 크기 경계만 허용하며 JPG/JPEG를 같은 MIME으로 처리 | P0 | Media application executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-005 | 같은 질문·scheme·segment·본문에 media 없음/ID A/ID B | fingerprint 생성·복원 | 현재 v1은 media ID 차이를 구분하고 결정적이며 nullable fingerprint 행은 저장된 의도로 복원됨 | P0 | Direction submission executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-006 | 최초 저장 뒤 현재 region·거리 설정·Clock이 변경된 동일 client 요청 | retry 비교 | 최초 post/audience/fingerprint/시각 snapshot으로 동일성을 판정하고 최초 결과를 반환 | P0 | Direction submission executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-007 | ACTIVE/BLOCKED/DELETED/OPERATOR/없는 account와 current/expired/location-missing presence | preview/submit actor resolution | ACTIVE USER와 current precise location만 통과하며 receiveAllowed=false 발신은 허용 | P0 | Direction application executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-008 | configured active scheme, inactive/stale scheme ID와 잘못된 segment | preview/submit scheme resolution | preview는 configured active scheme만 반환하고 submit은 stale/비활성 scheme을 조용히 치환하지 않고 거절 | P0 | Direction application executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-009 | 본인/타인 media, UPLOADING/READY/REJECTED/DELETED, 이미 첨부된 media | submission attachment validation | READY 본인 미첨부 media 한 건만 저장 port로 전달 | P0 | Media application executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-010 | preview result와 submit result/DTO reflection | public response mapping | preview는 scheme+segment count만, submit은 postId+SUBMITTED+최초 시각만 포함하고 recipient/좌표/storage 필드가 없음 | P0 | Direction web executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-011 | GLOBAL worker와 preview command 구성 | candidate query argument capture | 두 경계 모두 region filter를 `null`로 사용하고 같은 거리 상한과 server at을 전달 | P0 | Direction persistence executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-012 | ApiSpec·Controller·request/response source | architecture contract test | Controller가 ApiSpec을 구현하고 method mapping/springdoc은 ApiSpec에만 있으며 DTO package가 분리됨 | P1 | Direction web executor |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-UNIT-013 | null/blank/201자 Idempotency-Key와 정상 키 | submit header validation | 1~200자만 service로 전달되고 잘못된 키는 DB 호출 전 400 | P0 | Direction web executor |

## 7. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-001 | Security, preview Controller/service, PostgreSQL/PostGIS | 서울 sender와 같은/다른 region·country의 8방향 후보, configured scheme | Bearer JWT로 preview GET | 200, 모든 segment가 sort order로 한 번씩 나오고 해외 후보 count 포함, user/좌표 없음 | presence → account → region/scheme 역순 삭제 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-002 | Preview API, JDBC marker, log capture | sentinel 좌표·user ID와 기존 post/outbox count | preview 호출 | post/audience/recipient/outbox 무변경, response/error/log에 sentinel 없음 | appender 제거·marker 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-003 | Candidate SQL, PostGIS geography | 서울-제주와 서울-해외 synthetic points, 0m/20,100,000m 경계 안팎, date-line | preview repository와 worker 후보 query | country/region과 무관하게 거리·half-open 방향 조건만 적용되고 상한 초과는 제외 | container row 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-004 | Submit API, service, JDBC, Outbox | ACTIVE question/sender/presence/scheme, text-only request | Idempotency-Key로 POST | 202 SUBMITTED, post/audience/Outbox 각 1, attachment/recipient/slot 0, expiresAt=server at+12h | outbox → audience → post 등 역순 삭제 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-005 | Submit API, media repositories, deferred trigger | 소유자 READY JPEG 또는 PNG 한 건, body null | media-only POST | 202, attachment displayOrder 0과 post/audience/Outbox가 같은 commit에 존재 | attachment부터 역순 삭제 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-006 | Submit API, text+media | READY image와 300 code point body | combined POST | 202, 정규화된 body와 media 한 건이 저장되고 response에 media/storage 정보 없음 | 동일 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-007 | Bean/domain validation, repositories | 기존 marker와 빈/301자/body+2 media/WebP/타인/UPLOADING/attached media 요청 | 각 submit 호출 | 400/403/404/409 중 계획에 고정한 feature 오류, post/audience/outbox/새 attachment 0 | marker 확인 후 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-008 | Media upload API, MediaUploadService, LocalStack S3 | JPEG/PNG body와 test-only object storage | upload request → presigned PUT → confirm | upload request는 201과 mediaId/URL/contentType/만료를 반환하고 소유자만 요청 가능하며, 실제 size/type 일치 시 READY, confirm 재시도는 같은 200 terminal 결과 | object/media row/container 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-009 | Security, account/presence/question/scheme | token 없음, invalid subject, BLOCKED/DELETED/OPERATOR, expired presence, inactive question/scheme | preview/submit 호출 | 승인된 401/403/404/409 계약으로 거절되고 write table 무변경 | fixture 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-010 | Idempotent submit transaction | 최초 text+media 제출 결과 | 같은 key와 같은 request 재호출 | 같은 postId/submittedAt/expiresAt, post/attachment/audience/Outbox 각 1 | fixture 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-011 | Fingerprint conflict mapping | 최초 제출과 질문/body/segment/media ID 중 하나만 다른 요청 | 같은 key로 재호출 | 409 `DIR-APP-005`, 최초 모든 row 불변 | fixture 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-012 | PostgreSQL unique constraints, two threads/transactions | 같은 sender/key/request와 latch, pool-safe executor 2개 | 동시에 submit | 둘 다 같은 public 결과 또는 한 요청의 안전한 재조회, 물리 row 각각 1, partial media 없음 | executor 종료·row 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-013 | Submit transaction, media/outbox failure injection | 정상 command와 attachment save 또는 Outbox insert 강제 실패 | submit commit 시도 | post·attachment·audience·Outbox가 모두 rollback되고 media asset은 READY 미첨부로 재사용 가능 | trigger/double 제거·row 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-014 | Submit, Outbox claim, DirectionMatchingWorker | 해외 candidate와 moderation PASSED post | submit 후 worker 처리 | API는 recipient를 동기 생성하지 않고 worker가 GLOBAL 재계산으로 해외 recipient를 확정 | confirmed outbox/recipient/receive state 역순 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-015 | Server Clock, post expiry | fixed at과 12h TTL | 제출 후 저장/응답 시각과 exact expiry boundary 조회 | 최초 submittedAt과 expiresAt이 일치하고 retry는 재계산하지 않으며 `at == expiresAt`에 신규 matching 불가 | row 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-016 | Springdoc, ApiSpec, artifact generator | test profile | `/v3/api-docs` 생성 | preview 200, upload 201/confirm 200, submit 202, Bearer/400/401/403/404/409/503 계약과 media 0/1·300자 schema가 있고 좌표·recipient·storage key example 없음 | 결정적 artifact 재생성 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-017 | ObjectStoragePort failure mapping | UPLOADING media와 timeout/5xx double | confirm API 호출 | 503 `ANS-EXT-001`, media는 UPLOADING 유지, post 제출 row 없음, 오류/log에 storage key/URL 없음 | double 제거·row 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-018 | Idempotent replay, mutable config/account region | 최초 제출 후 post 거리 설정과 Account region을 변경한 격리 context/fixture | 같은 HTTP request/key 재시도 | 현재 정책으로 충돌하지 않고 최초 post/audience/fingerprint/시각/202 결과 반환 | context·row 정리 |
| TEST-PLAN-GH-122-DIRECTION-PREVIEW-SUBMISSION-API-INT-019 | PostgreSQL planner, GLOBAL candidate SQL | representative multi-country presence rows와 production-like statistics | `EXPLAIN (FORMAT JSON)` 및 제한된 실행 | geography 계산과 정렬 비용을 기록하고 regression threshold를 넘으면 #127 BLOCKED 위험으로 보고; 임의 인덱스/캐시를 #122에 추가하지 않음 | container 종료 |

## 8. Cross-cutting scenarios

### Database and transactions

- 신규 migration 없이 V1 media constraint와 V12 fingerprint column을 우선 재사용한다.
  새 fingerprint가 `VARCHAR(80)`을 넘으면 구현 전에 plan/TASK 변경 승인을 받는다.
- `direction_post`, `media_attachment`, `post_audience`, MatchRequested Outbox는 같은
  transaction manager와 commit 경계를 사용한다.
- media validation은 FK/PK 위반 전에 실행하고 deferred content trigger는 최종 방어선으로
  남긴다.
- GLOBAL은 `direction_post.coarse_region_code` 저장을 제거하지 않는다. 해당 값은 표시
  snapshot이며 candidate SQL 필터만 `NULL`로 분리한다.

### Concurrency and idempotency

- `(sender_id, idempotency_key)` unique와 media attachment PK/order unique를 최종 방어선으로
  사용한다.
- 동일 key 경합은 두 thread와 latch로 재현하되 test pool 크기를 초과하지 않는다.
- 현재 v1 fingerprint는 normalized client intent와 단일 media ID를 구분한다. 서버 정책·지역·
  시각이 바뀐 retry는 최초 저장 snapshot으로 비교한다.
- media confirm은 기존 terminal 상태를 반환하고 HeadObject를 반복하지 않는 현재 멱등
  계약을 보존한다.

### External APIs

- 실제 외부 연동은 S3 presigned PUT과 HeadObject뿐이며 test에서는 LocalStack 또는
  `ObjectStoragePort` double을 사용한다.
- 실제 AWS 자격 증명·bucket URL·account 값은 계획·로그·fixture에 기록하지 않는다.
- upload endpoint만 presigned URL을 의도적으로 반환한다. preview/submit/confirm의 응답,
  모든 오류와 로그에는 URL·storage key가 없어야 한다.

### Failure recovery and reconciliation

- submit 실패는 DB 전체 rollback이며 READY media asset은 미첨부 상태로 남아 같은 요청에
  재사용할 수 있어야 한다.
- Outbox commit 후 worker 실패는 #119 retry/DEAD 계약을 사용하며 API가 보상 recipient를
  직접 만들지 않는다.
- storage timeout/5xx는 missing object와 구분해 media를 REJECTED로 확정하지 않고 503으로
  재시도 가능하게 한다.
- GLOBAL 쿼리 성능이 목표를 충족하지 못해도 범위를 지역으로 몰래 축소하지 않는다.
  결과를 BLOCKED/위험으로 기록하고 #127에서 데이터·인덱스·분할 전략을 검토한다.

## 9. Test data and isolation

- Fixtures: ACTIVE/BLOCKED/DELETED/OPERATOR account, 서울·제주와 국가가 다른 synthetic
  region/country, current/expired presence, configured/inactive scheme와 8 segment, ACTIVE
  question, JPEG/PNG/WebP media의 UPLOADING/READY/REJECTED/attached 상태.
- Distance fixtures: 0m 인접점, 서울-제주, 서울-해외, 날짜 변경선, 20,100,000m 경계
  안팎. 실제 사용자 위치나 운영 좌표를 사용하지 않는다.
- Database isolation: PostgreSQL/PostGIS Testcontainers, test marker 기반 row를 만들고
  confirmed outbox → attachment/recipient → audience → post → media/presence/account/region/
  scheme 순으로 FK 역순 정리한다.
- Clock/randomness: unit은 `Clock.fixed`; integration은 test Clock 또는 저장된 server 응답
  시각을 기준으로 검증하고 sleep을 사용하지 않는다. UUID/storage key 값 자체를 assertion에
  복사하지 않는다.
- External API doubles: LocalStack S3와 `ObjectStoragePort` fake/failure double만 사용한다.
- Concurrency: 2개 thread, 명시적 latch/barrier, `Future` timeout과 `finally` executor 종료를
  사용한다.
- Cleanup: log appender, failure trigger/double, executor와 container resource를 항상 해제한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 10. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Direction application executor | 신규 `direction/config/DirectionPostProperties.java`, 신규 `direction/service/DirectionPostApplicationService.java`, `application.properties`, 신규 properties/application unit test | UNIT-001, UNIT-003, UNIT-007~008 | 대상 unit tests + Spring binding test |
| 2 | Media application executor | `answer/config/MediaStorageProperties.java`, `answer/service/MediaUploadService.java`, `answer/service/MediaAttachmentService.java`, 관련 기존/신규 media unit tests | UNIT-004, UNIT-009 | media unit tests; 기존 answer media 회귀 |
| 3 | Direction submission executor | `direction/service/DirectionPostService.java`, `direction/domain/DirectionRequestFingerprint.java`, 신규 submission unit/integration test | UNIT-005~006, INT-004~007, INT-010~013, INT-015, INT-018 | submission unit + PostgreSQL transaction/idempotency integration |
| 4 | Direction persistence executor | `direction/matching/DirectionMatchingWorker.java`, 필요한 경우 candidate port/adapter의 GLOBAL 호출부, 신규 global preview/worker integration test | UNIT-011, INT-001~003, INT-014, INT-019 | PostGIS preview/worker + planner evidence |
| 5 | Direction web executor | 신규 direction preview/submit ApiSpec·Controller·request/response DTO, 신규 media upload/confirm ApiSpec·Controller·DTO, web contract/API integration test | UNIT-002, UNIT-010, UNIT-012~013, INT-008~009, INT-017 | MockMvc/security/API integration |
| 6 | API docs executor | `docs/api/openapi.json`, `OpenApiSpecificationIntegrationTest.java` | INT-016 | generated spec integration; artifact diff |
| 7 | Test orchestrator | `docs/reports/tests/gh-122-*.md` | 전체 실행 결과·잠재 문제 | target tests + repository required checks |
| 8 | Independent reviewer | read-only 전체 diff와 실행 artifact | 전체 | Issue/TASK/plan, source, tests, schema, OpenAPI 증거 대조 |

실행자는 같은 파일을 동시에 소유하지 않는다. Direction web executor는 API docs executor가
검토할 ApiSpec source를 작성하되 `docs/api/openapi.json`과 specification integration test는
수정하지 않는다. API docs executor는 Controller/application source를 수정하지 않는다.

## 11. Completion criteria

- [x] GLOBAL·PT12H·300자·JPEG/PNG 한 장과 콘텐츠 조합 정책 승인
- [x] media upload/confirm HTTP 경계를 #122에 포함하는 계획 승인
- [ ] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 모든 테스트 클래스 헤더의 정확한 ISO 8601 생성 시각과 source scenario 검증
- [x] 단위·MockMvc 테스트 통과
- [ ] PostgreSQL/PostGIS GLOBAL·transaction·concurrency 통합 테스트 통과
- [x] LocalStack media upload/confirm 통합 테스트 통과
- [x] 기존 #117/#118/#120/#121 및 answer media 회귀 테스트 통과
- [x] OpenAPI 산출물 재생성과 privacy assertion 통과
- [x] DB·동시성·transaction·S3·장애 복구·GLOBAL 성능 잠재 문제 분석
- [x] `templates/test-report.md` 기반 테스트 보고서 생성
- [x] 저장소 필수 검증 통과 또는 미실행 항목·영향·남은 위험 기록

> Dedicated API transaction rollback, two-thread idempotency, expiry-boundary and
> production-like planner scenarios remain unchecked and are recorded as residual
> risk in the generated test report.

## 12. Verification commands

```bash
./gradlew test --tests "com.dnd.qello.direction.*"
./gradlew test --tests "com.dnd.qello.answer.MediaUploadServiceTest"
./gradlew test --tests "com.dnd.qello.answer.MediaAttachmentServiceTest"
./gradlew integrationTest --tests "com.dnd.qello.DirectionPostApiIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.DirectionPostSubmissionMediaIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.DirectionPreviewGlobalIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.DirectionMatchingWorkerIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.MediaAssetStorageIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest"
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

필수 명령 실패는 구현 문제와 환경 문제를 분리하되 어느 경우에도 PASS로 숨기지 않는다.
환경 문제면 실패 명령·오류 요약·재현 조건·미검증 범위·남은 위험을 보고서에 기록한다.

## 13. Human approval

- Reviewer: User
- Decision: `APPROVED`
- Approved at: `2026-08-14` (conversation approval)
