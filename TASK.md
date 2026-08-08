# GitHub Issue #88 Task Contract

> Generated at: `2026-08-08T17:30:56+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `앱 사용자 온보딩 국가 필수값과 기기 자격증명 선행 조건 구현`
- GitHub Issue: `#88`
- Branch: `feat/gh-88-onboarding-country-required`
- Base branch: `main`

## Objective

앱 사용자 온보딩에서 국가 정보를 필수로 입력받아 검증하고 계정에 저장한다. 유효한
국가 정보가 없으면 일반 사용자 계정, 기기 자격증명과 첫 access token을 모두 생성하지
않는다. 운영자 계정은 국가 필수 조건에서 제외한다.

## Design contract

- Design ID: `ADR-0007`
- Design document: `docs/adr/0007-require-country-before-user-account-creation.md`
- Detailed design: `docs/product/ONBOARDING_COUNTRY_DESIGN.md`
- Design status: `APPROVED_FOR_IMPLEMENTATION`
- 국가 미입력 상태를 표현하기 위한 일반 사용자 계정이나 `INACTIVE` 상태를 만들지 않는다.
- 앱에는 국가명을 표시하되 API와 DB는 자유 입력 문자열 대신 정규화된 `countryCode`를
  계약으로 사용한다.
- `countryCode`는 ISO 3166-1 alpha-2 대문자 코드이며, `region_code` 마스터에서
  지원 국가로 확인되어야 한다.
- `POST /api/v1/auth/devices`는 국가 검증을 통과한 뒤에만 계정·기기 자격증명·첫
  access token을 단일 트랜잭션으로 생성한다.
- `USER` 계정은 유효한 국가 코드 없이 생성할 수 없고, `OPERATOR`는 이 불변식의
  예외다.

## Scope

- 온보딩 과정에 국가명 입력과 필수 검증을 추가한다.
- 선택한 국가를 정규화된 국가 코드로 사용자 계정에 저장한다.
- 국가명 누락·공백·유효하지 않은 값의 처리 규칙을 정의한다.
- 국가 정보가 없는 요청에서는 일반 사용자 계정 자체를 생성하지 않는다.
- 국가 검증을 통과한 사용자의 계정 생성과 기기 자격증명 발급 흐름을 연결한다.
- 운영자 계정 생성은 국가 필수 조건에서 제외한다.
- 기존 일반 사용자의 국가는 저장된 `coarseRegionCode`의 최상위 COUNTRY에서
  결정적으로 이관하며, 확인할 수 없는 데이터가 있으면 migration을 실패시킨다.
- 국가 정보 형식과 저장 방식에 대한 단위·통합 테스트를 추가한다.

## Explicit exclusions

- 국가별 서비스 정책·콘텐츠 정책
- 국가별 기능 차등 제공
- 국가 정보 기반 추천 또는 통계
- 기존 사용자의 국가 정보를 외부 데이터로 자동 추정
- 국가 미입력 일반 사용자를 저장하기 위한 사전 계정 또는 온보딩 세션
- 국가 변경 API와 국가별 서비스 이용 정책
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 온보딩·계정·기기 인증 | Feature executor | 국가 필수 조건과 자격증명 발급 경계 검토 |

## Approved implementation targets after design approval

- `account` 도메인과 JPA 매핑의 `countryCode` 불변식
- 국가 마스터와 지역 계층을 조회하는 repository port 및 JDBC adapter
- `auth` 기기 등록 request, controller, service와 오류 코드
- 다음 사용 가능한 Flyway migration, DBML, ERD와 schema manifest
- OpenAPI와 오류 코드 문서
- 관련 단위 테스트, web 테스트, migration·등록 통합 테스트와 테스트 보고서

ADR-0007이 승인되기 전에는 위 구현 파일을 수정하지 않는다.

## Existing user-owned changes

- 설계 시작 시 `git status --short`에서 Issue #88 초기화 과정에서 작성된
  `TASK.md` 변경을 확인했으며, 해당 내용을 기반으로 설계 계약을 보강했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Risks and rollout

- `POST /api/v1/auth/devices` 요청에 필수 필드가 추가되므로 이전 앱 버전의 신규 등록은
  400으로 실패한다. 백엔드 강제 전에 국가를 보내는 앱 버전을 배포하고 최소 지원
  버전 정책을 확인한다.
- DB migration은 기존 일반 사용자의 국가를 이관한 뒤 USER 국가 필수 CHECK와 FK를
  활성화한다. 국가를 확정할 수 없는 행이 하나라도 있으면 전체 migration을 실패시킨다.
- 새 DB 제약이 적용된 뒤 국가를 모르는 이전 백엔드로 단독 롤백하면 신규 USER insert가
  실패한다. migration 파일을 수정하지 않으며, roll-forward를 우선하고 필요하면 별도
  승인 migration으로 호환성을 복구한다.
- ADR-0007은 사용자 지시에 따라 구현 대상으로 승인되었으며, 구현 후 검증 승인을
  별도로 수행한다.

## Completion criteria

- [x] 온보딩 요청에 국가 정보가 필수로 검증된다.
- [x] 유효한 국가 정보가 사용자 계정에 저장된다.
- [x] 국가 정보가 없거나 공백이거나 국가 마스터에 없으면 일반 사용자 계정이 생성되지 않는다.
- [x] 국가 정보가 국가가 아닌 하위 지역이거나 `coarseRegionCode`와 다른 국가에 속하면
      일반 사용자 계정이 생성되지 않는다.
- [x] 국가 검증 실패 시 기기 자격증명과 access token도 발급되지 않는다.
- [x] 국가 정보가 있는 사용자의 기기 자격증명 발급이 기존 흐름과 함께 동작한다.
- [x] 운영자 계정은 국가 정보 없이도 기존 시드·로그인 흐름을 유지한다.
- [x] 기존 일반 사용자의 국가는 외부 추정 없이 기존 지역 계층의 최상위 COUNTRY로 이관된다.
- [x] 단위 테스트와 통합 테스트가 추가된다.
- [x] 국가 정보가 토큰, 로그, 오류 응답에 불필요하게 노출되지 않는다.
