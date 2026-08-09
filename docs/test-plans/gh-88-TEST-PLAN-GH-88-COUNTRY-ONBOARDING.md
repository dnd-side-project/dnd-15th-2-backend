# Test Plan: TEST-PLAN-GH-88-COUNTRY-ONBOARDING

> Created at: `2026-08-08T17:52:36+09:00`
> GitHub Issue: `#88`
> Status: Approved for implementation by user instruction

## 1. Objective

일반 앱 사용자가 유효한 국가를 제출하기 전에는 계정·기기 자격증명·access token을
얻을 수 없고, 유효한 요청은 기존 기기 등록 흐름을 유지하는지 검증한다. 운영자
계정의 국가 예외와 기존 사용자 이관의 데이터 무결성도 함께 확인한다.

## 2. Scope

### Included

- `countryCode` 요청 필수성, 정규화와 국가 마스터 검증
- `countryCode`와 `coarseRegionCode`의 최상위 국가 일치
- 검증 실패 시 account·credential·token 생성 0건
- 정상 등록 시 국가 저장과 기존 credential/token 발급
- USER 필수·OPERATOR 예외 DB 제약
- 기존 USER 국가 backfill과 미확정 계층 migration 실패
- 등록 트랜잭션 rollback과 기존 installation 멱등성

### Excluded

- 국가별 서비스·콘텐츠 정책
- 국가 변경 API
- 외부 국가 API 연동
- 실제 배포, rollback migration 적용과 프로덕션 데이터 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #88 | 국가가 없거나 유효하지 않으면 일반 사용자 계정·자격증명·토큰을 만들지 않으며 운영자는 예외다. |
| ADR-0007 | 기존 기기 등록에서 검증 후 단일 트랜잭션으로 생성한다. `country_code`는 USER 필수, OPERATOR nullable이다. |
| `ONBOARDING_COUNTRY_DESIGN.md` | ISO alpha-2 정규화, 지역 계층 일치, migration과 오류 시 저장 0건 계약 |
| `AUTH_DESIGN.md` 3.4·4.3절 | `user_account.country_code`와 `POST /api/v1/auth/devices` 계약 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 국가 검증을 계정 저장 뒤 수행 | 국가 없는 USER·고아 credential 발생 | 중간 | P0 | 실패 요청에서 모든 저장/issuer 호출 0건 |
| 국가와 기준 지역 불일치 | 잘못된 위치 정책·개인정보 분류 | 중간 | P0 | 최상위 COUNTRY 비교 실패 400 |
| DB 제약 누락 | 우회 SQL로 국가 없는 USER 생성 | 낮음 | P0 | USER NULL·하위 지역 FK 직접 insert 실패 |
| credential 저장 중 장애 | 계정만 남아 재로그인 불가 | 낮음 | P0 | 통합 rollback에서 account row 없음 |
| 기존 계층 미확정 | 일부 사용자만 이관되어 상태 불일치 | 중간 | P0 | migration 사전 검증 실패 시 전체 rollback |
| 동일 installation 동시 등록 | 중복 계정·credential | 낮음 | P1 | unique constraint와 기존 오류 계약 회귀 |
| 이전 앱 버전 등록 | 정상 사용자 등록 실패 | 높음 | P1 | countryCode 누락 400, 토큰 재발급 회귀 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-001 | `countryCode`가 `kr`이고 국가 마스터에 `KR/COUNTRY`가 있다 | 국가 코드 정규화 | `KR`을 반환하고 저장 경계에는 대문자만 전달한다 | P0 | Feature executor |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-002 | 값이 null·공백·길이/문자 형식 오류 또는 미지원 코드다 | 국가 검증 | 400 계약 오류가 발생하고 account 저장을 호출하지 않는다 | P0 | Feature executor |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-003 | `coarseRegionCode`의 최상위 국가가 선택 국가와 다르다 | 기기 등록 검증 | `AUT-VAL-004`와 함께 거절되고 account·credential·issuer 호출이 없다 | P0 | Feature executor |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-004 | 유효한 국가와 같은 국가의 기준 지역이 있다 | 기기 등록 | `countryCode`를 포함한 USER를 저장하고 credential·access token을 발급한다 | P0 | Feature executor |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-005 | credential 저장 port가 예외를 던진다 | 등록 트랜잭션 실행 | account 저장도 rollback 대상이 되고 token을 반환하지 않는다 | P0 | Test executor |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-006 | OPERATOR seed 입력에 국가가 없다 | 운영자 생성 | 기존 seed·로그인 경로가 국가 없이 동작한다 | P0 | Test executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-001 | MockMvc, device registration, DB | `country_code` 국가 마스터와 같은 국가의 region fixture | 유효한 등록 요청 | `user_account.country_code`와 credential이 저장되고 201 응답이 난다 | transaction rollback |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-002 | MockMvc, device registration, DB | 국가 필드 누락·공백·하위 지역·국가 불일치 요청 | 등록 요청 | 400, account·credential row 0건, secret/token 미응답 | transaction rollback |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-003 | PostgreSQL schema, JPA/JDBC | USER·OPERATOR insert fixture | country NULL과 non-COUNTRY 참조를 직접 저장 | USER는 제약 위반, OPERATOR NULL은 성공 | fixture 삭제 |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-004 | Flyway, `user_account`, `region_code` | V8 상태의 기존 USER와 잘못된 계층 fixture | 다음 migration 실행 | 정상 행은 root COUNTRY로 backfill되고 미확정 행은 전체 migration을 rollback한다 | isolated database |
| TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-005 | device registration, unique index | 동일 installation 요청 2개 | 순차·동시 등록 | 하나만 성공하고 다른 요청은 `AUT-APP-005` 계약을 따른다 | rollback |

