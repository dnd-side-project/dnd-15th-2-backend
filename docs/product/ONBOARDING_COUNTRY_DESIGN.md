# 온보딩 국가 필수값 설계

> Design ID: `ADR-0007`
>
> GitHub Issue: `#88`
>
> 작성일: 2026-08-08
>
> 상태: `APPROVED_FOR_IMPLEMENTATION`

## 1. 목표와 불변식

일반 앱 사용자는 유효한 국가를 선택한 뒤에만 계정과 인증 수단을 얻는다. 운영자는
앱 온보딩을 거치지 않으므로 예외다.

- 유효한 국가가 없는 `USER` 계정은 존재할 수 없다.
- 국가 검증 실패 시 계정, 기기 자격증명과 access token 생성 결과는 모두 0건이다.
- `OPERATOR`는 국가 없이 생성·로그인할 수 있다.
- 국가 미입력 상태를 위한 `INACTIVE` 계정이나 비인증 온보딩 세션은 만들지 않는다.
- 계정 생성과 기기 자격증명 저장은 기존처럼 한 트랜잭션으로 유지한다.

## 2. 요청 계약

기존 `POST /api/v1/auth/devices`에 `countryCode`를 추가한다. 별도 온보딩 API는
추가하지 않는다.

```json
{
  "installationId": "<installation-id>",
  "platform": "IOS",
  "countryCode": "KR",
  "coarseRegionCode": "KR-11",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul",
  "nickname": "바람"
}
```

`countryCode` 계약은 다음과 같다.

- ISO 3166-1 alpha-2 코드다.
- 서버가 앞뒤 공백을 제거하고 `Locale.ROOT` 기준 대문자로 정규화한다.
- 정규화한 값은 `^[A-Z]{2}$` 형식이어야 한다.
- `region_code`에 같은 코드의 `COUNTRY` 행이 있어야 한다. 마스터에 없는 국가는
  ISO 형식이어도 현재 서비스의 미지원 국가로 처리한다.
- 자유 입력 국가명과 부분 일치, 별칭, locale 기반 추정은 허용하지 않는다.
- `coarseRegionCode`의 최상위 조상이 같은 `countryCode`여야 한다.

등록 성공 응답은 기존 `userId`, `deviceSecret`, `accessToken`, `expiresIn` 계약을
유지한다. `countryCode`를 토큰 claim이나 등록 응답에 추가하지 않는다.

## 3. 처리 순서와 실패 원자성

```text
요청 값 검증
  → 기존 installation 중복 확인
  → countryCode 정규화·국가 마스터 확인
  → coarseRegionCode 최상위 국가 일치 확인
  → USER 계정 저장
  → 기기 자격증명 저장
  → 첫 access token 발급
```

요청 값 검증 이후의 과정은 `DeviceRegistrationService.register` 트랜잭션 안에서
수행한다. 계정 저장 전 국가 검증이 끝나야 한다. 계정이나 기기 자격증명 저장이
실패하면 전체 DB 쓰기를 rollback하며 토큰을 응답하지 않는다.

| 실패 조건 | HTTP | 오류 계약 | 저장 결과 |
| --- | ---: | --- | --- |
| `countryCode` 누락·공백 | 400 | 공통 요청 검증 오류 | 0건 |
| 형식 오류·미지원 코드 | 400 | `AUT-VAL-004` | 0건 |
| COUNTRY가 아닌 지역 코드 | 400 | `AUT-VAL-004` | 0건 |
| `coarseRegionCode`와 국가 불일치 | 400 | `AUT-VAL-004` | 0건 |
| 기존 ACTIVE installation | 409 | `AUT-APP-005` | 0건 |
| 계정·자격증명 저장 실패 | 기존 5xx/충돌 계약 | 기존 예외 매핑 | 전체 rollback |

오류 응답의 `field`는 `countryCode` 또는 `coarseRegionCode`만 사용하고 제출된 값을
`reason`, 애플리케이션 로그, APM에 포함하지 않는다.

## 4. 도메인과 저장 모델

`Account.createUser`는 `countryCode`를 필수 인자로 받고 정규화가 끝난 코드만
허용한다. `Account.restore`와 JPA mapper도 필드를 보존한다. `updateProfile`은 이번
범위에서 국가를 바꾸지 않는다.

`Account.createOperator`는 국가를 요구하지 않는다. 일반 등록 API는 role을 입력받지
않으므로 클라이언트가 운영자 예외를 선택할 수 없다.

`user_account`에는 다음 컬럼과 제약을 추가한다.

- `country_code VARCHAR(2)`: USER 필수, OPERATOR nullable
- `country_level`: 항상 COUNTRY인 DB 생성 컬럼
- `(country_code, country_level)` → `region_code(code, level)` 복합 FK
- `country_code`의 대문자 alpha-2 형식 CHECK
- `role <> 'USER' OR country_code IS NOT NULL` CHECK

`coarse_region_code`는 공개 기준 지역이므로 유지한다. 국가와 기준 지역을 한 컬럼으로
합치면 기준 지역을 도시로 변경할 때 온보딩 국가가 사라지므로 별도 컬럼이 필요하다.

## 5. 조회 경계

지역 계층 확인은 DB 재귀 조회가 필요하므로 ADR-0002에 따라 JDBC adapter가 담당한다.

