# GitHub Issue #230 Task Contract

> Generated at: `2026-09-17T00:42:16+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `테스트 서버용 CORS와 actuator 헬스체크, dev 프로파일 추가`
- GitHub Issue: `#230`
- Branch: `feat/gh-230-cors-actuator-dev-profile`
- Base branch: `main`
- Test plan: `docs/test-plans/gh-230-GH-230-CORS-ACTUATOR-DEV-PROFILE.md`
  (Approved, `tkv00`, 2026-09-17T00:49:35+09:00)

## Objective

- 프론트엔드가 브라우저에서 EC2 테스트 서버(#229)의 `/api/**`를 호출할 수
  있도록 CORS를 추가한다.
- `/actuator/health`를 인증 없이 열어 서버 상태를 확인할 수 있게 한다.
- `application-dev.yml`을 신설해 `SPRING_PROFILES_ACTIVE=dev`와 필수
  환경변수만으로 애플리케이션이 기동하게 한다.
- API 경로, HTTP method, 상태 코드, 요청·응답 구조는 바꾸지 않는다.

## Scope

- `src/main/java/com/dnd/qello/auth/config/SecurityConfiguration.java`:
  `CorsConfigurationSource` 빈 추가, `/api/**` 체인에 연결. `/actuator/health`
  허용 체인 추가(`apiDocsSecurityFilterChain`과 동일하게
  `@ConditionalOnProperty`로 실제 노출 여부에 연동해 다른 프로파일의 기존
  동작을 건드리지 않는다).
- `src/main/resources/application-dev.yml` 신설: datasource, media bucket,
  auth secret을 환경변수로 받는다. actuator 노출을 `health`로 제한한다.
  `qello.notification.push.policy.*`(bundle-window 등)에 fixture 값을 채운다
  — 이 설정은 프로파일과 무관하게 항상 바인딩되기 때문이다.
- **범위 확장(사람 승인, 2026-09-17)**:
  `src/main/java/com/dnd/qello/notification/config/PushConfiguration.java`,
  `PushTokenProperties.java`의 `@Profile` 조건에 `dev`를 추가해
  `local`/`test`/`integration`과 동일하게 `NoOpPushProvider`를 쓰게 한다.
- `compose.yaml`: `app` 서비스에 `QELLO_AUTH_ACCESS_TOKEN_SECRET`,
  `QELLO_MEDIA_BUCKET` 환경변수와 `/actuator/health` 기반 healthcheck 추가.
- `.env.example`: 위 두 키 추가(사람이 이번 작업에 한해 파일 접근 권한 허용).
- `src/integrationTest/java/com/dnd/qello/ActuatorExposureIntegrationTest.java`
  갱신 + CORS·dev 프로파일 통합 테스트 신규 작성(테스트 계획 §6 참고).

## Explicit exclusions

- EC2, Terraform, AWS 리소스 변경.
- 인증 방식 변경. 디바이스 기반 JWT 발급 흐름 유지.
- production 프로파일과 운영 환경 설정.
- 운영자 계정 시드 데이터.
- FCM 실제 자격 증명 발급이나 SSM 파라미터 추가.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 구현 (`src/main/**`, `compose.yaml`, `.env.example`) | `tkv00` | PR 승인 |
| 테스트 (`src/integrationTest/**`) | `tkv00` | 테스트 계획 승인(완료) + PR 승인 |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자 변경이 없다.

## Validation

```bash
./gradlew spotlessApply
./gradlew javaConventionCheck
./gradlew test
./gradlew integrationTest
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 허용 origin의 preflight `OPTIONS /api/v1/auth/devices`가 200과
      `Access-Control-Allow-Origin` 헤더를 반환하는 통합 테스트 통과.
- [ ] 허용하지 않은 origin의 같은 요청이 그 헤더 없이 끝나는 테스트 통과.
- [ ] `GET /actuator/health`가 인증 없이 200과 `{"status":"UP"}`을 반환하는
      통합 테스트 통과.
- [ ] `/actuator/` 아래 health 외 엔드포인트가 노출되지 않는 것을 테스트로 확인.
- [ ] `SPRING_PROFILES_ACTIVE=dev`와 필수 환경변수만으로 애플리케이션이
      기동하는 것을 컨텍스트 로드 테스트로 확인(FCM/push 값 없이).
- [ ] `docker compose up`이 `app`과 `db` 모두 healthy 상태에 도달한다
      (compose 문법 검증 + 가능하면 로컬 수동 확인).
- [ ] 모든 테스트 메서드에 `@DisplayName`이 있고 테스트 클래스 상단에 ISO 8601
      생성 시각과 테스트 계획 식별자를 기록한다.
- [ ] `./harness pr-ready --project-tests`가 통과한다.

## 참고

- 테스트 계획: `docs/test-plans/gh-230-GH-230-CORS-ACTUATOR-DEV-PROFILE.md`
- 선행 이슈: #229(EC2 테스트 서버, PR #236/#238로 구현됨)
- 관련 ADR: `docs/adr/0006-split-operator-and-device-authentication.md`
