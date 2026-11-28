# GitHub Issue #376 Task Contract

> Generated at: `2026-11-24T21:27:31+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `운영자 비밀번호 작성 규칙·변경 API·변경 주기 적용`
- GitHub Issue: `#376`
- Branch: `feat/gh-376-operator-password-policy`
- Base branch: `main`
- Test plan: `docs/test-plans/gh-376-TEST-PLAN-GH-376-OPERATOR-PASSWORD-POLICY.md` (2026-11-24T22:30:42+09:00 tkv00 승인, D1~D5 권장안)

## Objective

- 운영자 비밀번호에 작성 규칙을 적용하고, 운영자가 직접 비밀번호를 바꾸는 API를 추가하고, 초기 비밀번호나 변경 주기가 지난 비밀번호를 쓰는 운영자는 바꾸기 전까지 운영자 API를 쓰지 못하게 한다. 위치기반서비스 운영 배포 전 점검표 첨부 2 1항의 비밀번호 작성 규칙과 주기 변경(C4)이다.

## Scope

- `auth/security`에 작성 규칙 검증기를 추가한다. 영문 대문자·소문자·숫자·특수문자 중 2종 이상이면 10자 이상, 3종 이상이면 8자 이상이다. 허용 문자는 ASCII 출력 문자(공백 포함)이고 최대 72자다. login_id와 같은 값은 대소문자를 무시하고 거부한다.
- V35 migration으로 `operator_credential.password_change_required BOOLEAN NOT NULL DEFAULT TRUE`를 추가한다. 시드는 TRUE, 변경 성공은 FALSE다.
- `POST /admin/password`를 추가한다. 잠금 → 현재 비밀번호 → 작성 규칙 → 현재와 같은 값 순서로 검사한다. 현재 비밀번호 불일치는 로그인과 같은 실패 카운터에 더한다. 성공하면 비밀번호와 `password_updated_at`을 갱신하고, 세션 ID를 재발급하고, 세션 권한을 `ROLE_OPERATOR`로 바꾼다.
- 로그인할 때 변경 필요(초기 비밀번호 또는 `password_updated_at` + 90일 경과)를 판정한다. 변경이 필요하면 `ROLE_OPERATOR_PASSWORD_CHANGE`만 부여한다. 이 권한은 비밀번호 변경·로그아웃·CSRF 발급만 통과하고, 나머지 `/admin/**`, `/api/v1/operator/**`는 403 `AUT-APP-008`이다. 주기는 `qello.auth.operator-password.max-age`로 바꿀 수 있다.
- 로그인 응답에 `passwordChangeRequired`를 더한다.
- `OperatorSeedService.seedIfAbsent`는 새 운영자를 만들 때만 작성 규칙을 검사한다.
- 새 오류 코드 `AUT-VAL-005`, `AUT-APP-008`, `AUT-APP-009`, `AUT-APP-010`을 `AuthErrorCode`와 `docs/error-codes.md`에 추가하고 `docs/api/openapi.json`을 재생성한다.
- 변경 요청과 제한 세션의 호출은 기존 접근 기록 필터의 `API_CALL`로 남긴다. 제한 세션도 기록 대상에 넣는다.
- ERD, DBML, schema manifest와 Flyway 테스트의 최신 버전·고정 개수를 갱신한다.
- `config/java-conventions/baseline.json`에서 `JAVA-CONV-0039`(`OperatorSeedService`)를 지운다.

## Explicit exclusions

- 직전 N개 비밀번호 재사용 금지.
- 다른 운영자의 비밀번호 초기화.
- OTP 2차 인증(#377).
- 비밀번호 변경 성공 시 같은 운영자의 다른 세션 무효화.
- 만료 전 사전 알림, 비밀번호 분실 복구.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 테스트 계획과 이 계약 | 부모 오케스트레이터 | tkv00 계획 승인 |
| migration, 자격증명 도메인·저장소, 스키마 문서 | persistence-executor | 독립 verifier, PR 승인 1명(작성자 제외) |
| 작성 규칙, 로그인·변경·시드 서비스, 컨트롤러, 보안 설정, 오류 코드, API 문서 | auth-flow-executor | 독립 verifier, PR 승인 1명(작성자 제외) |

파일 단위 소유는 테스트 계획 9절을 따른다.

## Existing user-owned changes

- 2026-11-24 작업 시작 시 `git status --short`는 비어 있었다. 이후 `./harness task-init`으로 갱신한 이 파일과 테스트 계획 문서만 추가했다.

## Validation

```bash
./harness check
./gradlew test
./gradlew integrationTest
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 길이 미달, 문자 종류 부족, login_id와 같은 값, 현재 비밀번호와 같은 값이 각각 400과 새 오류 코드로 거부된다.
- 변경 성공 후 `password_updated_at`이 갱신되고 세션 ID가 바뀌며, 이전 비밀번호로 로그인하면 `AUT-APP-001`이 나온다.
- 현재 비밀번호가 틀린 변경 요청이 5회 쌓이면 자격증명이 잠긴다.
- 만료됐거나 초기 비밀번호인 운영자 세션으로 운영자 API를 호출하면 403 `AUT-APP-008`이고, 비밀번호 변경 API는 호출된다(`Clock` 고정).
- `openapi.json` 재생성 diff에 변경 API, 로그인 응답 필드, 오류 코드가 반영된다.
- 기존 통합 테스트는 시드 helper 적용 외에 기대값을 바꾸지 않고 통과한다.
- 테스트 계획의 P0 시나리오가 모두 구현되고 통과한다. 테스트 보고서가 있다.
- `./harness pr-ready --project-tests`가 통과한다.
