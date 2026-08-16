# Test Report: TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION

> Created at: `2026-08-17T00:39:10+09:00`
> GitHub Issue: `#145`
> Branch: `feat/gh-145-question-proposal-followup`
> Commit: `0dcd02d`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 질문 제안 판정(승인·반려)과 `QUESTION_PROPOSAL_REVIEWED` outbox
  event 발행의 결합, outbox 저장 실패 시 판정·이력의 원자적 롤백, 동시 판정에서의
  최종 상태 수렴, 제안자별 조회 격리, `propose()` 트랜잭션 롤백, payload의 JSONB
  유효성. 계획의 P0 6건과 P1 5건을 모두 구현·실행했다.
- Unverified scope: R9(`created_at` 동률 시 목록 정렬 결정성, P2)은 시나리오를
  만들지 않아 **미실행**이다. fan-out worker가 없으므로 알림이 사용자 기기까지
  도달하는 end-to-end 경로는 이 이슈 범위 밖이며 검증하지 않았다.
- Release recommendation: 병합 가능. 다만 아래 6절의 "우연한 방어" 항목은
  후속 이슈로 추적할 가치가 있다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain 21 |
| Spring Boot | 3.5.16 |
| Database | 테스트 컨테이너(PostGIS 16-3.5-alpine), 클래스별 격리 |
| Test runner | JUnit 5 (Mockito, AssertJ, MockMvc standalone, Testcontainers) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Unit (`./harness test-run` 내 `./gradlew test`) | PASS | 442 (실패 0, 오류 0, skip 0) | 8s | `build/test-results/test/TEST-*.xml` |
| Integration (`./harness test-run` 내 `./gradlew integrationTest`) | PASS | 358 (실패 0, 오류 0, skip 0) | 4m 1s | `build/test-results/integrationTest/TEST-*.xml` |
| `./harness check` | PASS | — | — | Secret preflight 806 파일, JUnit policy 124 파일, convention·workflow·label·husky 검사 통과 |
| `npm run hooks:validate` | PASS | — | — | `Husky validation passed.` |
| `git diff --check` | PASS | — | — | 공백 오류 없음(exit 0) |
| `./harness pr-ready --project-tests` | PASS | — | 4m 41s | `BUILD SUCCESSFUL`, `Harness checks passed`, `Local PR readiness checks passed` |

`AGENTS.md` 10절의 필수 검증 네 가지를 모두 실행했다. `./harness pr-ready`는
`./harness check`와 `git diff --check`를 포함하지만 증거를 남기기 위해 개별로도
실행했다.

이 계획이 새로 추가한 테스트는 단위 4건, 통합 9건이다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `QuestionReviewServiceTest#rejectPropagatesOutboxFailure` | outbox 실패가 삼켜지지 않고 전파됨 |
| UNIT-002 | PASS | `QuestionReviewServiceTest#approvePropagatesOutboxFailure` | 동일 |
| UNIT-003 | PASS | `QuestionReviewServiceTest#rejectOnAlreadyRejectedProposalIsBlocked` | 이력·이벤트 미추가 확인 |
| UNIT-004 | PASS | `QuestionReviewServiceTest#approveOnAlreadyApprovedProposalIsBlocked` | 승인 질문·이벤트 미추가 확인 |
| INT-001 | PASS | `QuestionProposalReviewConcurrencyIntegrationTest#concurrentRejectionCommitsOnlyOnce` | 성공 1건, review 1행, outbox 1행 |
| INT-002 | PASS | `QuestionProposalReviewConcurrencyIntegrationTest#concurrentApprovalCommitsOnlyOnce` | 성공 1건, `approved_question` 1행 |
| INT-003 | PASS | `QuestionProposalApiIntegrationTest#reviewKeepsSingleOutboxEventWhenDedupKeyAlreadyTaken` | 중복 억제만 검증. 조기 반환 경로라 롤백은 다루지 않음 |
| INT-009 | PASS | `QuestionProposalApiIntegrationTest#outboxFailureRollsBackReviewAndProposal` | trigger로 outbox 삽입을 실패시켜 판정·이력 롤백을 확인 |
| INT-004 | PASS | `QuestionProposalApiIntegrationTest#findMineDoesNotLeakOtherProposals` | 타 계정 id·문구 미노출 |
| INT-005 | PASS | `QuestionProposalReviewConcurrencyIntegrationTest#fanOutWorkerDoesNotClaimQuestionProposalEvent` | `claimed()==0`, status `PENDING`, lease 없음 |
| INT-006 | PASS | `QuestionProposalApiIntegrationTest#proposeLeavesNoOrphanDraftWhenSubmitFails` | 고아 DRAFT 0행 |
| INT-007 | PASS | `QuestionProposalApiIntegrationTest#secondRejectionIsBlockedAndAddsNoHistory` | review 1행, outbox 1행 유지 |
| INT-008 | PASS | `QuestionProposalApiIntegrationTest#publishedPayloadIsQueryableJsonObject` | `jsonb_typeof` = `object` |
| R9 관련 | NOT_RUN | — | 정렬 동률 시나리오를 계획에 넣지 않았다(P2) |

