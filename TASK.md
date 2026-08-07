# GitHub Issue #82 Task Contract

> Generated at: `2026-08-07T22:20:16+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Swagger/OpenAPI 문서 생성 에이전트와 스킬 추가`
- GitHub Issue: `#82`
- Branch: `chore/gh-82-api-docs-agent`
- Base branch: `main`
- 관련 PR: `#81` (`feat/gh-73-device-credential-token`, open). `/api/**` JWT 인증
  체인과 `DeviceAuthController`가 이 PR에 있다. 이 브랜치는 그 위에 쌓지 않고
  `main`에서 분기했다.

## Objective

REST API가 추가·변경될 때마다 API 문서를 손으로 맞추는 대신, springdoc이 런타임에
추출한 OpenAPI 스펙을 저장소 산출물로 고정하고 CI가 최신성을 강제한다. 에이전트는
추출된 스펙만으로는 부족한 설명·예시·오류 응답을 보강한다.

현재 컨트롤러는 `/admin` 2개(`CsrfTokenController`, `OperatorLoginController`)뿐이고
앱 REST 컨트롤러는 아직 없다. 이 이슈는 문서화 대상이 늘어나기 전에 도구를 먼저
완성해, 이후 도메인별 컨트롤러가 추가될 때마다 문서가 자동으로 따라오게 한다.

## Scope

### springdoc 스펙 추출

- `build.gradle`에 `springdoc-openapi-starter-webmvc-api`를 추가한다. Swagger UI
  번들(`-ui`)은 기본으로 서빙하지 않는다. 공개 UI 자체가 공격면이고, 운영 노출
  여부는 인증과 함께 별도로 결정할 사안이다.
- Spring Boot 3.5.16과 호환되는 springdoc 버전을 확인해 명시적으로 고정한다.
  Spring Boot BOM이 springdoc을 관리하지 않는다.
- `OpenApiConfiguration`에 title, version, security scheme을 선언한다.
  `/admin`은 세션 쿠키, `/api/**`는 bearer JWT다.
- `ApiResponse<T>` 성공 래퍼와 `ApiErrorResponse` 오류 포맷이 스펙 스키마에
  반영되게 한다. 제네릭 래퍼는 springdoc이 그대로 풀지 못할 수 있으므로 확인이
  필요하다.
- 스펙을 `docs/api/openapi.json`으로 생성한다. 통합 테스트가 `/v3/api-docs`를
  호출해 파일로 쓰는 방식을 우선 검토한다. CI가 이미 Testcontainers를 실행하므로
  별도 인프라가 필요 없다.
- springdoc 경로가 `SecurityConfiguration`의 fallback `denyAll` 체인에 막히므로
  접근 규칙을 정한다.

### CI 자동 동기화

목표는 개발자가 스킬이나 재생성 명령을 직접 호출하지 않는 것이다. 검증이 아니라
자동 커밋으로 간다.

- `harness-policy.yml`에 `sync-api-docs` job을 추가한다. PR 이벤트에서 스펙을
  재생성하고, 달라졌으면 PR 브랜치에 커밋해 push한다.
- 커밋 메시지는 브랜치에서 타입과 이슈 번호를 뽑아 조립한다
  (`feat/gh-73-...` → `feat(docs): sync the openapi specification (#73)`).
  husky 훅이 CI에서 돌지 않으므로 job이 `scripts/validate-conventions.py`로
  커밋 직전에 직접 검증한다.
- 권한은 job 단위로만 `contents: write`를 준다. `policy`와 `test`는 read를 유지한다.
- fork PR은 `GITHUB_TOKEN`이 읽기 전용이라 push할 수 없다. 이 경우에만 실패시킨다.
- `.github/workflows/**` 변경은 `scripts/validate-workflows.py`를 통과해야 한다.

### 공통 규칙 커스터마이저

PR마다 LLM을 돌리지 않고도 대부분의 격차를 없애기 위해, 어느 엔드포인트에서나
참인 규칙은 `OpenApiConventionCustomizer` 빈 하나가 주입한다. 새 컨트롤러도
자동으로 적용받는다.

- 모든 응답 content type을 `application/json`으로 좁힌다.
- 모든 operation에 `400`, `500`을 넣는다(`GlobalExceptionHandler` 근거).
- `ApiErrorResponse` 스키마를 등록한다.
- 엔드포인트마다 다른 정보는 넣지 않는다. 추측하면 사실과 다른 문서가 된다.

### 에이전트와 스킬

- `agents/api-docs-executor.md` — 역할 계약. 읽기 권한과 쓰기 범위를 명시한다.
- `.claude/agents/api-docs-executor.md` — 에이전트 정의.
- `.claude/skills/harness-api-docs/SKILL.md` — 추출된 스펙과 컨트롤러를 읽어
  `@Operation`, `@Schema`, `@ApiResponse` 보강안을 제시하고 승인 후 적용한다.
