# GitHub Issue #72 Task Contract

> Generated at: `2026-08-07T13:13:20+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `백오피스 운영자 로그인과 Spring Security 골격`
- GitHub Issue: `#72`
- Branch: `feat/gh-72-operator-login-security`
- Base branch: `main`

## Objective

- 저장소에 API 계층과 인증이 없다. 백오피스 운영자 로그인을 첫 수직 슬라이스로
  삼아 Spring Security 골격을 세우고, 앱 사용자 인증(#73)이 얹힐 기반을 만든다.
  운영자는 팀 내부 5명 규모라 위험이 낮다.

## Scope

- `docs/adr/0006-split-operator-and-device-authentication.md` — 운영자·기기 인증
  분리 결정 기록. 설계 초안이 근거로 든 `0003`은 전역 예외 처리가, Issue 본문이
  적은 `0005`는 API 응답 계약(#74)이 이미 점유해 `0006`으로 정한다.
- 의존성 추가 — `spring-boot-starter-security`,
  `spring-boot-starter-oauth2-resource-server`, `spring-session-jdbc`
- `SecurityFilterChain` 2개 분리 — `/admin/**`(세션+CSRF), `/api/**`(stateless)
- `V5` 마이그레이션 — `operator_credential` 생성, `user_account (id, role)` 복합
  unique 추가, `password_hash` 컬럼과 `ck_user_account_password_hash` 제거
- `Account` 도메인에서 `passwordHash` 제거, `createOperator()` 정리,
  `ACC-DOM-003 INVALID_PASSWORD_HASH_STATE` 사용 중단
- `auth` feature 패키지 신설 — domain / repository / service / web
- `POST /admin/login`, 로그아웃, 실패 5회 15분 잠금, 세션 ID 재발급
- 운영자 계정 시드 경로 (Flyway 시드 또는 관리 CLI)

## Explicit exclusions

- 앱 사용자 기기 인증과 액세스 토큰 발급 — #73
- 등록 rate limit, 차단 사용자 캐시, 복구 코드 — 설계 문서 8절 제품 결정 후
- 운영자 자체 가입, 비밀번호 재설정 메일
- 백오피스 화면
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| TODO | TODO | TODO |

## Existing user-owned changes

- `docs/product/AUTH_DESIGN.md`와 인증 ADR은 이 브랜치 생성 전에 사용자가 작성해
  `stash`에 보관돼 있던 초안이다. 브랜치 생성 후 복원했다.
- 복원하면서 다음을 고쳤다. 설계 내용 자체는 사용자 초안을 유지한다.
  - ADR 번호를 `0003` → `0006`으로 조정 (`0003`, `0005` 모두 점유됨)
  - `POST /admin/login` 응답을 204에서 200 + `ApiResponse<Void>`로 변경
    (ADR-0005가 204를 쓰지 않기로 결정)
  - 9절의 "`V3`가 main에 없다" 전제를 `V5` 추가 경로로 갱신 (#48 병합 완료)

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] `(user_id, role)` 복합 FK로 USER 계정에 자격증명이 붙지 않음을 DB가 거절한다.
- [ ] `user_account.role`을 USER로 강등하면 FK 위반으로 실패한다.
- [ ] 로그인 실패 5회 후 15분 잠금되고, 잠금 중 요청은 423을 받는다.
- [ ] 존재하지 않는 `login_id`와 잘못된 비밀번호의 응답 메시지와 응답 시간이
      구분되지 않는다.
- [ ] 로그인 성공 시 세션 ID가 재발급된다.
- [ ] `/admin/**`에 CSRF가 켜져 있고 `/api/**`는 stateless임을 테스트가 고정한다.
- [ ] 운영자 계정 생성 엔드포인트가 존재하지 않는다.
- [ ] 평문 비밀번호가 로그·응답·예외에 남지 않는다.
- [ ] 인증 엔드포인트가 ADR-0005의 응답 계약을 따르고
      `ApiResponseConventionTest`가 통과한다.
