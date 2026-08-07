# GitHub Issue #74 Task Contract

> Generated at: `2026-08-07T09:12:26+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `API 성공 응답 공통 포맷과 전역 적용`
- GitHub Issue: `#74`
- Branch: `feat/gh-74-api-response-contract`
- Base branch: `main`

## Objective

- 오류 응답만 `ApiErrorResponse`로 고정돼 있고 성공 응답에는 계약이 없다.
  컨트롤러가 아직 하나도 없는 지금 성공 응답 형식을 고정해, 이어질 모든
  엔드포인트가 처음부터 같은 계약을 상속하게 한다.

## Scope

- `ApiResponse<T>` 신설 — `status`, `data`, `timestamp`. 오류 응답과 필드 구성을
  대칭으로 맞춘다.
- 적용 방식 결정 — 컨트롤러 명시 래핑 대 `ResponseBodyAdvice` 전역 자동 래핑을
  비교해 하나를 고르고 근거를 문서에 남긴다. 자동 래핑 검토 시
  `String` 반환의 `StringHttpMessageConverter` 충돌, actuator·springdoc 응답 오염,
  이미 `ApiErrorResponse`인 오류 경로 제외를 함께 다룬다.
- 본문 없는 응답 규칙 — 201 Created, 204 No Content 처리 방식을 정한다.
- `timestamp`를 `Clock` 빈에서 얻도록 `ApiErrorResponse`를 함께 정리한다.
  현재 `Instant.now()` 직접 호출이라 테스트에서 고정할 수 없다.
  `JpaAuditingConfiguration`이 제공하는 기존 `Clock` 빈을 사용한다.
- `docs/error-codes.md` 1절 확장 또는 API 응답 계약 문서 분리 — 성공·오류 양쪽
  형식을 한곳에서 볼 수 있게 한다.

## Explicit exclusions

- 페이지네이션 응답 형식 — 목록 API가 생길 때 별도 결정
- 오류 코드 체계 변경
- API 문서화 도구(OpenAPI) 도입
- 기존 엔드포인트 마이그레이션 — 대상이 아직 없다
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| TODO | TODO | TODO |

## Existing user-owned changes

- 브랜치 생성 시점 `git status --short`는 비어 있다.
- `stash@{0}` — `docs/adr/0003-split-operator-and-device-authentication.md`,
  `docs/product/AUTH_DESIGN.md`. gh-48 브랜치에서 남은 미추적 초안이며 #74와
  무관하다. 이 브랜치에서 커밋하지 않는다. ADR 번호 0003은 main의
  `0003-global-exception-handling.md`와 충돌하므로 별도 작업에서 재번호가 필요하다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 성공 응답이 `status`, `data`, `timestamp`를 갖고 오류 응답과 필드가 대칭이다.
- [ ] 적용 방식(명시 래핑 대 자동 래핑) 결정과 근거가 문서에 남는다.
- [ ] 본문 없는 응답(201·204)의 규칙이 정해지고 테스트로 고정된다.
- [ ] 성공·오류 응답의 `timestamp`가 모두 `Clock` 빈에서 나오고, 테스트가 시각을
      고정할 수 있다.
- [ ] 자동 래핑을 택한 경우, 래핑에서 제외되는 경로가 테스트로 고정된다.
- [ ] `data`가 null인 경우의 직렬화 결과가 정해진다.
