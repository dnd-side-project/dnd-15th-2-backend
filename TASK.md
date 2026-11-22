# GitHub Issue #375 Task Contract

> Generated at: `2026-11-22T21:06:48+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `운영자 로그인·로그아웃·운영자 API 호출 접근 기록`
- GitHub Issue: `#375`
- Branch: `feat/gh-375-operator-access-log`
- Base branch: `main`
- Test plan: `docs/test-plans/gh-375-TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG.md` (2026-11-22T21:56:28+09:00 tkv00 승인, D1~D4 권장안)

## Objective

- 운영자 로그인 결과 5종, 로그아웃, 운영자 API 호출을 append-only `operator_access_log`에 한 행씩 남긴다. 위치기반서비스 운영 배포 전 점검표 첨부 2 3항의 운영자 접근 기록(C3)이다.

## Scope

- V34 migration으로 `operator_access_log` 테이블을 추가한다. FK는 두지 않는다.
- `OperatorLoginService.login`에서 성공, `AUT-APP-001`, `AUT-APP-002`, `AUT-APP-003`을 로그인 트랜잭션 안에서 기록한다.
- `/admin/login`의 요청 제한 거절(`AUT-APP-007`)과 `/admin/logout`을 기록한다.
- `/admin/**`, `/api/v1/operator/**` 세션 체인에 운영자 API 호출 기록 필터를 추가한다. `/admin/login`, `/admin/logout`, `/admin/csrf`는 제외한다.
- 클라이언트 주소는 `remoteAddr`에서 얻고 `X-Forwarded-For`는 무시한다.
- ERD, DBML, schema manifest와 `FlywayMigrationIntegrationTest`를 갱신한다.

## Explicit exclusions

- 보존 기간이 지난 행의 파기와 보존 기간 결정.
- DB 수준의 위·변조 방지와 RDS 감사 로그(I4).
- 프록시 헤더 신뢰 설정(C7).
- 앱 사용자(`/api/**` JWT) API의 접근 기록, 접근 기록 조회 API.
- 비밀번호 변경(#376)과 OTP(#377) 이벤트.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 테스트 계획과 이 계약 | 부모 오케스트레이터 | tkv00 계획 승인 |
| migration, 도메인 값, 저장소, 스키마 문서 | persistence-executor | 독립 verifier, PR 승인 1명(작성자 제외) |
| 로그인 서비스·컨트롤러, 기록 필터, 보안 설정 | auth-flow-executor | 독립 verifier, PR 승인 1명(작성자 제외) |

파일 단위 소유는 테스트 계획 9절을 따른다.

## Existing user-owned changes

- 2026-11-22 작업 시작 시 `git status --short`는 비어 있었다. 이후 `./harness task-init`으로 갱신한 이 파일과 테스트 계획 문서만 추가했다.

## Validation

```bash
./harness check
./gradlew test
./gradlew integrationTest
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 로그인 성공, 자격증명 불일치, 잠금, 비활성 계정, 요청 제한 결과가 각각 `operator_access_log` 한 행으로 남는다.
- 미존재 login_id로 실패한 행은 운영자 id가 NULL이고, 기존 계정 열거 방지 테스트가 수정 없이 통과한다.
- 로그아웃과 운영자 API 호출이 운영자 id, IP, 시각, 결과와 함께 기록된다.
- 기록 행에 비밀번호, 세션 ID, CSRF 토큰, 요청 본문, query string이 없다.
- 애플리케이션 코드에 `operator_access_log`를 수정·삭제하는 경로가 없다.
- 테스트 계획의 P0 시나리오가 모두 구현되고 통과한다. 테스트 보고서가 있다.
- `./harness pr-ready --project-tests`가 통과한다.
