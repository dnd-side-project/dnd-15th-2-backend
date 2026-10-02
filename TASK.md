# GitHub Issue #300 Task Contract

> Generated at: `2026-10-02T16:19:48+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `피드 목록 응답의 첨부 이미지 조회 URL 제공`
- GitHub Issue: `#300`
- Branch: `feat/gh-300-feed-media-view-url`
- Base branch: `main`

## Objective

- 받은 질문·보낸 질문·답변 목록 응답의 `mediaIds`를 제거하고, 클라이언트가 바로 렌더링할 수 있는 presigned GET URL을 담은 `media: [{ mediaId, url, expiresAt }]`로 교체한다. 방식은 `ProfileImageResolver`와 같다. 받은 질문·보낸 질문 상세 응답은 목록 카드 타입을 재사용하므로 함께 바뀐다(D4).

## Scope

- `InboxQuerySql`, `SentPostQuerySql`, `PostAnswerQuerySql`의 미디어 서브쿼리가 `media_asset`을 조인해 `storage_key`를 읽고 `status = 'READY'`인 자산만 `display_order` 순서로 남긴다.
- feed 서비스 계층에서 `ObjectStoragePort.issueGetUrl(storageKey, viewUrlTtl)`로 URL과 만료 시각을 발급한다.
- `InboxCard`, `SentPostCard`, `AnswerCard`와 `InboxListingResponse`, `SentPostListingResponse`, `AnswerListingResponse`의 `mediaIds`를 `media`로 교체한다. 호환 기간 없이 제거한다. 이 카드를 재사용하는 `InboxDetailResponse`, `SentPostDetailResponse`에도 같은 변경이 적용된다.
- storage key와 버킷 이름은 view와 response에 넣지 않는다.
- response `@Schema` 설명에 URL 만료와 만료 시 목록 재조회를 적고 `docs/api/openapi.json`을 재생성한다.
- feed 단위·통합 테스트의 `mediaIds` 단언을 `media` 기준으로 이전한다.

## Explicit exclusions

- 만료된 URL만 다시 받는 별도 엔드포인트
- `view-url-ttl` 값 변경과 피드 전용 TTL 분리
- CloudFront 등 인프라 변경
- `SubmitDirectionPostRequest`, `SubmitAnswerRequest`의 `mediaIds` 입력 필드
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| feed SQL·view·response·서비스 변경, openapi.json 재생성 | 실행 에이전트 | 사용자 PR 리뷰 |
| feed 단위·통합 테스트 변경 | 실행 에이전트 | 사용자 PR 리뷰 |

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

- 세 목록 API 응답에 `mediaIds`가 없고 `media[].mediaId`, `media[].url`, `media[].expiresAt`가 있다(재생성한 `openapi.json`으로 확인).
- 받은 질문·보낸 질문 상세 응답의 `card`에도 `mediaIds`가 없고 `media`가 있다(단위·통합 테스트).
- READY가 아닌 자산은 `media`에 포함되지 않는다(통합 테스트).
- 응답 JSON에 storage key와 버킷 이름이 나오지 않는다(통합 테스트).
- `media` 순서가 `media_attachment.display_order`와 같다.
- 위 Validation 명령이 전부 통과한다.

## Decisions

- D1 (2026-10-02, 사용자 승인): `docs/test-plans/gh-300-TEST-PLAN-GH-300-FEED-MEDIA-VIEW-URL.md`를 승인했다.
- D2 (2026-10-02, 사용자 결정, test plan H1): presign 실패 시 목록 전체를 `STORAGE_UNAVAILABLE`(503)로 실패시킨다.
- D3 (2026-10-02, 사용자 결정, test plan H2): `media_asset.moderation_status` 필터는 이번 범위에서 제외한다. 이미지 검수 기능이 아직 없어 모든 자산이 `PENDING`이며, 검수 기능을 후속 작업으로 구현할 때 필터를 추가한다.
- D4 (2026-10-02, 사용자 결정): `InboxDetailResponse.card`와 `SentPostDetailResponse.card`가 목록 카드 타입을 재사용해 상세 응답에도 `mediaIds`가 있었고 이번 변경으로 `media`로 바뀐다. 상세 화면도 이미지를 표시해야 하므로 상세 2개를 범위에 넣는다. Issue #300 본문을 같이 정정했다. 최초 조사에서 상세 Response 파일만 확인해 이 재사용을 놓쳤다.
- D5 (2026-10-02, 사용자 승인): test plan Revision 2(상세 시나리오 UNIT-008·009, INT-009·010, INT-008 확장)를 승인했다.
