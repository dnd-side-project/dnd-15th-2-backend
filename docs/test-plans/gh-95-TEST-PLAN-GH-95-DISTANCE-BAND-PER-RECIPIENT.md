# Test Plan: TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT

> Created at: 2026-08-10T17:20:19+09:00
> GitHub Issue: #95
> Checkout branch: fix/gh-95-distance-band-per-recipient
> Repository TASK contract: TASK.md is for GitHub Issue #95
> Status: IMPLEMENTED_PENDING_FULL_VERIFICATION — user clarified viewer-to-question-origin distance policy

## 1. Objective

같은 방향 글을 여러 수신자에게 발송할 때 각 수신자의 실제 후보 거리로
post_recipient.distance_band를 서버가 독립적으로 파생하는지 검증한다. 임의의
호출자 문자열이 모든 수신자에게 복사되지 않아야 하며, distance_m 스냅샷과
수신함 카드와 답변 목록의 노출 규칙(근거리 하한 미만은 `10km 이내`만, 하한 이상은
정확 거리만)이 서로 어긋나지 않아야 한다. 답변 목록의 거리는 답변 작성자가 아니라
현재 조회자와 질문 원점 사이의 거리다.

`10km 미만 → 10km 이내`, `10km 이상 → 정확 거리`를 본 이슈의 표시 정책으로
확정하고, DB의 NOT NULL 제약을 위해 원거리 저장에는 `EXACT_DISTANCE` 내부 표식을
사용한다. 이 표식은 API 응답으로 노출하지 않는다.

## 2. Scope

### Included

- DirectionCandidate.distanceMeters를 입력으로 하는 단일 거리→band 파생 정책.
- DirectionPostService.send()의 후보별 PostRecipient 저장값과 distance_m 보존.
- SendCommand에서 호출자가 band를 주입할 수 없는 계약. 호환 필드를 남기는 경우에도
  그 값이 저장 결과에 영향을 주지 않는지 검증한다.
- 3km와 900km처럼 한 발송 안에서 거리가 다른 후보들의 저장 결과가 서로 다른지 검증.
- near-distance-floor-m 경계(하한 직전·정확히 하한·하한 직후)의 카드 노출 회귀.
- 답변 목록에서 현재 조회자의 post_recipient.distance_m을 사용한 거리 노출.

### Excluded

- 스키마 구조 변경, Flyway migration, 컬럼 삭제와 기존 운영 행 백필. Issue 본문이
  스키마 변경 없음을 전제로 하므로, 기존 행의 재분류 여부는 별도 운영 결정으로 남긴다.
