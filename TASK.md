# GitHub Issue #342 Task Contract

> Generated at: `2026-10-09T20:33:05+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `운영자 세션 수명·쿠키 설정`
- GitHub Issue: `#342`
- Branch: `feat/gh-342-operator-session-lifetime`
- Base branch: `main`

## Objective

`docs/product/AUTH_DESIGN.md` 2절은 운영자 세션 수명을 미사용 8시간, 최대 12시간으로 정했다. 그런데
`application.yml`에 세션 timeout 설정이 없어 Spring Boot 기본값 30분이 적용된다. Spring Session JDBC는 최대
수명을 제공하지 않아 따로 구현해야 하고, 운영 환경의 세션 쿠키 `Secure` 속성과 프록시 헤더 처리도 함께 정한다.

## Scope

- `application.yml`: `spring.session.timeout`을 `PT8H`로 설정한다.
- 최대 수명: `/admin/**`·`/api/v1/operator/**` 체인에 필터를 추가한다. 생성 후 12시간이 지난 세션은
  무효화하고 401로 응답한다. 로그인할 때 세션을 새로 발급하므로 생성 시각이 곧 로그인 시각이다. 값은
  `qello.auth.operator-session.absolute-timeout`(기본 `PT12H`)으로 주입한다.
- 세션 쿠키: `server.servlet.session.cookie.secure`의 기본값을 `true`로 둔다. HTTP로 직접 노출하는
  `local`·`dev` 프로필만 `false`로 둔다.
- 프록시 헤더: 앞단 프록시가 없는 지금은 `server.forward-headers-strategy`를 켜지 않는다(#315의
  `ClientAddressKey` 전제 유지). 프록시나 LB를 둘 때 켤 설정(`native`, 신뢰할 프록시 범위)을
  `docs/product/AUTH_DESIGN.md`에 기록한다.
- `docs/product/AUTH_DESIGN.md` 2절·5.2절에 구현 결과를 반영한다.
- 위 변경의 단위·통합 테스트. 테스트 계획은 `/harness-test-plan`으로 먼저 승인받는다.

## Explicit exclusions

- ALB·TLS 도입과 `forward-headers-strategy` 실제 활성화(인프라 별도 이슈)
- 동시 로그인 제한, 강제 로그아웃 API
- 앱 API 토큰 수명
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 세션 설정·최대 수명 필터·테스트·설계 문서 반영 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`63855a2b`)에서 분기했다.
- 시작 전 메인 클론 `.git/config`의 `core.bare=true`를 사용자 승인을 받아 `false`로 되돌렸다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 로그인으로 만든 `SPRING_SESSION` 행의 `MAX_INACTIVE_INTERVAL`이 28800이다(통합 테스트).
- 생성 후 12시간이 지난 세션으로 두 경로를 호출하면 401이 나오고 세션 행이 지워진다. 12시간 전에는
  통과한다(`Clock` 주입 테스트).
- 기본 설정에서 로그인 응답의 `SESSION` 쿠키에 `Secure`·`HttpOnly`·`SameSite=Lax`가 있다(통합 테스트).
- `./harness check`, `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-09 사용자 결정: 이슈 유형은 feature(`area: security`), Sprint Week 10, Priority P1, Status In Progress로 한다.
- 2026-10-09 사용자 승인: `docs/test-plans/gh-342-TEST-PLAN-GH-342-OPERATOR-SESSION-LIFETIME.md`. 생성 후 12시간 정각부터
  만료로 본다. 만료 세션은 무효화한 뒤 요청을 익명으로 계속 진행시켜 보호 경로는 401, 로그인·CSRF 경로는 통과한다.
  필터 시각은 `Clock` 빈을 쓴다.
