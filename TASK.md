# GitHub Issue #301 Task Contract

> Generated at: `2026-10-02T16:32:12+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `추천 질문 목록 조회 API(임시: 활성 질문 풀 반환)`
- GitHub Issue: `#301`
- Branch: `feat/gh-301-question-recommendation-listing`
- Base branch: `main`

## Objective

- 프론트엔드가 질문 전송 흐름을 테스트할 수 있도록 `GET /api/v1/questions/recommendations`를 추가한다. 사용자별 추천 배정이 미완성이므로 현재는 `findAssignableAt(now)`의 활성 질문 풀 전체를 반환하고, 경로·응답 형태는 최종 추천 API와 같게 둔다.

## Scope

- `question/web`에 추천 질문 조회 ApiSpec·Controller를 추가한다. 앱 로그인 사용자만 호출할 수 있다.
- `question/service`에서 `ApprovedQuestionRepository.findAssignableAt(now)`로 `status = ACTIVE`이고 활성 기간 안의 질문을 `id` 순으로 조회한다.
- 응답 항목은 `approvedQuestionId`, `questionText`, `answerFormat`인 객체 배열이다.
- operation description에 현재는 사용자 구분 없이 전체 활성 질문을 반환하고, 추천 배정 완성 후 사용자의 현재 cycle 배정 질문으로 바뀐다는 점을 적는다.
- `docs/api/openapi.json`을 재생성한다.
- 승인된 테스트 계획의 단위·통합 테스트를 추가한다.

## Explicit exclusions

- 사용자별 추천 배정 생성(cycle 생성 스케줄러, 질문 선택 로직)
- `question_assignment`의 `first_viewed_at`·`used_at` 갱신
- 질문 시드 데이터 마이그레이션과 운영자 질문 직접 생성 API
- 질문 전송 시 배정 여부 검증 추가
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| question web·service 추가, openapi.json 재생성 | 실행 에이전트 | 사용자 PR 리뷰 |
| 단위·통합 테스트 추가 | 실행 에이전트 | 테스트 계획 승인, 사용자 PR 리뷰 |

## Existing user-owned changes

- 새 worktree(`origin/main` 기준)에서 시작했고 `git status --short`는 `task-init`이 수정한 `TASK.md`뿐이다.
- 원래 체크아웃의 `feat/gh-300-feed-media-view-url` 작업은 별도 worktree에 있으며 이 작업에서 건드리지 않는다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 활성 질문, 비활성(`PENDING_REVIEW`·`INACTIVE`) 질문, 기간 밖 질문을 함께 둔 상태에서 응답에 활성 기간 안의 `ACTIVE` 질문만 포함된다(통합 테스트).
- 응답의 `approvedQuestionId`로 `POST /api/v1/direction/posts`를 호출했을 때 `QUESTION_NOT_ACTIVE`로 거부되지 않는다(통합 테스트).
- 비로그인 요청은 401을 반환한다(MockMvc 테스트).
- 재생성한 `openapi.json`에 해당 operation과 응답 스키마가 있다.
- 위 Validation 명령이 전부 통과한다.

## Decisions

- D1 (2026-10-02, 사용자 승인): 경로는 `/api/v1/questions/recommendations`, 데이터는 기존 제안 → 운영자 승인 흐름으로 만든다. 작업은 별도 worktree에서 진행한다.
- D2 (2026-10-02, 사용자 승인): 테스트 계획 `TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING`을 승인했다. 추천 조회는 `findMine`과 같이 ACTIVE USER 계정만 허용한다.

## 실행 증거 (2026-10-02)

- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` 통과.
- 단위 1,089개 / 통합 749개, 실패·오류·skip 0.
- `openapi.json` diff는 새 operation, `RecommendedQuestionResponse`·`ApiResponseListRecommendedQuestionResponse` 스키마, "추천 질문" 태그 추가와 최상위 태그 순서 이동뿐이다.
- 테스트 보고서: `docs/reports/tests/gh-301-TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING.md`
