# Test Plan: TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY

> Created at: `2026-11-24T21:30:16+09:00`
> GitHub Issue: `#376`
> Status: Approved — 사람 승인 완료

## 1. Objective

운영자 비밀번호가 작성 규칙을 지키는지, 운영자가 직접 비밀번호를 바꿀 수 있는지, 시드로 받은 초기 비밀번호나 변경 주기가 지난 비밀번호를 쓰는 운영자가 비밀번호를 바꾸기 전까지 운영자 API를 쓰지 못하는지 검증한다.
이 검증이 실패하면 위치기반서비스 운영 배포 전 점검표 첨부 2 1항의 비밀번호 작성 규칙과 주기 변경(C4)을 채우지 못한다. 규칙이 없으면 짧거나 login_id와 같은 비밀번호가 그대로 쓰인다. 변경 API가 없으면 시드 비밀번호가 영구히 남는다. 변경 강제가 새면 만료된 비밀번호로도 위치정보 관련 운영자 API를 계속 쓸 수 있다. 변경 경로가 실패 카운터와 세션 재발급을 빠뜨리면 비밀번호 대입과 session fixation의 새 통로가 된다.

## 2. Scope

### Included

- `auth/security`의 비밀번호 작성 규칙 검증기. 수치는 11절 D2를 따른다.
- 비밀번호 변경 API `POST /admin/password`. 현재 비밀번호와 새 비밀번호를 받는다. 성공하면 `password_updated_at`을 갱신하고, 세션 ID를 재발급하고, 세션 권한을 정상 운영자 권한으로 바꾼다.
- 변경 요청의 현재 비밀번호 불일치를 로그인과 같은 `OperatorCredential` 실패 카운터에 더한다. 5회면 잠긴다.
- 초기 비밀번호 판별. 11절 D1 A면 V35로 `operator_credential.password_change_required`를 추가한다.
- 변경 주기 판정. 로그인 시점에 `password_updated_at`과 설정된 주기로 판정한다(D3).
- 변경이 필요한 운영자의 세션 제한. 로그인은 되지만 비밀번호 변경·로그아웃 외의 `/admin/**`, `/api/v1/operator/**`는 403과 새 오류 코드로 거부한다.
- 로그인 응답 `OperatorSessionResponse`에 `passwordChangeRequired`를 더한다.
- `OperatorSeedService.seedIfAbsent`가 새 운영자를 만들 때 같은 규칙을 적용한다.
- 새 오류 코드(`AuthErrorCode`, `docs/error-codes.md`)와 `docs/api/openapi.json` 재생성.
- 변경 요청과 제한 세션의 호출이 #375 접근 기록에 남는지 검사한다(D5).
- V35를 쓰면 `FlywayMigrationIntegrationTest`·`FlywayMigrationContractTest`의 최신 버전과 고정 개수, ERD·DBML·schema manifest를 갱신한다.
- 기존 운영자 로그인·세션·요청 제한·접근 기록·운영자 API 통합 테스트의 회귀.

### Excluded