- 근거리 하한 10,000m 자체의 재설계.
- 답변 저장 모델의 answer.distance_m을 조회자별 표시값으로 재작성하는 작업.
- 좌표를 API·로그·분석 이벤트에 노출하는 구현이나 외부 API 연동.
- 컨트롤러·인증·클라이언트 UI 구현.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #95 | 같은 발송의 수신자도 각자 거리로 band를 갖고, 호출자 임의 문자열 주입이 불가능하며, 하한 경계 카드 노출이 정확해야 한다 |
| DirectionPostService.java:75-82 | 후보에 이미 distanceMeters가 있으나 현재 command.distanceBand()를 모든 수신자에게 복사한다 — 핵심 재현 지점 |
| DirectionPostService.java:169-189 | 현재 SendCommand에 distanceBand가 입력 필수로 존재한다 — 계약 변경/호환 여부를 검증해야 한다 |
| DirectionCandidate.java:8-43 | 후보 거리는 BigDecimal이고 음수는 거절된다 — 매핑 정책은 후보 실제 거리에서 파생해야 한다 |
| InboxQuerySql.java:13-25 | distance_m < nearFloor이면 band만, 그 외에는 정확 거리만 ResultSet에 싣는다 |
| InboxQueryIntegrationTest.java:264-290 | 10,000m 직전·정확히·직후의 기존 카드 경계 회귀 테스트 |
| V8__widen_answer_visibility_to_recipients.sql:121-122,169-170 | distance_band는 근거리 표시용이고 answer.distance_m은 post_recipient.distance_m과 같은 스냅샷이어야 한다 |
| 사용자 요구사항 | 10km 미만은 `10km 이내`, 10km 이상은 정확 거리이며 답변도 보는 사람 기준이다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 발송 커맨드의 한 문자열이 모든 수신자에게 저장되어 거리 표시가 오염됨 | High | High | P0 | INT-001 |
| 거리→band 구간이 여러 코드 위치에 복제되어 경계별 결과가 갈라짐 | High | Medium | P0 | UNIT-001, INT-002 |
| distanceMeters를 정수로 먼저 자르거나 double로 변환해 경계에서 잘못된 band를 선택함 | High | Medium | P0 | UNIT-002, INT-002 |
| band는 올바르게 저장됐지만 수신함 SQL이 하한 이상에서도 band를 노출하거나 정확 거리와 함께 노출함 | High | Medium | P0 | INT-003, 기존 InboxQuery 회귀 |
| 호환용 입력 필드가 남아 호출자가 악의적/임의 band를 주입함 | High | Medium | P0 | UNIT-003, INT-001 |
| send 재시도에서 후보가 달라지거나 입력 문자열이 달라져 기존 snapshot이 바뀜 | Medium | Medium | P1 | INT-004 |
| 현재 생산 경로가 없는 answer.distance_band를 임의로 새로 만들어 범위를 확장함 | Medium | Medium | P1 | DEC-001, 조건부 INT-005 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-001 | near-distance-floor-m=10,000과 대표 경계 거리 | canonical policy를 호출 | 9,999m는 `10km 이내`, 10,000m·10,001m는 `EXACT_DISTANCE`로 매핑된다 | P0 | Distance-policy executor |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-002 | 하한 설정값 9,000m와 음수 거리 | 매핑 정책 호출 | 8,000m는 `9km 이내`, 9,000m는 `EXACT_DISTANCE`가 되며 음수는 전역 feed 오류로 변환된다 | P0 | Distance-policy executor |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-003 | SendCommand의 새 계약 | command 생성 및 send 호출 | 호출자 band 필드가 없어 임의 문자열을 전달할 수 없고, 저장 결과는 후보 거리로 결정된다 | P0 | Direction-service executor |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-004 | 동일한 거리와 동일한 정책 버전 | 순서·반복 호출 | 항상 같은 band를 반환하고 전역 mutable 상태나 발송 순서에 의존하지 않는다 | P1 | Distance-policy executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-001 | DirectionPostService.send, PostGIS candidate query, PostRecipientRepository | 같은 발송에 3km와 900km 후보를 만들고, 수신 상태·활성 질문·방향 scheme을 준비한다 | 한 번의 send 실행 | 두 수신자 모두 실제 distance_m을 snapshot하고, 각 행의 distance_band는 자신의 거리 결과(`10km 이내`/`EXACT_DISTANCE`)와 일치한다. | 고유 TEST region/idempotency key로 recipient→audience→post→presence→user fixture 순서 삭제 |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-002 | DirectionPostService.send + PostGIS distance calculation | band 경계에 해당하는 후보들을 승인된 거리표에 맞춰 준비한다. 소수점 경계가 실제 PostGIS 반환값으로 가능한지 먼저 확인한다 | send 실행 후 DB의 distance_m, distance_band 조회 | 저장된 band가 서비스가 받은 후보 거리와 같은 기준으로 계산된다. 후보 거리와 저장값이 달라서 다른 band를 선택하는 조기 절삭이 없고, 불가능한 정밀도는 테스트 보고서에 명시된다 | 동일 |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-003 | InboxQueryService, JdbcInboxQueryRepository, InboxQuerySql | 동일 발송의 수신자 행을 카드 조회 대상으로 만들고 nearDistanceFloorM=10000을 사용한다 | 수신함 조회 | 9,999m는 distanceM=null·승인된 band만, 10,000m와 10,001m는 정확 distanceM만 반환하고 distanceBand=null이다. 두 필드는 항상 상호 배타적이다 | 동일 |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-004 | send idempotency + recipient snapshot | 같은 sender/idempotency key로 1차 send 후 후보 presence 또는 호환 입력값을 바꾼다 | 동일 key로 send 재시도 | 첫 발송의 post_recipient.distance_m·distance_band가 그대로 반환되고 새 recipient 행·slot reserve가 생기지 않는다 | 동일 |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-005 | PostAnswerQueryService, JdbcPostAnswerQueryRepository | 한 질문에 5km 수신자와 15km 수신자를 만들고 답변 작성자의 answer.distance_m은 별도 값으로 둔다 | 각 수신자로 답변 목록 조회 | 현재 조회자의 post_recipient.distance_m으로 5km는 `10km 이내`, 15km는 정확 거리만 반환한다 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- send()에서 band 파생 또는 유효성 검사가 실패하면 post, audience, recipient와
  recipient_receive_state reserve가 함께 rollback되는지 INT-001/002의 실패 변형으로
  확인한다. 특히 첫 번째 후보 저장 후 두 번째 후보의 매핑 오류가 발생했을 때 partial
  recipient와 슬롯만 남아서는 안 된다.
- 스키마 변경은 이 계획의 대상이 아니다. post_recipient.distance_band와
  answer.distance_band의 NOT NULL 제약을 만족하는지, 별도 migration/backfill이
  필요한지 구현 전 결정 기록만 남긴다.
- SQL 카드 projection은 기존 nearFloor 설정을 사용해야 하며, band 계산 정책의
  경계와 카드 노출 하한을 같은 값이라고 가정하지 않는다. 전자는 저장 정책, 후자는
  개인정보 노출 정책이다.

### Concurrency and idempotency

- 같은 idempotency key의 동시/반복 send는 기존 post를 반환하고 band를 재계산해 덮어쓰지
  않아야 한다. 최소 INT-004를 순차 재시도로 수행하고, 구현이 후보 저장을 별도
  비동기화하면 동시 실행 시나리오를 추가한다.
