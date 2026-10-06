# Test Report: TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION

> Created at: `2026-10-06T11:35:00+09:00`
> Updated at: `2026-10-06T12:45:00+09:00` (Windows 개발 환경 지원 범위 추가)
> GitHub Issue: `#312`
> Branch: `feat/gh-312-country-only-registration`
> Commit: `18b1adc` (작업 트리 변경 미커밋 상태에서 실행)

## 1. Executive summary

- Result: `PASS`. Windows(이 PC)에서 `./harness pr-ready --project-tests`가 끝까지 통과했다.
- Tested scope: 국가코드 단일 입력 시나리오 UNIT-001~003, INT-001~004와 Windows 지원 시나리오 UNIT-004~007,
  INT-005. 전체 단위 테스트 1148건, 전체 통합 테스트 773건.
- Unverified scope: macOS에서는 실행하지 않았다. POSIX 경로는 기존 동작을 유지하도록 작성했고 Linux CI로 확인한다.
  Windows에서 skipped 2건(UNIT-007)은 macOS·Linux에서만 실행된다.
- Release recommendation: 백엔드를 앱보다 먼저 배포한다(ADR-0007 변경 이력). 이전 앱의 `coarseRegionCode`
  포함 요청이 201로 처리되는 것을 INT-002로 확인했다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| OS | Windows 11 Enterprise, Git Bash |
