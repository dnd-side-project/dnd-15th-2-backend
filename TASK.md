# GitHub Issue #112 Task Contract

> Generated at: `2026-08-17T18:46:47+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `작성자 appeal과 수동 복원`
- GitHub Issue: `#112`
- Branch: `feat/gh-112-appeal-and-manual-restore`
- Base branch: `main`
- 선행 이슈 `#103`(Foundation, F00), `#110`(G, F07) 모두 CLOSED 확인.

## Objective

- `#103`이 `appeal_case`의 정체성과 유일성(`INV-APL-002`)만 만들어 두고
  "6개월 만료, `UPHOLD/OVERTURN` 결과, 통지 시각은 `#112`(F10)가 컬럼을
  추가해 구현한다"라고 명시했다(`V10__create_filtering_schema.sql` 134-135행,
  `AppealCase` 주석 8-10행). 이 이슈가 그 자리를 채운다.
- `BLOCK` 판정으로 비공개(`HIDDEN`) 처리된 답변에 대해 작성자가 이의를
  제기하고, 검토자가 `UPHOLD_HIDDEN`/`OVERTURN_HIDDEN`을 수동으로 결정하며,
  `OVERTURN_HIDDEN`일 때만 공개 복원 콜백이 나가는 경로를 완성한다.
- 답변 자체의 공개 상태 필드는 이 시스템이 바꾸지 않는다. 필터링 시스템은
  outbox 콜백만 발행하고, 실제 반영은 답변 담당 코드가 그 콜백을 받아
  처리한다(`filtering/package-info.java`의 "콜백/이벤트 계약으로만 연결한다").

## Scope

1. `appeal_case` 확장(V18): `appellant_user_id`, `status`(`OPEN`/`RESOLVED`),
   `window_started_at`, `expires_at`, `acceptance_reason_code`,
   `decision`(`UPHOLD_HIDDEN`/`OVERTURN_HIDDEN`), `decided_at`,
   `decided_by_operator_user_id`, `restore_blocked_reason_code`.
   `outbox_event`의 `ck_outbox_event_aggregate_type`·`ck_outbox_event_event_type`에
   `APPEAL_CASE`·`MODERATION_APPEAL_RESOLVED`를 추가한다.
2. 도메인: `AppealCaseStatus`, `AppealDecision`, `AppealAcceptanceReasonCode`,
   `AppealAcceptance`, `AppealWindow`(6개월 접수 기간의 순수 평가 + 단축 금지
   가드), `AppealCase` 확장(`file`/`decide`/`extendExpiry`).
3. 영속화: `AppealCaseJpaEntity`·`AppealCaseJpaMapper`·`AppealCaseRepository`
   확장(내 appeal 조회, OPEN 큐 조회, 행 잠금 조회).
4. 서비스 `AppealCaseService`(`filtering.moderation`): 접수·검토자 결정·만료
   연장. `OVERTURN_HIDDEN` 결정 시 공개 금지 사유를 재검증한 뒤에만
   `MODERATION_APPEAL_RESOLVED` outbox 이벤트를 발행한다.
5. 포트 2개(`filtering.moderation`)와 답변 도메인 어댑터 2개(`answer.service`):
   `AppealTargetOwnershipChecker`(작성자 본인 확인),
   `PublicationBlockChecker`(계정 차단·삭제 등 다른 공개 금지 사유 재검증).
6. API: 작성자용 `POST /api/v1/filtering/appeals`, `GET /api/v1/filtering/appeals`,
   검토자용 `GET /admin/filtering/appeal-cases`,
   `POST /admin/filtering/appeal-cases/{id}/decide`,
   `POST /admin/filtering/appeal-cases/{id}/extend`.
7. 오류 코드 추가(`FilteringErrorCode`)와 OpenAPI 스펙 갱신.
8. 단위 테스트, PostgreSQL 통합 테스트(동시성 포함), 테스트 계획과 보고서.