- 동일 발송 안의 후보들은 독립적으로 band를 계산해야 한다. 공유 mutable command 값,
  stream 외부 변수, 마지막 후보의 band 재사용을 금지하는 assertion을 INT-001에 둔다.
- distance_band 계산은 외부 API나 실시간 좌표 재조회가 아니라 send transaction의
  DirectionCandidate.distanceMeters snapshot을 사용해야 한다.

### External APIs

- 없음. PostGIS는 Testcontainers 내부 DB 경계이며 외부 API double은 필요하지 않다.

### Failure recovery and reconciliation

- 정책표에 없는 거리, 음수/누락 후보, 매핑 예외가 발생하면 명시적 validation failure로
  끝나고 partial DB write가 없어야 한다. 후보 생성자가 음수를 이미 거절하더라도
  repository/service 경계에서의 null·정밀도 실패를 별도로 기록한다.
- 기존 행의 band 재분류는 운영 데이터 조회·백필 결정이 없는 상태에서 테스트가 대신
  수행하지 않는다. 구현자가 migration을 추가하려면 별도 Issue와 승인된 계획이 필요하다.

### Decision-needed scenarios

| ID | Decision needed | Why it blocks execution |
| --- | --- | --- |
| DEC-001 | RESOLVED | 10km 미만은 `10km 이내`, 10km 이상은 정확 거리. 저장 제약용 원거리 내부 표식은 `EXACT_DISTANCE` |
| DEC-002 | RESOLVED | SendCommand.distanceBand 제거 |
| DEC-003 | RESOLVED | answer.distance_band는 답변 표시에서 사용하지 않고, 조회자 post_recipient 거리로 projection |
| DEC-004 | EXCLUDED | 기존 저장 행 백필은 별도 운영 결정 범위 |

## 8. Test data and isolation

- Fixtures: 고유 TEST region, sender, 활성 질문, 8-segment scheme, sender/recipient
  presence, 후보별 거리 3km·9,999m·10km·10,001m·900km, receive-state row.
- Database isolation: 기존 PostgisContainerIntegrationTestSupport와 @ActiveProfiles("test");
  시나리오별 고유 idempotency key를 사용해 다른 테스트와 충돌하지 않는다.
- Clock/randomness: 고정 Instant; 현재 시각·랜덤 좌표를 사용하지 않는다. PostGIS가
  산출한 실제 거리와 의도한 거리의 오차 허용 범위는 DEC-001 승인 시 정한다.
- External API doubles: 없음.
- Cleanup: 테스트 전후 post_recipient, post_audience, direction_post,
  recipient_receive_state, presence, scheme/segment, approved question, test user를
  FK 역순으로 정리한다. 다른 테스트의 seed는 삭제하지 않는다.

실제 자격 증명이나 .env 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Distance-policy executor | src/main/java/com/dnd/qello/feed/config/DistanceBandPolicy.java, src/test/java/com/dnd/qello/feed/config/DistanceBandPolicyTest.java | UNIT-001, UNIT-002 | ./gradlew test --tests "com.dnd.qello.feed.config.DistanceBandPolicyTest" |
| 2 | Direction send integration executor | src/integrationTest/java/com/dnd/qello/DirectionPostDistanceBandIntegrationTest.java (신규) | INT-001, INT-002, INT-004 | ./gradlew integrationTest --tests "com.dnd.qello.DirectionPostDistanceBandIntegrationTest" |
| 3 | Feed/answer regression executor | InboxQueryIntegrationTest.java, PostAnswerQueryIntegrationTest.java, AnswerCard.java, JdbcPostAnswerQueryRepository.java | INT-003, INT-005 | ./gradlew integrationTest --tests "com.dnd.qello.InboxQueryIntegrationTest" --tests "com.dnd.qello.PostAnswerQueryIntegrationTest" |
| 4 | Verification executor | 테스트 파일 외 production 변경 없음; docs/reports/tests/gh-95-DISTANCE-BAND-PER-RECIPIENT.md | 모든 P0, 회귀·잠재 문제 | ./harness check, ./harness pr-ready --project-tests, npm run hooks:validate, git diff --check |

현재 실행에서는 Executor 1~3의 구현과 관련 회귀 테스트를 수행했다. 전체 하네스 검증과
멱등성·실패 rollback 검증 결과는 별도 보고서에 기록한다.

## 10. Completion criteria

- [x] DEC-001~DEC-004가 결정 또는 명시적 범위 제외로 기록됨
- [x] #95 전용 Issue branch와 TASK.md 계약이 일치함
- [x] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 @DisplayName
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 관련 통합 테스트 통과
- [x] 3km/900km 후보의 distance_band가 개별 파생됨
- [x] 9,999m/10,000m/10,001m 카드 노출이 정확히 상호 배타적임
- [x] 답변 목록이 현재 조회자의 질문 원점 거리를 표시함
- [x] 하한 설정과 저장 band·조회 표시 문구가 같은 정책을 사용함
- [ ] send 실패·재시도에서 partial write와 band snapshot 변경이 없음
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer:
- Decision:
- Approved at:
