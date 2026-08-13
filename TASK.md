# GitHub Issue #121 Task Contract

> Generated at: `2026-08-13T22:22:17+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `ActiveUserPresence 갱신 API`
- GitHub Issue: `#121`
- Branch: `feat/gh-121-active-user-presence-api`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API`
- Test plan approval: `APPROVED` — 사용자가 추천 계약과 모든 설정값을
  `2026-08-14T00:47:55+09:00`에 승인했다.

## Objective

- 인증된 앱 사용자가 자신의 최신 위치와 수신 허용 상태를
  `active_user_presence` 읽기 모델에 갱신할 수 있는 API 경계를 제공한다.
- 사용자 식별자는 요청 값이 아니라 검증된 Bearer JWT의 `sub`에서만 가져온다.
- 위치 시각·정확도·좌표를 검증하고, 순서가 뒤바뀐 요청이 더 최신 presence를
  덮어쓰지 못하게 한다.
- 정확 좌표가 성공 응답, 오류 payload, 로그와 OpenAPI 예시에 노출되지 않게 한다.
- 저장된 presence가 만료되면 기존 preview·매칭 후보 SQL에서 제외됨을 실제
  PostgreSQL/PostGIS 통합 테스트로 검증한다.

## Recommended contract

1. `PUT /api/v1/direction/presence`를 앱 Bearer 인증 경로로 추가한다.
2. 요청은 `latitude`, `longitude`, `accuracyMeters`, `receiveAllowed`, `observedAt`만
   받으며 `userId`, `expiresAt`, `coarseRegionCode`는 받지 않는다.
3. `userId`는 JWT `sub`, `coarseRegionCode`는 서버의 Account 읽기 결과,
   `expiresAt`은 승인된 TTL 설정으로 서버가 계산한다.
4. ACTIVE USER만 갱신할 수 있다. 토큰은 유효하지만 계정이 BLOCKED/DELETED이거나
   존재하지 않으면 쓰기 전에 거절한다.
5. UPSERT는 `location_at`이 현재 저장값보다 늦을 때만 갱신한다. 같거나 오래된
   재시도는 멱등 no-op으로 처리해 최신 위치를 보존한다.
6. 성공 응답에는 정확 좌표·사용자 ID·지역을 넣지 않고 조건부 UPSERT 적용 여부인
   `applied`만 둔다. 요청 DTO의 문자열 표현도 좌표를 redaction한다.
7. 운영 조정값은 `DirectionPresenceProperties` 하나에 모으고
   `qello.direction.presence.*` 설정으로 외부화한다. 코드·SQL·테스트 fixture에 정책
   숫자를 중복 하드코딩하지 않는다.
8. `auth/web`과 같은 API 분리 규칙을 적용한다.
   - `ActiveUserPresenceApiSpec`: 메서드 매핑, `@Tag`, `@Operation`,
     `@ApiResponses`, `@SecurityRequirement`, request validation 계약
   - `ActiveUserPresenceController`: `@RestController`, 클래스 수준
     `@RequestMapping`, `implements ActiveUserPresenceApiSpec`, `@Override`와 서비스 호출
   - request DTO: `direction.web.request.UpdateActiveUserPresenceRequest`
   - response DTO: `direction.web.response.UpdateActiveUserPresenceResponse`
   - ApiSpec과 Controller만 `direction.web`에 두고 request/response DTO를 하위
     패키지로 분리한다.

## Configuration contract

| Property | Type | State | Meaning |
| --- | --- | --- | --- |
| `qello.direction.presence.ttl` | `Duration` | `CONFIRMED: PT24H` | 관측 시각부터 신규 매칭에 사용할 수 있는 기간 |
| `qello.direction.presence.max-accuracy-meters` | decimal meters | `CONFIRMED: 100` | 이 값을 초과하는 부정확한 위치 갱신 거절 |
| `qello.direction.presence.max-future-skew` | `Duration` | `CONFIRMED: PT30S` | 서버 시각보다 앞선 관측 시각의 허용 오차 |
| `qello.direction.presence.max-observation-age` | `Duration` | `CONFIRMED: PT5M` | API 수신 시 허용할 과거 관측 시각의 최대 나이 |

- `ttl`은 양수, `maxAccuracyMeters`와 `maxObservationAge`는 양수,
  `maxFutureSkew`는 0 이상이어야 한다.
- `maxObservationAge < ttl`을 강제해 승인된 요청이 저장 즉시 만료되지 않게 한다.
- Spring Boot 시작 시 설정 관계가 잘못되면 fail-fast하고, 로그에 좌표나 요청값을
  포함하지 않는다.
- 앱 종료는 `receiveAllowed=false`로 바꾸지 않는다. 24시간 안에는 마지막 위치로
  신규 질문을 받을 수 있고, 앱 복귀 시 위치 갱신으로 다시 24시간을 계산한다.
- 24시간이 지나도 이미 생성된 `PostRecipient`는 유지되며 신규 매칭만 중단한다.

## Policy decisions

| Decision | Recommended direction | Required human input |
| --- | --- | --- |
| Presence TTL | `observedAt + PT24H`, client `expiresAt` 금지 | `CONFIRMED` |
| 허용 정확도 | `0 <= accuracyMeters <= 100m`, `accuracyMeters` 필수 | `CONFIRMED` |
| 관측 시각 허용 범위 | 최대 미래 `PT30S`, 최대 과거 `PT5M`, `observedAt` 필수 | `CONFIRMED` |
| 지역 출처 | 요청이 아닌 Account의 `coarseRegionCode` 사용 | `CONFIRMED` |
| stale/equal 요청 | `200`과 `applied=false`, DB no-op | `CONFIRMED` |
| 수신 허용 의미 | `receiveAllowed`는 수신 후보 여부만 제어하고 유효 위치가 있는 사용자의 발신은 허용 | `CONFIRMED` |

위 값은 MVP 운영 기본값이며 최종 제품 정책이 아니다. 모든 값을
`qello.direction.presence.*`에서 독립적으로 조정할 수 있게 하고 코드와 SQL에는
정책 숫자를 하드코딩하지 않는다.

## Scope

- JWT `sub`에서 인증 사용자 ID를 안전하게 추출하는 앱 API 경계
- ACTIVE USER 재검증과 서버 권위 `coarseRegionCode` 조회
- presence TTL·정확도·관측 시각 설정과 입력 검증
- 현재 위치 유효성과 수신 후보 자격 분리
- `ActiveUserPresence` 생성 및 service/repository 연결
- 사용자별 `active_user_presence` 조건부 UPSERT
- 같은 요청 재시도와 늦게 도착한 이전 위치의 멱등 no-op
- 좌표 없는 성공 응답, 안전한 오류 메시지와 request 문자열 redaction
- 앱 Bearer 인증이 명시된 OpenAPI 계약과 `docs/api/openapi.json` 재생성
- 도메인·service 단위 테스트와 MockMvc/PostgreSQL/PostGIS 통합 테스트
- 만료 경계 `expires_at <= at`에서 preview·matching 후보 제외 회귀 검증
- 테스트 보고서 작성

## Explicit exclusions

- 위치 보존 기간의 최종 제품 정책 확정
- 지도 마커·H3·coarse cell 집계와 reverse geocoding
- `region_code` 공간 경계와 좌표의 지리적 일치 검증
- 위치 이력 테이블, 분석 이벤트, Outbox 또는 외부 위치 API
- 기존 `active_user_presence` schema/migration 변경
- #122 preview·질문글 제출 Controller와 #120 매칭 worker 변경
- 운영 rate limit, 기기 백그라운드 위치 수집 주기와 모바일 권한 UX
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Implementation plan

1. **정책과 입력 계약**
   - TTL·정확도·관측 시각 한계를 하나의 `DirectionPresenceProperties`로 정의하고
     `application.properties`에는 MVP 기본값을 한 곳에만 둔다.
   - 설정 객체 생성·Spring binding·값 사이 관계를 단위 테스트한다.
   - 요청 DTO는 nullable wrapper와 Bean Validation을 사용해 `receiveAllowed` 누락을
     `false`로 오인하지 않게 하고, 도메인 검증은 좌표·시각 값을 메시지에 넣지 않는다.
2. **인증·application 경계**
   - Controller는 JWT `sub`만 사용자 ID로 사용하고 요청에 target user ID를 두지 않는다.
   - service는 Account 존재·role·ACTIVE 상태와 서버 권위 지역을 확인한 뒤 `Clock`과
     정책으로 `expiresAt`을 계산한다.
   - `ActiveUserPresence`의 현재 위치 판정과 수신 가능 판정을 분리해
     `receiveAllowed=false`여도 위치가 유효하면 preview·발신 원점으로 사용할 수 있게 한다.
3. **조건부 persistence**
   - 기존 `ActiveUserPresenceSql.UPSERT`를 새 행 insert 또는 더 최신 `location_at`만
     update하도록 좁히고 affected row/result로 `applied`를 반환한다.
   - FK/NUMERIC 범위를 repository 밖의 feature validation으로 먼저 거절한다.
4. **응답·로그·문서 프라이버시**
   - response schema와 `toString()`에 좌표·정확 위치 식별자를 넣지 않는다.
   - 메서드 매핑과 springdoc 애노테이션은 모두 `ActiveUserPresenceApiSpec`에 두고,
     Controller에는 문서 애노테이션이나 메서드 매핑을 중복하지 않는다.
   - ApiSpec에 `appAccessToken` Bearer 인증과 근거가 확인된 200/400/401/403 응답을
     문서화한다. 공통 400/500은 `OpenApiConventionCustomizer`와 중복하지 않는다.
   - `docs/api/openapi.json`은 직접 편집하지 않고
     `OpenApiSpecificationIntegrationTest`로 재생성한다.
5. **검증**
   - 승인된 테스트 계획의 P0 단위·통합 시나리오를 구현한다.
   - API → UPSERT → 기존 candidate SQL을 잇는 만료 회귀, 소유권, stale write,
     rollback, privacy 검증을 실행하고 테스트 보고서를 생성한다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 정책·도메인·service와 단위 테스트 | Direction application executor | 미확정 숫자 하드코딩, Account 경계, Clock 사용 리뷰 |
| 조건부 JDBC UPSERT와 persistence 통합 테스트 | Direction persistence executor | stale/equal 경합, affected-row 의미, PostGIS 회귀 리뷰 |
| Controller·`web/request`·`web/response` DTO·MockMvc | Direction web executor | JWT 소유권, 패키지 경계, 좌표 비노출, 오류 응답 리뷰 |
| ApiSpec·OpenAPI 산출물 | API docs executor | 인증·응답·privacy 문서 계약 리뷰 |
| 테스트 계획·보고서 | Test orchestrator | 시나리오 증거와 미검증 위험 리뷰 |
| 최종 변경 | Independent reviewer | Issue/TASK 범위와 구현 증거 독립 검증 |

각 실행자는 계획에서 지정한 파일만 소유하고 다른 실행자나 사용자의 변경을
되돌리지 않는다.

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 clean이었다.
- `./harness start`가 최신 `origin/main` commit
  `886c423a72e7a4e9eedd7b62cb624e2d785ad9e6`에서 브랜치를 만들었다.
- 현재 작업 트리에는 이 계약·테스트 계획/보고서와 #121 구현·테스트·OpenAPI 변경이 있다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.direction.*"
./gradlew integrationTest --tests "com.dnd.qello.ActiveUserPresenceApiIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.ActiveUserPresenceConfigurationIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.ActiveUserPresenceUpdateIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest"
./gradlew integrationTest --tests "com.dnd.qello.DirectionPreviewIntegrationTest"
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 인증 사용자 본인의 presence만 갱신되고 요청 값으로 다른 사용자 ID를 선택할 수 없다.
- 유효 토큰이어도 존재하지 않거나 ACTIVE USER가 아닌 계정은 DB 변경 전에 거절된다.
- 좌표·정확도·관측 시각·TTL 검증 실패는 feature 오류 계약으로 400을 반환한다.
- 새 위치 또는 더 최신 위치만 저장되며 stale/equal 요청과 동시 실행이 최신 행을
  덮어쓰지 않는다.
- 기본 TTL은 `PT24H`이고, TTL·정확도·관측 시각 제한은 코드 수정 없이 설정으로
  독립 조정할 수 있으며 잘못된 조합은 애플리케이션 시작 시 거절된다.
- `expires_at <= at`, `receive_allowed = false`, 정확 위치 없음은 기존 후보 SQL에서
  계속 제외된다.
- `receive_allowed = false`여도 현재 위치 자체가 유효하면 발신자의 preview·질문 제출
  원점으로 사용할 수 있다.
- 정확 좌표가 성공 응답, 오류 payload, 애플리케이션 로그, OpenAPI example에 없다.
- `ActiveUserPresenceController`는 `ActiveUserPresenceApiSpec`을 구현하고, 경로·메서드
  매핑과 OpenAPI 애노테이션은 ApiSpec에만 존재한다.
- 새 migration 없이 기존 schema와 UPSERT 경계를 사용한다.
- 승인된 P0 테스트와 저장소 필수 검증이 통과하고 테스트 보고서가 남는다.
- 실행하지 못한 검증과 최종 정책 미확정 위험을 보고서에 기록한다.
