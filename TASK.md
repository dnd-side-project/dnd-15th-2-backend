# GitHub Issue #189 Task Contract

> Generated at: `2026-08-23T02:45:06+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Answer/Media·Direction/Post Reaction 도메인 OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-answer-direction-api-description`
- Base branch: `main`

## 결정 게이트에 대한 중요한 주의

- `#189`의 담당 도메인 중 남은 두 그룹의 설명을
  `docs/api/OPENAPI_WRITING_GUIDE.md` 기준으로 검토·개선한다.
  - Answer/Media: `AnswerSubmissionApiSpec`, `AnswerReactionApiSpec`,
    `MediaAssetApiSpec` (5개 엔드포인트)
  - Direction/Post Reaction: `DirectionPostApiSpec`, `PostReactionApiSpec`,
    `ActiveUserPresenceApiSpec` (5개 엔드포인트)
- Feed와 Notification은 각각 `docs/gh-189-feed-api-description`,
  `docs/gh-189-notification-api-description`에서 완료했다.

### 두 그룹을 한 브랜치에서 다루는 이유

`#189`는 도메인별 분리를 권하지만 두 그룹 모두 담당자가 같고, 공감 응답
(`ReactionResponse`)을 네 경로가 공유해 함께 봐야 사실 대조가 정확하다.
검증(`./harness pr-ready --project-tests`)도 한 번으로 끝난다. 커밋을 그룹별로
나눠두므로 필요하면 나중에 브랜치를 쪼갤 수 있다.

## Scope

1. `/harness-api-docs` review 모드로 6개 파일 전체를 6점 대조하고 보고서 2개를
   만든다: `docs/reports/gh-189-API-DOCS-REVIEW-ANSWER-MEDIA.md`,
   `docs/reports/gh-189-API-DOCS-REVIEW-DIRECTION.md`.
2. 제안을 검토해 승인한 문장만 `*ApiSpec`과 응답 DTO에 직접 반영한다.
3. 반영 후 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`로
   `docs/api/openapi.json` 재생성을 확인한다(최종 동기화는 CI `sync-api-docs`).
4. "API 계약이 이상하다"고 판단되는 항목은 이 브랜치에서 고치지 않고 기록만 남긴다.

## Explicit exclusions

- Feed, Notification 도메인. 각각 별도 브랜치에서 완료.
- `tkv00` 담당 도메인(Account/Profile, Auth, Question, Filtering/Appeal,
  Safety/Report).
- API 동작·비즈니스 로직 변경. 문서가 코드와 다르면 문서를 코드에 맞춘다.
- API 계약 변경(오류 코드·상태 코드 변경 등). 별도 이슈로 분리한다.
- GitHub Pages 정적 문서 제공. `#189`에서 보류 처리됨.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Answer/Media `*ApiSpec` 문장 | Byuntil | 6점 대조 결과와 문장 기준 일치 |
| Direction/Post Reaction `*ApiSpec` 문장 | Byuntil | 6점 대조 결과와 문장 기준 일치 |
| 검토 산출물 (`docs/reports/gh-189-API-DOCS-REVIEW-*.md`) | Byuntil | PR에는 요약만, 커밋 여부는 담당자 판단 |

## Existing user-owned changes

- 브랜치 생성 시 `git status --short`는 clean이었다. 범위 밖 변경 없음.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.safety.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*ReportCase*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*ReportContentSnapshot*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*Purge*" --console=plain
./gradlew integrationTest --tests "*Flyway*" --console=plain
./harness test-run --id <TEST-PLAN-ID>
./harness check
./harness pr-ready --project-tests
git diff --check
```

### Validation evidence (2026-08-23)

- `./gradlew compileJava`: 통과.
- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`:
  반영 전 기준선(diff 없음)과 반영 후 두 번 실행, 모두 통과. 반영 후
  `docs/api/openapi.json` diff 100줄(73 추가/27 삭제).
- 산출물 재파싱 검증:
  - 대상 DTO 필드 29개(Answer/Media 11, Direction 응답 13·요청 5) 중 무설명 **0개**.
  - `GET /api/v1/direction/preview` 응답 코드가 `200,400,401,409,500` →
    `200,400,401,403,404,409,500`으로 바뀐 것을 확인.
  - `PUT /api/v1/direction/presence`의 400 설명이 커스터마이저 기본 문구에서
    도메인 문구로 바뀐 것을 확인.
- 변경 범위 검증: 산출물 diff의 변경 키 57개가 **전부** Answer/Media·Direction
  범위 안이었다(대상 밖 0개). 다른 도메인 영향 없음.
- `./harness pr-ready --project-tests`: **통과.** `BUILD SUCCESSFUL in 5m 40s`,
  종료 코드 0. `Harness checks passed`·`Local PR readiness checks passed`.
  `:test`·`:integrationTest`·`:check` 전부 통과.
- 미실행 범위 없음.

## Completion criteria

- [x] 6개 `*ApiSpec`의 10개 엔드포인트 전체가 6점 대조를 거쳤다.
      보고서 2개(`docs/reports/gh-189-API-DOCS-REVIEW-{ANSWER-MEDIA,DIRECTION}.md`)에 기록.
- [x] 승인된 문장 개선이 반영됐고 DTO에 없는 사실을 지어내지 않았다. 반영 내역:
      - **누락된 오류 응답 2건 추가**: `GET /direction/preview`의 403(`DIR-APP-007`)과
        404(`DIR-APP-006`, `DIR-DOM-006`). `submit`과 같은 서비스 경로를 쓰면서
        `preview`만 빠뜨렸던 실제 결함.
      - `@Operation` `summary`·`description` 7건 재작성(`react`·`cancel` 4건은
        이미 기준을 만족해 그대로 둠)
      - `@ApiResponse` 오류 코드 표기 13건 추가·보강
      - `@Schema(description)` 26건 추가(응답 24, 요청 2)
      - **§1 종결어미 위반 3문장 수정**(`ActiveUserPresenceApiSpec`, 24개 `*ApiSpec`
        중 유일하게 `description` 전체가 한다체였음)
- [x] `docs/api/openapi.json`이 최신 코드에서 재생성됐다(로컬 재생성 확인,
      최종 동기화는 PR 시 CI `sync-api-docs` job).
- [x] 계약이 이상해 보이는 항목은 코드를 고치지 않고 별도로 기록했다.
      `AnswerReactionService`가 "없는 답변"에 404 대신 400(`ANS-VAL-001`)을 낸다.
      `ANSWER_NOT_FOUND`(ANS-DOM-012)·`RECIPIENT_NOT_FOUND`(ANS-DOM-011)가 이미
      정의돼 있는데도 쓰지 않는다. 상태 코드 변경은 이 브랜치의 제외 범위이므로
      문서만 코드에 맞추고 후속 선택지를 보고서 §4.1에 남겼다.
- [x] 완료 전 검증을 모두 실행하고 실패·미실행 범위를 구분해 기록했다.
      위 Validation evidence 참고. 미실행 범위 없음.

## 후속으로 남긴 항목

1. `AnswerReactionService`의 400/404 계약 정리 (보고서 ANSWER-MEDIA §4.1).
2. `docs/api/OPENAPI_WRITING_GUIDE.md` §8 예시가 저장소 코드와 어긋나는 건
   (Notification 브랜치의 보고서 §4.2에 기록). 가이드는 `#191` 소유.
3. `docs/error-codes.md` §13의 `FeedErrorCode` 7개 누락 (Feed 브랜치 보고서 §4).