- account 영역에 국가 조회 repository port를 둔다.
- JDBC adapter는 국가 코드가 COUNTRY인지 확인하고, `coarseRegionCode`에서 최상위
  COUNTRY를 한 번의 읽기 쿼리로 결정한다.
- 결과가 없거나 하나로 확정되지 않으면 유효하지 않은 입력으로 처리한다.
- `DeviceRegistrationService`는 이 port의 검증 결과를 받은 뒤에만
  `AccountRepository.save`를 호출한다.

국가 마스터를 외부 API로 실시간 조회하지 않는다. 등록 성공 여부가 외부 서비스
가용성에 종속되지 않게 하고, 지원 국가 집합을 저장소 데이터로 고정하기 위해서다.

## 6. 기존 데이터 migration

이미 적용된 V1~V8은 수정하지 않고 다음 사용 가능한 Flyway version을 사용한다.

1. `country_code`를 nullable로 추가한다.
2. 각 기존 USER의 `coarse_region_code`부터 부모를 따라 최상위 COUNTRY를 찾는다.
3. COUNTRY가 정확히 하나이고 alpha-2 형식인 행만 `country_code`로 이관한다.
4. 미확정 USER 행 수가 0인지 확인한다. 하나라도 있으면 예외를 발생시켜 migration
   전체를 rollback한다.
5. OPERATOR의 `country_code`는 NULL로 둔다.
6. 복합 FK와 USER 필수 CHECK를 추가하고 검증한다.

외부 GeoIP, locale, timezone이나 자유 입력 국가명으로 기존 값을 추정하지 않는다.
DBML, 설명 ERD와 schema manifest는 같은 변경에서 migration과 동기화한다.

## 7. 변경 대상

| 영역 | 변경 |
| --- | --- |
| Account domain | `countryCode` 필드, USER 필수·OPERATOR 예외 불변식 |
| Account persistence | entity·mapper·repository 매핑과 국가 계층 JDBC 조회 |
| Device registration | request 필드, 서비스 선행 검증, controller 전달 |
| Error contract | `AUT-VAL-004 INVALID_COUNTRY_CODE`와 문서 |
| Database | 새 Flyway migration, DBML, ERD, manifest |
| API documentation | OpenAPI 요청 스키마와 400 응답 |
| Tests | domain·service·web 단위 테스트와 migration·등록 통합 테스트 |

## 8. 검증 시나리오

- null, 빈 문자열과 공백뿐인 `countryCode`를 거절한다.
- 소문자 alpha-2 코드는 정규화하고 저장값은 대문자인지 확인한다.
- alpha-2 형식이 아닌 값, 마스터에 없는 값과 REGION/CITY/DISTRICT 코드를 거절한다.
- 국가와 `coarseRegionCode`의 최상위 국가가 다르면 거절한다.
- 모든 거절 사례에서 account·credential repository 저장과 token issuer 호출이
  발생하지 않았는지 검증한다.
- 정상 요청은 국가, 계정과 기기 자격증명을 한 트랜잭션으로 저장하고 토큰을 발급한다.
- 자격증명 저장 실패 시 계정도 rollback되는지 통합 테스트한다.
- OPERATOR는 국가 NULL로 저장되고 기존 로그인 흐름이 유지되는지 확인한다.
- 직접 SQL로 국가 없는 USER나 COUNTRY가 아닌 코드를 저장하면 DB가 거절하는지 확인한다.
- 기존 USER 이관 성공과 미확정 지역 계층에서 migration 전체 실패를 검증한다.
- 기존 installation 중복과 토큰 재발급 회귀 테스트를 유지한다.

JUnit 5 테스트 계획은 구현 전에 별도 승인된 테스트 계획 식별자로 작성한다. 모든
테스트 클래스 헤더와 `@DisplayName`은 `AGENTS.md` 계약을 따른다.

## 9. 배포와 복구

1. 지역 마스터와 기존 USER 이관 가능 여부를 읽기 전용으로 사전 점검한다.
2. `countryCode`를 보내는 앱을 먼저 배포한다.
3. migration과 백엔드 강제를 함께 배포한다.
4. 이전 앱 버전에는 업데이트 경로를 제공한다. 기존 자격증명의 토큰 재발급은
   영향을 받지 않는다.

새 DB 제약이 적용된 뒤 이전 백엔드만 롤백하면 신규 USER 저장이 실패한다. 적용된
migration을 수정하지 않고 roll-forward를 우선한다. 복구 migration이 필요하면
국가 데이터는 보존하고 별도 Issue와 사람 승인을 거친다.

대량 백필과 제약 검증의 잠금 시간이 허용 범위를 넘으면 migration을 단계별로 나누고
다시 승인받는다.

## 10. 승인 조건

- Issue #88, `TASK.md`, ADR-0007과 이 문서의 범위가 일치한다.
- 모바일 팀이 필수 요청 필드와 이전 앱 버전 처리 방식을 확인한다.
- 기존 지역 마스터가 alpha-2 COUNTRY와 USER 이관 조건을 만족한다.
- PM/리뷰어가 DB 변경, 배포 순서와 복구 절차를 승인한다.

위 조건은 배포 전 PM·리뷰어가 확인할 검토 항목이다. 사용자 구현 시작 지시로 ADR은
구현 대상으로 승인되었으며, 실제 배포와 운영 데이터 migration은 별도의 승인 게이트를
통과해야 한다.
