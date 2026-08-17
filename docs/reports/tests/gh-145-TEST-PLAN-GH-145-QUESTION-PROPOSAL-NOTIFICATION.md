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
- Release recommendation: 병합 가능. 단, 이 보고서는 **한 차례 정정됐다.** 초기
  판정은 동시 판정 위험(R1·R2)을 "결함 아님"으로 기록했으나 CI가 반례를 잡았고,
  판정 경로에 행 잠금을 도입해 수정했다. 상세는 5절에 있다. `question_proposal_review`의
  DB 차원 중복 방지 부재는 후속 이슈로 남는다.

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
| INT-001 | PASS (최초 CI 실패 → 결함 수정 후 통과) | `QuestionProposalReviewConcurrencyIntegrationTest#concurrentRejectionCommitsOnlyOnce` | 성공 1건, review 1행, outbox 1행. 진 쪽이 `INVALID_PROPOSAL_STATUS`인지까지 단정 |
| INT-002 | PASS | `QuestionProposalReviewConcurrencyIntegrationTest#concurrentApprovalCommitsOnlyOnce` | 성공 1건, `approved_question` 1행. 진 쪽 예외 타입 단정 포함 |
| INT-003 | PASS | `QuestionProposalApiIntegrationTest#reviewKeepsSingleOutboxEventWhenDedupKeyAlreadyTaken` | 중복 억제만 검증. 조기 반환 경로라 롤백은 다루지 않음 |
| INT-009 | PASS | `QuestionProposalApiIntegrationTest#outboxFailureRollsBackReviewAndProposal` | trigger로 outbox 삽입을 실패시켜 판정·이력 롤백을 확인 |
| INT-004 | PASS | `QuestionProposalApiIntegrationTest#findMineDoesNotLeakOtherProposals` | 타 계정 id·문구 미노출 |
| INT-005 | PASS | `QuestionProposalReviewConcurrencyIntegrationTest#fanOutWorkerDoesNotClaimQuestionProposalEvent` | `claimed()==0`, status `PENDING`, lease 없음 |
| INT-006 | PASS | `QuestionProposalApiIntegrationTest#proposeLeavesNoOrphanDraftWhenSubmitFails` | 고아 DRAFT 0행 |
| INT-007 | PASS | `QuestionProposalApiIntegrationTest#secondRejectionIsBlockedAndAddsNoHistory` | review 1행, outbox 1행 유지 |
| INT-008 | PASS | `QuestionProposalApiIntegrationTest#publishedPayloadIsQueryableJsonObject` | `jsonb_typeof` = `object` |
| R9 관련 | NOT_RUN | — | 정렬 동률 시나리오를 계획에 넣지 않았다(P2) |

## 5. Failures and diagnostics

### CI에서 드러난 동시성 결함 (수정 완료)

로컬 실행은 전부 통과했으나 **GitHub Actions에서 INT-001이 실패했다**
(`QuestionProposalReviewConcurrencyIntegrationTest:96`, 성공 건수 단정). 로컬은
두 transaction이 일관되게 직렬화되어 결함을 드러내지 못했고, CI의 다른 타이밍이
반대 순서를 만들었다. **테스트가 불안정한 것이 아니라 실제 결함을 찾은 것이다.**

원인은 판정 경로의 read-then-write가 무방비라는 점이다. `QuestionProposal`에는
version column이 없어 낙관적 잠금이 없고, 행 잠금도 걸지 않았다. 여기에
`publishReviewed()`의 dedupKey 조기 반환이 겹치면 다음 순서가 성립한다.

```
T1 조회 → UNDER_REVIEW
T2 조회 → UNDER_REVIEW          (둘 다 상태 기계를 통과)
T1 review 저장 → proposal REJECTED → dedup 없음 → outbox 삽입 → COMMIT
T2 review 저장 → proposal REJECTED (version 검사가 없어 통과)
   → dedup 조회에서 T1이 커밋한 행 발견 → 조기 반환(삽입 생략) → COMMIT 성공
```

두 transaction이 모두 성공해 `question_proposal_review`에 판정 이력이 2행 남고,
반려 사유가 나중 transaction 값으로 덮인다. 감사 이력 오염이다.

**초기 보고서가 "R1·R2는 결함이 아니다"라고 기록했던 결론은 틀렸다.** outbox
dedup 제약이 중복을 막아준다고 보았으나, 그 방어는 뒤늦은 transaction의 dedup
조회가 먼저 커밋된 행을 **발견하는 순간 조기 반환으로 무력화**된다.

