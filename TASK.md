# GitHub Issue #170 Task Contract

> Generated at: `2026-08-19T01:55:36+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `피드 읽기·상호작용 API 노출`
- GitHub Issue: `#170`
- Branch: `feat/gh-170-feed-read-interaction-api`
- Base branch: `main`
- 선행 이슈 `#67`, `#124`, `#125`, `#55`, `#79` 전부 CLOSED 확인. 차단 요소 없음.

## Objective

- `내가 보낸 질문`, 답변 목록, 공감, 답변 읽음 처리는 service·repository 계층이
  `#67`, `#55`, `#79`에서 이미 구현·검증됐지만 HTTP로 노출된 적이 없다. `openapi.json`의
  앱 엔드포인트 13개 중 방향 글을 읽는 경로는 수신함(`/inbox`)뿐이다.
- 그 결과 디자인 화면 5장 중 3장(`내가 보낸 질문` 목록, 수신 질문 상세의 답변 목록과
  하트, `새로운 답변 N개` 배지)이 호출할 API가 없다.
- 이미 있는 service를 web 계층으로 올려 읽기·상호작용 경로를 닫는다.

## Scope

1. **`내가 보낸 질문` 조회** — `feed.web.SentPostController`와 `SentPostApiSpec` 신규.
   - `GET /api/v1/direction/posts` — `filter`(ALL·IN_PROGRESS·EXPIRED),
     `cursorSubmittedAt`·`cursorPostId`, `limit`. `SentPostQueryService.list` 배선.
   - `GET /api/v1/direction/posts/{postId}` — `SentPostQueryService.detail` 배선.
     남의 질문글이면 404.
2. **답변 목록 조회**
   - `GET /api/v1/direction/posts/{postId}/answers` — `cursorPublishedAt`·
     `cursorAnswerId`, `limit`. `PostAnswerQueryService` 배선. 질문글 작성자와 그
     질문글의 수신 자격자만 내용을 받는다.
3. **답변 읽음 처리**(`새로운 답변 N개` 배지 해제)
   - `PUT /api/v1/direction/posts/{postId}/answers/read` —
     `DirectionPostService.markAnswersRead` 배선(질문자용).
   - `PUT /api/v1/direction/inbox/{postRecipientId}/answers/read` —
     `PostRecipientService.markAnswersRead` 배선(수신자용).
4. **공감**
   - `PUT`·`DELETE /api/v1/direction/posts/{postId}/reaction` — 질문글 공감.
   - `PUT`·`DELETE /api/v1/direction/answers/{answerId}/reaction` — 답변 공감.
   - `PostReactionService`와 `AnswerReactionService`의 `toggle`을 `react`/`cancel`로
     분해하고, 기존 `toggle`은 이 둘을 조합해 남긴다(기존 통합 테스트 계약 보존).
   - `AnswerReactionRepository.countByAnswerId` 추가. `PostReactionRepository`는
     `countByPostId`가 이미 있다.
5. **`reactedByMe` 보강** — `InboxCard`와 `InboxQuerySql.SELECT_CARD`에 뷰어 본인의
   질문글 공감 여부를 추가한다. `AnswerCard`에는 이미 있다.
6. 새 경로에도 `InboxApplicationService.requireEligibleAccount`와 같은 계정 자격
   게이트를 적용한다.
7. 단위 테스트, PostgreSQL 통합 테스트, 테스트 계획·보고서, `openapi.json` 재생성.

## Design decisions (구현 전 확정, 리뷰 필요)

1. **공감은 toggle 단일 호출이 아니라 idempotent `PUT`/`DELETE` 쌍으로 노출한다.**
   `toggle`은 호출 한 번이 상태를 뒤집으므로 같은 요청이 두 번 도착하면 결과가
   반대가 된다. 누르기(`PUT`)와 취소(`DELETE`)로 나누면 각 요청이 몇 번 도착해도
   최종 상태가 하나로 정해진다. `/inbox/{id}/skip`이 이미 같은 쌍이다. 또한
   `AnswerReactionRepository.react`가 "같은 transaction 안에서 `cancel` 직후 같은
   key로 `react`는 안전하지 않다"고 명시하는데, 요청을 나누면 그 제약을 구조적으로
   피한다.
2. **cursor는 불투명 토큰이 아니라 명시적 두 파라미터로 받는다.** 정렬 키
   (`submittedAt`+`postId`, `publishedAt`+`answerId`)가 이미 응답에 공개된 값이라
   숨길 이유가 없고, 인코딩·검증 계층을 새로 만들지 않는다.
3. **`reactedByMe`는 `InboxCard`에만 추가한다.** 발송자는 자기 질문글의 수신자가
   아니므로 `PostReactionService`가 공감을 거부한다 — `SentPostCard`에서는 항상
   false여서 실을 의미가 없다.
