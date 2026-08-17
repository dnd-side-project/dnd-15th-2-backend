# GitHub Issue #125 Task Contract

> Generated at: `2026-08-17T14:59:06+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `답변 제출·공개 API`
- GitHub Issue: `#125`
- Branch: `feat/gh-125-direction-answer-api`
- Base branch: `main`

## Objective

- 인증된 수신자가 만료·차단·넘김 상태와 수신 자격을 서버에서 재검증한 뒤
  답변을 멱등 제출하고, 비동기 안전 검사 결과가 허용한 답변만 공개하면서
  수신 슬롯과 공개 Outbox를 정확히 한 번 변경할 수 있게 한다.

## Scope

1. `POST /api/v1/direction/inbox/{postRecipientId}/answers` 답변 제출 endpoint.
2. `Idempotency-Key`와 요청 fingerprint를 이용한 동일 요청 재생 및 다른 요청의
   키 재사용 거절.
3. ACTIVE USER 계정, 수신자 소유권, ACTIVE·미삭제 질문글, 제출 시각의 만료,
   양방향 활성 차단과 `AVAILABLE`·`DISCOVERED`·`OPENED` 상태 검증.
4. 비동기 텍스트 moderation을 위한 필수 답변 본문과 선택적 미디어의 소유권·준비·안전 상태 검증. 사용자·시각·지역·
   방위·거리는 요청값이 아니라 인증과 서버 스냅샷에서 결정한다.
5. 답변·첨부·moderation job·실행 요청 Outbox를 동일한 원자적 제출 경계에 저장.
6. `MODERATION_VERDICT_READY` 결과의 ALLOW·BLOCK 처리와
   `MODERATION_DEADLINE_ELAPSED` fail-closed 처리.
7. ALLOW 시 Answer 공개, `PostRecipient.ANSWERED` 전이, 수신 슬롯 1회 해제와
   `ANSWER_PUBLISHED` Outbox 생성을 동일 transaction에서 처리.
8. 만료 전에 제출된 검사 중 답변은 제출 시점 자격을 보존한다. 만료 sweep은
   해당 수신 항목을 선점하지 않고, 늦게 도착한 유효 ALLOW도 공개할 수 있다.
9. 한 `PostRecipient`당 활성 답변 1건 제약과 관련 오류 코드·DB 제약 매핑.
10. Controller·ApiSpec·요청/응답 DTO와 `docs/api/openapi.json` 갱신.
11. 정식 테스트 계획
    `TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API`에 따른 JUnit 5 단위·
    MockMvc·PostgreSQL/PostGIS 통합·동시성·장애 복구 테스트와 테스트 보고서.

## Approved design decisions

- 외부 HTTP endpoint는 제출만 제공한다. 공개는 내부 moderation 결과 consumer가
  수행하며 클라이언트가 공개 endpoint를 직접 호출하지 않는다.
- 안전 검사 결과가 없는 답변은 공개하지 않는 fail-closed 정책을 유지한다.
- 만료 전에 정상 제출된 검사 중 답변은 제출 시점 자격을 보존하고, 질문글 만료
  뒤 도착한 ALLOW도 공개할 수 있다.
- 동일 멱등키의 동일 요청은 기존 결과를 반환한다. 같은 키로 다른 수신 항목·본문·
  미디어 조합을 보내면 충돌로 거절한다.
- 정확 좌표, 내부 사용자 식별자와 자유 텍스트 본문을 공개 응답·로그·알림 Outbox
  payload에 포함하지 않는다. moderation 실행 payload의 원문은 해당 내부 경계에서만
  사용하고 로그에 기록하지 않는다.
- `deadlineWindow`는 5분(`PT5M`)으로 승인됐다(사용자 승인, `2026-08-17`,
  현재 Claude Code 대화). `qello.filtering.answer-moderation.deadline-window`로
  설정하고 `filtering.config.AnswerModerationIntakeConfig`에서
  `AnswerModerationJobIntakeService`를 빈으로 등록한다.
- moderation 언어는 우선 `ModerationLanguage.KO`로 고정한다(사용자 승인,
  `2026-08-17`, 현재 Claude Code 대화). 다국어 판정 로직 자체는 이 이슈 범위 밖이며,
  필요해지면 별도 이슈에서 재검토한다.

## Explicit exclusions

- 답변 편집·삭제 및 수정 재검사 흐름.
- 답변 공감과 답변 목록·상세 조회 계약 변경.
- `ANSWER_PUBLISHED`를 실제 인앱 알림이나 FCM/APNs로 fan-out하는 worker.
- moderation 공급자, retry/backoff, manual review 정책 자체의 변경.
- `AnswerFormat`별 TEXT/PHOTO/BOTH 조합을 임의로 기본값으로 고정하는 변경(구현
  전에 별도 사람 결정을 받는다 — `deadlineWindow`는 위 "Approved design decisions"에서
  이미 결정됐으므로 이 항목에서 제외한다).