## Design decisions (구현 전 확정, 리뷰 필요)

이 이슈의 본문은 여러 항목을 "미결정"으로 남겨 두었다. 아래는 구현을 위해
확정한 판단과 그 근거이며, 리뷰에서 뒤집힐 수 있는 지점이다.

1. **appeal 대상은 `ANSWER`로 한정한다.** 이슈 본문이 "`HIDDEN` 처리된
   답변"이라고 대상을 못박았고, `NICKNAME`은 `NicknameSyncModerationGate`의
   동기 경로라 애초에 `HIDDEN` 상태가 없다. `NICKNAME` 접수는
   `UNSUPPORTED_APPEAL_TARGET`으로 거절한다.
2. **접수 기간 6개월은 설정값이 아니라 상수로 고정한다.** `#110`이
   `agingThreshold`를 호출자에게 받은 것과 반대 선택이다. 접수 기간을 호출자나
   설정이 주입할 수 있으면 그 자체가 "6개월보다 줄이는 경로"가 되어
   `INV-APL-008`·`INV-APL-009`를 위반한다. `AppealWindow`는 생성자에서
   `GLOBAL_ACCEPTANCE_WINDOW`(184일) 미만을 거절한다.
3. **6개월을 184일로 환산한다.** 이슈가 "calendar-month·timezone 계산"을
   미결정으로 제외했으므로 `Period`를 쓰지 않는다. 어떤 6개 달력월 구간도
   최대 184일(7·8·10·12월이 낀 구간)이므로, 184일은 어떤 경우에도 6개월보다
   짧아지지 않는다. 하한을 지키는 방향으로 반올림한 값이다.
4. **접수 기간의 기산점은 `filter_decision.decided_at`이다.** 통지 시각을
   기산점으로 쓰지 않는다 — 이슈가 "통지 성공 증명"을 미결정으로 제외했고,
   필터링 시스템은 작성자 통지 여부를 알 수 있는 경로 자체가 없다.
   `notified_at` 컬럼은 쓸 코드가 없으므로 추가하지 않는다.
5. **정합성이 불명확하면 접수를 허용한다(fallback).** 기산점을 확정할 수
   없는 경우(`decided_at`이 없거나 현재 시각보다 미래)에는 거절하지 않고
   접수한 뒤 `acceptance_reason_code = WINDOW_UNVERIFIABLE`로 기록한다.
   이슈 본문의 "통지·만료 정합성이 불명확하면 접수를 허용하는 fallback"을
   그대로 구현한 것이다.
6. **복원 콜백은 신규 이벤트 타입 `MODERATION_APPEAL_RESOLVED`로 낸다.**
   기존 `MODERATION_VERDICT_READY`를 재사용하지 않는다. 재사용하면 이미
   `BLOCK`으로 확정된 `filter_job`의 판정 원장을 뒤집어야 하는데, 이는
   `INV-MAN-004`(수동 종결된 job의 판정은 나중에 바뀌지 않는다)와
   `filter_decision`의 append-only 성격을 훼손한다. appeal은 판정을 고쳐
   쓰는 것이 아니라 판정 이후의 별도 구제 절차이므로 별도 aggregate
   (`APPEAL_CASE`)와 별도 이벤트로 표현한다.
7. **포트의 어댑터는 `answer` 패키지에 둔다.** 인터페이스는 `filtering`이
   소유하고 구현체가 `answer`를 참조하므로, `filtering → answer` 방향
   의존은 생기지 않는다(`filtering/package-info.java`의 경계 유지).
8. **`restore_blocked_reason_code`는 열거형이 아닌 문자열(30자)이다.**
   이슈가 "상세 reason"을 미결정으로 제외했고, 향후 공개 금지 사유
   (법적 명령 등)가 늘어날 때 필터링 도메인을 고치지 않아도 되게 한다.

## Explicit exclusions