## 5. Failures and diagnostics

실패한 테스트가 없다. 구현 중 발견해 수정한 사항은 다음 하나다.

- 통합 테스트 최초 작성 시 outbox payload 단정을 `"decision":"APPROVED"`(공백 없음)로
  적었으나 실제 저장 값은 PostgreSQL JSONB 정규화를 거쳐 `"decision": "APPROVED"`
  (콜론 뒤 공백)였다. 문자열 포함 검사를 실제 저장 형식에 맞췄고, 이후 INT-008에서
  문자열 매칭 대신 `payload ->> 'key'` 연산자로 조회하도록 바꿔 형식 의존을 제거했다.
  구현 결함이 아니라 테스트의 잘못된 가정이었다.

## 6. Potential issues

### Application code

- **`publishReviewed()`의 payload를 `String.format`으로 수동 조립한다.** 현재
  값은 식별자 두 개와 고정 문자열뿐이라 안전하지만, 향후 제안 문구나 반려 사유
  같은 사용자 입력이 payload에 들어가면 따옴표·역슬래시가 JSON을 깨뜨린다. 같은
  저장소의 `AnswerModerationJobIntakeService`는 `ObjectMapper`로 직렬화한다 —
  사용자 입력을 넣기 전에 그 패턴으로 옮겨야 한다.