- 직전 N개 비밀번호 재사용 금지. 비밀번호 이력 테이블이 필요하다.
- 다른 운영자의 비밀번호 초기화.
- OTP 2차 인증(#377).
- 비밀번호 변경 성공 시 같은 운영자의 다른 세션 무효화. 4절 위험에 기록한다.
- 만료 전 사전 알림, 비밀번호 분실 복구.
- 로그인 요청의 `password` 입력에 작성 규칙을 적용하는 것. 로그인은 저장된 해시와만 비교한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #376 완료 조건 1 | 길이 미달, 문자 종류 부족, login_id와 같은 값, 현재 비밀번호와 같은 값이 각각 400과 새 오류 코드로 거부된다 → UNIT-001~005·012, INT-004 |
| GitHub Issue #376 완료 조건 2 | 변경 성공 후 `password_updated_at`이 갱신되고 세션 ID가 바뀌며, 이전 비밀번호로 로그인하면 `AUT-APP-001` → UNIT-014, INT-003·009 |
| GitHub Issue #376 완료 조건 3 | 현재 비밀번호가 틀린 변경 요청이 5회 쌓이면 자격증명이 잠긴다 → UNIT-010·013, INT-005 |
| GitHub Issue #376 완료 조건 4 | 만료됐거나 초기 비밀번호인 운영자 세션으로 운영자 API를 호출하면 거부되고, 비밀번호 변경 API는 호출된다(`Clock` 고정) → UNIT-007·009·016, INT-002·008 |
| GitHub Issue #376 완료 조건 5 | `openapi.json` 재생성 diff에 변경 API, 로그인 응답 필드, 오류 코드가 반영된다 → INT-011 |
| GitHub Issue #376 범위 | 시드 초기 비밀번호에도 같은 규칙 → UNIT-015 |
| GitHub Issue #376 선행 관계 | 비밀번호 변경 성공·실패도 #375 접근 기록에 남긴다 → UNIT-017, INT-007 |
| ADR-0006 | 운영자 자격증명은 계정과 분리된 애그리거트이고 자체 가입 API를 두지 않는다. 변경 API는 로그인한 본인만 호출한다 |
| `docs/error-codes.md` AUT 절 | `AUT-APP-001`은 계정 없음과 비밀번호 불일치를 구분하지 않는다. 새 코드가 이 규칙을 깨지 않아야 한다(변경 API는 로그인한 세션에서만 호출되므로 계정 존재가 이미 알려져 있다) |
| V5 migration | `operator_credential`에 `@Version`이 없고 실패 카운터 경합의 증가 유실을 허용한다. 7절에 따른다 |
| `application.yml` | 운영자 세션 절대 만료 `PT12H`. D3 A의 세션 도중 만료 반영 지연의 상한이다 |
| Spring Security 6.5 `BCryptPasswordEncoder` | 72바이트를 넘는 입력은 해시하지 않고 예외를 던진다. 새 비밀번호 최대 길이를 이 한도에 맞춘다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 초기 비밀번호 강제로, 시드 직후 운영자 API를 호출하는 기존 통합 테스트가 403으로 실패한다 | 대량 회귀, 기대값을 고치려는 유혹 | High | P0 | 8절 helper로 시드 후 변경 완료 상태로 바꾼다. 기대값은 바꾸지 않는다. INT-010 |
| V35 적용 후 test-server의 기존 운영자가 다음 로그인부터 변경을 강제받는다 | 운영자가 예고 없이 막힌다 | High | P1 | 의도한 동작이다. PR 본문 참고에 적는다. INT-001이 기존 행을 TRUE로 채우는지 확인한다 |
| 접근 거부 핸들러가 CSRF 403까지 변경 필요 403으로 바꾼다 | 프론트가 원인을 잘못 안내한다 | Medium | P0 | UNIT-016, INT-006 |
| 변경 성공 뒤 세션 ID를 재발급하지 않거나 권한을 바꾸지 않는다 | session fixation, 변경 후에도 계속 제한 | Medium | P0 | INT-003 |
| 제한 세션이 로그아웃·변경 API까지 막힌다 | 운영자가 빠져나올 수 없다 | Medium | P0 | INT-002 |
| 현재 비밀번호 불일치의 실패 카운터가 예외와 함께 롤백된다 | 변경 API가 잠금 없는 대입 통로가 된다 | Medium | P0 | `noRollbackFor`, INT-005 |
| 새 비밀번호가 72바이트를 넘으면 BCrypt가 예외를 던져 500이 된다 | 규칙 위반 대신 서버 오류 | Medium | P1 | D2 최대 72자, ASCII만 허용. UNIT-003, INT-004 |
| 로그인·현재 비밀번호 입력이 72바이트를 넘을 때 `matches`가 예외를 던질 수 있다 | 기존 로그인 경로의 500 | Low | P2 | 실행 중 동작을 확인해 보고서에 적는다. 이번 범위는 새 비밀번호만 막는다 |
| 시드 검증이 이미 있는 운영자에게도 적용되어 기존 배포가 기동에 실패한다 | test-server 배포 실패 | Medium | P0 | 새로 만들 때만 검증한다. UNIT-015 |
| 오류 응답·로그·접근 기록에 비밀번호가 실린다 | 비밀값 유출 | Low | P0 | UNIT-005·018, INT-007 |
| 만료 경계(정확히 90일)에서 판정이 어긋난다 | 하루 일찍 또는 늦게 막힘 | Medium | P1 | UNIT-007, INT-008 |
| 세션 도중 만료는 다음 로그인까지 반영되지 않는다(D3 A) | 최대 12시간 만료 비밀번호 세션 유지 | High | P2 | 설계상 지연이다. 절대 만료 12시간이 상한이다. 보고서에 적는다 |
| 같은 운영자의 다른 세션은 비밀번호 변경 뒤에도 유지된다 | 탈취된 세션이 변경 뒤에도 남는다 | Medium | P2 | 범위 밖. 절대 만료가 상한이다. 후속 이슈 후보로 보고서에 적는다 |
| Byuntil의 Draft PR #384가 V34를 쓰고 있어 번호를 바꿔야 하고, 이 작업도 V35를 쓴다 | migration 버전 충돌 | High | P1 | 먼저 병합되는 쪽이 V35를 쓰고 나중 쪽이 번호와 고정 개수를 다시 맞춘다. PR 전에 main을 다시 확인한다 |
| `OperatorSeedService`를 고치면 baseline의 LEGACY 항목 `JAVA-CONV-0039` 해시가 어긋난다 | 병합 뒤 main의 `QELLO-JAVA-BASELINE-006` | High | P1 | 항목을 지우고 생성자를 `@RequiredArgsConstructor`로 바꾼다(#375의 `JAVA-CONV-0040`과 같은 경우) |

## 5. Unit scenarios

담당자는 9절의 실행자다. A는 persistence-executor, B는 auth-flow-executor다. 클래스 이름은 9절을 따른다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-001 | login_id `qello-admin` | 작성 규칙 검증: 3종 8자, 2종 10자, 4종 8자 / 2종 9자, 3종 7자, 1종 30자 | 앞의 셋은 통과. 뒤의 셋은 `AUT-VAL-005`, field `newPassword`, reason이 길이·종류 부족을 구분한다 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-002 | 같은 login_id | 공백이 섞인 값, 한글·이모지·탭·제어문자가 섞인 값 | D2 A면 공백은 허용하고 특수문자로 센다. ASCII 출력 문자 밖의 문자는 `AUT-VAL-005` | P1 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-003 | 같은 login_id | 72자, 73자 | 72자는 통과, 73자는 `AUT-VAL-005` | P1 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-004 | login_id `qello-admin` | `qello-admin`, `QELLO-ADMIN`, `qello-admin-2026!` | 앞의 둘은 `AUT-VAL-005`(대소문자 무시 동일). login_id를 포함만 하는 셋째는 통과(이슈 범위대로 동일 값만 막는다) | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-005 | 규칙 위반 입력 | 검증 예외 | 예외의 field·reason·message 어디에도 입력값이 없다 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-006 | `OperatorCredential` | `issue` / `changePassword` / `restore` | `issue`는 변경 필요 true. `changePassword`는 false, 실패 횟수 0, 잠금 해제, `passwordUpdatedAt` = 변경 시각. `restore`는 넘긴 값을 보존한다 | P0 | A |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-007 | 주기 90일, 변경 필요 false, `passwordUpdatedAt` = T | 판정 시각 T+90d−1s, T+90d, T+90d+1s / 변경 필요 true인 경우 | false, true, true / 항상 true | P0 | A |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-008 | 설정 바인딩 | 주기 null, 0, 음수 / `application.yml` 기본값 | 바인딩 실패 / `P90D` | P1 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-009 | `Clock.fixed`, fake 저장소 | 초기·최근 변경·만료 자격증명으로 `login` | 결과의 변경 필요가 각각 true, false, true. `LOGIN_SUCCEEDED` 기록과 `recordSuccess`는 세 경우 모두 그대로 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-010 | 정상 자격증명 | 현재 비밀번호가 틀린 변경 | `AUT-APP-009`. `recordFailure` 1회 반영. 새 비밀번호의 규칙 검증과 해시 계산은 하지 않는다 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-011 | 현재 비밀번호는 맞음 | 규칙 위반 새 비밀번호 | `AUT-VAL-005`. 실패 카운터·저장 변화 없음 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-012 | 현재 비밀번호는 맞음 | 새 비밀번호 = 현재 비밀번호 | `AUT-APP-010`. 저장 변화 없음 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-013 | 잠긴 자격증명 | 변경 | `AUT-APP-002`. 비밀번호 비교를 호출하지 않는다(로그인과 같은 순서) | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-014 | 현재 비밀번호 맞음, 새 값 규칙 통과 | 변경 | 새 해시로 갱신, `passwordUpdatedAt` = `Clock`, 변경 필요 false, 실패 횟수 0 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-015 | 시드 | 이미 있는 login_id + 규칙 위반 비밀번호 / 없는 login_id + 규칙 위반 / 없는 login_id + 규칙 통과 | false, 검증·저장 없음 / `AUT-VAL-005`, 계정·자격증명 저장 없음 / true, 변경 필요 true로 저장 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-016 | 접근 거부 핸들러 | 제한 권한 + `AccessDeniedException` / 제한 권한 + `InvalidCsrfTokenException`·`MissingCsrfTokenException` / 정상 운영자 + `AccessDeniedException` | 403 `AUT-APP-008` / 기존 공통 403 / 기존 공통 403 | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-017 | `SecurityContext` | 제한 권한(principal `"42"`)으로 운영자 id 추출 | 42. 접근 기록 필터와 로그아웃 기록이 제한 세션도 남긴다(#375 D1을 운영자 세션 전체로 넓힌다) | P0 | B |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-UNIT-018 | 변경 요청 DTO | `toString` | 두 비밀번호 모두 `REDACTED` | P1 | B |

## 6. Integration scenarios

테스트 클래스:

- P: 신규 `OperatorPasswordChangeIntegrationTest`. `@ActiveProfiles({"test", "account-persistence"})`이고 `@Import`를 두지 않아 `OperatorLoginIntegrationTest`와 컨텍스트를 공유한다.
- E: 신규 `OperatorPasswordExpiryIntegrationTest`. 고정 `Clock`을 `@Import`하므로 컨텍스트를 따로 쓴다.
- M: 기존 `FlywayMigrationIntegrationTest`(D1 A일 때).
- O: 기존 `OpenApiSpecificationIntegrationTest`. 수정하지 않고 재생성만 한다.
- R: 시드 직후 운영자 API를 호출하는 기존 통합 테스트(8절).

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-001 | M: Flyway + PostgreSQL | 빈 DB에 전체 migration 적용 | V35 카탈로그 조회. V34까지 적용한 상태에서 넣은 자격증명 행을 V35 뒤에 조회 | 최신 버전 35. `password_change_required`는 `BOOLEAN NOT NULL DEFAULT TRUE`. 기존 행은 TRUE. 테이블·CHECK·인덱스 총계는 바뀌지 않음을 확인하고 migration 개수 고정값만 올린다 | 기존 lifecycle |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-002 | P: 로그인 + 세션 체인 | 시드 운영자(변경 필요 true) | 로그인 → `GET /api/v1/operator/report-cases` → `POST /admin/logout` | 로그인 200, `data.passwordChangeRequired` = true. 운영자 API 403 `AUT-APP-008`. 로그아웃 200, 이후 같은 쿠키로 401 | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-003 | P: 변경 API | 시드 운영자로 로그인한 제한 세션 | `POST /admin/password` 정상 변경 → 이전 쿠키·새 쿠키로 운영자 API → 로그아웃 → 이전·새 비밀번호로 로그인 | 200. `SESSION` 쿠키 값이 바뀐다. 이전 쿠키는 401, 새 쿠키는 200. DB의 `password_hash`·`password_updated_at`이 바뀌고 변경 필요 false. 이전 비밀번호 401 `AUT-APP-001`, 새 비밀번호 200·`passwordChangeRequired` false | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-004 | P | 제한 세션 | 길이 미달·종류 부족·login_id 동일·ASCII 밖 문자·73자 새 비밀번호 각각, 현재와 같은 새 비밀번호 | 앞의 다섯은 400 `AUT-VAL-005`, 마지막은 400 `AUT-APP-010`. 모두 500이 아니다. 해시·`password_updated_at`·실패 횟수 변화 없음 | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-005 | P | 제한 세션 | 현재 비밀번호를 틀린 변경 5회 → 6번째 변경 → 올바른 비밀번호로 로그인 | 1~5회 400 `AUT-APP-009`. `failed_attempt_count` 5, `locked_until` 설정. 6번째 변경 423 `AUT-APP-002`. 로그인 423 | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-006 | P | 제한 세션 / 세션 없음 | CSRF 헤더 없이 변경 / 세션 없이 CSRF만 붙여 변경 | 공통 403(변경 필요 코드가 아니다), DB 변화 없음 / 401 | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-007 | P + `operator_access_log` | INT-002~005 흐름 | 접근 기록 조회 | 변경 성공 `API_CALL POST /admin/password 200`, 불일치 `400`, 제한 세션의 거부된 운영자 API `403`이 각각 운영자 id와 함께 남는다. 모든 행을 JSON 문자열로 바꿨을 때 사용한 비밀번호가 없다 | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-008 | E: 고정 `Clock` | 변경 필요 false인 운영자 두 명. `password_updated_at`이 고정 시각 −90일, −90일+1초 | 각자 로그인 후 운영자 API | −90일: `passwordChangeRequired` true, 403 `AUT-APP-008`, 변경 API 200. −90일+1초: false, 200 | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-009 | P | 변경 필요 false인 정상 세션 | 스스로 비밀번호 변경 | 200, 세션 ID 재발급, 운영자 API 계속 200 | TRUNCATE |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-010 | R: 기존 통합 테스트 | 8절 helper 적용 | `./gradlew integrationTest` 전체 | 기존 기대값을 바꾸지 않고 전부 통과. `OperatorLoginIntegrationTest`의 계정 열거 방지 검사는 수정 없이 통과 | 기존 |
| TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY-INT-011 | O: springdoc | 구현 완료 | `OpenApiSpecificationIntegrationTest` 실행 후 `docs/api/openapi.json` diff | `POST /admin/password`와 400·401·403·423 응답, 로그인 응답의 `passwordChangeRequired`가 들어간다. 스펙에 예시 비밀번호가 없다 | 없음 |

## 7. Cross-cutting scenarios

### Database and transactions

- 변경 서비스는 로그인과 같이 `@Transactional(noRollbackFor = AuthException.class)`로 둔다. 현재 비밀번호 불일치의 실패 카운터 증가는 예외와 함께 커밋되어야 한다(INT-005).
- 규칙 위반·현재와 같은 값은 실패 카운터를 건드리지 않는다. 현재 비밀번호를 먼저 확인하므로 형식이 틀린 요청도 불일치면 카운터에 더해진다(UNIT-010·011).
- 성공 변경은 `password_hash`, `password_updated_at`, `password_change_required`, 실패 횟수, 잠금을 한 UPDATE로 바꾼다. 새 저장소 메서드를 두고 `updateLoginState`는 비밀번호를 바꾸지 않는 지금 계약을 유지한다.
- 세션 ID 재발급과 권한 교체는 변경 트랜잭션이 커밋된 뒤 컨트롤러에서 한다. 커밋 전에 세션을 바꾸면 롤백된 변경에 새 세션이 남는다.
- 접근 기록은 기존 필터의 독립 기록(fail-open)을 그대로 쓴다. 기록 실패가 변경 결과를 바꾸지 않는다.

### Concurrency and idempotency

- 같은 세션의 동시 변경 두 건은 둘 다 현재 비밀번호 확인을 통과할 수 있고 나중 커밋이 이긴다. 결과는 둘 중 하나의 새 비밀번호이며 어느 쪽이든 규칙을 지킨다. 테스트하지 않고 보고서에 적는다.
- 동시 불일치 요청은 `@Version`이 없어 실패 증가 하나를 잃을 수 있다(V5의 기존 결정). INT-005는 순차 요청으로 검증한다.

### External APIs

- 외부 연동은 없다.

### Failure recovery and reconciliation

- 변경 트랜잭션이 실패하면 비밀번호와 세션 모두 이전 상태로 남고 운영자는 다시 시도한다.
- 시드 검증 실패는 `ApplicationRunner` 예외로 기동을 멈춘다(fail-fast). 이미 있는 운영자는 검증하지 않으므로 기존 배포의 기동에는 영향이 없다(UNIT-015).
- 운영자가 비밀번호를 잊으면 복구 경로가 없다(범위 밖). 지금처럼 DB 운영 절차로 처리한다.

## 8. Test data and isolation

- Fixtures: 시드 비밀번호는 기존 테스트 값 `example-operator-password`(소문자·특수문자 2종, 25자, 규칙 통과)를 그대로 쓴다. 새 비밀번호는 `Changed-Passw0rd-376`처럼 실제처럼 보이지 않는 가짜 값을 쓴다.
- 기존 통합 테스트: `seedIfAbsent`를 호출하는 10개 클래스 중 시드 직후 운영자 API를 호출하는 클래스에 공용 helper를 적용한다. helper는 시드한 행의 `password_change_required`를 FALSE로 바꾼다. 후보는 `OperatorReportCaseIntegrationTest`, `OperatorAccessLogIntegrationTest`, `OperatorAccessLogFailureIntegrationTest`, `OperatorSessionLifetimeIntegrationTest`, `FilterReleaseRegistryIntegrationTest`, `ManualReviewPriorityIntegrationTest`, `AppealCaseIntegrationTest`, `SnapshotHealthMigrationIntegrationTest`, `AuthRateLimitIntegrationTest`, `OperatorLoginIntegrationTest`이다. 첫 실행에서 403이 나는 클래스만 고치고, 시드 호출 외의 기대값은 바꾸지 않는다.
- Database isolation: 공유 컨텍스트와 클래스별 TRUNCATE(#350)를 따른다. E는 고정 `Clock` 때문에 컨텍스트를 따로 쓴다.
- Clock/randomness: E는 `@TestConfiguration`의 `@Primary Clock.fixed`. 만료 경계 행은 JDBC로 `password_updated_at`을 직접 넣는다.
- External API doubles: 없음.
- Cleanup: 기존 lifecycle.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 부모 오케스트레이터 | `TASK.md`, 이 계획 | 요구사항·파일·시나리오 계약 | Issue·branch·TASK 일치, `git diff --check` |
| 2 | persistence-executor (A, 승인 후 호출) | 아래 A 파일 | UNIT-006~007, INT-001 | 실패 테스트 확인 → 구현 → `./gradlew test --tests '*OperatorCredential*'`, `./gradlew integrationTest --tests '*FlywayMigrationIntegrationTest'` |
| 3 | auth-flow-executor (B, A 완료 후 호출) | 아래 B 파일, 테스트 보고서 | UNIT-001~005·008~018, INT-002~011 | 실패 테스트 확인 → 구현 → `./gradlew test`, `./gradlew integrationTest`, `./harness check` |
| 4 | auth-verifier (구현 후 독립 호출) | 소스 수정 권한 없음 | 전체 | 실제 diff, JUnit 결과, 범위 밖 파일 불변, `./harness pr-ready --project-tests`, `git diff --check` |

두 실행자는 작업 전에 `src/main/java/AGENTS.md`와 `docs/harness/JAVA_CONVENTIONS.md`를 읽는다. 아래 이름은 제안이며, 구현 중 이름이 바뀌면 이 표를 먼저 고친다.

A 소유 파일(D1 A 기준):

- 신규 production: `src/main/resources/db/migration/V35__add_operator_password_change_required.sql`
- 수정 production: `auth/domain/OperatorCredential.java`, `auth/repository/OperatorCredentialRepository.java`, `auth/repository/jpa/OperatorCredentialJpaEntity.java`, `auth/repository/jpa/OperatorCredentialJpaMapper.java`, `auth/repository/jpa/JpaOperatorCredentialRepository.java`
- 문서: `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md`, `docs/product/data-model/direction_communication.dbml`, `docs/product/data-model/schema-manifest.md`
- 수정 테스트: `src/test/java/com/dnd/qello/auth/domain/OperatorCredentialTest.java` (UNIT-006~007)
- 수정 테스트: `src/test/java/com/dnd/qello/FlywayMigrationContractTest.java`, `src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java` (INT-001), `src/integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java`(V24 이후 migration 수)

B 소유 파일:

- 신규 production: `auth/security/OperatorPasswordPolicy.java`
- 신규 production: `auth/config/OperatorPasswordProperties.java`, `application.yml`의 `qello.auth.operator-password.max-age`
- 신규 production: `auth/service/OperatorPasswordChangeService.java`, `auth/service/OperatorLoginResult.java`
- 신규 production: `auth/web/OperatorPasswordChangeRequest.java`
- 수정 production: `auth/error/AuthErrorCode.java`, `auth/service/OperatorLoginService.java`, `auth/service/OperatorSeedService.java`, `auth/web/OperatorLoginController.java`, `auth/web/OperatorLoginApiSpec.java`, `auth/web/OperatorSessionResponse.java`, `auth/web/OperatorAccessContext.java`, `auth/web/AuthEntryPoints.java`, `auth/config/SecurityConfiguration.java`
- 설정: `config/java-conventions/baseline.json`에서 `JAVA-CONV-0039` 삭제
- 문서: `docs/error-codes.md`, `docs/api/openapi.json`(재생성), `docs/product/AUTH_DESIGN.md`(5.2절의 "초기 비밀번호 강제 변경은 아직 구현하지 않았다"를 고친다. 구현 중 추가)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/security/OperatorPasswordPolicyTest.java` (UNIT-001~005)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/config/OperatorPasswordPropertiesTest.java` (UNIT-008)
- 수정 테스트: `src/test/java/com/dnd/qello/auth/service/OperatorLoginServiceTest.java` (UNIT-009)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/service/OperatorPasswordChangeServiceTest.java` (UNIT-010~014)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/service/OperatorSeedServiceTest.java` (UNIT-015)
- 신규 테스트: `src/test/java/com/dnd/qello/auth/web/AuthEntryPointsTest.java` (UNIT-016), `OperatorAccessContextTest.java` (UNIT-017), `OperatorPasswordChangeRequestTest.java` (UNIT-018)
- 신규 테스트: `src/integrationTest/java/com/dnd/qello/OperatorPasswordChangeIntegrationTest.java` (INT-002~007·009), `OperatorPasswordExpiryIntegrationTest.java` (INT-008)
- 신규 테스트 지원: `src/integrationTest/java/com/dnd/qello/OperatorTestAccounts.java` (8절 helper)
- 수정 테스트: 8절 후보 중 helper가 필요한 클래스 (INT-010)
- 보고서: `docs/reports/tests/gh-376-TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY.md` (`templates/test-report.md` 기반)

INT-010에서 helper 적용 외의 이유로 기존 테스트가 실패하면 기대값을 고치지 않고 원인을 보고한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

가정(ASSUMED, 승인 시 함께 확정):

- 검사 순서는 잠금 → 현재 비밀번호 → 작성 규칙 → 현재와 같은 값이다.
- 변경이 필요한 세션은 `ROLE_OPERATOR` 대신 `ROLE_OPERATOR_PASSWORD_CHANGE`를 받는다. 이 권한은 `POST /admin/password`, `POST /admin/logout`, `GET /admin/csrf`만 통과한다.
- 로그인 응답 필드 이름은 `passwordChangeRequired`이고 이유(초기·만료)는 구분하지 않는다.
- 변경 API의 현재 비밀번호 불일치로 잠겨도 지금 세션은 끊지 않는다. 잠금은 로그인과 변경 API만 막는다.
- 변경 API에는 IP 요청 한도를 걸지 않는다. 세션과 계정 잠금이 대입을 막는다.
- 세션 ID를 재발급해도 절대 만료의 기준인 로그인 시각은 유지한다.

승인할 결정:

1. D1 초기 비밀번호 판별. 권장 A: V35로 `operator_credential.password_change_required BOOLEAN NOT NULL DEFAULT TRUE`를 추가한다. 시드는 TRUE, 변경 성공은 FALSE다. 지금까지 변경 API가 없었으므로 기존 행을 TRUE로 채우는 것이 사실과 맞다. 이 안을 고르면 이슈 #376 본문의 "DB: 스키마 변경 없음"을 고친다. 대안 B: 스키마를 바꾸지 않고 주기 만료만 적용한다. 시드 직후 비밀번호를 강제로 바꾸게 할 수 없어 완료 조건 4의 "초기 비밀번호" 부분을 채우지 못한다. `created_at`과 비교하는 방법은 앱과 DB 시계가 달라 판별이 틀릴 수 있어 권하지 않는다.
2. D2 작성 규칙. 권장 A: 이슈 기본안대로 영문 대문자·소문자·숫자·특수문자 중 2종 이상이면 10자 이상, 3종 이상이면 8자 이상이다. 허용 문자는 ASCII 출력 문자(공백 포함)로 하고, 영숫자가 아닌 문자는 특수문자로 센다. 최대 72자는 BCrypt 입력 한도와 같다. login_id와 같은 값은 대소문자를 무시하고 거부한다. 대안 B: 관리지침 양식에서 다른 수치를 정해 준다.
3. D3 변경 주기와 판정 시점. 권장 A: 주기는 90일이고 `qello.auth.operator-password.max-age`로 바꿀 수 있다. 로그인할 때 판정해 세션 권한에 반영한다. 세션 도중에 만료되면 다음 로그인까지 반영되지 않고, 절대 만료 12시간이 그 상한이다. 대안 B: 운영자 요청마다 자격증명을 조회해 판정한다. 즉시 반영되지만 요청마다 쿼리가 하나 는다.
4. D4 API와 오류 코드. 권장: 경로는 `POST /admin/password`, 본문은 `currentPassword`·`newPassword`다. 새 코드는 `AUT-VAL-005` 작성 규칙 위반(400), `AUT-APP-008` 비밀번호 변경 필요(403), `AUT-APP-009` 현재 비밀번호 불일치(400), `AUT-APP-010` 현재와 같은 새 비밀번호(400)다. 불일치에 `AUT-APP-001`(401)을 다시 쓰면 프론트가 세션 만료로 오해하므로 쓰지 않는다.
5. D5 접근 기록. 권장 A: 변경 요청은 기존 필터의 `API_CALL`로 남긴다(method·경로·상태). 제한 세션도 운영자 세션이므로 기록 대상에 넣는다. 스키마를 바꾸지 않는다. 대안 B: `PASSWORD_CHANGED`·`PASSWORD_CHANGE_FAILED` 이벤트를 더하고 결과 코드를 남긴다. V35에서 `operator_access_log`의 이벤트 CHECK도 바꿔야 한다.

- Reviewer: `tkv00`
- Decision: 승인. D1~D5를 권장안(D1 A, D2 A, D3 A, D4 권장안, D5 A)으로 확정하고 위 가정도 함께 확정한다. D1 A에 따라 이슈 #376 본문의 "DB: 스키마 변경 없음"을 V35 컬럼 추가로 고친다.
- Approved at: `2026-11-24T22:30:42+09:00`