조치: 판정 경로(`submit`/`startReview`/`reject`/`approve`)가 제안 행을
`PESSIMISTIC_WRITE`로 잠그고 읽도록 바꿨다(`findByIdForUpdate`). 뒤늦은
transaction은 잠금 대기 후 갱신된 상태를 읽어 `INVALID_PROPOSAL_STATUS`로
거절된다. INT-001/002에 "진 쪽의 예외가 도메인 오류인지" 단정을 추가해 잠금이
실제로 직렬화했음을 검증한다 — 수정 전이라면 진 쪽이 성공하거나 DB 제약 위반으로
실패하므로 이 단정이 회귀 가드가 된다.

### 그 밖에 구현 중 발견해 수정한 사항

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
- 경쟁에서 진 운영자는 이제 `INVALID_PROPOSAL_STATUS`(409, "현재 제안 상태로는
  요청을 처리할 수 없습니다.")를 받는다. 행 잠금 도입 전에는 `uq_outbox_event_dedup`
  위반이 `DUPLICATED_EVENT`("이미 처리된 알림입니다.")로 매핑되어 상황과 어긋난
  문구가 나갔다. 잠금 도입으로 도메인 오류 경로로 수렴해 문구도 실제 상황에
  맞게 됐다.
- `question` 도메인이 `notification` 도메인을 직접 참조하게 됐다. `answer` 도메인도
  같은 방식이므로 저장소 관례에는 부합하지만, producer가 늘수록 이 결합이
  누적된다는 점은 기록해둔다.

### Infrastructure and resource limits

- 통합 스위트 전체가 4분 1초다. 이 계획이 컨테이너 기반 클래스를 하나 추가해
  Spring context가 하나 더 생성된다(`@ActiveProfiles`가 달라 캐시를 공유하지
  않는다). 현재는 허용 범위지만 클래스가 더 늘면 CI 시간이 선형으로 증가한다.

### Database and migrations

- **신규 마이그레이션 없음.** 기존 V1 스키마만 사용한다.
- **`question_proposal_review`에 `proposal_id` unique 제약이 없다(계획 R2).** 이
  결함이 CI에서 실제로 발현했다(5절 참고). 중복 방지를 outbox dedup 제약에 기대는
  구조였고 그 기대는 성립하지 않았다 — 뒤늦은 트랜잭션이 먼저 커밋된 event를
  발견하면 삽입을 건너뛰고 그대로 성공하기 때문이다. 이번 변경에서 판정 경로에
  행 잠금을 도입해 애플리케이션 계층에서 직렬화하도록 고쳤다.
- 다만 **DB 차원의 보호는 여전히 없다.** 잠금은 `QuestionReviewService`를 거치는
  경로만 보호하므로, 다른 코드가 `question_proposal_review`에 직접 쓰거나 잠금 없이
  상태를 전이시키면 중복 이력이 다시 가능해진다. `question_proposal_review`에 부분
  unique 제약을 추가하는 마이그레이션을 후속 이슈로 검토할 가치가 있다.

### Concurrency and idempotency

- 판정 경로가 제안 행을 `PESSIMISTIC_WRITE`로 잠그므로 동시 판정은 DB 수준에서
  직렬화된다. 뒤늦은 트랜잭션은 잠금 대기 후 갱신된 상태를 읽어
  `INVALID_PROPOSAL_STATUS`로 거절된다. INT-001/002가 성공 건수뿐 아니라 진 쪽의
  예외 타입까지 단정해 이 경로를 고정한다.
- 잠금 도입 전에는 이 수렴이 보장되지 않았다. 상세는 5절을 참고한다.
- **테스트가 특정 인터리빙을 강제하지 못한다.** `CountDownLatch`는 두 스레드의
  출발만 맞추고, 실제로 두 트랜잭션이 겹쳤는지는 관측하지 않는다. 이 한계가 초기
  구현에서 결함을 놓치게 한 원인이다 — 로컬은 항상 한쪽 순서만 재현했고 CI가
  반대 순서를 만들어서야 드러났다. 지금은 진 쪽의 예외 타입까지 단정해 "어느
  방어선이 작동했는지"를 구분하지만, 인터리빙 자체를 강제하지 못한다는 한계는
  남는다. 잔여 위험으로 7절에 기록한다.
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
