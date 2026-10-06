# Test Plan: TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION

> Created at: `2026-10-06T11:09:23+09:00`
> GitHub Issue: `#312`
> Status: Approved

## 1. Objective

기기 등록(`POST /api/v1/auth/devices`)이 지역코드 없이 국가코드만으로 계정을 만들고, 일반 사용자의
`coarse_region_code`에 정규화한 국가코드를 저장하는지 검증한다. 지역 계층 검증을 지우면서 국가 검증까지
약해져 미지원 국가 계정이 생기거나, 클라이언트가 보낸 지역 값이 그대로 저장되는 것이 실패 시 위험이다.

## 2. Scope

### Included

- `DeviceRegistrationService.register`의 새 시그니처(`coarseRegionCode` 인자 제거)와 국가 검증
- `Account.createUser`에 넘기는 `coarseRegionCode`가 정규화한 국가코드인지
- `DeviceRegistrationRequest`에서 필드를 제거한 뒤 HTTP 계약(필드 생략, 이전 형식 요청)
- 국가 검증 실패 시 계정·기기 자격증명 저장 0건
- `docs/api/openapi.json` 재생성과 `coarseRegionCode` 제거 확인

### Excluded

- 운영자 시드(`OperatorSeedService`)와 `Account.createOperator`
- 방향 글·presence·답변의 `coarse_region_code` 전파(값을 계정에서 그대로 복사하며 이번 변경에서 코드가 바뀌지 않는다)
- dev DB에 남은 REGION 행과 기존 계정 데이터
- `region_code` 시드 내용(#294에서 검증)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #312 | `coarseRegionCode` 없이 `countryCode=KR` 등록 시 201, `user_account.coarse_region_code = 'KR'` |
| GitHub Issue #312 | `coarseRegionCode`를 함께 보내는 이전 형식 요청도 201 |
| GitHub Issue #312 | 형식 오류·미지원 `countryCode`는 400 `AUT-VAL-004`, 계정·자격증명 0건 |
| GitHub Issue #312 | `src/main`에 `findCountryAncestors` 0건, OpenAPI 요청 스키마에 `coarseRegionCode` 없음 |
| ADR-0007 변경 이력(2026-10-06) | 결정 조건 3 삭제, `coarse_region_code`에 COUNTRY 코드 저장 |
| schema | `user_account.coarse_region_code` NOT NULL, `region_code(code)` FK. `country_code`는 `(code, level='COUNTRY')` 복합 FK |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 지역 검증 제거와 함께 국가 마스터 검증이 빠져 미지원 국가 계정이 생긴다 | 데이터 정합성 | 낮음 | P0 | UNIT-002, INT-003 |
| `coarse_region_code`에 정규화 전 값(소문자)이 저장돼 FK 위반 또는 표시 오류 | 등록 실패(500) | 중간 | P0 | UNIT-001, INT-001 |
| 이전 앱이 보내는 `coarseRegionCode`가 거절되거나 그 값이 저장된다 | 기존 앱 등록 장애, 클라이언트 값 신뢰 | 중간 | P0 | INT-002 |
| 검증 실패 후 계정·자격증명이 일부 저장된다 | 고아 계정 | 낮음 | P0 | UNIT-002·003, INT-003의 0건 단언 |
| OpenAPI 산출물이 코드와 어긋나 프론트엔드가 옛 필드를 계속 보낸다 | 문서 불일치 | 중간 | P1 | INT-004 |
| 생성자를 `@RequiredArgsConstructor`로 바꾸면서 빈 주입 순서·대상이 달라진다 | 기동 실패 | 낮음 | P1 | 통합 테스트 컨텍스트 기동 |

## 5. Unit scenarios

대상 파일: `src/test/java/com/dnd/qello/auth/service/DeviceRegistrationServiceTest.java`

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-001 | `existsCountry("KR")`가 true인 fake | `countryCode="kr"`로 등록 | 저장된 계정의 `countryCode`와 `coarseRegionCode`가 모두 `KR` | P0 | 실행 에이전트 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-002 | fake 마스터에 `US` 없음 | `countryCode="US"`로 등록 | `INVALID_COUNTRY_CODE`, 계정·자격증명 0건 | P0 | 실행 에이전트 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-003 | 동일 fake | `countryCode`가 `"KOR"`, `"1A"`, `"   "`인 요청 각각 | 앞의 둘은 `INVALID_COUNTRY_CODE`, 공백은 `REQUIRED_VALUE_MISSING`. 모두 계정·자격증명 0건 | P0 | 실행 에이전트 |

기존 테스트 정리(새 시나리오가 아니라 시그니처 변경 반영):

- 모든 `service.register(...)` 호출에서 `"KR-11"` 인자를 제거한다.
- `FakeCountryCatalogRepository.findCountryAncestors`를 제거한다(인터페이스에서 삭제됨).
- `rejectsUnsupportedOrMismatchedCountryBeforePersistence`의 지역 불일치 단언을 제거하고 UNIT-002로 대체한다.
- 클래스 헤더 `Created at`은 유지하고 `Source scenario`에 UNIT-001~003을 추가한다.

## 6. Integration scenarios

대상 파일: `src/integrationTest/java/com/dnd/qello/DeviceAuthIntegrationTest.java`

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-001 | MockMvc, `DeviceRegistrationService`, PostgreSQL | `KR` COUNTRY 행 존재 | `coarseRegionCode` 없는 요청 등록 | 201, `user_account.country_code`와 `coarse_region_code`가 `KR` | `setUp`의 `device_credential`, `user_account` 삭제 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-002 | 동일 | 동일 | `coarseRegionCode:"XX-99"`(마스터에 없는 값)를 함께 보내 등록 | 201, `coarse_region_code`가 `KR`(요청 값 미저장) | 동일 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-003 | 동일 | 동일 | `countryCode:"ZZ"`로 등록 | 400, `errorDetail.code = AUT-VAL-004`, `user_account`·`device_credential` 0건 | 동일 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-004 | `OpenApiSpecificationIntegrationTest` | 없음 | 스펙 재생성 | 테스트 통과, `docs/api/openapi.json`의 `DeviceRegistrationRequest`에 `coarseRegionCode` 없음(`grep` 확인) | 생성물은 커밋 대상 |

기존 테스트 정리:

- `register()` 헬퍼와 `installationId`·국가 누락 요청 본문에서 `coarseRegionCode`를 제거한다.
- `rejectsRegistrationWhenCountryDoesNotMatchRegion`은 V29 시드에 `US`가 있어 이제 201이 되므로 INT-003으로 대체한다.
- `setUp`의 테스트 전용 REGION 행(`TEST-COUNTRY`) 삽입을 제거한다.
- 클래스 헤더 `Source scenario`에 INT-001~003을 추가한다.

승인 후 추가(2026-10-06, 시그니처 반영만): `DeviceRegistrationTransactionIntegrationTest`와
`NicknameDuplicateModerationIntegrationTest`가 `register`를 옛 시그니처로 호출해 컴파일되지 않는다.
두 파일에서 `REGION_CODE` 인자만 제거하고 시나리오·단언은 바꾸지 않는다.

## 6.1 추가 범위: Windows 개발 환경 지원 (사용자 지시 2026-10-06)

사용자가 이 PR에서 함께 진행하도록 지시했다. 변경 대상과 검증 방법은 대화에서 표로 제시한 내용과 같다.
macOS·Linux 동작은 바꾸지 않는 것이 조건이며, 이 PC(Windows)에서 실행하고 macOS·Linux는 CI로 확인한다.

| Scenario ID | Given | When | Then | Priority |
| --- | --- | --- | --- | --- |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-004 | `python3`가 Store 실행 별칭인 Windows 또는 일반 macOS·Linux | `PlatformCommands.python(...)`으로 Python 3 코드를 실행 | 종료 코드 0, 출력 `3` (`PlatformCommandsTest`) | P0 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-005 | 저장소 root | `PlatformCommands.gradlew(...)` | Windows는 `gradlew.bat`, 그 외는 `gradlew`이고 파일이 존재 (`PlatformCommandsTest`) | P0 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-006 | Windows | `JavaConventionBaselineTest`, `JavaStaticAnalysisRuleTest`, `RepoMapToolTest`, `*BoundaryTest` 실행 | 환경 요인 실패 없음. 경계 테스트는 경로 구분자와 무관하게 판정 | P0 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-007 | 심볼릭 링크 생성이 막힌 환경, 또는 shebang 스크립트를 실행할 수 없는 Windows | `RepoMapToolTest`의 링크 탈출·가짜 java 런타임 시나리오 | 해당 테스트만 skipped(사유 포함). 나머지 단언은 별도 테스트로 분리해 계속 실행 | P1 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-005 | Windows | `StructuredLoggingProfileIntegrationTest` | 하위 JVM이 argument file로 classpath를 받아 기동하고 기존 단언 통과 | P0 |

비테스트 검증:

- `sh scripts/find-python.sh`가 Windows에서 실제 Python 경로를 출력하고, `QELLO_PYTHON`이 잘못되면 127로 끝난다.
- `./harness doctor`, `./harness check`, `npm run hooks:validate`가 Windows Git Bash에서 실행된다.
- `.gitattributes` 변경 후 `git status`에 renormalize로 인한 변경이 생기지 않는다.

## 7. Cross-cutting scenarios

### Database and transactions

- 국가 검증은 저장 전에 끝난다. UNIT-002·003, INT-003의 0건 단언으로 확인한다.
- `coarse_region_code` FK는 `region_code(code)`의 COUNTRY 행으로 충족한다. INT-001이 실제 FK에서 검증한다.
- 스키마 변경이 없어 migration 시나리오는 없다.

### Concurrency and idempotency

- 같은 `installationId` 동시 등록 경로는 바뀌지 않았다. 기존 중복 등록 테스트를 회귀로 유지한다.

### External APIs

- 닉네임 moderation 호출 경로는 바뀌지 않았다. 단위 테스트는 기존 fake checker를 쓴다.

### Failure recovery and reconciliation

- 배포 순서(백엔드 먼저)는 INT-002가 이전 앱 요청 호환으로 뒷받침한다.
- dev DB의 기존 REGION 코드 계정은 범위 밖이며 보고서의 잠재 문제로 기록한다.

## 8. Test data and isolation

- Fixtures: 단위는 `FakeCountryCatalogRepository`(`KR`만 지원). 통합은 `setUp`이 넣는 `KR` COUNTRY 행과 V29 시드.
- Database isolation: `DeviceAuthIntegrationTest.setUp`이 `device_credential`, `user_account`를 비운다.
- Clock/randomness: 단위는 고정 `Clock`. 시크릿은 기존 생성기를 쓰고 값 자체는 단언하지 않는다.
- External API doubles: 기존 `ConfigurableNicknameModerationChecker`, 통합 프로필의 기존 moderation 설정.
- Cleanup: 각 테스트 `setUp`에서 정리한다. 추가 정리 없음.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 실행 에이전트 | `src/test/java/com/dnd/qello/auth/service/DeviceRegistrationServiceTest.java` | UNIT-001~003 | `./gradlew test --tests "*DeviceRegistrationServiceTest"` |
| 2 | 실행 에이전트 | `src/integrationTest/java/com/dnd/qello/DeviceAuthIntegrationTest.java` | INT-001~003 | `./gradlew integrationTest --tests "*DeviceAuthIntegrationTest"` |
| 3 | 실행 에이전트 | `docs/api/openapi.json`(생성물) | INT-004 | `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"` 후 `grep` |

통합 테스트는 Testcontainers(Docker)가 필요하다. 2026-10-06 현재 이 PC에서 Docker 데몬에 연결되지 않아,
Docker가 준비되지 않으면 Order 2·3은 `BLOCKED`로 보고한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: 사용자(tkv00)
- Decision: 승인
- Approved at: 2026-10-06
