# GitHub Issue #315 Task Contract

> Generated at: `2026-10-06T13:59:46+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `인증·닉네임 변경 요청 한도와 닉네임 변경 주기 제한`
- GitHub Issue: `#315`
- Branch: `feat/gh-315-auth-nickname-rate-limit`
- Base branch: `main`

## Objective

기기 등록, 토큰 재발급, 운영자 로그인, 닉네임 변경에 요청 한도가 없다. `docs/product/AUTH_DESIGN.md` §8.2는
기기 등록의 IP 단위 rate limit을 1차 필수 방어로 정했고, 닉네임 변경은 요청마다 OpenAI moderation을 호출해
비용이 든다. 네 경로에 설정으로 주입하는 요청 한도를 두고, F01의 닉네임 변경 주기 제한을 구현한다.

## Scope

- 단일 인스턴스 메모리 고정 윈도 카운터(Clock 주입, 만료 키 정리). 새 의존성은 추가하지 않는다.
- 기기 등록(`POST /api/v1/auth/devices`)·토큰 재발급(`POST /api/v1/auth/token`)·운영자 로그인: 클라이언트 IP 단위 한도.
  클라이언트 IP는 연결 주소(`remoteAddr`)를 쓰고 `X-Forwarded-For`는 신뢰하지 않는다(현재 앞단 프록시 없음).
- 닉네임 변경 시도: 사용자 단위 한도. 중복·moderation 거절로 실패한 시도도 센다.
- 닉네임 변경 주기(F01): 마지막 성공 변경 후 일정 기간 재변경 거절. 가입 시 지정한 닉네임은 주기에 넣지 않는다.
  주기 위반은 moderation 호출 전에 거절한다.
- Flyway: `user_account.nickname_changed_at` 컬럼 추가(nullable, 기존 행 NULL)
- 오류 코드: `AUT-APP-007`, `ACC-APP-003`(닉네임 변경 시도 한도), `ACC-APP-004`(닉네임 변경 주기). 모두 429
- 한도·윈도·주기 값은 `application.yml` 기본값과 환경 변수로 주입한다.
  기본값: 등록 IP당 10회/1시간, 재발급 IP당 60회/1시간, 운영자 로그인 IP당 20회/15분,
  닉네임 변경 시도 사용자당 10회/1일, 닉네임 변경 주기 30일
- 문서: 해당 ApiSpec의 429 응답, `docs/api/openapi.json`, `docs/error-codes.md`, `AUTH_DESIGN.md` §8.2·§9

## Explicit exclusions

- Redis 등 공유 저장소와 다중 인스턴스 지원(재시작 시 카운터 초기화를 수용)
- 프록시·LB 도입 시 필요한 forwarded header 신뢰 설정
- `Retry-After` 헤더
- Play Integrity / App Attest 기기 무결성 검증(§8.2 2차)
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요청 한도 컴포넌트·auth/account 서비스·오류 코드·설정 | 실행 에이전트 | 사용자 PR 리뷰 |
| Flyway 마이그레이션(`nickname_changed_at`) | 실행 에이전트 | 사용자 PR 리뷰 |
| ApiSpec·OpenAPI·`docs/error-codes.md`·`AUTH_DESIGN.md` | 실행 에이전트 | 사용자 PR 리뷰 |
| 테스트 수정·추가 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`18b1adc`)에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 네 경로 모두 한도를 넘은 요청이 429와 해당 오류 코드로 거절된다.
- 한도 값은 설정으로 주입되고 테스트에서 바꾼 값이 반영된다.
- 주기 안의 닉네임 재변경은 429이고 닉네임이 바뀌지 않으며 moderation을 호출하지 않는다.
- 윈도가 지나면 다시 허용된다(Clock 기반 검증).
- `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-06 사용자 결정: 초안 범위·권장 기본값 승인. 닉네임 변경 주기 기본값은 30일.
- 2026-10-06 사용자 결정: #312 위에 쌓지 않고 `origin/main`에서 분기한다. #312(PR #314)와 등록 코드가 겹치므로
  먼저 머지된 쪽에 맞춰 rebase한다.
- 2026-10-06 사용자 결정: 테스트 계획 `TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT` 승인. D1 IP 한도는 컨트롤러에 도달한
  요청을 성공·실패와 무관하게 센다. D2 닉네임 변경은 시도 한도 → 계정 조회·주기 → 중복 → moderation → 저장 순서이고
  주기 위반도 시도 1회로 센다. D3 마지막 변경 시각 + 주기 시점부터 허용한다. D4 IPv6는 /64 접두사 단위로 센다.

## Environment notes

- 처음에는 #312의 Windows 수정이 없는 `origin/main`(`18b1adc`)에서 분기해, Java가 포함된 커밋 7개를 사용자 승인 아래
  pre-commit 훅 단계를 직접 실행한 뒤 `--no-verify`로 커밋했다. 기록은 테스트 보고서 3절에 있다.
- 2026-10-06 PR 전에 #314 머지 후의 `origin/main`(`c070ae0`)으로 rebase했다. 충돌은 `TASK.md`, `DeviceAuthController`,
  `DeviceRegistrationServiceTest`, `docs/api/openapi.json`에서 났고 #314의 지역코드 제거와 #315의 한도 변경을 모두 남겼다.
  `openapi.json`은 테스트로 다시 생성했다. 이후 커밋은 Windows에서도 훅을 켠 채로 만든다.
