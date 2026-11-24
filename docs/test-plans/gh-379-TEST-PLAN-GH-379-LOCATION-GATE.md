# Test Plan: TEST-PLAN-GH-379-LOCATION-GATE

> Created at: `2026-11-23T16:36:11+09:00`
> GitHub Issue: `#379`
> Task: `TASK.md` / branch `feat/gh-379-location-collection-gate`
> Status: `APPROVED`
> 단위 8개 + 통합 5개 = 시나리오 ID 13개. 매개변수별 실행 횟수와 다르다.
> 이번 단계는 계획 작성뿐이며 테스트와 구현은 실행하지 않았다.

## 1. Objective

위치기반서비스사업 신고 수리 참조가 설정되기 전에는 정확 좌표를 받거나 읽는 세 API가
서버에서 거부되고, 거부된 요청이 좌표 관련 행을 남기지 않는지 검증한다. 게이트가
켜진 설정(test·dev·local)에서는 기존 위치·미리보기·발송 동작이 바뀌지 않아야 한다.

실패하면 두 위험 중 하나가 생긴다. 게이트가 열린 채 운영 배포되면 신고 수리 전에 실제
사용자 좌표를 수집한다. 반대로 게이트가 dev·test를 막으면 모바일 테스트와 기존 회귀
테스트가 깨진다.

## 2. Scope

### Included

- `qello.location.collection.enabled`와 `qello.location.collection.lbs-registration-reference`의
  바인딩, 기본값, 참조 판정 규칙, fail-closed 기동 검사, dev·local 예외.
- `PUT /api/v1/direction/presence`, `GET /api/v1/direction/preview`,
  `POST /api/v1/direction/posts`의 거부 응답과 거부 시 DB 무변경.
- 거부 시점이 계정 조회·멱등 replay·저장보다 앞서는지.
- 게이트를 켠 test·dev·local 프로필의 기동과 기존 통합 테스트 회귀.
- `DirectionErrorCode` 새 코드, ApiSpec 오류 응답, `docs/api/openapi.json` 재생성.

### Excluded

- 이번 단계의 production 코드·설정·테스트 수정과 테스트 실행.
- 위치정보 이용 동의 확인(C9), 좌표 파기(C6), 운영 프로필(C7).
- `DirectionMatchingWorker`, sweep, 알림 worker 차단. 게이트가 꺼지면 새 좌표가 들어오지 않는다.
- 이미 저장된 dev 좌표 데이터 정리.
- 실행 중 게이트 전환(런타임 토글). 게이트 값은 기동 시점 설정이고 변경은 재배포로 한다.
- commit/push/PR 생성, 배포.

## 3. Source requirements

축약 ID에는 모두 `TEST-PLAN-GH-379-LOCATION-GATE-` 접두사를 붙인다.

| Source | Requirement / acceptance criterion | Evidence |
| --- | --- | --- |
| Issue #379 범위 | 기본값 `false`, `enabled: true` + 참조 누락·placeholder면 기동 실패 | UNIT-001~003, UNIT-008 |
| Issue #379 범위 | dev·local 프로필에서만 참조 검사 생략, dev·local YAML은 `enabled: true` | UNIT-003, UNIT-008, INT-004~005 |
| Issue #379 완료 조건 1 | 게이트가 꺼지면 세 API가 `LOCATION_COLLECTION_DISABLED`, 관련 테이블 행 수 불변 | UNIT-004~006, INT-001 |
| Issue #379 완료 조건 2 | dev·local이 아닌 프로필에서 참조 누락 시 context 기동 실패 | UNIT-003, INT-002 |
| Issue #379 완료 조건 3 | dev 프로필에서 게이트가 켜져 있고 위치 갱신 성공 | INT-004 |
| Issue #379 완료 조건 4 | 기존 위치·발송·미리보기 통합 테스트가 게이트를 켠 설정으로 통과 | INT-003, §9 회귀 |
| Issue #379 범위 | ApiSpec 오류 응답, 오류 코드 문서 | UNIT-007, INT-003(OpenAPI) |
| AGENTS §10 / TASK Validation | harness check, pr-ready/project-tests, hooks, diff | §10 |

### 현재 소스에서 관찰한 계약

- 정확 좌표의 유일한 입력은 `ActiveUserPresenceController` → `DirectionPresenceService.update`다.
  다른 web request 클래스에는 `latitude`가 없다.
- `DirectionPostApplicationService.preview`와 `submit`은 `currentPresence`로 발신자 좌표를 읽고,
  `submit`은 그보다 먼저 `postService.replayIfExists`를 호출한다. 거부를 replay보다 앞에 두지 않으면
  게이트가 꺼진 뒤에도 같은 멱등키 재요청이 기존 결과를 돌려준다.
- `@RequestBody @Valid`는 컨트롤러 진입 전에 동작한다. 서비스 첫 줄에서 거부하면 형식이 틀린
  요청은 400이 먼저 나간다. 인증 실패(401)도 게이트보다 먼저다.
- `FilteringProductionGate`는 `@PostConstruct`에서 검사하고, 참조 판정은
  `FilteringProductionGateProperties`의 private 규칙(placeholder 15개 거부, 8자 이상)이다.
  이번 작업은 filtering 패키지를 고치지 않고 같은 규칙을 새 클래스에 둔다.
- 403은 이미 `PRESENCE_ACCOUNT_NOT_ELIGIBLE`(`DIR-APP-007`)이 쓴다. "설정되지 않아 쓸 수 없음"을
  503으로 내는 선례는 `ANS-APP-004 MODERATION_INTAKE_NOT_CONFIGURED`다.
- `DirectionPostApplicationService`와 `DirectionPresenceService`를 직접 생성하는 테스트가
  단위 2개, 통합 6개다(`QuestionRecommendationIntegrationTest`,
  `ActiveUserPresenceConfigurationIntegrationTest`, `DirectionMatchingPerformanceIntegrationTest`,
  `DirectionMatchingVerticalFlowIntegrationTest`, `DirectionMatchingE3PerformanceIntegrationTest`,
  `DirectionMatchingVerticalFlowRecoveryIntegrationTest`). 생성자에 게이트가 추가되면 함께 고친다.
- 통합 테스트는 모두 `@ActiveProfiles`를 지정하고 대부분 `test` 프로필이다. `test`는 예외 프로필이
  아니므로 `src/integrationTest/resources/application-test.yml`이 `enabled: true`와 테스트 전용 참조
  값을 가져야 기존 테스트가 기동한다.
- `docs/api/openapi.json`은 `OpenApiSpecificationIntegrationTest`가 생성·비교한다.
- `docs/error-codes.md`는 수동 목록이며 자동 검사는 없다.

### 승인이 필요한 설계 결정

| ID | 결정 | 제안 | 대안 |
| --- | --- | --- | --- |
| D1 | 거부 위치 | 세 서비스 메서드의 첫 줄(계정 조회·replay·트랜잭션 작업 전) | HandlerInterceptor: 400보다 먼저 거부하지만 경로 목록을 따로 관리해야 한다 |
| D2 | 게이트가 꺼진 뒤 멱등 replay | 거부한다 | 기존 결과 반환: 좌표를 새로 읽지 않지만 "꺼지면 위치 기능 전부 거부" 규칙에 예외가 생긴다 |
| D3 | 응답 | 503, `DIR-APP-008`, `ErrorCategory.APP`, 이름 `LOCATION_COLLECTION_DISABLED` | 403: 계정 부적격 403과 구분할 수 없다 |
| D4 | 예외 프로필 판정 | 활성 프로필에 `dev` 또는 `local`이 하나라도 있으면 참조 검사 생략 | 별도 플래그: 운영 설정에 잘못 들어가도 막을 수 없다 |
| D5 | 클래스 위치 | `direction/gate/LocationCollectionGate`, `LocationCollectionGateProperties`. 참조 규칙은 filtering과 공유하지 않고 복제 | 공용 유틸 추출: filtering 패키지 수정이 범위에 들어온다 |
| D6 | test 프로필 | `application-test.yml`에 `enabled: true`와 테스트 전용 참조(`TEST-ONLY-LBS-REGISTRATION-FIXTURE`) | 예외 프로필에 `test` 추가: 운영과 같은 검사 경로를 테스트하지 못한다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 기본값이 `true`이거나 운영 프로필이 예외로 판정돼 신고 전 좌표 수집 | 법적 위험, 출시 차단 | 중 | P0 | UNIT-003, UNIT-008 |
| 세 API 중 하나라도 거부를 빠뜨림 | 좌표 수집 또는 좌표 사용 | 중 | P0 | UNIT-005~006, INT-001 |
| 거부 전에 replay·저장·outbox 기록이 실행됨 | 거부 응답인데 행이 남음 | 중 | P0 | UNIT-006, INT-001 행 수 비교 |
| placeholder(`ok`, `true` 등)나 짧은 값으로 게이트 통과 | 참조 추적 불가 | 중 | P0 | UNIT-001 |
| 기동 실패 메시지나 로그에 참조 값 노출 | 문서 번호 노출 | 낮음 | P1 | UNIT-003 메시지 검사 |
| test 프로필 fixture 누락으로 기존 통합 테스트 전체 기동 실패 | 회귀 차단 | 높음 | P0 | INT-003, 전체 `integrationTest` |
| dev 게이트가 꺼져 모바일 테스트 중단 | dev 기능 정지 | 중 | P0 | INT-004 |
| 거부 응답 본문에 좌표·요청 값 노출 | 정확 좌표 유출 | 낮음 | P1 | INT-001 본문 검사 |
| OpenAPI 산출물 미갱신 | `OpenApiSpecificationIntegrationTest` 실패 | 높음 | P1 | INT-003 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | 참조 값이 `null`, 빈 문자열, 공백, placeholder 15개(대소문자·앞뒤 공백 변형 포함), 7자 값 / 8자 이상 추적 가능한 값 | `LocationCollectionGateProperties`의 참조 판정 | 앞의 값은 미확인, 8자 이상 값만 확인으로 판정 | P0 | Unit executor |
| UNIT-002 | `enabled=false`, 참조 없음, 활성 프로필 없음 | 게이트 초기화 | 예외 없이 기동하고 `isEnabled()`는 false | P0 | Unit executor |
| UNIT-003 | `enabled=true`, 참조 미확인. 활성 프로필은 매개변수(없음, `default`, `test`, `prod`, `dev`, `local`, `dev`+`prod`) | `ApplicationContextRunner`로 게이트 구성 기동 | `dev`·`local`이 들어간 경우만 기동. 나머지는 `IllegalStateException`이고 메시지에 속성 이름은 있으나 참조 값은 없음 | P0 | Unit executor |
| UNIT-004 | 게이트 꺼짐 / 켜짐 + 확인된 참조 | `ensureEnabled()` 호출 | 꺼짐은 `DirectionException(LOCATION_COLLECTION_DISABLED)`, 켜짐은 통과 | P0 | Unit executor |
| UNIT-005 | 게이트 꺼짐, mock 저장소 | `DirectionPresenceService.update` | `LOCATION_COLLECTION_DISABLED`. `accountRepository`·`presenceRepository` 호출 없음 | P0 | Unit executor |
| UNIT-006 | 게이트 꺼짐, mock 저장소·`DirectionPostService` | `preview`, `submit`(replay 대상이 있는 멱등키 포함) | 둘 다 `LOCATION_COLLECTION_DISABLED`. `replayIfExists`, `send`, `previewAll`, 저장소 호출 없음 | P0 | Unit executor |
| UNIT-007 | `DirectionErrorCode.LOCATION_COLLECTION_DISABLED` | 값 조회 | HTTP 503, 코드 `DIR-APP-008`, `ErrorCategory.APP`, 다른 상수와 코드 중복 없음 | P1 | Unit executor |
| UNIT-008 | 저장소의 `application.yml`, `application-dev.yml`, `application-local.yml` | YAML을 읽어 `qello.location.collection` 확인 | 기본은 `enabled: false`, dev·local은 `enabled: true`. 세 파일 모두 실제 참조 값을 쓰지 않음 | P0 | Unit executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | MockMvc, Security, 세 서비스, PostgreSQL | `test` 프로필 + `qello.location.collection.enabled=false` 별도 context. ACTIVE 사용자, 현재 presence 한 행, 활성 질문·구획을 SQL로 준비 | 인증된 요청으로 presence PUT, preview GET, posts POST(같은 멱등키 2회 포함). 미인증 요청 1회 | 인증 요청은 503·`DIR-APP-008`. 본문에 위도·경도·정확도 값 없음. `active_user_presence`(행 내용 포함), `direction_post`, `post_audience`, `outbox_event` 행 수 불변. 미인증은 401 | 테스트가 만든 행 삭제 |
| INT-002 | `SpringApplicationBuilder`, 실제 YAML | 프로필 `test`, `enabled=true`, 참조를 빈 값 / `ok`로 덮어씀 | context 기동 | 기동 실패, 원인 체인에 `IllegalStateException`과 속성 이름 | context 닫기 |
| INT-003 | 기존 통합 테스트 전체, OpenAPI 생성 | `application-test.yml`에 D6 fixture 추가, 직접 생성 테스트 6개에 켜진 게이트 주입 | `./gradlew integrationTest` | 기존 위치·미리보기·발송·매칭 테스트 통과. `docs/api/openapi.json`에 세 API의 503 응답이 들어가고 두 번 생성해도 같음 | 기존 정리 방식 유지 |
| INT-004 | `DevProfileBootIntegrationTest` | `dev` 프로필, 참조 미설정 | 게이트 상태 확인, ACTIVE 사용자로 presence PUT | 기동 성공, `isEnabled()` true, 200 응답과 presence 행 저장 | 기존 정리 방식 |
| INT-005 | `QelloLocalProfileIntegrationTest` | `local` 프로필, 참조 미설정 | context 기동 | 기동 성공, `isEnabled()` true | 없음 |

## 7. Cross-cutting scenarios

### Database and transactions

- 거부는 `DirectionPresenceService.update`의 `@Transactional` 안 첫 줄에서 일어나며 쓰기 전이다.
  INT-001은 행 수뿐 아니라 기존 presence 행의 좌표·`location_at`이 그대로인지 확인한다.
- `submit`은 replay 조회 전에 거부해야 한다(UNIT-006, INT-001 같은 멱등키 2회).
- migration은 없다.

### Concurrency and idempotency

- 게이트 값은 기동 시점에 고정되고 런타임 전환이 없으므로 요청 간 경쟁 조건은 없다.
- 멱등키 재요청은 게이트가 꺼져 있으면 첫 요청과 같은 503을 받는다(D2).

### External APIs

- 외부 호출이 없다. 거부된 발송은 outbox를 만들지 않으므로 FCM·moderation 호출도 생기지 않는다
  (INT-001의 `outbox_event` 행 수로 확인).

### Failure recovery and reconciliation

- 롤백: 운영에서 수집을 멈추려면 `enabled: false`로 재배포한다. 켜려면 신고 수리 참조를 넣어
  재배포한다. `docs/location-collection-gate.md`에 이 절차를 쓴다(문서 검토로 확인).
- 이미 저장된 좌표는 게이트가 지우지 않는다. 파기는 C6 범위다.

## 8. Test data and isolation

- Fixtures: 테스트 전용 참조 `TEST-ONLY-LBS-REGISTRATION-FIXTURE`. 실제 신고 번호를 쓰지 않는다.
  좌표는 기존 통합 테스트가 쓰는 합성 좌표를 재사용한다.
- Database isolation: INT-001은 게이트 꺼짐 properties로 별도 context를 쓴다. 다른 클래스와
  context cache를 공유하지 않는다. 사용자 id는 테스트별 고유값을 쓴다.
- Clock/randomness: presence `observed_at`은 고정 `Clock` 또는 기존 fixture의 시각 계산을 따른다.
- External API doubles: 없음. test 프로필의 기존 fake push provider를 유지한다.
- Cleanup: 테스트가 삽입한 사용자·presence·질문 행을 `@AfterEach`에서 지운다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Implementation executor | `src/main/java/com/dnd/qello/direction/gate/**`(신규), `direction/error/DirectionErrorCode.java`, `direction/service/DirectionPresenceService.java`, `direction/service/DirectionPostApplicationService.java`, `direction/web/ActiveUserPresenceApiSpec.java`, `direction/web/DirectionPostApiSpec.java`, `src/main/resources/application.yml`, `application-dev.yml`, `application-local.yml`, `docs/location-collection-gate.md`(신규), `docs/error-codes.md` | 구현 대상 | `./gradlew compileJava` |
| 2 | Unit executor | `src/test/java/com/dnd/qello/direction/gate/**`(신규), `src/test/java/com/dnd/qello/direction/service/DirectionPresenceServiceTest.java`, `DirectionPostApplicationServiceTest.java`, `src/test/java/com/dnd/qello/direction/error/**`(신규 또는 기존) | UNIT-001~008 | `./gradlew test` |
| 3 | Integration executor | `src/integrationTest/resources/application-test.yml`, 신규 `LocationCollectionGateDisabledIntegrationTest.java`, `LocationCollectionGateBootIntegrationTest.java`, `DevProfileBootIntegrationTest.java`, `QelloLocalProfileIntegrationTest.java`, §3에 적은 직접 생성 통합 테스트 6개, `docs/api/openapi.json`(생성 산출물) | INT-001~005 | `./gradlew integrationTest` |
| 4 | Report | `docs/reports/tests/gh-379-TEST-PLAN-GH-379-LOCATION-GATE.md` | 전체 | `templates/test-report.md` 형식 |

세 실행자는 파일을 공유하지 않는다. 2와 3은 1의 생성자 시그니처가 정해진 뒤 시작한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과(`./gradlew test`)
- [ ] 통합 테스트 통과(`./gradlew integrationTest`)
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

실패 판단: P0 시나리오 하나라도 실패하거나, 기존 통합 테스트가 게이트 때문에 기동하지 못하면 FAIL이다.
Docker를 쓸 수 없어 통합 테스트를 실행하지 못하면 BLOCKED로 보고하고 통과로 쓰지 않는다.

## 11. Human approval

- Reviewer: @Byuntil
- Decision: `APPROVED` — 계획과 설계 결정 D1~D6을 제안대로 승인
- Approved at: 2026-11-23
