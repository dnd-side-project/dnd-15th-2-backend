# GitHub Issue #145 Task Contract

> Generated at: `2026-08-16T18:56:51+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `질문 제안 알림 발행·filtering 연동·문서화 마무리`
- GitHub Issue: `#145`
- Branch: `feat/gh-145-question-proposal-followup`
- Base branch: `feat/gh-144-question-proposal-api`

## Objective

- `#144`에서 질문 제안 제출·조회(사용자)와 검토(운영자) API는 완성했지만,
  같은 이슈 범위에 있던 알림 발행 연결, `filtering` 도메인 연동 확인,
  정식 테스트 계획·통합 테스트는 아직 남아 있다(`#144`의 "Test plan
  exception"에서 이 이슈로 이관하기로 명시했다). API가 실제로 알림을
  보내고 통합 시나리오로 검증되도록 마저 완성한다.

## Scope

1. `QuestionProposalReview`의 반려·승인 판정 시 `QUESTION_PROPOSAL_REVIEWED`
   outbox 알림 이벤트를 실제로 발행하는 연결 작업(제안자에게 전달). 기존
   `notification.domain.OutboxEvent`/`OutboxEventRepository` 패턴을 그대로
   따르되, `AnswerNotificationService` 같은 기존 발행 지점의 구조를 먼저
   확인한다.
2. 제안 제출 텍스트가 `filtering` 도메인(비속어·선정성 검사)을 거치는지
   확인한다. 현재 `filtering` intake는 `AnswerModerationJobIntakeService`처럼
   답변 전용으로 결합돼 있어, 질문 제안에도 적용하려면 신규 연동 경로
   설계가 필요할 수 있다 — 설계 전 기존 구조를 조사해 필요 여부부터
   확정한다.
3. `/harness-test-plan`으로 정식 테스트 계획을 수립하고 승인받은 뒤,
   통합(PostgreSQL) 테스트와 테스트 보고서를 작성한다. `#144`는 정식 계획
   없이 예외 승인을 받았으므로, 이 이슈는 그 부채를 갚는 자리다.

## Explicit exclusions

- 질문 배정/추천 주기(`question_assignment_cycle`) 로직 변경 — 별도 이슈.
- Slack 등 알림 채널 확장 — 기존 outbox 패턴만 사용.
- `QUESTION_PROPOSAL_REVIEWED` outbox event를 실제 인앱 알림·push로
  fan-out하는 worker 배선 — producer(event 발행)까지만 이 이슈에서
  다룬다. 기존 `RecipientNotificationFanOutWorker`가 `AnswerNotificationService`
  같은 producer와 별도 클래스로 분리돼 있는 구조를 그대로 따른 결정이며,
  fan-out worker 자체는 이 이슈 범위 밖이다.
- 콘텐츠 안전 검사 신규 정책 설계 — 기존 `filtering` 도메인 연동 확인까지만.
- 질문 제안을 `filtering`에 실제로 연결하는 구현 — 아래 "Filtering
  integration decision" 참고. 조사 결과 연결하지 않기로 결정했다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

### Filtering integration decision (사용자 승인, 2026-08-16)

- 조사 결과: `filtering` 파이프라인은 질문 제안뿐 아니라 **어떤 도메인에도
  아직 실제로 연결되어 있지 않다.**
  - `AnswerModerationJobIntakeService`(답변 전용 진입점)는 주석에 "의도적으로
    Spring bean이 아니다"라고 명시돼 있고, `deadlineWindow`(운영값)가
    미정이라 배선을 보류한 상태다. `grep`으로 확인한 결과 `filtering`
    패키지 밖 어디에서도 이 서비스를 호출하지 않는다.
  - `DirectionPost`는 생성 시 `moderationStatus = PENDING`으로 시작하고,
    `DirectionMatchingWorker`는 PENDING/REVIEW_HELD를 매칭 불가로 취급하는데,
    이를 `PASSED`로 전이시키는 코드가 어디에도 없다 — 방향 글도 현재
    상태로는 영구히 매칭되지 않아야 정상이다.
  - `FilterTargetType`은 `ANSWER`, `NICKNAME` 둘뿐이며 `QUESTION_PROPOSAL`
    값 자체가 없다.
- 결정: 질문 제안만 지금 `filtering`에 연결하지 않는다.
- 이유: 질문 제안만 먼저 연결하면 "어떤 콘텐츠는 검사되고 어떤 콘텐츠(답변·
  방향 글)는 안 되는" 도메인 간 불일치가 생긴다. `filtering` 프로덕션 배선
  (운영값 확정, 각 도메인 진입점 연결)은 저장소 전체가 공유하는 별도
  production-gate 이슈에서 한 번에 다뤄야 한다.
- 추적: 별도 이슈 없음(아직 생성하지 않음). `filtering` 프로덕션 배선
  이슈가 생기면 그 범위에 질문 제안도 포함시킨다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `QUESTION_PROPOSAL_REVIEWED` 알림 발행, `filtering` 연동 확인, 정식 테스트 계획·통합 테스트·보고서 | Feature executor | 알림 발행이 반려/승인 트랜잭션을 오염시키지 않는지, `filtering` 미연동 결정 시 그 근거가 타당한지 리뷰 |

## Existing user-owned changes

- `feat/gh-144-question-proposal-api`(origin에 push된 PR #146 상태) 위에서
  새로 분기했다(`./harness start --issue 145 --type feat --slug
  question-proposal-followup --base feat/gh-144-question-proposal-api`).
  분기 시점 `git status --short`는 비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 반려 시 사유가 기록되고 `QUESTION_PROPOSAL_REVIEWED` 알림이 제안자에게
      실제로 발행된다. `QuestionReviewService.reject()`/`approve()`가 같은
      transaction에서 outbox event를 저장하고, `QuestionProposalApiIntegrationTest`가
      실제 PostgreSQL `outbox_event` 테이블에서 `decision`/`proposerId`가
      기록됨을 확인한다. fan-out worker(인앱 알림·push 실제 전달)는 이
      이슈 범위 밖이다("Explicit exclusions" 참고).
- [x] 제안 제출 텍스트의 `filtering` 연동 여부가 확인되고, 필요하면
      연결된다(미연동 결정이면 그 근거를 기록한다). 조사 결과 `filtering`이
      답변·방향 글을 포함해 어떤 도메인에도 아직 연결되어 있지 않음을
      확인했고, 질문 제안만 먼저 연결하지 않기로 결정했다(사용자 승인,
      2026-08-16 — 근거는 "Filtering integration decision" 절 참고).
- [ ] `/harness-test-plan` 승인을 받은 정식 테스트 계획이 존재한다.
- [ ] 통합(PostgreSQL) 테스트와 테스트 보고서가 존재한다.
