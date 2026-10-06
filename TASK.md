# GitHub Issue #312 Task Contract

> Generated at: `2026-10-06T11:02:59+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `기기 등록 요청의 coarseRegionCode 제거와 국가코드 단일 입력`
- GitHub Issue: `#312`
- Branch: `feat/gh-312-country-only-registration`
- Base branch: `main`

## Objective

기기 등록 API(`POST /api/v1/auth/devices`)가 `countryCode`와 함께 `coarseRegionCode`를 필수로 받는다.
2026-10-06 팀 회의에서 지역코드를 쓰지 않고 국가코드만 입력받기로 결정했고, 이 결정으로 #283의
`coarse_region_code` 단위 확정 항목을 대체한다. 등록 요청 계약과 관련 문서만 바꾸고 DB 스키마는 유지한다.

## Scope

- `DeviceRegistrationRequest`에서 `coarseRegionCode` 필드를 제거한다.
- `DeviceRegistrationService.register`가 정규화한 `countryCode`를 `Account.createUser`의 `coarseRegionCode` 인자로 넘긴다.
- `DeviceRegistrationService.validateCountry`에서 `findCountryAncestors` 호출을 제거하고, 호출처가 없어지는
  `CountryCatalogRepository.findCountryAncestors`와 `JdbcCountryCatalogRepository` 구현을 삭제한다.
- `DeviceAuthApiSpec`의 요청 설명과 `docs/api/openapi.json`을 새 요청 계약에 맞춘다.
- `docs/product/ONBOARDING_COUNTRY_DESIGN.md`, `docs/product/AUTH_DESIGN.md`의 요청 예시·처리 순서·실패 표에서
  `coarseRegionCode`를 제거한다.
- `docs/adr/0007-require-country-before-user-account-creation.md`의 계층 일치 검증 결정이 이번 결정으로 대체됐음을
  기록한다. ADR 본문 수정과 신규 ADR 중 어느 쪽으로 할지는 미결정이다.
- `coarseRegionCode`를 요청에 넣는 테스트를 새 계약에 맞춘다(`DeviceRegistrationServiceTest`,
  `DeviceAuthIntegrationTest` 등 7개 파일).

### 추가 범위: Windows 개발 환경 지원 (사용자 지시 2026-10-06)

별도 Issue 없이 이 PR에서 진행한다. macOS 동작은 바꾸지 않고 Windows에서도 하네스·훅·테스트가 실패하지 않게 한다.

- Python 실행기 선택을 `QELLO_PYTHON` → 실제로 실행되는 `python3` → `python` → `py -3` 순서로 통일한다.
  Windows의 `python3` 실행 별칭(Store 안내)은 실행 확인에서 걸러낸다. 대상: `harness`, `.husky/*`,
  `scripts/python.mjs`, `build.gradle`(`validateJavaConventionBaseline`), Python을 호출하는 테스트.
- `scripts/run-hook.py`와 테스트가 Windows에서 `gradlew.bat`을 실행하게 한다.
- `*PersistenceBoundaryTest`, `AnswerJdbcBoundaryTest`의 경로 비교를 구분자와 무관하게 한다.
- `StructuredLoggingProfileIntegrationTest`의 하위 JVM classpath를 argument file로 넘긴다.
- `.gitattributes`에 텍스트 파일 LF 규칙을 추가한다. 저장소의 기존 파일은 모두 LF라 내용 변경이 없다.
- 심볼릭 링크를 만들 수 없는 환경(개발자 모드가 꺼진 Windows)에서는 링크가 필요한 단언만 건너뛴다.

## Explicit exclusions

- `coarse_region_code`, `matched_region_code` 컬럼 이름 변경과 삭제
- 피드 응답 필드 이름 변경
- 운영자 시드 설정(`OperatorSeedProperties.coarseRegionCode`) 변경
- dev DB에 수동 투입한 REGION 행(`KR-11` 등)과 그 코드로 등록된 계정 데이터 정리
- 매칭 범위 변경(`delivery-scope: GLOBAL` 기본값 유지)
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 등록 요청 계약·서비스·repository 변경 | 실행 에이전트 | 사용자 PR 리뷰 |
| 설계 문서·ADR·OpenAPI 갱신 | 실행 에이전트 | ADR 처리 방식 사용자 결정 후 PR 리뷰 |
| 테스트 수정·추가 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |
| Windows 지원(하네스·훅·빌드·테스트·`.gitattributes`) | 실행 에이전트 | 사용자 지시로 범위 추가, PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 직전 브랜치에서 내용 차이 없이 수정됨으로 표시되던 파일 1159개는
  사용자 확인 후 `git restore .`로 정리했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- `coarseRegionCode` 없이 `countryCode=KR`로 등록하면 201이고 `user_account.coarse_region_code`가 `KR`이다(`DeviceAuthIntegrationTest`).
- `coarseRegionCode`를 함께 보내는 기존 형식 요청도 201이다(Jackson 미지정 필드 무시 기본값 확인).
- 형식 오류·미지원 `countryCode`는 400 `AUT-VAL-004`이고 계정·기기 자격증명 저장이 0건이다.
- `grep -rn "findCountryAncestors" src/main` 결과가 0건이다.
- `docs/api/openapi.json`의 `DeviceRegistrationRequest` 스키마에 `coarseRegionCode`가 없다.
- `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.
- Windows(이 PC)에서 `./gradlew test`와 `./gradlew integrationTest`가 환경 요인 실패 없이 통과한다.
  심볼릭 링크 단언처럼 플랫폼 기능이 없어 건너뛴 항목은 보고서에 skipped로 기록한다.
- `git ls-files --eol`에서 `gradlew.bat` 외 모든 텍스트 파일이 `eol=lf` 속성을 갖는다.
