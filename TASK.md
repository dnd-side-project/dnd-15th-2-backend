# GitHub Issue #144 Task Contract

> Generated at: `2026-08-16T00:01:22+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[API] 질문 제안 제출/검토 API 추가`
- GitHub Issue: `#144`
- Branch: `feat/gh-144-question-proposal-api`
- Base branch: `main`

## Objective

- 질문 제안(F03)의 도메인·서비스·저장 계층(`QuestionProposal`,
  `QuestionReviewService`, 관련 Repository)은 `#38`에서 이미 구현되어
  있으나 이를 호출할 REST API가 없어 사용자가 실제로 질문을 제안할 수
  없다. 제안 제출·조회, 운영자 검토(승인/반려) API를 추가해 F03을 실제로
  동작하는 기능으로 완성한다.

## Scope

1. 사용자 질문 제안 제출 API (`QuestionReviewService.submit` 연결) —
   `POST /api/v1/questions/proposals`.
2. 내가 제안한 질문 목록 조회 API — `GET /api/v1/questions/proposals/me`.
3. 운영자 검토(승인/반려) API (`startReview`/`approve`/`reject` 연결,
   운영자 인증 필요) — 경로는 설계 단계에서 확정한다.
4. `QUESTION_PROPOSAL_REVIEWED` outbox 알림 이벤트 실제 발행 연결.
5. 콘텐츠 안전 검사(`filtering` 도메인) 연동 여부 확인 및 필요 시 연결.
6. `docs/api/openapi.json` 갱신.
7. 신규 마이그레이션 필요 여부는 구현 중 확인한다(기존
   `question_proposal`/`question_proposal_review`/`approved_question`
   테이블 재사용을 우선한다).

`/harness-review` 검토 결과, 4번(알림 발행 연결)과 5번(filtering 연동 확인)은
이 브랜치에서 구현하지 않고 후속 이슈 `#145`로 이관했다. Issue #144 본문도
동일하게 갱신했다.

## Explicit exclusions

- 질문 배정/추천 주기(`question_assignment_cycle`) 로직 변경 — 별도
  이슈.
- 콘텐츠 안전 검사 신규 정책 설계 — 기존 `filtering` 도메인 연동 확인
  까지만.
- Slack 등 알림 채널 확장 — 기존 outbox 패턴만 사용.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 제안 제출·조회 API, 운영자 검토 API, outbox 알림 발행 연결, `filtering` 연동 확인, `docs/api/openapi.json`, 단위·통합 테스트 | Feature executor | 인증·권한 분리(일반 사용자 vs 운영자), `QuestionProposalReview` append-only 불변식, `#38` 기존 도메인 계약과의 호환성 리뷰 |

## Existing user-owned changes

- `main`에서 새로 분기했다(`./harness start --issue 144 --type feat
  --slug question-proposal-api`). 분기 시점 `git status --short`는 비어
  있었다.
- 이전 작업 브랜치 `feat/gh-109-snapshot-health-migration`의 미커밋
  변경(이슈 #109, snapshot health/emergency migration 구현 중)은 분기
  전 `git stash`로 보존했다(`stash@{1}`: "WIP: gh-109
  snapshot-health-migration (before starting gh-144)"). 해당 브랜치로
  복귀 시 `git stash pop`으로 복원해야 한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 제안 제출 API가 `QuestionProposal`을 생성하고 DRAFT→SUBMITTED로
      전이한다. `QuestionReviewService.propose()`와
      `QuestionProposalApiMockMvcTest#submitReturnsCreatedProposal`로 확인했다.
- [x] 운영자 승인 API 호출 시 `ApprovedQuestion`이 생성되고
      `QuestionProposalReview`가 append-only로 기록된다. 판정 로직 자체는
      `#38`에서 구현됐고, 이 브랜치는
      `OperatorQuestionProposalApiMockMvcTest#approveDelegatesWithExactArguments`로
      컨트롤러가 `QuestionReviewService.approve`에 정확한 인자를 넘기는지
      확인했다.
- [ ] 반려 시 사유가 기록되고 `QUESTION_PROPOSAL_REVIEWED` 알림이
      제안자에게 발행된다. 사유 기록은
      `OperatorQuestionProposalApiMockMvcTest#rejectDelegatesWithExactArguments`로
      확인했지만, 알림 실제 발행 연결은 이 브랜치 범위에서 제외하고 `#145`로
      이관했다.
- [x] 인증되지 않은 사용자는 제안 제출·조회를 할 수 없다.
      `QuestionProposalApiMockMvcTest#submitRequiresAuthentication`,
      `#findMineRequiresAuthentication`으로 확인했다.
- [ ] 단위·통합 테스트와 테스트 보고서. 단위·컨트롤러 테스트(Mockito/MockMvc)
      20건과 `QuestionProposalApiIntegrationTest`(PostgreSQL, 5건 — propose
      단일 행 생성, application service 제출·조회·부적격 계정 거부, propose로
      제출한 제안의 승인·반려 흐름)를 추가했다. 정식 `/harness-test-plan`
      승인과 테스트 보고서는 여전히 없으며 `#145`로 이관했다.

### Test plan exception (사용자 승인, 2026-08-16)

- 결정: 정식 `/harness-test-plan` 승인 없이 이 PR을 병합하는 것을 예외로
  승인함.
- 이유: `/harness-review`가 지적한 gap(컨트롤러 인자 wiring, 제출→승인·
  제출→반려 happy path)은 이후 추가한 단위·MockMvc·PostgreSQL 통합
  테스트 35건으로 커버됐다. 정식 계획이 주는 값은 주로 엣지 케이스·임계값
  설계인데, 이 PR 범위(REST wiring)에는 그런 임계값이 없다.
- 남은 위험: 테스트 범위가 사전에 설계된 계획이 아니라 사후에 리뷰 지적을
  메우는 방식으로 정해졌다. 놓친 경계 조건이 있어도 계획 문서가 없어
  드러나지 않을 수 있다.
- 추적: 정식 계획과 보고서는 `#145`(알림 발행·filtering 연동과 함께)에서
  작성한다.