- `MODERATION_APPEAL_RESOLVED` 콜백을 실제로 소비해 답변의 `moderationStatus`와
  공개 상태를 바꾸는 구현 — 이슈 본문이 "답변 자체의 상태 필드 반영은 이
  시스템이 아니라 답변 담당 코드가 콜백을 받아 처리한다"라고 명시했다.
  producer(이벤트 발행)까지만 다룬다.
- 편집(edit) 이후 새 콘텐츠 제출과 revision 모델. `target_version`은 계속 0이다.
- 통지 성공 증명, calendar-month·timezone 계산, 처리 SLA, 악용 제한(rate limit),
  외부 분쟁조정, 상세 reason 텍스트·UI, 기록 보관 기간 — 전부 미결정.
- `safety` 패키지(`Report`/`ModerationReview`)와의 통합.
- appeal 만료를 배치로 감지해 상태를 바꾸는 스케줄러 — 만료는 접수 시점에
  평가하며 별도 상태 전이를 두지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `appeal_case` 확장, appeal 접수·결정·연장 서비스, 포트와 어댑터, API, 테스트 | Feature executor | 접수 기간을 6개월보다 줄이는 경로가 없는지, appeal 접수가 공개 상태를 건드리지 않는지, `OVERTURN_HIDDEN`에서 공개 금지 사유 재검증을 건너뛸 수 있는 경로가 없는지 |

## Existing user-owned changes

- `origin/main`(2bfec4c)에서 새로 분기했다. 분기 시점 `git status --short`는
  비어 있었다. 사용자의 `dnd-worktrees/gh-144` 워크트리(`#145` 작업)는
  건드리지 않았다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 같은 대상의 appeal이 중복 생성되지 않는다. (`INV-APL-002`)
      `uq_appeal_case_target_decision`이 강제하고, 서비스가 사전 조회와 유일성
      위반을 모두 `DUPLICATE_CASE`로 변환한다. 순차 재접수(INT-003)와 2스레드
      동시 접수(INT-004) 모두 최종 1건으로 수렴함을 확인했다.
- [x] appeal 대기 중 콘텐츠가 노출되지 않는다. (`INV-APL-003`)
      접수 경로는 `appeal_case` 행만 만든다. INT-002가 접수 후 `filter_job`
      상태, 답변 상태, `outbox_event` 개수가 모두 그대로임을 확인한다.
- [x] 만료 뒤에도 작성자는 새 콘텐츠를 제출할 수 있다. (`INV-APL-011`)
      필터링 시스템에 제출을 막는 경로가 없다. INT-013이 만료된 appeal이 있는
      작성자의 신규 답변 제출이 성공함을 확인한다.
- [x] 법률·정책상 연장은 가능하지만 기간을 6개월보다 줄이는 경로가 없다.
      (`INV-APL-008`, `INV-APL-009`) 세 겹으로 막았다 — `AppealWindow` 생성자가
      184일 미만을 거절하고(UNIT-001), `AppealCase.extendExpiry`가 이르거나 같은
      시각을 거절하며(UNIT-013), DB CHECK
      `ck_appeal_case_expires_after_window_start`가 직접 UPDATE도 막는다(INT-012).
- [x] `OVERTURN_HIDDEN` 결정 시 다른 공개 금지 사유를 재검증한 뒤에만 복원
      콜백이 발행된다. 계정 `BLOCKED` 상태에서는 결정만 기록되고 콜백이 나가지
      않으며 사유가 남는다(INT-009). 사유가 없을 때만
      `MODERATION_APPEAL_RESOLVED`가 발행된다(INT-008). 포트가 예외를 던지면
      트랜잭션 전체가 롤백되어 fail-closed로 동작한다.
- [x] 승인된 테스트 계획과 실행 보고서가 존재한다.
      계획 `docs/test-plans/gh-112-TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE.md`
      (Status: Approved), 보고서
      `docs/reports/tests/gh-112-TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE.md`.
      단위 537건·통합 415건 전체 통과했다(신규 단위 20, 통합 15).