- **경쟁에서 진 운영자가 받는 메시지가 상황과 어긋난다.** `uq_outbox_event_dedup`
  위반은 `ConstraintExceptionMapper`가 `DUPLICATED_EVENT`(409, "이미 처리된
  알림입니다.")로 매핑한다. 상태 코드는 적절하지만 문구는 "다른 운영자가 먼저
  판정했다"는 실제 상황을 전달하지 못한다. 운영 UX 관점의 개선 여지다(P2).
- `question` 도메인이 `notification` 도메인을 직접 참조하게 됐다. `answer` 도메인도
  같은 방식이므로 저장소 관례에는 부합하지만, producer가 늘수록 이 결합이
  누적된다는 점은 기록해둔다.

### Infrastructure and resource limits

- 통합 스위트 전체가 4분 1초다. 이 계획이 컨테이너 기반 클래스를 하나 추가해
  Spring context가 하나 더 생성된다(`@ActiveProfiles`가 달라 캐시를 공유하지
  않는다). 현재는 허용 범위지만 클래스가 더 늘면 CI 시간이 선형으로 증가한다.

### Database and migrations

- **신규 마이그레이션 없음.** 기존 V1 스키마만 사용한다.
- **`question_proposal_review`에 `proposal_id` unique 제약이 없다(계획 R2).**
  INT-001에서 판정 이력이 1행으로 수렴한 것은 이 테이블의 제약 때문이 **아니라**,
  같은 트랜잭션의 outbox 삽입이 `uq_outbox_event_dedup`에 걸려 트랜잭션 전체가
  롤백됐기 때문이다. 즉 판정 이력의 중복 방지가 **알림 발행에 우연히 얹혀 있는
  구조**다. 앞으로 "알림을 발행하지 않는 판정 경로"(예: 자동 만료, 일괄 정리)가
  추가되면 이 보호가 사라지고 중복 이력이 남을 수 있다. 후속 이슈에서
  `question_proposal_review`에 부분 unique 제약을 추가하거나 판정 시 제안 행을
  잠그는 방식을 검토할 가치가 있다.

### Concurrency and idempotency

- 동시 판정의 최종 상태는 두 경로 모두에서 동일하게 수렴한다: 도메인 상태 기계가
  후발 호출을 `INVALID_PROPOSAL_STATUS`로 막거나, DB 제약이 트랜잭션을 롤백한다.
- **테스트가 특정 인터리빙을 강제하지 못한다.** `CountDownLatch`는 두 스레드의
  출발만 맞추고, 실제로 두 트랜잭션이 겹쳤는지는 관측하지 않는다. 두 방어선이
  같은 최종 상태를 만들기 때문에 불변식은 검증되지만 "어느 방어선이 작동했는지"는
  구분되지 않는다. 잔여 위험으로 7절에 기록한다.
- dedupKey가 `proposalId`에 고정돼 재시도는 멱등이다. 제안은 `UNDER_REVIEW`에서
  한 번만 전이하므로(재검토 경로 없음) 정상 흐름에서 키 충돌이 발생하지 않는다.

### Transactions and event ordering

- 판정 이력, 제안 상태, 승인 질문, outbox event가 한 커밋 단위임을 INT-009가
  결정적으로 확인했다. `outbox_event` 삽입을 거부하는 trigger를 걸고 `reject()`를
  호출하면 제안은 `UNDER_REVIEW`로, 판정 이력은 0행으로 되돌아간다. `reject()`는
  review와 proposal을 DB에 쓴 **뒤** 이벤트를 발행하므로, 이력이 비어 있다는 사실
  자체가 롤백의 증거다.
- INT-003(dedupKey 선점)은 이 원자성을 검증하지 못한다. `publishReviewed()`가
  사전 조회에서 조기 반환하므로 저장 실패가 발생하지 않기 때문이다. 초기 계획은
  이 둘을 한 시나리오로 묶었으나 코드 리뷰 지적을 받아 INT-009로 분리했다.
- `approve()`는 `approvedQuestionRepository.save()` **뒤에** 이벤트를 발행하므로,
  승인 질문 생성이 실패하면 이벤트가 발행되지 않는다(기존
  `QuestionPersistenceIntegrationTest#rollsBackApprovalWhenApprovedQuestionInsertFails`
  가 커버). "판정 없는 알림"이 생기지 않는 순서다.

### External APIs

- **해당 없음.** `TASK.md`의 "Filtering integration decision"에 따라 질문 제안
  경로에는 외부 moderation 호출이 없다. 외부 API double을 사용하지 않았다.

### Failure recovery and reconciliation

- 발행된 event는 `PENDING`으로 남는다. INT-005가 기존
  `RecipientNotificationFanOutWorker`의 오소비를 배제했다(이 worker는
  `RECIPIENTS_CONFIRMED`만 claim한다).
- **PENDING 적체를 감시하는 지표가 없다.** 소비자 부재는 의도된 상태이지만,
  worker를 배선하는 시점에 이미 쌓인 event를 어떻게 처리할지(전량 발송 여부,
  오래된 판정에 대한 알림 억제 여부)는 정해져 있지 않다. worker 배선 이슈에서
  결정해야 한다.

## 7. Regression and residual risk

- **R9 미검증**: `findAllByProposerIdOrderByCreatedAtDesc`가 `created_at` 동률일
  때의 순서는 확인하지 않았다. INT-004는 시각을 60초씩 벌려 두었으므로 동률
  상황을 만들지 않는다. 실제로는 같은 초에 두 건을 제출하면 목록 순서가 흔들릴 수
  있다. 영향은 목록 표시 순서에 한정된다(P2).
- **인터리빙 비결정성**: 위 6절 참고. 동시성 테스트가 통과했다고 해서 TOCTOU
  경로가 실제로 실행됐다는 뜻은 아니다.
- **end-to-end 알림 미검증**: producer까지만 다뤘다. 사용자가 실제로 알림을 받는
  경로는 fan-out worker 배선 이후에 검증할 수 있다.
- **기존 테스트 회귀 없음**: 단위 442건, 통합 357건 전량 통과. `#144`에서 추가한
  테스트도 그대로 통과한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-145-TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION.md`
- CI run: 미실행(로컬 `./harness test-run`, `./harness pr-ready --project-tests`로 검증)
- Related ADR: 없음
- PR: `#145` 작업 브랜치는 `#144`(PR #146) 위에 쌓인 stacked 브랜치다. PR은
  아직 생성하지 않았다.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (R9 정렬 동률)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — `question_proposal_review` 중복
      방지가 outbox 제약에 의존하는 문제와 payload 수동 직렬화는 아직 이슈를
      만들지 않았다. 리뷰어 판단으로 생성한다.
- [x] 실행 결과와 PR 설명이 일치함 (PR 생성 시 이 보고서를 링크한다)