- 본문 없는 사진 전용 답변. 현재 text pipeline은 공백 원문을 거절하므로 사진 전용
  공개 흐름은 `AnswerFormat` 정책과 함께 별도 승인 전까지 구현하지 않는다.
- Flyway migration. 기존 상태와 unique/FK/constraint로 구현할 수 없다고 확인되면
  범위를 넓히지 않고 별도 승인을 받는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Answer 제출 인터페이스·도메인·HTTP 계약 | Answer/API executor | 요청 fingerprint, 인증 subject, 응답 privacy와 오류 코드 리뷰 |
| 수신 자격 잠금·답변 저장·슬롯·Outbox transaction | Persistence executor | 만료·차단·넘김 경합, 잠금 순서, unique/rollback 리뷰 |
| Filtering intake seam·moderation 결과 consumer | Moderation executor | ALLOW/BLOCK/deadline, lease fencing, fail-closed·재처리 리뷰 |
| 단위·MockMvc·PostgreSQL/PostGIS·동시성 테스트 | Test executor | 시나리오 추적성, 실제 DB 제약, 픽스처 격리와 실패 판정 리뷰 |
| 전체 변경 및 검증 증거 | Independent verifier | 구현 설명이 아닌 diff·실행 결과 기반 독립 검증 |

## Existing user-owned changes

- 작업 시작 시 `main`의 `git status --short`는 비어 있었다.
- `./harness start`가 최신 `origin/main`을 fetch했고 fast-forward 대상이 없어
  `main`이 최신 상태임을 확인한 뒤 `feat/gh-125-direction-answer-api`를 생성했다.
- 브랜치 생성 전에 보존해야 할 기존 사용자 변경은 없었다.
- 브랜치 생성 후 이 계약과 정식 테스트 계획만 새 변경으로 만들었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- Test plan: `TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API`
- Design approval evidence: user approval in the current Codex conversation before
  test-plan generation on `2026-08-17`.
- Test-plan approval: approved in the current Claude Code conversation on
  `2026-08-17`.

## Completion criteria

- [x] 인증된 수신자만 자신에게 열린 수신 항목에 답변을 제출할 수 있다.
      (UNIT-004/005, INT-007)
- [x] 동일 멱등 요청은 기존 답변을 반환하고 답변·첨부·filter job·Outbox 수가
      증가하지 않으며, 다른 요청의 키 재사용은 거절된다. (UNIT-007/008,
      INT-003/004/005/006)
- [x] 만료·차단·넘김 확정 뒤의 새 답변과 한 수신 항목의 중복 활성 답변이
      기능 오류 코드로 거절된다. (UNIT-005/009, INT-007/008/009)
- [x] 제출 transaction은 답변과 moderation 작업을 모두 commit하거나 모두
      rollback하며 외부 moderation 호출을 요청 thread에서 실행하지 않는다.
      (INT-001/002)
- [x] ALLOW만 답변을 공개하고 BLOCK/deadline은 공개하지 않는다.
      (UNIT-011/012/013, INT-010/011/017/018)
- [x] 만료 전 제출된 검사 중 답변의 자격은 보존되고 늦은 ALLOW도 공개된다.
      (INT-009/010)
- [x] 공개 시 `PostRecipient`가 ANSWERED로 전이하고 수신 슬롯과
      `ANSWER_PUBLISHED` Outbox가 정확히 한 번 변경된다. (INT-011/013/014)
- [x] 정확 좌표, 내부 사용자 식별자와 답변 본문이 공개 응답·로그·알림 Outbox에
      노출되지 않는다. (UNIT-019, INT-022)
- [x] OpenAPI와 오류 코드 문서가 실제 Controller 계약과 일치한다.
      `docs/api/openapi.json` 재생성, `docs/error-codes.md`에 신규
      `ANS-DOM-011`/`ANS-APP-001~004`/`ANS-INFRA-002` 6건 반영.
- [x] 승인된 테스트 계획의 모든 P0 시나리오와 필수 회귀 검증이 통과하고
      `templates/test-report.md` 기반 보고서가 남는다.
      `docs/reports/tests/gh-125-TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API.md`
      (Result: PASS, unit 536건·integration 405건 실패 0건). 테스트 실행 중
      발견한 `AnswerModerationVerdictWorker` batch 격리 결함은 사용자 승인 후
      즉시 수정하고 회귀 재확인했다.
