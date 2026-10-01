# GitHub Issue #294 Task Contract

> Generated at: `2026-10-01T09:39:30+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `국가 코드(region_code COUNTRY) 시드 데이터 적재`
- GitHub Issue: `#294`
- Branch: `feat/gh-294-country-code-seed`
- Base branch: `main`

## Objective

`region_code`에 COUNTRY 행이 없어 `JdbcCountryCatalogRepository.existsCountry`가 항상 false를 반환하고
`user_account.country_code`(V9 `fk_user_account_country`)가 참조할 대상이 없다.
ISO 3166-1 alpha-2 국가 시드를 Flyway 마이그레이션으로 적재한다.

## Scope

- `src/main/resources/db/migration/`에 V29 마이그레이션을 추가해 `region_code`에 `level = 'COUNTRY'`, `parent_code = NULL`인 행을 삽입한다.
- `code`는 `^[A-Z]{2}$`(ISO 3166-1 alpha-2 전체), `display_name`은 한국어 국가명이다.
- `INSERT ... ON CONFLICT (code) DO NOTHING`으로 재실행에 안전하게 만든다.
- 시드 검증 통합 테스트를 추가한다(`existsCountry("KR")` true, `ZZ` false, 중복 적용 시 행 수 불변).

## Explicit exclusions

- REGION, CITY, DISTRICT 계층 시드
- 국가 목록 조회 API 추가
- 기존 `user_account` 데이터 백필
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| V29 시드 마이그레이션 | 실행 에이전트 | 사용자 PR 리뷰 |
| 시드 검증 통합 테스트 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했고 `TASK.md`만 `task-init`으로 수정되었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- V29가 오류 없이 적용되고 모든 행이 `ck_region_code_root`, `ck_region_code_level`, `code ~ '^[A-Z]{2}$'`를 만족한다.
- `existsCountry("KR")`이 true, `ZZ`는 false를 반환하는 통합 테스트가 통과한다.
- V29를 두 번 적용해도 행 수가 변하지 않는다.
- `./harness check`와 `./gradlew check`가 통과한다.
