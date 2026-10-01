# GitHub Issue #296 Task Contract

> Generated at: `2026-10-01T14:20:23+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `답변 목록 API Swagger 위치·설명 정정`
- GitHub Issue: `#296`
- Branch: `docs/gh-296-answer-listing-api-docs`
- Base branch: `main`

## Objective

- `GET /api/v1/direction/posts/{postId}/answers`가 `SentPostApiSpec`("내가 보낸 질문" 태그)에 있어 질문자 전용으로 읽힌다. 실제 조회 자격(질문자와 수신 자격을 유지한 수신자)에 맞게 OpenAPI 위치와 설명을 바로잡는다. 경로와 동작은 바꾸지 않는다.

## Scope

- `feed/web`의 answers 엔드포인트를 `SentPostApiSpec`·`SentPostController`에서 분리해 별도 ApiSpec·Controller와 "답변 목록" Tag로 옮긴다.
- operation description에 보낸 질문·받은 질문 공용 사용, 앱 로그인 요구, cursor 짝 지정 규칙을 적는다. 빈 목록 정책은 기존 200 응답 설명에 남긴다.
- `SentPostApiSpec` Tag description에서 답변 목록 문구를 뺀다.
- `docs/api/openapi.json`을 재생성한다.
- 컨트롤러 분리로 깨지는 기존 단위 테스트의 answers 단언을 새 클래스로 이전한다(아래 결정 D1).

## Explicit exclusions

- `GET /inbox/{postRecipientId}/answers` 같은 별칭 엔드포인트 추가
- 답변 조회 자격 SQL과 빈 목록 정책 변경
- 다른 ApiSpec의 경로 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| answers ApiSpec·Controller 분리, openapi.json 재생성 | 실행 에이전트 | 사용자 PR 리뷰 |
| 기존 answers 단위 테스트 단언 이전 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했고 `TASK.md`만 `task-init`으로 수정되었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 재생성한 `openapi.json`에서 answers operation의 tags가 "내가 보낸 질문"이 아니다.
- operation description에 보낸 질문·받은 질문 공용 사용, 앱 로그인 요구, cursor 짝 지정 규칙이 들어 있고 200 응답 설명에 빈 목록 정책이 남아 있다.
- `openapi.json` diff가 tag·description 변경으로만 이루어진다.
- 위 Validation 명령이 전부 통과한다.

## Decisions

- D1 (2026-10-01, 사용자 승인): 새 테스트 시나리오를 추가하지 않고 기존 단언만 이전한다. `/harness-test-plan` 정식 절차는 생략한다.
  - `FeedInteractionWebContractTest`: answers 경로 단언을 `PostAnswerApiSpec` 기준으로 바꾸고 `assertBoundary`에 `PostAnswerApiSpec`·`PostAnswerController` 쌍을 추가한다.
  - `SentPostApiMockMvcTest`의 답변 목록 200 시나리오와 401 단언을 `PostAnswerApiMockMvcTest`로 옮긴다. 원본 시나리오 ID는 `TEST-PLAN-GH-170-FEED-READ-INTERACTION-API-UNIT-013`, `UNIT-014`다.
- D2 (2026-10-01, 사용자 결정): 사용자가 `PostAnswerApiSpec` description을 직접 줄여 조회 자격·만료 규칙·inbox 카드 `postId` 사용 문장을 뺐다. 범위와 완료 조건을 이 결정에 맞췄다. 이 내용은 Swagger operation description에 나오지 않는다.

## 실행 증거 (2026-10-01)

- 작업 전 `OpenApiSpecificationIntegrationTest` 재생성: `docs/api/openapi.json` diff 없음.
- 작업 후 재생성 diff: answers operation의 `summary`·`description`·`tags`와 최상위 tag 목록(설명 변경, "답변 목록" 추가, 순서 이동)뿐이다. paths·parameters·schemas는 바뀌지 않았다.
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과(D2 이후 재실행 포함).
- D2 이후 `spotlessApply`로 `PostAnswerApiSpec` 들여쓰기를 탭으로 되돌렸고 `spotlessJavaCheck` 통과. 스펙 재생성 결과는 줄어든 description만 반영됐다.
- 단위 1,081개 / 통합 747개, 실패·오류·skip 0.
- 변경한 테스트 파일 두 개의 나머지 diff는 `spotlessApply`의 포맷(import 순서, 줄바꿈)이다. ratchet 규칙상 변경 파일 전체가 포맷 대상이다.