## 7. Cross-cutting scenarios

### Database and transactions

- migration은 기존 V1~V8을 수정하지 않고 다음 version으로만 추가한다.
- backfill 대상이 하나라도 미확정이면 migration 전체가 rollback되어 부분 이관이 없다.
- USER 국가 필수 CHECK와 COUNTRY 복합 FK는 직접 SQL 우회도 막아야 한다.
- credential 저장 실패는 account insert와 함께 rollback되어 고아 계정이 없어야 한다.

### Concurrency and idempotency

- `uq_active_device_installation`이 최종 중복 방어선이다.
- check-then-insert 경합에서도 기존 `AUT-APP-005` 매핑이 유지되는지 확인한다.
- country master가 유효해도 동일 installation이면 새 account를 만들지 않는다.

### External APIs

- 외부 API 호출은 없다. 국가 master는 같은 PostgreSQL의 `region_code`를 사용한다.
- 외부 국가 서비스 mock이나 실제 credential은 테스트에 사용하지 않는다.

### Failure recovery and reconciliation

- 이전 앱 요청은 countryCode 누락으로 400이 되며 기존 token reissue는 영향이 없어야 한다.
- 새 제약 이후 이전 backend rollback은 신규 USER insert를 깨뜨릴 수 있으므로 roll-forward
  우선 정책을 문서화하고 테스트는 데이터 보존을 확인한다.
- migration 실패 후 다음 시도에서 부분 `country_code`가 남지 않아야 한다.

## 8. Test data and isolation

- Fixtures: ISO alpha-2 COUNTRY `KR`, 하위 `REGION/CITY`, 다른 국가 `US`, USER와 OPERATOR
- Database isolation: Testcontainers PostgreSQL 또는 기존 integration test 격리 전략; migration
  실패 시나리오는 별도 database/schema
- Clock/randomness: 기존 fixed `Clock`, deterministic token issuer와 secret generator double
- External API doubles: 없음
- Cleanup: 각 테스트 transaction rollback 또는 fixture id 기준 삭제; 공유 master는 삭제하지 않음

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `src/main/java/**/account/**`, `src/main/java/**/auth/**`, migration과 API 문서 | UNIT-001~004, INT-001~003 | `./gradlew test` |
| 2 | Test executor | `src/test/**`, `src/integrationTest/**`, `docs/reports/tests/gh-88-*.md` | UNIT-005~006, INT-004~005 | `./gradlew integrationTest` |
| 3 | Verification executor | 변경 파일 read-only | 전체 | `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성
- [ ] account·credential·token에 국가 원문이 로그·응답·토큰 claim으로 노출되지 않음

## 11. Human approval

- Reviewer: User instruction in current implementation request
- Decision: Approved for implementation; test plan is the execution contract
- Approved at: 2026-08-08T17:52:36+09:00
