# GitHub Issue #189 Task Contract

> Generated at: `2026-08-23T01:09:51+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Feed 도메인 OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-feed-api-description`
- Base branch: `main`

## Objective

- `#189`의 담당 도메인 중 Feed(`AnswerReadApiSpec`, `InboxApiSpec`, `SentPostApiSpec`,
  9개 엔드포인트) 설명을 `docs/api/OPENAPI_WRITING_GUIDE.md` 기준으로 검토·개선한다.
- 이 브랜치는 Feed 도메인만 다룬다. Answer/Media·Direction/Post Reaction·
  Notification은 별도 브랜치·PR로 진행한다.

## Scope

1. `/harness-api-docs` review 모드로 Feed 3개 파일 전체를 6점 대조하고
   `docs/reports/gh-189-API-DOCS-REVIEW-FEED.md`를 만든다.
2. 제안을 검토해 승인한 문장만 `*ApiSpec` 3개 파일에 직접 반영한다.
3. 반영 후 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`로
   `docs/api/openapi.json` 재생성 여부를 로컬에서 확인한다(최종 동기화는 CI의
   `sync-api-docs` job이 담당).
4. 6점 대조 중 "API 계약이 이상하다"고 판단되는 항목(예: 사전 조사에서 발견한
   `AnswerReactionService`의 400/404 처리)은 이 브랜치에서 고치지 않고 별도
   GitHub Issue로 분리해 기록만 남긴다.

## Explicit exclusions

- Answer/Media, Direction/Post Reaction, Notification 도메인 설명 개선. 각각
  별도 브랜치.
- API 동작·비즈니스 로직 변경. 문서가 코드와 다르면 문서를 코드에 맞춘다.
- API 계약 변경(오류 코드·상태 코드 변경 등)은 이 브랜치에서 하지 않고 별도
  이슈로 분리한다.
- GitHub Pages 정적 문서 제공. `#189`에서 보류 처리됨(저장소 admin 권한 없음).
  해제되면 별도 이슈로 진행한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Feed `*ApiSpec` 문장 (`docs/api/OPENAPI_WRITING_GUIDE.md` 기준) | Byuntil | 6점 대조 결과와 문장 기준 일치 |
| 검토 산출물 (`docs/reports/gh-189-API-DOCS-REVIEW-FEED.md`) | Byuntil | 임시 산출물, PR에는 요약만 남기고 커밋 여부는 도메인 담당자 판단 |

## Existing user-owned changes

- 브랜치 생성 시 `git status --short`는 clean이었다. 범위 밖 변경 없음.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

### Validation evidence (2026-08-23)

- `./gradlew compileJava`: 통과.
- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`:
  통과. 반영 전후 `docs/api/openapi.json` diff 295줄(183 추가/112 삭제) 확인 —
  9개 엔드포인트의 404·parameter·schema 변경이 예상대로 반영됨.
- `./harness pr-ready --project-tests`: **통과.** `BUILD SUCCESSFUL in 5m 10s`.
  secret preflight(1156개 파일)·JUnit 정책(222개 파일)·convention·workflow·
  label·husky 검증 전부 통과, 단위 테스트(`:test`)·통합 테스트
  (`:integrationTest`)·`:check` 전부 통과.
- 미실행 범위 없음.

## Completion criteria

- [x] Feed 3개 `*ApiSpec` 전체가 6점 대조를 거쳤다.
      `docs/reports/gh-189-API-DOCS-REVIEW-FEED.md`에 기록.
- [x] 승인된 문장 개선이 `*ApiSpec`에 반영됐고 DTO에 없는 사실을 지어내지
      않았다. 반영 내역:
      - `@Tag` 종결어미 위반 1건 수정(`AnswerReadApiSpec`, 한다체→합니다체)
      - 404 응답 6건에 `FED-APP-001`(계정 게이트발) 추가
      - query parameter `@Parameter(description)` 8건 추가
      - 응답 DTO 8개 전 필드(약 50개) `@Schema(description)` 추가
      - 스키마 이름 충돌 위험 4건 수정(`@Schema(name)`으로 `InboxCard`·
        `DirectionChip`·`AnswerCard`·`SentPostCursor` 지정)
- [x] `docs/api/openapi.json`이 최신 코드에서 재생성됐다(로컬 재생성 확인,
      최종 동기화는 PR 시 CI `sync-api-docs` job).
- [x] 계약이 이상해 보이는 항목은 코드를 고치지 않고 별도 이슈로 분리했다.
      `docs/error-codes.md`의 `FeedErrorCode` 7개 누락은 `*ApiSpec` 밖이라
      이번 반영에서 제외하고 보고서 §4에 남김(별도 후속 필요).
- [x] 완료 전 검증을 모두 실행하고 실패·미실행 범위를 구분해 기록했다.
      아래 Validation evidence 참고. 미실행 범위 없음.