- 기존 하네스 스킬(`harness-commit`, `harness-pr`)과 같은 구조를 따른다. 승인
  없이 파일을 고치지 않는다.

### 문서

- 산출물 경로, 생성 명령, 갱신 절차를 `docs/api-response.md`에 기록한다. 5절
  "아직 정하지 않은 것"의 OpenAPI 항목을 해소한다.

## Explicit exclusions

- **앱 REST 컨트롤러 구현.** answer(4), direction(3), feed(2), question(2),
  safety(1)의 12개 서비스에 컨트롤러가 없으나 이번 이슈에서 만들지 않는다.
  도메인별 feature 이슈로 분리하고 PR #81의 `/api/**` 인증 체인 머지 이후
  진행한다.
- Swagger UI를 운영 환경에 서빙하는 것.
- 문서 외부 포털 게시.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| springdoc 의존성과 설정 | API docs executor | 버전 호환·제네릭 스키마 리뷰 |
| 스펙 생성 경로와 산출물 | API docs executor | 결정성·재현성 리뷰 |
| Security 경로 규칙 | API docs executor | 운영 노출 범위 리뷰 |
| CI 검증 job | API docs executor | 실패 조건·실행 시간 리뷰 |
| 에이전트와 스킬 | API docs executor | 권한 범위·승인 게이트 리뷰 |

## Existing user-owned changes

- 세션 시작 시 작업 트리는 `M TASK.md` 하나였고, 그 내용은 `h task-init`이 생성한
  이 파일의 스캐폴드다. 다른 사람의 미커밋 변경은 없다.
- 이 브랜치는 `./harness start`가 최신 `origin/main`(`7c8ea8c`)에서 새로 만들었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

- Docker가 사용 가능하므로 Testcontainers 기반 통합 테스트를 로컬에서 실행한다.
- 스펙 생성이 결정적인지 확인한다. 두 번 실행해 `docs/api/openapi.json`이 동일해야
  CI 검증이 거짓 실패를 내지 않는다.

## Completion criteria

- [x] `docs/api/openapi.json`이 현재 컨트롤러 기준으로 생성된다. `/admin/csrf`,
      `/admin/login`, `/admin/logout` 3개 경로가 나온다.
- [x] 생성된 스펙에 `ApiResponse<T>` 성공 포맷이 반영된다. springdoc이 제네릭을
      타입 인자별로 풀어 `ApiResponseOperatorSessionResponse` 등으로 만든다.
- [x] 같은 커밋에서 두 번 생성해도 산출물이 바이트 단위로 동일하다. 별도 JVM
      실행 간에도 sha256이 같음을 확인했다.
- [x] 컨트롤러를 바꾸면 CI가 스펙을 재생성해 PR 브랜치에 커밋한다. 봇 커밋
      메시지가 저장소 규칙을 통과하는 것을 브랜치 형태별로 확인했다.
      워크플로 자체의 실제 실행은 PR을 올려야 검증된다.
- [x] 공통 규칙이 커스터마이저로 주입된다. 모든 operation에 `400`·`500`이 붙고
      content type이 `application/json`으로 좁혀지며 `CsrfToken` 누출이 사라졌다.
- [x] `agents/api-docs-executor.md`와 `.claude/agents/api-docs-executor.md`에
      읽기·쓰기 범위가 명시되어 있다.
- [x] 평문 비밀번호, `device_secret`, 토큰이 스펙 예시 값으로 노출되지 않는다.
      스펙에 example 값이 없고 테스트가 금지 문자열을 검사한다.
- [x] springdoc 엔드포인트의 운영 환경 노출 여부가 명시적으로 결정되어 있다.
      기본 `enabled=false`로 운영에는 라우트가 생기지 않는다. UI 번들은 제외했다.
- [x] 산출물 경로와 갱신 절차가 `docs/api-response.md` 5절에 기록된다.
- [x] `./gradlew test`와 `./gradlew integrationTest`가 통과한다.
- [ ] `.claude/skills/harness-api-docs/SKILL.md` 호출로 스펙과 보강 문서가
      갱신된다. 스킬은 작성했으나 실제 호출로 보강까지 수행하지는 않았다.
      현재 컨트롤러 3개는 모두 `/admin` 백오피스 경로이고 앱 REST 컨트롤러가
      아직 없어 보강 대상이 적다.
- [x] `./harness pr-ready --project-tests`가 통과한다.

## 알려진 보강 대상

생성된 스펙에서 확인한 격차 중 이번 이슈에서 해소한 것과 남은 것이다.

해소함(커스터마이저와 애노테이션):

- content type `*/*` → `application/json`
- 모든 operation에 `400`, `500` 오류 응답
- `CsrfToken` 누출 → `@Parameter(hidden = true)`

남음(엔드포인트별 지식이 필요해 `/harness-api-docs`가 다룬다):

- 엔드포인트별 오류 응답. 로그인의 `401`·`403`·`423`이 아직 없다.
- operation `summary`와 `description`이 비어 있다.
- 경로별 `security` 선언이 없다.