4. **읽음 처리는 `PUT`이다.** 두 repository의 `advanceAnswersReadAt`이
   `GREATEST(현재값, at)`로만 전진하므로 반복 호출과 순서 역전이 읽음 지점을 과거로
   되돌리지 않는다.
5. **답변 목록의 자격 없는 뷰어는 403이 아니라 빈 목록을 받는다.**
   `PostAnswerQueryRepository.findAnswers`의 기존 계약(질문글 존재 여부 비노출)을
   web 계층까지 그대로 올린다.
6. **`limit` 기본 20, 상한 50.** `nextCursor`는 반환 건수가 `limit`과 같을 때만
   채우고 그보다 적으면 `null`이다.
7. **7개 엔드포인트의 application 계층은 전부 `feed.service`에 둔다.** 계정 자격
   게이트(`requireEligibleAccount`)가 한 곳에만 있어야 갈라지지 않는다. controller는
   `feed.web`, `direction.web`, `answer.web`에 각각 두되 application service는 feed가
   소유하고, 403·404는 기존 `FED-APP-002`·`FED-APP-001`을 그대로 쓴다. feed가
   `PostRecipientService`를 이미 참조하므로 참조 방향이 새로 생기지 않는다.
8. **공감 취소(`DELETE`)는 공감 자격을 검사하지 않고 본인 행만 삭제한다.** 질문글
   만료나 넘김 확정으로 자격을 잃은 뒤에도 자기가 남긴 공감은 거둘 수 있어야 한다.
   삭제 조건이 `(postId, reactorId)`·`(answerId, reactorId)`라 남의 공감에는 닿지
   않고, DB의 FK와 constraint trigger는 삽입만 막으므로 안전하다. 공감
   누르기(`PUT`)에는 기존 자격 검사를 그대로 유지한다.
9. **구현 전에 `/harness-test-plan`으로 테스트 계획을 먼저 확정한다.** 테스트 클래스
   헤더의 `Source scenario`가 참조할 시나리오 식별자가 계획에서 나온다.

## Explicit exclusions

- 알림 목록 조회 API. `notification`은 도메인·repository·fan-out 워커까지만 있고
  service·web 계층이 없어 "이미 있는 service를 노출한다"는 이 이슈의 성격과 다르다.
- 답변 수정·삭제 API. `Answer.delete`는 도메인에 있지만 service·정책이 없다.
- 신고·차단 진입점(`#154`~`#157`이 담당).
- 닉네임·프로필 이미지(`#168`, `#166`이 담당).
- 지도 활동 마커 집계.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `feed.web`·`direction.web`·`answer.web` 신규 엔드포인트, reaction service 분해, `InboxQuerySql` 보강, 테스트 | Feature executor | 자격 없는 사용자가 남의 질문글·답변·공감에 접근할 경로가 없는지, 공감 `PUT`/`DELETE`와 읽음 처리가 반복·역순 호출에서 최종 상태를 흔들지 않는지, 응답에 정확 위치와 내부 사용자 식별자가 실리지 않는지 |

## Existing user-owned changes

- `origin/main`(e7086dc)에서 새로 분기했다. 분기 시점 `git status --short`는 비어
  있었다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.account.*" --console=plain
./gradlew test --tests "com.dnd.qello.filtering.moderation.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.NicknameDuplicateModerationIntegrationTest" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
./harness test-run --id TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 경로 7개, operation 9개(`GET` 3, `PUT` 4, `DELETE` 2)가 동작하고
      `docs/api/openapi.json`에 반영된다. (원본 이슈의 `PUT 3` 집계는 수신자 읽음
      처리 경로 하나가 빠진 오타였다 — 실제로는 질문자·수신자 읽음 처리 2개와
      공감 `PUT` 2개로 4개다.)
- [ ] 자격 없는 사용자가 남의 `내가 보낸 질문`, 답변 목록, 공감 경로로 데이터를
      얻지 못한다(통합 테스트로 확인).
- [ ] 공감 `PUT`·`DELETE`를 반복 호출해도 최종 상태와 공감 수가 달라지지 않는다.
- [ ] 답변 읽음 처리를 반복·역순으로 호출해도 `answers_read_at`이 뒤로 가지 않는다.
- [ ] `reactedByMe`가 수신함 목록·상세에 뷰어 기준으로 정확히 실린다.
- [ ] 정확 위치와 내부 사용자 식별자를 응답에 싣지 않는다(기존 수신함 규칙 유지).
- [ ] 단위·통합 테스트가 통과하고 `@DisplayName`·클래스 헤더 규칙을 충족한다.
- [ ] `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.
