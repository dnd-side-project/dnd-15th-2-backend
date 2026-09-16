# Test Plan: TEST-PLAN-GH-230-CORS-ACTUATOR-DEV-PROFILE

> Created at: `2026-09-17T00:45:27+09:00`
> GitHub Issue: `#230`
> Status: Approved (`tkv00`, 2026-09-17T00:49:35+09:00)

## 1. Objective

프론트엔드가 EC2 테스트 서버(#229)의 API를 브라우저에서 호출하려면 CORS가
필요하고, 운영자가 서버 상태를 확인하려면 `/actuator/health`가 필요하며,
`SPRING_PROFILES_ACTIVE=dev`만으로 애플리케이션이 실제로 기동해야 한다.
지금은 셋 다 없다. 실패 시 위험: 프론트가 테스트 서버를 전혀 쓸 수 없거나,
서버가 기동조차 하지 못한다.

## 2. Scope

### Included

- `SecurityConfiguration`에 CORS 설정과 `/actuator/health` 허용 체인 추가.
- `application-dev.yml` 신설: datasource, media bucket, auth secret을
  환경변수로 받고 actuator 노출 범위를 `health`로 제한.
- `compose.yaml`의 `app` 서비스에 `QELLO_AUTH_ACCESS_TOKEN_SECRET`,
  `QELLO_MEDIA_BUCKET` 환경변수와 healthcheck 추가(완료 조건 "app과 db
  모두 healthy" 충족에 필요 — 현재 `app`에는 healthcheck 자체가 없다).
- **범위 확장 필요(§3 확인 필요 참고)**: `PushConfiguration`,
  `PushTokenProperties`의 `@Profile` 조건에 `dev`를 추가해 FCM/push 관련
  필수값 없이도 `dev` 프로파일이 기동하게 한다.
- `application-dev.yml`에 `qello.notification.push.policy.*` fixture 값
  추가(이 설정은 프로파일과 무관하게 항상 바인딩되므로 `dev` 프로파일
  제외 대상이 아니다).
- 기존 `ActuatorExposureIntegrationTest`(`test` 프로파일)를 새 동작에 맞게
  갱신.

### Excluded

- EC2, Terraform, AWS 리소스 변경(#229/#233 영역).
- 인증 방식 변경. 디바이스 기반 JWT 발급 흐름 유지.
- production 프로파일과 운영 환경 설정.
- 운영자 계정 시드 데이터.
- `.env.example` 실제 값 수정 — 이 세션의 파일 접근 권한이 `.env*` 경로를
  차단한다. 두 키(`QELLO_AUTH_ACCESS_TOKEN_SECRET`, `QELLO_MEDIA_BUCKET`)
  추가는 사용자가 직접 하거나 별도 승인이 필요하다.
- FCM 실제 자격 증명 발급이나 SSM 파라미터 추가(#4의 확인 필요 결정에 따라
  달라짐).

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #230 | CORS·actuator health·dev 프로파일 완료 조건 8건(§ 완료 조건 참고) |
| `docs/adr/0006-split-operator-and-device-authentication.md` | 체인 분리 원칙 유지 — CORS는 `/api/**` 체인에만 적용, `/admin/**` 세션·CSRF 체인은 건드리지 않음 |
| 코드 조사 | `management.endpoints.enabled-by-default: false`, `web.exposure.include: ""`(application.yml) — 기본값은 전부 닫힘. `dev`가 명시적으로 열어야 함 |
| 코드 조사 | `PushPolicyProperties`는 프로파일 무관하게 항상 바인딩(`@EnableConfigurationProperties`가 `PushConfiguration` 최상위에 있음), 필수값에 default 없음 |
| 코드 조사 | `PushProperties`/`PushTokenProperties`는 `@Profile("!test & !local & !integration")`로만 바인딩 — `dev`는 제외 목록에 없어 그대로면 필수값 요구가 걸림 |
| 코드 조사 | `compose.yaml`의 `app` 서비스에 healthcheck가 없어 "app과 db 모두 healthy" 완료 조건을 지금 구조로는 만족할 수 없음 |

## 확인 필요 (구현 전 사람 결정) — 확정됨

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| FCM/push 필수값 처리 | **확정 — (a) 채택**(`tkv00`, 2026-09-17). `PushConfiguration`의 `@Profile({"test","local","integration"})`(NoOp)과 `ProductionPushConfiguration`/`PushTokenProperties`의 `@Profile("!test & !local & !integration")`에 `dev`를 추가한다 | `local`/`test`/`integration`과 동일한 패턴 재사용. dev 테스트 서버가 실제 FCM에 우발적으로 연결되는 경로를 원천 차단한다. 이슈 #230 "범위" 절에 없던 파일(`PushConfiguration.java`, `PushTokenProperties.java`) 변경이라 범위 확장으로 처리한다 |
| `.env.example` 두 키 추가 | **확정 — 이번 작업에 한해 권한 허용**(`tkv00`, 2026-09-17) | `QELLO_AUTH_ACCESS_TOKEN_SECRET`, `QELLO_MEDIA_BUCKET` 두 키를 추가한다 |
| `app` 서비스 healthcheck | **확정 — 추가**(`tkv00`, 2026-09-17) | `/actuator/health`를 확인하는 healthcheck를 `app`에 추가해 "app과 db 모두 healthy" 완료 조건을 만족시킨다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| CORS 설정이 `/admin/**`·`/api/v1/operator/**` 체인에 새어 들어가 세션 기반 인증에 영향 | 백오피스 보안 경계 붕괴 | 낮음(체인이 이미 경로로 분리됨) | P0 | 통합 테스트로 각 체인이 CORS 설정과 무관하게 동작함을 확인 |
| 허용하지 않은 origin이 우회 가능(와일드카드 origin + credentials 조합 등) | CORS 우회로 임의 origin에서 API 호출 가능 | 중간 | P0 | 허용/비허용 origin 양쪽 시나리오 |
| `dev` 프로파일 기동 시 FCM 관련 바인딩 실패로 컨테이너가 크래시 루프 | 테스트 서버 전체 장애(#229의 알려진 위험) | 높음(현재 코드로는 확정적으로 발생) | P0 | `dev` 프로파일 컨텍스트 로드 테스트 |
| actuator 노출 범위가 의도보다 넓어짐(`health` 외 엔드포인트 노출) | 정보 노출 | 낮음 | P1 | dev 프로파일에서 `/actuator/env` 등이 여전히 닫혀 있는지 확인 |
| 기존 `test` 프로파일의 `ActuatorExposureIntegrationTest`가 새 체인 추가로 회귀 | 기존 테스트 실패 | 확정적(새 체인이 `/actuator/health` 매칭 방식에 따라 401→404로 바뀔 수 있음) | P0 | 기존 테스트를 새 동작에 맞게 갱신, 다른 3개 경로는 그대로 401 유지 확인 |
| `docker compose up`이 실제로 healthy에 도달하는지는 CI에서 직접 실행하지 않는 한 통합 테스트로 완전히 재현되지 않음 | 완료 조건 미검증 가능성 | 중간 | P1 | 로컬에서 `docker compose up` 수동 실행으로 별도 확인, 통합 테스트는 Spring 컨텍스트 레벨까지만 검증 |

## 5. Unit scenarios

이 이슈는 Configuration 클래스와 프로파일 설정이 중심이라 순수 단위 테스트로
의미 있게 격리할 로직이 거의 없다. 전부 Spring 컨텍스트가 필요해 통합
테스트로 다룬다. 단위 테스트는 없음.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-230-CORS-ACTUATOR-DEV-PROFILE-INT-001 | SecurityConfiguration, CORS | `qello.web.allowed-origins`에 허용 origin 설정, `test` 프로파일 | 허용 origin으로 `OPTIONS /api/v1/auth/devices` preflight 요청 | 200, `Access-Control-Allow-Origin` 헤더에 요청 origin 반영 | 없음 |
| ...-INT-002 | 위와 동일 | 위와 동일 | 허용하지 않은 origin으로 같은 preflight 요청 | `Access-Control-Allow-Origin` 헤더 없음(CORS 거부, Spring이 403 또는 헤더 누락으로 처리 — 실제 동작 확인 후 기록) | 없음 |
| ...-INT-003 | SecurityConfiguration, CORS | 허용 origin 설정 | 허용 origin에서 실제 GET(디바이스 등록 없이 인증 필요한 `/api/**` 경로)에 Origin 헤더 포함 요청 | 응답에 `Access-Control-Allow-Origin` 포함(CORS 자체는 인증 여부와 무관하게 헤더가 붙는지 확인) | 없음 |
| ...-INT-004 | SecurityConfiguration, `/admin/**` 체인 | `test` 프로파일 | Origin 헤더를 포함해 `/admin/login`에 POST | 기존 세션·CSRF 동작이 CORS 설정 유무와 무관하게 그대로임을 확인(체인 분리 검증) | 없음 |
| ...-INT-005 | Actuator, SecurityConfiguration | `dev` 프로파일 컨텍스트 | `GET /actuator/health` | 200, body `{"status":"UP"}` | 없음 |
| ...-INT-006 | Actuator | `dev` 프로파일 컨텍스트 | `GET /actuator/metrics`, `GET /actuator/env`, `GET /actuator` | health 외에는 노출되지 않음(404 또는 401 — 실제 값 확인 후 기록) | 없음 |
| ...-INT-007(기존 갱신) | Actuator, SecurityConfiguration | `test` 프로파일 | `/actuator`, `/actuator/health`, `/actuator/metrics`, `/actuator/env` | `/actuator/health`를 제외한 3개는 기존과 동일하게 401. `/actuator/health`는 새 동작 확인 후 정확한 코드로 갱신 | 없음 |
| ...-INT-008 | Spring context, `dev` 프로파일 | `SPRING_PROFILES_ACTIVE=dev` + 이슈가 명시한 필수 환경변수(DB, media bucket, auth secret)만 설정, FCM/push 관련 값은 설정하지 않음 | Spring 컨텍스트 로드(`@SpringBootTest(webEnvironment = RANDOM_PORT)` 또는 컨텍스트 전용 슬라이스) | 컨텍스트가 예외 없이 로드된다 — `PushProperties`/`PushTokenProperties` 바인딩 실패로 죽지 않는다 | 없음 |
| ...-INT-009 | `application-dev.yml` | `dev` 프로파일 | `springdoc.api-docs.enabled` 값 확인 | 이슈가 명시한 값(운영과 동일하게 `false`인지, 디버깅 편의로 `true`인지는 §3 확인 필요 목록에 추가하거나 이슈 재확인) | 없음 |

CORS 거부 시 정확한 HTTP 상태 코드는 Spring Security의 실제 `CorsFilter`
동작에 따라 다르므로, INT-002 구현 시 실제 응답을 관찰해 시나리오의
`Expected result`를 확정한다(추측으로 단정하지 않는다).

## 7. Cross-cutting scenarios

### Database and transactions

- 해당 없음. 이 이슈는 스키마·트랜잭션을 변경하지 않는다.

### Concurrency and idempotency

- 해당 없음.

### External APIs

- FCM: `dev` 프로파일이 실제 FCM에 연결을 시도하지 않아야 한다(INT-008이
  이를 컨텍스트 로드 실패 여부로 간접 검증). §3 결정에 따라 NoOp provider로
  대체되면 이 경계는 자연히 닫힌다.

### Failure recovery and reconciliation

- 해당 없음. 이 이슈는 재시도·복구 로직을 다루지 않는다.

## 8. Test data and isolation

- Fixtures: 없음(DB 데이터 없이 컨텍스트/보안 체인 레벨 테스트).
- Database isolation: 기존 `PostgisContainerIntegrationTestSupport` 패턴 재사용.
- Clock/randomness: 해당 없음.
- External API doubles: `dev` 프로파일 컨텍스트 테스트는 §3 결정에 따라
  `NoOpPushProvider`를 그대로 쓰거나, real provider 활성화 시 FCM 호출을
  실제로 발생시키지 않는지 별도 확인이 필요하다(빈 생성만으로 외부 호출은
  없음 — `RestClient`/`FcmAccessTokenProvider`는 빈 생성 시점에 네트워크
  호출을 하지 않는다).
- Cleanup: 컨텍스트 캐시로 인한 프로파일 간 오염을 피하기 위해 `dev`
  프로파일 테스트는 별도 컨텍스트로 격리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 구현 에이전트 | `src/main/java/com/dnd/qello/auth/config/SecurityConfiguration.java`, `src/main/resources/application-dev.yml`, `src/main/java/com/dnd/qello/notification/config/PushConfiguration.java`, `PushTokenProperties.java`(§3 결정에 따름) | 해당 없음(구현) | `./gradlew compileJava` |
| 2 | 구현 에이전트 | `compose.yaml` | 해당 없음(구현) | `docker compose config` 문법 확인 |
| 3 | 테스트 실행 에이전트 | `src/integrationTest/java/com/dnd/qello/CorsConfigurationIntegrationTest.java`(신규), `ActuatorExposureIntegrationTest.java`(갱신), `DevProfileBootIntegrationTest.java`(신규) | INT-001~009 | `./gradlew integrationTest` |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과(해당 없음 — §5 참고)
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성
- [ ] §3 "확인 필요" 3건에 대한 사람 결정 확보

## 11. Human approval

- Reviewer: `tkv00`
- Decision: Approved. §3의 확인 필요 3건 전부 확정(FCM/push는 (a) NoOp 확장,
  `.env.example`은 이번 작업 한정 권한 허용, `app` healthcheck는 추가)
- Approved at: `2026-09-17T00:49:35+09:00`