| Java | Microsoft Build of OpenJDK 21.0.12 (`JAVA_HOME` 지정) |
| Python | 3.12 (`python3`는 Store 실행 별칭, 실제 인터프리터는 `python`) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostGIS 컨테이너(Docker Desktop, 로컬) |
| Test runner | JUnit 5 (Gradle wrapper) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./harness pr-ready --project-tests` | PASS | - | 15분 36초 | base 동기화, `harness check`, `./gradlew check`(단위·통합·정적 검사), `git diff --check` |
| `./gradlew test` (위 명령 안에서 실행) | PASS | 1148, skipped 2 | - | `build/test-results/test` |
| `./gradlew integrationTest` (위 명령 안에서 실행) | PASS | 773 | - | `build/test-results/integrationTest` |
| `./harness check` | PASS | - | - | secret preflight, JUnit policy, convention, workflow, label, husky, baseline self-test |
| `./harness doctor` | PASS | - | - | `python 3.12.10`을 실행 중인 인터프리터로 보고 |
| `npm run hooks:validate` | PASS | - | - | `scripts/python.mjs`가 Store 별칭을 건너뛰고 `python`을 선택 |
| `sh scripts/find-python.sh` | PASS | - | - | 실제 인터프리터 경로 출력. 잘못된 `QELLO_PYTHON`은 127 |
| `grep -rn "findCountryAncestors" src/main` | PASS | - | - | 0건 |
| `git status` after `.gitattributes` 변경 | PASS | - | - | 저장소 텍스트 파일이 모두 LF라 renormalize 변경 없음 |

변경 전 같은 PC의 결과: 전체 단위 1144건 중 27건, 전체 통합 773건 중 2건이 Windows 환경 요인으로 실패했다(5절).

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-001 | PASS | `DeviceRegistrationServiceTest.normalizesCountryCodeBeforeAccountCreation` | `kr` 입력 시 `countryCode`, `coarseRegionCode` 모두 `KR` |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-002 | PASS | `DeviceRegistrationServiceTest.rejectsUnsupportedCountryBeforePersistence` | `INVALID_COUNTRY_CODE`, 계정·자격증명 0건 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-003 | PASS | `DeviceRegistrationServiceTest.rejectsMalformedOrBlankCountryBeforePersistence` | `KOR`·`1A`는 `INVALID_COUNTRY_CODE`, 공백은 `REQUIRED_VALUE_MISSING` |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-004 | PASS | `PlatformCommandsTest.resolvesRunnablePython3` | Store 별칭인 `python3`를 건너뛰고 `python` 선택 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-005 | PASS | `PlatformCommandsTest.resolvesGradleWrapperForCurrentOs` | Windows에서 `gradlew.bat` |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-006 | PASS | `JavaConventionBaselineTest`, `JavaStaticAnalysisRuleTest`, `RepoMapToolTest`, `*PersistenceBoundaryTest`, `AnswerJdbcBoundaryTest` | 변경 전 Windows 실패 27건이 모두 통과 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-007 | SKIPPED(Windows) | `RepoMapToolTest.symlinkEscape`, `RepoMapToolTest.reportedRuntimeChanges` | 사유: 개발자 모드가 꺼져 심볼릭 링크 생성 불가, shebang 스크립트를 java 실행 파일로 실행 불가. 같은 테스트의 다른 단언은 `unsafeOutputTarget`, `actualToolChanges`로 분리해 통과 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-001 | PASS | `DeviceAuthIntegrationTest.storesCountryCodeAsCoarseRegionWithoutRegionInput` | 실제 FK에서 `coarse_region_code = 'KR'` 저장 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-002 | PASS | `DeviceAuthIntegrationTest.ignoresLegacyCoarseRegionCodeField` | 마스터에 없는 `XX-99`를 보내도 201, 저장값은 `KR` |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-003 | PASS | `DeviceAuthIntegrationTest.rejectsUnsupportedCountryBeforePersistence` | `ZZ`는 400 `AUT-VAL-004`, 0건 |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-004 | PASS | `OpenApiSpecificationIntegrationTest` + 스키마 확인 | `DeviceRegistrationRequest`에 `coarseRegionCode` 없음. 남은 1건은 응답 스키마 `SentPostCard.coarseRegionCode`(범위 밖) |
| TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-INT-005 | PASS | `StructuredLoggingProfileIntegrationTest` | 하위 JVM classpath를 argument file로 전달 |

## 5. Failures and diagnostics

변경 전 Windows에서 실패하던 원인과 조치:

| 원인 | 영향받던 테스트·명령 | 조치 |
| --- | --- | --- |
| `python3`가 Microsoft Store 안내 실행 별칭 | `RepoMapToolTest` 12건, `JavaConventionBaselineTest` 일부, `./harness`, Git hook, `validateJavaConventionBaseline` | `scripts/find-python.sh`(sh), `scripts/python.mjs`(node), `PlatformCommands`(테스트)가 실행 확인으로 인터프리터를 고른다. Gradle은 `QELLO_PYTHON` 또는 OS 기본값을 쓴다 |
| 셸 스크립트 `./gradlew` 직접 실행 | `JavaConventionBaselineTest`, `JavaStaticAnalysisRuleTest`, `scripts/run-hook.py` | Windows에서 `gradlew.bat` 실행 |
| 경로 구분자 `\` | `*PersistenceBoundaryTest`, `AnswerJdbcBoundaryTest` 6건 | 비교 전에 `/`로 정규화 |
| 명령줄 길이 제한(`CreateProcess error=206`) | `StructuredLoggingProfileIntegrationTest` 2건 | JDK argument file(`@file`) |
| `core.autocrlf=true` 체크아웃의 CRLF | #294 보고서의 Flyway SHA 계약 실패, 1159개 파일 수정 표시 | `.gitattributes`에 `* text=auto eol=lf` |

## 6. Potential issues

### Application code

- `coarse_region_code`가 국가 단위가 되면서 피드 응답의 `coarseRegionCode`, `senderCoarseRegionCode`,
  `authorCoarseRegionCode`가 국가코드를 담는다. 필드 이름 변경은 #312 범위 밖이다.
- 운영자 시드는 여전히 `coarseRegionCode` 설정값을 받는다. 지역 행이 없는 환경에서는 국가코드를 넣어야 한다.

### Infrastructure and resource limits

- 등록 시 국가 검증 쿼리가 2회(국가 존재 확인, 재귀 CTE)에서 1회로 줄었다.
- Windows 개발자는 Gradle 실행에 JDK 17 이상(`JAVA_HOME`)이 필요하다. 이 PC의 기본 `java`는 8이라 `JAVA_HOME`을
  JDK 21로 지정해 실행했다. 코드로 해결하지 않았다.

### Database and migrations

- 스키마 변경 없음. dev DB에 수동으로 넣은 REGION 행(`KR-11` 등)과 그 코드로 등록된 계정은 남아 있다.
  신규 계정과 기존 계정의 `coarse_region_code` 단위가 섞인다(#312 제외 범위).
- `.gitattributes` LF 규칙은 체크아웃 줄바꿈만 바꾼다. 저장소 내용은 바뀌지 않았다.

### Concurrency and idempotency

- 등록 경합 경로는 바뀌지 않았다. `NicknameDuplicateModerationIntegrationTest`가 통과했다.

### Transactions and event ordering

- `DeviceRegistrationService`에 클래스 단위 `@Transactional(readOnly = true)`를 추가했다. `register`는 메서드 단위
  `@Transactional`이 우선한다. 롤백은 `DeviceRegistrationTransactionIntegrationTest`로 확인했다.

### External APIs

- 닉네임 moderation 호출 순서는 바뀌지 않았다.

### Failure recovery and reconciliation

- 앱이 백엔드보다 먼저 배포되면 기존 백엔드가 `coarseRegionCode` 누락으로 400을 반환한다. 배포 순서를 ADR-0007에 기록했다.
- `QELLO_PYTHON`이 잘못 지정돼 있으면 이제 다른 후보로 넘어가지 않고 127로 끝난다. 이전 hook은 값을 검증 없이 사용했다.

## 7. Regression and residual risk

- macOS 로컬 실행은 하지 않았다. `python3`가 정상이면 기존과 같은 후보를 고르고, `./gradlew` 경로도 그대로다.
- `spotlessApply`가 수정한 파일 전체에 포매터를 적용해 diff가 실제 변경보다 크다(경계 테스트 등).
- 경계 테스트 3개의 헤더 주석은 포매터가 Javadoc을 한 줄로 합치지 않도록 파일 맨 위로 옮겼다. 내용은 같다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-312-TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION.md`
- CI run: 아직 없음(PR 전)
- Related ADR: `docs/adr/0007-require-country-before-user-account-creation.md`
- PR: 아직 없음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
