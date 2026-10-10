# GitHub Issue #349 Task Contract

> Generated at: `2026-10-10T14:51:58+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `앱 액세스 토큰 role·iss·aud 검증과 서명 키 길이 기동 검사`
- GitHub Issue: `#349`
- Branch: `feat/gh-349-harden-jwt-validation`
- Base branch: `main`

## Objective

`/api/**` 체인은 앱 액세스 토큰의 서명과 만료만 확인하고 인증 여부만 본다. 디코더가 `iss`·`aud`를 검증하지 않고
`role` 클레임을 권한으로 쓰지 않아, 같은 키로 서명된 다른 용도의 토큰이나 USER가 아닌 토큰도 통과한다. 서명 키 길이를
기동 때 확인하지 않아 32바이트 미만 키는 첫 발급 요청에서 500으로 드러난다.

## Scope

- `AccessTokenConfiguration.jwtDecoder`: 기본 검증(만료·nbf)에 `iss` 일치, `aud` 포함 검증을 더한다.
- `SecurityConfiguration.appApiSecurityFilterChain`: `role` 클레임을 `ROLE_<role>` 권한으로 바꾸는 converter를 연결하고
  인증이 필요한 `/api/**`에 `hasRole("USER")`를 요구한다. 등록·재발급·OPTIONS 허용 규칙은 그대로 둔다.
- `AccessTokenProperties`: `secret`이 없거나 UTF-8 기준 32바이트 미만이면 바인딩 단계에서 실패해 앱이 기동하지 않는다.
- 통합 테스트 중 `jwt()` post-processor로 role 없는 인증을 만드는 5개 파일(19곳)에 USER 권한을 준다.
- 위 동작의 단위·통합 테스트를 추가한다. 테스트 계획은 `/harness-test-plan`으로 먼저 승인받는다.

## Explicit exclusions

- 차단 사용자 즉시 차단 캐시(AUTH_DESIGN 4.6 2단계)
- 비대칭 키 전환과 키 회전
- 토큰 TTL·클레임 구성 변경, 발급 코드(`AccessTokenIssuer`) 변경
- 운영자 세션 체인(`/admin/**`, `/api/v1/operator/**`) 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 앱 토큰 검증 설정·테스트 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`68f530fa`)에서 별도 워크트리로 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- role이 USER가 아니거나 없는 토큰으로 인증 필요 `/api/**`를 호출하면 403이다.
- `iss`가 다르거나 `aud`에 설정한 audience가 없는 토큰은 401이다.
- 정상 USER 토큰의 기존 통합 테스트가 그대로 통과한다.
- secret이 없거나 31바이트인 설정으로는 컨텍스트가 기동하지 않는다.
- `./harness check`, `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-10 사용자 승인: 이슈 유형 feature, `area: security`, Sprint Week 10, Priority P1, Status In Progress.
  메인 클론과 분리된 워크트리에서 작업한다. role 불일치는 403, iss·aud 불일치는 401, 짧은 키는 바인딩 시점 실패.
- 2026-10-10 사용자 승인: `docs/test-plans/gh-349-TEST-PLAN-GH-349-HARDEN-JWT-VALIDATION.md`. 통합 시나리오는 기존
  `DeviceAuthIntegrationTest`에 넣어 컨텍스트 기동을 늘리지 않는다.
