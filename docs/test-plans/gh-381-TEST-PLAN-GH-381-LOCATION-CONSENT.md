# Test Plan: TEST-PLAN-GH-381-LOCATION-CONSENT

> Created at: `2026-11-23T22:14:25+09:00`
> GitHub Issue: `#381`
> Status: APPROVED — 2026-11-23, @Byuntil (D5 수정 승인, 나머지 추천안 승인)
> Task: `TASK.md`, `feat/gh-381-location-consent-history`
> Base: `feat/gh-379-location-collection-gate` (#380 미병합)
> 단위 10개 / 통합 15개. 표의 축약 ID 앞에는 `TEST-PLAN-GH-381-LOCATION-CONSENT-`를 붙인다.
> 설계 제안은 아직 승인되지 않았다. 이번 산출물은 계획 하나이며 구현·테스트 실행은 하지 않는다.

## 1. Objective

현재 약관에 동의한 인증 사용자만 위치 갱신·미리보기·발송을 사용하고, 동의·철회
요청의 이력을 서버 시각으로 추가한다. 철회 성공 후에는 해당 사용자의 presence가
없어야 하며, 경쟁 요청이나 저장 실패 때문에 이력과 위치가 어긋나서는 안 된다.

## 2. Scope

### Included

- 인증 본인의 동의·철회·현재 상태, 현재 약관 버전 설정, append-only 이력.
- 세 위치 API에서 B1 다음 동의 검사, 철회와 presence 삭제의 원자성.
- Flyway·API 명세·오류 코드, 기존 회귀 및 배포 순서 검토.

### Excluded

- 이번 단계의 production·테스트·migration 작성, 테스트 실행, TASK.md 변경.
- 약관 작성·홈페이지 게시, 제3자 제공·아동 동의, C6 시간 경과 파기.
- 기존 사용자 자동 동의 생성, 배포, commit/push/PR/Issue 코멘트.
- 법적 보존 기간의 확정. D2는 확인할 제품·법무 결정이며 법적 판단이 아니다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| Issue #381 / TASK Scope (CONFIRMED) | 요청마다 이력 추가, 이전 행 불변; INT-001~003 |
| Issue #381 완료 조건 (CONFIRMED) | 무기록·철회·이전 버전 거부, 현재 동의 허용; INT-004~006 |
| TASK 사용자 결정 (CONFIRMED) | 철회와 presence 삭제는 같은 트랜잭션; INT-007~009 |
| ADR-0001 / ADR-0002 (accepted) | Flyway만 DDL 소유, FK ID 경계, JPA/JDBC 같은 트랜잭션; INT-001,007~009 |
| 기존 API 관찰 | AccountWithdrawalController가 ApiResponseFactory로 200 응답; 새 API도 동일 제안 |
| #379 계획 / 실제 gate 코드 | B1은 세 메서드 첫 줄; submit은 replay보다 먼저 검사; INT-005 |
| TASK Validation | 구현 승인 후 §10 명령 수행; 이번 단계에서 성공으로 간주하지 않음 |

### 관찰한 코드 경계

- `DirectionPresenceService.update`는 쓰기 트랜잭션이다. 계정은 일반 조회하고
  `saveIfNewer`는 location_at 비교 UPSERT이므로 현재 동의 여부를 원자적으로 보호하지 않는다.
- `DirectionPostApplicationService.preview`는 read-only, `submit`은 NOT_SUPPORTED다.
  submit의 replay와 send는 별도 쓰기 트랜잭션이므로 facade에서 얻었다 놓은 잠금은
  후속 쓰기를 보호하지 않는다. 이번 제안은 진입 시점 동의 판정과 presence 쓰기/철회 직렬화를 구분한다.
- `AccountWithdrawalService.request`는 상태 변경과 cleanup을 같은 트랜잭션에서 수행한다.
  `PresenceWithdrawalCleanup`은 요청 시 위치를 삭제한다. 취소 시 위치는 복원하지 않는다.
- V33은 WITHDRAWAL_PENDING과 요청 시각을 추가한다. completion은 계정을 물리 삭제하지
  않고 DELETED 및 닉네임 정리를 수행한다. 기존 낙관적 잠금만으로 위치 UPSERT 경합은 막지 못한다.
- 현재 `AccountRepository`에는 잠금 조회가 없다. 새 공통 잠금 경로가 필요하다(D7).
- V33까지 존재한다. V34 번호는 실행 직전에 stacked base와 다른 작업의 migration을 재확인한다.

### 사람 승인이 필요한 설계 결정 (2026-11-23 승인, D5는 아래 수정안으로 승인)

| ID | 대안과 장단점 | 추천안 |
| --- | --- | --- |
| D1 스키마·현재 상태 | A: 이력 최신 행 조회는 단일 원본이나 매번 인덱스 조회 필요. B: 이력+현재 상태 테이블은 조회가 쉽지만 이중 쓰기 불일치 위험 | A. `location_consent_history`: `id BIGINT identity PK`, `user_id BIGINT NOT NULL FK user_account(id) ON DELETE RESTRICT`, `terms_version VARCHAR(100) NOT NULL`(공백 금지), `action VARCHAR(16) NOT NULL CHECK IN ('CONSENT','WITHDRAW')`, `recorded_at TIMESTAMPTZ NOT NULL`. `(user_id,id DESC)` 인덱스. 사용자 전체 최신 id 한 행이 CONSENT이고 현재 버전과 같을 때만 허용. 사용자+버전 unique는 두지 않음 |
| D2 탈퇴·보존 | A: soft-delete 계정 FK와 원장을 보존하면 증빙 연결 유지, 개인정보 보존 부담. B: 탈퇴 완료 시 삭제/익명화하면 최소화에 유리하나 원장 불변 계약 위반 및 증빙 손실 | 이번 변경은 A 구조만 제안하되 무기한 보존을 승인한 것으로 보지 않음. 법무/제품이 보존 목적·기간·기산점·접근권한·만료 파기 및 익명화 범위를 확정하기 전 운영 출시 BLOCKED. B 선택 시 원장 수정 금지와 충돌하므로 별도 승인·범위 변경 필요 |
| D3 API·멱등성 | A: POST 이벤트 API는 요청별 행 추가가 명확하나 네트워크 재시도도 행 증가. B: PUT/DELETE 상태 API는 상태 멱등성이 명확하나 요청별 감사 이력 의미가 불분명 | A. `POST /api/v1/location-consents` body `{termsVersion}`, `POST /api/v1/location-consents/withdrawals` body 없음, `GET /api/v1/location-consents/current`. 200 ApiResponse의 data는 `currentTermsVersion`, `consented`, nullable `lastAction`, `lastTermsVersion`, `recordedAt`. 인증 subject만 사용. 재동의·중복 철회도 성공마다 한 행, 상태만 멱등. 철회 행 버전은 최신 이력 버전, 무기록이면 현재 설정 버전. 철회는 모든 버전을 무효화 |
| D4 오류·순서 | A: 403은 사용자 동의 부족을 명시, B: 409는 복구 가능한 상태 충돌 표현이나 기존 권한 거부 의미와 다름 | 403 `LOCATION_CONSENT_REQUIRED` / `DIR-APP-009`; 버전 불일치 409 `LOCATION_CONSENT_VERSION_MISMATCH` / `DIR-APP-010` 제안. B1 → 동의 확인 → 기존 계정·위치·replay 처리. 인증 401 및 MVC 형식 검증 400은 서비스 이전일 수 있음. 실행 시 코드 충돌 재확인 |
| D5 설정 누락 | A: 모든 프로필 기동 실패는 누락 조기 발견, B: 기동하되 위치 기능 503은 비위치 기능 가용성 유지하나 오류 경로 증가 | A. `qello.location.consent.current-terms-version` 필수, 1~100자 비공백·앞뒤 공백 거부. B1 false여도 필수. dev/local/test도 동의 우회 없음; test만 명시적 합성 버전 fixture, dev/local 실제 게시 버전은 운영자가 주입. 설정 변경은 재배포, 다중 인스턴스 혼합 버전 요청은 거부될 수 있음 |
| D6 병합·출시 | A: 모바일 동의 화면 준비 후 서버와 조율 배포는 공백 최소화, B: 서버 먼저 배포하면 보호는 빠르나 기존 앱 위치 기능 전부 차단 | A. #380 병합 후 base 정리, 모바일·서버 API 계약 선합의, 동의 화면 지원 버전 출시 확인 후 서버 enforcement 배포. 혼합 기간 위치 기능 일시 중단을 명시적으로 승인. 기존 사용자 자동 동의 금지; dev도 기록 없으면 차단된다는 공지와 수동 동의 준비 |
| D7 경쟁 순서 | A: 사용자 계정 행의 공통 DB 잠금은 무이력 사용자도 직렬화 가능하나 계정 변경과 경합. B: 동의 최신 행만 잠그면 단순하지만 최초 행 부재와 새 이벤트 삽입 경쟁을 막지 못함 | A. 동의/철회/위치 갱신 모두 계정 행을 동일 순서로 잠근 뒤 최신 이력 재조회, append 또는 UPSERT, commit까지 유지. READ COMMITTED에서 잠금 획득 후 새 statement 조회. recorded_at은 잠금 후 서버 Clock, 순서는 잠금 후 발급 id. HTTP 도착 순서는 보장하지 않음 |
| D8 철회와 조회·발송 | A: 진입 시 동의 판정은 기존 submit 트랜잭션 구조 유지, 이미 진입한 작업은 완료 가능. B: 최종 발송 commit까지 잠금 유지하면 강한 중단 보장이나 NOT_SUPPORTED/replay/send 재설계와 긴 잠금 필요 | 이번 범위는 A 제안. 철회 commit 이후 진입한 요청은 replay도 거부. 진행 중 발송까지 취소해야 한다면 B로 계획·소유 파일 재승인. 위치 쓰기는 예외 없이 D7로 보호 |

승인 기록(2026-11-23, @Byuntil):

- D1, D2, D3, D4, D7, D8: 위 추천안대로 승인. D2는 구조만 승인하며 보존 목적·기간·파기 정책은 운영 출시 전에 따로 확정한다.
- D5 수정 승인: 현재 약관 버전은 YAML에 적고 리뷰를 거친 설정 변경으로만 바꾼다. 환경변수 placeholder(`${...}`)로 두지 않는다. dev·local은 합성 값 `dev-v1`, test는 합성 값(`test-v1`, `test-v2`)을 쓴다. 운영 게시 버전은 약관을 게시할 때 `application.yml`에 리뷰로 추가한다. 아직 게시 버전이 없으므로 `application.yml` 기본 프로필은 값을 두지 않는다. B1 게이트가 켜져 있는데 값이 없거나 형식이 틀리면 기동 실패한다. B1이 꺼져 있으면 위치 API가 이미 막혀 있으므로 값이 없어도 기동한다. 동의 검사 자체는 모든 프로필에서 우회하지 않는다.
- D6: 추천안 A 승인. 구현과 PR 리뷰는 진행하되, 모바일과 동의 API 계약을 먼저 맞추고 동의 화면이 들어간 앱이 나온 뒤에 서버 PR을 병합한다.

UNKNOWN: 실제 게시 약관 버전/시행일, 보존 정책의 근거와 기간, 모바일 출시 준비 및 dev
중단 허용 시점, C1 작업과 파일 충돌, D8 진행 중 발송의 제품 기대.
이 정보 없이 배포나 정책 확정을 하지 않는다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 무동의 위치 사용 | 개인정보 처리 위반 가능성 | 중 | P0 | INT-004~006 |
| 이력·삭제 부분 commit | 철회 성공인데 좌표 잔존 | 중 | P0 | INT-007~009 |
| 최초 동의·철회 경쟁 | 최신 상태 오판 | 중 | P0 | INT-008 |
| 시각 동률·역행 | 과거 동의 부활 | 중 | P0 | UNIT-001, INT-003 |
| 탈퇴·좌표 갱신 경합 | 비활성 사용자 좌표 잔존 | 중 | P0 | INT-010 |
| 설정 누락·혼합 배포 | 전면 차단·버전 불일치 | 높음 | P0 | INT-012, D6 |
| FK·migration 불일치 | 배포 실패 | 중 | P0 | INT-001 |
| 로그·응답 개인정보 노출 | 불필요한 정보 노출 | 낮음 | P1 | INT-011 |
| 외부 호출·outbox 발생 | 거부인데 후속 처리 | 중 | P0 | INT-005,014 |
| 기존 fixture 동의 누락 | 기존 테스트 전면 실패 | 높음 | P0 | INT-015 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | 무기록/최신 철회/구버전/현재 동의, 시각 동률 | 현재 상태 판정 | 최신 id 및 현재 버전 일치만 허용 | P0 | Unit |
| UNIT-002 | 현재 버전과 다른/빈/과도하게 긴 입력 | 동의 요청 | 거부, append 없음 | P0 | Unit |
| UNIT-003 | 현재 버전 동의가 이미 존재 | 같은 버전 재동의 | 새 이력 1건, 이전 객체 불변, 서버 시각 | P0 | Unit |
| UNIT-004 | 무기록/동의/이미 철회 | 철회 | 버전 선택 D3, 이력 추가 및 본인 presence 삭제 | P0 | Unit |
| UNIT-005 | B1 꺼짐 | 세 서비스 진입 | 기존 B1 오류, 동의 조회·replay·쓰기 없음 | P0 | Unit |
| UNIT-006 | B1 켜짐, 동의 없음 | 세 서비스 진입 | 동의 오류, presence 조회/쓰기·replay/send 없음 | P0 | Unit |
| UNIT-007 | 현재 동의, ACTIVE USER | 위치·preview·submit | 기존 검증과 저장·조회 경로 진행 | P0 | Unit |
| UNIT-008 | 설정 누락/공백/길이 경계, dev/local/test 포함 | 설정 검증 | D5대로 전 프로필 동일 판정, 입력값 로그 미노출 | P0 | Unit |
| UNIT-009 | 새 오류 enum | 코드/상태 조회 | D4 매핑 및 코드 유일성 | P1 | Unit |
| UNIT-010 | 인증 본인과 임의 외부 사용자 값 | controller 호출 | 본인 subject만 사용, 200 공통 응답; 미인증 거부 | P0 | Unit |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | Flyway, 실제 PostGIS | 빈 DB 및 V33 DB 각각 | 새 migration·제약 위반 insert | 기존 checksum 불변, FK/NOT NULL/action/version 제약, 인덱스 존재, 사용자 물리 삭제 RESTRICT | 전용 DB 폐기 |
| INT-002 | MVC, 이력 저장소 | 두 사용자, 합성 버전 | 동의 2회·철회 2회·GET | 성공 요청마다 1행, 기존 행 모든 값 불변, 타인 이력 불변, GET 쓰기 없음 | fixture 역순 삭제 |
| INT-003 | 실제 DB 상태 조회 | 동일 시각 및 역행 시각 이벤트 | 구버전 동의→현재 동의→구버전 기준 철회 이력 조회 | 사용자 전체 최신 id로 판단, 과거 현재버전 동의를 찾아 부활시키지 않음 | fixture 삭제 |
| INT-004 | MVC, presence | 무기록/철회/구버전별 기존 presence 있음·없음 | 유효한 PUT | 403 DIR-APP-009, 행 수와 전체 내용 불변 | fixture 삭제 |
| INT-005 | MVC, preview/posts/outbox | INT-004 상태와 기존 replay 키, B1 on/off | preview·submit 반복 | B1 off면 기존 503 우선, on이면 403; post/audience/outbox와 presence 불변 | fixture 삭제 |
| INT-006 | 세 API 전체 | 현재 동의, 정상 질문·scheme·presence fixture | PUT→preview→submit | 갱신 저장, preview·발송 성공; 기존 발송 멱등성 유지 | fixture 역순 삭제 |
| INT-007 | 철회 서비스, DB transaction | presence와 동의 있음, append/삭제 각각 실패 주입 | 철회 호출 | 성공은 이력+삭제 함께 commit; 각 실패는 둘 다 rollback, 타인 불변 | 실패 주입 해제·정리 |
| INT-008 | 독립 connection 2개 | 무기록 및 기존 동의, 잠금 진입 신호 | 동의/철회를 양 순서로 경합 | 먼저 잠금 잡은 요청부터 이력 순서 확정, 성공 2건이면 2행, 최신 행과 상태 일치 | executor 종료·fixture 삭제 |
| INT-009 | 실제 service proxy, DB | 현재 동의, 위치 쓰기 직전 및 철회 잠금 후 신호 | 갱신→철회 / 철회→갱신 순서 강제 | 앞 순서는 갱신 후 삭제; 뒤 순서는 갱신 거부. 두 작업 종료 시 좌표 없음. presence 원래 없는 경우 포함 | executor 종료·fixture 삭제 |
| INT-010 | 탈퇴·동의·presence 서비스 | ACTIVE 사용자 | 탈퇴 요청·취소·완료, 위치 갱신 경합 | 탈퇴 이후 위치 없음, 비활성 계정 신규 동의/갱신 거부, 원장 D2대로 불변; 취소는 위치 복원 안 함 | 기존 탈퇴 fixture 정리 |
| INT-011 | Security, MVC | 두 사용자 및 비활성/운영자 계정 | 미인증·위조 대상·잘못된 버전/본문 | 401/기존 권한 오류/400/409 구분, 요청값·좌표 오류 본문 미노출, DB 무변경 | fixture 삭제 |
| INT-012 | 실제 profile 설정 context | 기본/dev/local/test, 버전 누락/정상 | context 시작 및 기존 무동의 사용자 PUT | D5 기동 실패/성공, 정상 기동해도 무동의는 차단 | context 종료 |
| INT-013 | OpenAPI 생성·기존 명세 검사 | 새 ApiSpec | 명세 재생성 및 비교 | 새 3 API, 인증·DTO·403/409, 기존 위치 API 403/503 정확; 산출물 일치 | 기존 생성 절차 |
| INT-014 | DB, 서비스, 기존 외부 fake | commit 전 실패 및 commit 후 응답 유실 모사 | rollback 후 재요청 / commit 후 동일 요청 | 전자는 최종 1행, 후자는 2행으로 상태 동일; 거부/철회가 외부 호출·outbox 생성 안 함 | fake reset·fixture 삭제 |
| INT-015 | 기존 단위·통합 전체 | 정상 동작 fixture에만 명시적 동의 추가 | 기존 위치·매칭·탈퇴·dev/local 회귀 | 기존 의미 유지, 미동의 시나리오에 자동 fixture 주입 금지 | 기존 정리 확장 |

통합 시나리오는 INT-013만 P1, 나머지는 P0. 실행 소유자는 Integration executor다.

## 7. Cross-cutting scenarios

### Database and transactions

- D7은 같은 사용자 계정 행 → 최신 이력 → presence 순서로 잠근다. 최초 이력 행을
  잠그는 방식은 행 부재를 보호하지 못하므로 사용하지 않는다.
- 철회 append와 presence 삭제는 하나의 실제 transaction/DataSource로 수행한다.
  INT-007은 서비스 proxy를 호출하고 commit 이후 별도 connection으로 상태를 검증한다.
- 계정 탈퇴와의 경합은 기존 상태 UPDATE 잠금과 호환되어야 한다. JPA persistence
  context에 잠금 전 읽은 계정이 남아 있으면 stale 상태를 재사용하지 않도록 한다(INT-010).
- 최신 조회는 현재 버전으로 먼저 필터링하지 않는다. 철회가 구버전으로 기록되더라도
  사용자 전체 최신 이벤트이므로 현재 동의는 false다.

### Concurrency and idempotency

- D7의 직렬화 순서는 서버 잠금 획득 순서이며 클라이언트 시각·HTTP 도착 순서가 아니다.
- 같은 동의/철회 재요청은 상태 멱등, 감사 행은 매 요청 증가한다. rejected 요청은 성공
  이벤트로 남기지 않는다. idempotency-key dedup은 Issue의 요청별 행 추가와 충돌하므로 추가하지 않는다.
- INT-008~010은 별도 connection/transaction과 bounded latch/future 신호를 사용한다.
  작업 시작 전에 신호를 등록하고 잠금 획득·재조회·쓰기 직전 사건으로 순서를 만든다.
  sleep, polling, 단순 반복 성공률로 경합 재현을 대체하지 않는다.
- 철회 후 명시적 재동의와 새로운 위치 요청은 다시 허용된다. 이 경우까지 좌표 부재를
  요구하지 않는다. D8의 진행 중 발송은 강한 취소 보장 대상이 아니다.

### External APIs

- 약관 홈페이지를 서버가 호출하지 않는다. OS 위치 권한과 서버 동의는 별도 상태다.
- 기존 FCM/moderation fake를 유지하고 INT-005/014에서 새 outbox와 호출 수 증가가
  없음을 확인한다. 기존에 큐에 있던 작업 취소는 범위 밖이다.

### Failure recovery and reconciliation

- 이력 추가 실패 또는 위치 삭제 실패는 함께 rollback. 응답 유실 후 재요청은 새 행이
  남지만 현재 상태와 삭제 결과는 동일해야 한다(INT-014).
- 애플리케이션 rollback으로 동의 검사를 없애지 않는다. 장애 시 B1 false로 위치 기능을
  닫고 승인된 수정 배포를 진행한다. 이미 적용한 migration/원장을 되돌리거나 지우지 않는다.
- 다중 인스턴스 버전 변경은 혼합 버전 기간을 최소화하고 구버전 앱의 409 처리 확인 후 진행한다.
  dev 기존 사용자의 실제 동의를 자동 생성하는 복구 방식은 금지한다.

## 8. Test data and isolation

- Fixtures: 합성 약관 `test-v1`, `test-v2`, ACTIVE USER 2명, 무기록/동의/철회/구버전 이력,
  기존 합성 좌표·활성 질문·scheme. 실제 게시 버전과 혼동하지 않는다.
- Database isolation: 기존 PostgisContainerIntegrationTestSupport 사용, 클래스별 고유 fixture,
  경쟁 테스트는 테스트 메서드 전체 rollback transaction 없이 실제 commit을 관찰한다.
- Clock/randomness: 주입 Clock 고정, 동률/역행도 별도 입력으로 검증. 사용자 fixture 키만
  기존 생성기를 사용하고 결과 순서를 무작위 실행 순서에 의존시키지 않는다.
- External API doubles: 기존 fake만 사용. 동의 저장소/잠금을 mock하여 경쟁 테스트를 통과시키지 않는다.
- Cleanup: 자식 행→이력→계정 순서로 테스트 소유 행만 정리; 모든 executor와 context 종료.
  테스트 DB fixture 삭제는 운영 원장 파기 정책의 근거가 아니다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Production executor | 아래 Production 파일 목록만 | D1~D8 구현 | `./gradlew compileJava` |
| 2 | Unit executor | 아래 Unit 파일 목록만 | UNIT-001~010 | `./gradlew test` |
| 3 | Integration executor | 아래 Integration 파일 목록만 | INT-001~015 | `./gradlew integrationTest` |
| 4 | 독립 검증 executor | 소스 수정 금지, 승인 후 테스트 보고서만 | 전체 | §10 명령 및 실제 diff 검토 |

아래는 승인 받을 파일 소유 제안이지 현재 수정 허가가 아니다. 공통 Java 기준 경로는
`src/main/java/com/dnd/qello/`, Unit은 `src/test/java/com/dnd/qello/`,
Integration은 `src/integrationTest/java/com/dnd/qello/`다.

Production 생성:
- `direction/consent/LocationConsent.java`, `LocationConsentAction.java`,
  `LocationConsentService.java`, `LocationConsentProperties.java`,
  `LocationConsentRepository.java`, `JpaLocationConsentRepository.java`,
  `LocationConsentEntity.java`, `SpringDataLocationConsentRepository.java`.
- `direction/web/LocationConsentController.java`, `LocationConsentApiSpec.java`,
  `request/LocationConsentRequest.java`, `response/LocationConsentResponse.java`.
- `src/main/resources/db/migration/V34__add_location_consent_history.sql`(번호 충돌 시 승인 갱신).

Production 수정:
- `direction/service/DirectionPresenceService.java`, `DirectionPostApplicationService.java`,
  `direction/error/DirectionErrorCode.java`, `direction/web/ActiveUserPresenceApiSpec.java`,
  `DirectionPostApiSpec.java`.
- `account/repository/jpa/JpaAccountRepository.java`: 계정 잠금 port의 adapter 구현.
  잠금 port는 `AccountRepository`에 넣지 않고 새 파일 `account/repository/AccountLockRepository.java`로 분리한다(2026-11-23 @Byuntil 승인. `AccountRepository` 가짜 구현을 둔 기존 단위 테스트 5개를 건드리지 않기 위해서다). 구체 SQL 소유 파일이 추가로 필요하면 승인 목록 갱신.
- `src/main/resources/application.yml`, `application-dev.yml`, `application-local.yml`,
  `docs/error-codes.md`, `docs/product/data-model/direction_communication.dbml`,
  `docs/product/data-model/schema-manifest.md`.

Unit 생성: `direction/consent/LocationConsentTest.java`, `LocationConsentServiceTest.java`,
`LocationConsentPropertiesTest.java`, `direction/web/LocationConsentControllerMockMvcTest.java`,
`direction/error/LocationConsentErrorCodeTest.java`.
Unit 수정: `direction/service/DirectionPresenceServiceTest.java`, `DirectionPostApplicationServiceTest.java`.

Integration 생성: `LocationConsentPersistenceIntegrationTest.java`(001~003),
`LocationConsentApiIntegrationTest.java`(004~006,011),
`LocationConsentConcurrencyIntegrationTest.java`(007~010,014),
`LocationConsentConfigurationIntegrationTest.java`(012).
Integration 수정: `OpenApiSpecificationIntegrationTest.java`(013),
`src/integrationTest/resources/application-test.yml`, `docs/api/openapi.json`.
INT-015 수정 후보는 기존 `ActiveUserPresenceUpdateIntegrationTest.java`,
`ActiveUserPresenceConfigurationIntegrationTest.java`, `ActiveUserPresenceApiIntegrationTest.java`,
`DirectionPreviewIntegrationTest.java`, `DevProfileBootIntegrationTest.java`,
`QelloLocalProfileIntegrationTest.java`, `QuestionRecommendationIntegrationTest.java`,
`DirectionMatchingPerformanceIntegrationTest.java`, `DirectionMatchingVerticalFlowIntegrationTest.java`,
`DirectionMatchingE3PerformanceIntegrationTest.java`, `DirectionMatchingVerticalFlowRecoveryIntegrationTest.java`,
`LocationCollectionGateDisabledIntegrationTest.java`다.
추가 fixture 영향 파일이 발견되면 실패를 우회하지 않고 실제 목록을 승인자에게 제출한다.

범위 추가 승인(2026-11-23 @Byuntil): `FlywayMigrationIntegrationTest.java`, `NotificationPreferenceMigrationIntegrationTest.java`, `AccountWithdrawalIntegrationTest.java`를 INT-015 수정 대상에 추가한다. 이후 V34 추가와 동의 요구 때문에 기존 테스트의 기대값·fixture·정리 순서만 바뀌는 경우는 추가 승인 없이 고치고 보고서에 파일별로 기록한다. 검증을 약화하거나 미동의 시나리오에 동의 fixture를 자동 주입하는 변경은 이 허용에 포함하지 않는다.

독립 보고서 경로(후속 승인 대상):
`docs/reports/tests/gh-381-TEST-PLAN-GH-381-LOCATION-CONSENT.md`.
Production 완료 후 Unit/Integration을 병렬로 진행할 수 있으나 파일 공유는 금지한다.
Java 관례·test-run 및 reporting 지침을 실행 전에 읽는다. C1과의 소유 충돌은 오케스트레이터가 조정한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

- [ ] D1~D8 사람 승인, UNKNOWN 중 구현에 필요한 계약 확정
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`
- [ ] 기존 회귀 실패와 새 실패 구분, OpenAPI 산출물 일치
- [ ] 운영 출시 전 D2 보존 정책 및 D6 모바일·배포 순서 별도 승인

`gh`, `harness` 실행 시 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`를 앞에 붙인다.
필수 P0 또는 기존 회귀 실패는 FAIL, Docker/권한/승인 부재로 검증 불가는 BLOCKED다.
계획 단계에서 위 체크박스를 완료로 바꾸지 않는다. 문구를 고정하는 테스트는 추가하지 않는다.

### 계획 단계 검증 기록

- Issue #381 조회, branch/base/status 확인, 지정 scaffold 명령 실행 성공.
- production·테스트 실행은 사람 승인 전이므로 미실행. 전체 harness/PR-ready 검사는 구현 후
  실행할 게이트로 남긴다. 계획 검증은 섹션/ID/소유 범위/공백 검사로 제한한다.
- 기존 TASK.md 변경은 사용자 소유로 보존한다. 현재 변경 허용 파일은 이 계획 하나뿐이다.

## 11. Human approval

- Reviewer: @Byuntil
- Decision: `APPROVED` — 단위 10개·통합 15개 계획 승인. D1·D2·D3·D4·D7·D8 추천안대로, D5 수정안, D6 추천안 A
- Approved at: 2026-11-23
- 구현 상태: 승인됨. 병합은 D6 조건(모바일 동의 화면 출시) 충족 후
