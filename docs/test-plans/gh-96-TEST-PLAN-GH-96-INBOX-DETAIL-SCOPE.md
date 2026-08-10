# Test Plan: TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE

> Created at: `2026-08-10T21:30:11+09:00`
> GitHub Issue: `#96`
> Checkout branch: `test/gh-96-inbox-detail-scope`
> Repository TASK contract: `TASK.md` is for GitHub Issue #96
> Status: Implemented and verified — user approved in chat on 2026-08-10

## 1. Objective

수신함 상세 조회가 목록 및 답변 조회와 같은 열람 자격 경계를 적용하는지
검증한다. 사용자가 넘긴 질문글, 답변 없이 만료된 질문글, 차단한 발신자의
질문글을 같은 `postRecipientId`로 다시 열어 본문·사진·방향·거리 snapshot을
얻지 못해야 한다.

반대로 답변을 이미 작성한 수신자는 질문글이 만료된 뒤에도 열람할 수 있어야
하고, `SKIP_PENDING`은 되돌리기 유예 중이므로 계속 열 수 있어야 한다. 자격
없는 항목과 존재하지 않는 항목을 같은 빈 결과로 처리해 질문글 존재 여부를
추측할 수 없어야 한다.

현재 `findDetail()` 계약에는 조회 시각이 없으므로, 만료 경계를 DB 현재 시각에
암묵적으로 의존하지 않고 명시적인 `at`으로 판정하는 계약을 테스트 입력에
포함한다. 이 변경은 컨트롤러가 아직 없다는 이슈 전제를 따른다.

## 2. Scope

### Included

- 상세 조회의 수신자 소유권과 `post_recipient` 상태 자격 검증.
- 질문글 `ACTIVE`, `deleted_at IS NULL`, 활성 차단(`released_at IS NULL`) 스코프.
- `SKIPPED`와 답변 없이 만료된 `EXPIRED`의 상세 차단.
- 만료 후 `ANSWERED`와 만료 전 `SKIP_PENDING`의 상세 유지.
- 목록·상세·답변 열람 경로가 동일한 정책 조각을 공유하는 구조적 가드와
  Testcontainers 기반 PostgreSQL/PostGIS 통합 검증.
- 존재하지 않는 ID와 타인의 수신 항목에 대한 동일한 빈 결과 검증.

### Excluded

- `CAN_VIEW_ANSWERS_SQL`에 발신자 차단 조건을 추가하는 정책 변경. 이슈가
  수신자 선정 이슈로 명시적으로 제외한다.
- `10km` 거리 표시, 방향 필터, 답변 목록 projection 자체의 변경.
- 스키마 변경, Flyway migration, 운영 행 백필.
- 컨트롤러, 인증, 마이탭 `내 답변` 조회 경로 구현.
- 외부 API, 인프라, 배포와 프로덕션 데이터 변경.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #96 | `SKIPPED` 수신 항목의 상세가 조회되지 않는다. |
| GitHub Issue #96 | 답변 없이 만료된 `EXPIRED` 수신 항목의 상세가 조회되지 않는다. |
| GitHub Issue #96 | `ANSWERED` 수신 항목은 만료 후에도 상세가 조회된다. |
| GitHub Issue #96 | `SKIP_PENDING`은 되돌리기 유예 중 상세가 조회된다. |
| GitHub Issue #96 | 발신자를 차단하면 상세가 조회되지 않는다. |
| GitHub Issue #96 | 존재하지 않는 항목과 자격 없는 항목의 응답이 구분되지 않는다. |
| `JdbcInboxQueryRepository.java` | 현재 `findDetail()`은 소유권과 삭제 여부만 검사하고 목록의 `SCOPE_FILTER`와 상태·만료 조건을 적용하지 않는다. |
| `InboxQuerySql.java` | 목록의 공통 scope는 질문글 ACTIVE, 삭제되지 않음, 만료 전, 활성 차단 발신자 제외를 검사한다. 상세에서는 `ANSWERED` 만료 후 허용 예외를 별도로 보존해야 한다. |
| `PostAnswerQuerySql.java` | `ANSWERED`는 만료 후에도 자격을 유지하고, `AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING`은 만료 전일 때만 자격을 유지한다. |
| `InboxQueryIntegrationTest.java` | 목록의 SKIP_PENDING·만료·차단·소유권 회귀 픽스처와 `@DisplayName` 작성 관례를 따른다. |
| `PostAnswerQueryIntegrationTest.java` | 답변 조회의 SKIPPED 차단, ANSWERED 만료 후 유지, 만료 시각 기반 판정 관례를 따른다. |
| ADR-0002 / schema manifest | projection·권한 SQL은 JDBC와 실제 PostgreSQL/PostGIS 통합 테스트로 검증하며, 이번 변경은 기존 스키마를 유지한다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 상세가 소유권만 검사해 SKIPPED·EXPIRED 질문글 본문을 다시 노출함 | High | High | P0 | INT-001, INT-002 |
| 만료 조건을 모든 상태에 적용해 ANSWERED 수신자의 정당한 사후 열람을 막음 | High | Medium | P0 | INT-003 |
| SKIP_PENDING을 SKIPPED와 같은 종결 상태로 취급해 유예 중 되돌리기 화면을 막음 | High | Medium | P0 | INT-004 |
| 발신자 차단 조건이 상세에서 빠져 목록에서 사라진 글이 직접 조회됨 | High | High | P0 | INT-005 |
| `at` 없이 DB 현재 시각을 사용해 만료 경계가 flaky하거나 테스트와 운영 의미가 달라짐 | High | Medium | P0 | UNIT-001, INT-002, INT-003 |
| 목록·상세·답변 경로가 서로 다른 상태 집합을 가져 정책이 다시 분기됨 | High | Medium | P0 | UNIT-002, INT-007 |
| 존재하지 않는 ID와 권한 없는 ID의 응답 차이로 질문글 존재 여부가 노출됨 | High | Medium | P0 | INT-006 |
| 삭제된 질문글 또는 비활성 질문글이 recipient 상태만으로 반환됨 | Medium | Low | P1 | INT-007 |
| 공유 테스트 컨테이너의 전역 DELETE가 다른 통합 테스트 픽스처를 오염시킴 | Medium | Medium | P1 | 전용 region·cleanup 순서 및 단독 실행 증거 |
| 조회 전용 변경에 불필요한 트랜잭션/쓰기 부작용이 생김 | Medium | Low | P1 | UNIT-002, DB read-only 리뷰 |

## 5. Unit scenarios

단위 테스트는 순수 자격 정책 또는 구현이 도입한 공통 SQL/predicate builder를
대상으로 한다. 정책을 SQL 상수로 유지한다면 문자열 전체를 고정하지 말고,
`at` 바인딩·상태 집합·공유 fragment 사용이라는 계약만 검사한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-UNIT-001 | `at`이 만료 전·정확히 만료 시각 이후로 고정되고, 수신 상태가 `AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING/ANSWERED/SKIPPED/EXPIRED/BLOCKED` 중 하나다 | 공통 열람 자격 정책을 평가한다 | `ANSWERED`는 만료 전후 모두 자격 유지, `SKIP_PENDING`은 만료 전만 유지, `AVAILABLE/DISCOVERED/OPENED`는 만료 전만 유지, `SKIPPED/EXPIRED/BLOCKED`는 항상 상실한다 | P0 | Policy executor |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-UNIT-002 | 목록·상세·답변 SQL 또는 predicate builder의 소스 | 세 경로의 자격 조건을 비교한다 | 상태 집합과 `:at` 기반 시간 판정이 공통 정책 조각에서 공급되고, 상세만 소유권 조건을 추가한다. 상세에 `CURRENT_TIMESTAMP`나 별도 상태 문자열 복제가 없어야 한다 | P0 | Persistence-boundary executor |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-UNIT-003 | 상세 조회 repository/service 호출 계약 | `at` 없이 상세를 호출하거나 자격 없는 결과를 매핑한다 | 조회 시각이 명시적으로 전달되며, 빈 ResultSet은 예외나 상세 객체가 아니라 `Optional.empty()`로 반환된다 | P1 | Persistence-boundary executor |

## 6. Integration scenarios

모든 시나리오는 `PostgisContainerIntegrationTestSupport`를 확장한 신규
`InboxDetailScopeIntegrationTest`에 배치한다. 기존 통합 테스트 파일을 수정하지
않아 실행 에이전트 간 파일 충돌을 막는다. 상세·목록·답변 조회의 비교가 필요한
시나리오는 동일 픽스처를 한 테스트 메서드 안에서 생성한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-001 | `InboxQueryService.detail`, `JdbcInboxQueryRepository` | ACTIVE 질문글과 수신자 본인을 만들고 recipient를 `SKIPPED`로 seed한다. `skip_requested_at`, `skipped_at`, `capacity_released_at`을 채운다 | 같은 수신자의 `postRecipientId`로 고정 `at`에 상세 조회 | `Optional.empty()`이며 질문 문장·본문·사진 ID·거리 snapshot이 반환되지 않는다 | 전용 region 기준 FK 역순 DELETE |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-002 | `InboxQueryService.detail` | 질문글 `expires_at`이 `at`보다 과거이고 recipient는 `EXPIRED`이며 답변은 없다 | `postRecipientId`로 상세 조회. 가능하면 상태가 아직 `AVAILABLE`인 행도 별도 생성해 status 전이 여부와 무관한 시간 경계를 확인한다 | 답변 없이 만료된 수신 항목은 `Optional.empty()`다. `at`을 만료 직전으로 바꾼 활성 수신 항목은 조회 가능해 경계가 명시적이다 | 동일 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-003 | `InboxQueryService.detail`, `PostAnswerQueryService` | 만료된 ACTIVE 질문글, 수신자 `ANSWERED`, 공개 답변 1건을 seed한다 | 만료 후 `at`에 상세·`canView()`·답변 목록을 각각 조회 | 상세가 present이고, 답변 열람 자격이 true이며, 답변 목록이 1건이다. 목록 조회는 기존 목록 계약대로 만료 후 비어도 되며, 상세의 사후 열람을 목록 결과와 혼동하지 않는다 | 동일 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-004 | 상세·목록·답변 조회 | 만료 전 ACTIVE 질문글, recipient `SKIP_PENDING`, `skip_requested_at`을 seed한다 | 유예 중 `at`에 세 경로 조회 | 상세 present, UNANSWERED 목록에 포함, `canView()` true 및 답변 조회 가능이다. `SKIPPED`와 같은 결과가 나오면 실패다 | 동일 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-005 | `InboxQueryService.detail`, 목록 조회 | ACTIVE 질문글과 `AVAILABLE` recipient를 만들고 recipient가 sender를 활성 차단한다 | 차단 이후 동일 `at`에 상세 및 UNANSWERED 목록 조회 | 상세와 목록 모두 empty다. `released_at`을 채운 해제 행으로 바꾼 뒤에는 차단 조건이 해제되어 상세가 present가 되는지 확인한다 | 동일 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-006 | `InboxQueryService.detail` | (a) 존재하지 않는 `postRecipientId`, (b) 실제 행이지만 다른 recipientId인 자격 없는 항목을 준비한다 | 두 경우를 각각 상세 조회 | 두 호출 모두 `Optional.empty()`이며 예외 타입·메시지·응답 shape로 존재 여부를 구분할 수 없다 | 동일 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-007 | 상세·목록·답변 조회 및 질문글 상태 | 같은 수신자에게 ACTIVE/DELETED 질문글과 ACTIVE/비활성 질문글을 각각 연결하고 recipient는 `OPENED`로 둔다 | `at`에 상세·목록·답변 자격 조회 | 삭제되었거나 질문글 status가 ACTIVE가 아닌 글은 상세와 목록에서 제외된다. ACTIVE·미삭제·만료 전 글만 수신자 상태 규칙에 따라 반환된다 | 동일 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-008 | 상태/시간 매트릭스 회귀 | 동일 질문글에 전용 recipient 행을 `AVAILABLE`, `DISCOVERED`, `OPENED`, `SKIP_PENDING`, `ANSWERED`, `SKIPPED`, `EXPIRED`, `BLOCKED`로 각각 seed한다. `atBeforeExpiry`와 `atAfterExpiry`를 고정한다 | 각 행에 대해 상세와 `canView()`를 비교하고, 목록은 category 계약으로 별도 확인한다 | `atBeforeExpiry`: ANSWERED·SKIP_PENDING·일반 미종결 상태만 상세/답변 열람 가능. `atAfterExpiry`: ANSWERED만 유지. SKIPPED/EXPIRED/BLOCKED는 양쪽 모두 거부. 목록은 만료 후 ANSWERED도 제외하는 기존 계약을 유지한다 | 행 단위로 생성/삭제하거나 테스트 전후 전용 region 전체 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- 실제 PostgreSQL/PostGIS Testcontainers에서 `post_recipient`, `direction_post`,
  `approved_question`, `user_block`을 사용한다. H2로 대체하지 않는다.
- 스키마 변경은 없다. `post_recipient.status`와 timestamp check, `user_block`
  복합 PK/`released_at` 조건, 질문글 `status`·`deleted_at`을 실제 제약과
  SQL로 검증한다.
- 상세 조회는 `@Transactional(readOnly = true)` 경계를 유지하고, 조회 때문에
  상태·읽음·답변·차단 행이 변경되지 않는지 INT-001~008의 전후 조회로 확인한다.
- 만료 판정은 서비스 호출자가 넘긴 `at` 하나를 사용한다. DB `clock_timestamp()`
  또는 테스트 실행 시각에 의존하는 구현은 실패로 본다.
- `ANSWERED` 만료 후 예외 때문에 목록용 `SCOPE_FILTER`를 상세에 그대로 붙이지
  않도록 한다. 공통 post visibility와 상태/시간별 recipient eligibility를
  분리해 공유하는지 코드 리뷰와 UNIT-002에서 확인한다.

### Concurrency and idempotency

- 이슈 변경은 read-only query라 새로운 write idempotency 계약은 없다. 두 동일
  조회의 결과가 달라지는 것을 검증하려면 공유 컨테이너에서 경쟁적으로 seed를
  변경하지 말고, 커밋된 동일 fixture와 동일 `at`으로 순차 호출한다.
- 별도 동시성 테스트는 P0가 아니다. 다만 구현이 상세 조회 중 상태를 갱신하거나
  `FOR UPDATE`를 추가하면 범위 이탈로 FAIL 처리하며, 리뷰에서 트랜잭션 경계를
  확인한다.
- 통합 테스트는 Testcontainers 공유 상태를 사용하므로 병렬 실행을 허용하지
  않는다. 동시 실행이 필요하면 전용 database/schema 격리가 먼저 승인되어야
  한다.

### External APIs

- 외부 API 연동은 없다. mock, LocalStack, 네트워크 자격 증명을 추가하지 않는다.

### Failure recovery and reconciliation

- 조회는 변경을 남기지 않으므로 보상 트랜잭션·백필·복구 작업은 없다.
- SQL 오류나 컨테이너 기동 실패는 구현 실패로 숨기지 않고 테스트 환경 실패로
  분리 보고한다. 실패 명령, 오류 요약, 재현 조건, 미검증 시나리오와 잔여 위험을
  `docs/reports/tests/` 보고서에 기록한다.
- 애플리케이션이 예외를 던져 존재하지 않는 ID와 권한 없는 ID를 구별하게 되면
  INT-006 실패이며, 오류 메시지 suppress로 통과시키지 않는다.

## 8. Test data and isolation

- **Fixtures**: `InboxQueryIntegrationTest`와 `PostAnswerQueryIntegrationTest`의
  `account`, `post`, `recipient`, `answer` helper 형태를 복제하되 기존 파일은
  수정하지 않는다. 전용 region code는 `TEST-INBOX96`을 사용한다.
- **Statuses**: 각 status에 필요한 `discovered_at`, `opened_at`, `skip_requested_at`,
  `skipped_at`, `expired_at`, `capacity_released_at`을 V1/V2 check에 맞게 채운다.
  `ANSWERED` 행은 실제 published answer를 연결하고, `EXPIRED` 행은 답변을
  연결하지 않는다.
- **Database isolation**: `@BeforeEach`에서 `answer_reaction` → `post_reaction`
  → `answer` → `media_attachment` → `post_recipient` → `post_audience` →
  `direction_post` → `approved_question` → `user_block` → 전용 region의
  `user_account` → `region_code` 순으로 FK 역순 정리한다. 공용 `KR` seed는
  `ON CONFLICT DO NOTHING`으로 보존한다.
- **Clock/randomness**: `Instant.parse(...)`의 고정 시각만 사용한다. 만료 직전,
  정확히 만료, 만료 직후를 각각 초 단위로 지정하고 `Instant.now()`를 사용하지
  않는다.
- **External API doubles**: 없음.
- **Cleanup**: 테스트 후에도 전용 region만 삭제하며 다른 테스트의 region이나
  사용자를 broad DELETE로 제거하지 않는다. 공유 컨테이너를 사용하는 기존
  integrationTest와 동시 실행하지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Production executor | `src/main/java/com/dnd/qello/feed/repository/jdbc/JdbcInboxQueryRepository.java`, `src/main/java/com/dnd/qello/feed/repository/jdbc/sql/InboxQuerySql.java`, `src/main/java/com/dnd/qello/feed/repository/InboxQueryRepository.java`, `src/main/java/com/dnd/qello/feed/service/InboxQueryService.java`, 그리고 필요 시 공통 feed scope SQL 파일 | 구현 범위(테스트 ID 없음) | `./gradlew compileJava` |
| 2 | Answer-scope executor | `src/main/java/com/dnd/qello/feed/repository/jdbc/JdbcPostAnswerQueryRepository.java`, `src/main/java/com/dnd/qello/feed/repository/jdbc/sql/PostAnswerQuerySql.java`, `src/main/java/com/dnd/qello/feed/repository/PostAnswerQueryRepository.java` — 공통 정책을 위해 실제 필요한 파일만 | UNIT-001~003, INT-003~004, INT-008 | `./gradlew test --tests "*FeedPersistenceBoundaryTest"` 및 compile |
| 3 | Unit executor | `src/test/java/com/dnd/qello/feed/FeedPersistenceBoundaryTest.java` 또는 승인된 신규 `src/test/java/com/dnd/qello/feed/InboxDetailScopePolicyTest.java` | UNIT-001~003 | `./gradlew test --tests "*FeedPersistenceBoundaryTest" --tests "*InboxDetailScopePolicyTest"` |
| 4 | Integration executor | 신규 `src/integrationTest/java/com/dnd/qello/InboxDetailScopeIntegrationTest.java`; 기존 `src/integrationTest/java/com/dnd/qello/InboxQueryIntegrationTest.java`는 명시적 `at` 인자 전달로 컴파일 계약만 갱신 | INT-001~008 및 기존 상세 소유권 회귀 | `./gradlew integrationTest --tests "*InboxDetailScopeIntegrationTest" --tests "*InboxQueryIntegrationTest"` |
| 5 | Independent verifier | 구현·테스트 파일 전체(수정 금지) | 모든 시나리오 및 회귀 | `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` |

### Ownership and handoff

- Order 1~2는 구현 에이전트가 맡고, Order 3~4는 구현 에이전트와 독립된
  테스트 실행 에이전트가 맡는다. 같은 파일을 두 실행자가 수정하지 않는다.
- 테스트 실행 에이전트는 승인된 계획의 시나리오 ID와 파일 소유권을 벗어나지
  않는다. 기존 `InboxQueryIntegrationTest`는 `detail()`의 명시적 `at` 계약에
  맞추는 호출부만 수정하고, 기존 assertion·시나리오 의미는 바꾸지 않는다.
  `PostAnswerQueryIntegrationTest`는 회귀 실행만 하고 수정하지 않는다.
- 통합 검증 전에 `git status --short`로 사용자 변경을 재확인한다. 실패 시
  테스트를 완화하거나 기존 실패를 suppress하지 않는다.

## 10. Completion criteria

- [x] 모든 P0 시나리오(UNIT-001~002, INT-001~006, 필요 시 INT-008의 P0 행)를 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 모든 테스트 클래스 상단에 정확한 ISO 8601 생성 시각과 source scenario ID
- [x] 단위 테스트 통과
- [x] `InboxDetailScopeIntegrationTest` 통합 테스트 통과
- [x] 기존 `InboxQueryIntegrationTest`와 `PostAnswerQueryIntegrationTest` 회귀 통과
- [x] 스키마·migration 변경이 없음을 확인
- [x] 애플리케이션·DB·트랜잭션·동시성·외부 연동·장애 복구 관점 잠재 문제 분석
- [x] `templates/test-report.md` 형식의 테스트 보고서 생성
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록

## 11. Failure judgement

- `INT-001`, `INT-002`, `INT-003`, `INT-004`, `INT-005`, `INT-006` 중 하나라도
  실패하면 `FAIL`이다.
- `UNIT-002`가 실패하면 정책이 세 조회 경로에서 다시 분리될 위험이 있으므로
  `FAIL`이다.
- Testcontainers·Docker·Gradle dependency 문제로 검증하지 못한 경우 구현
  성공으로 표현하지 않고 `BLOCKED` 또는 테스트 환경 실패로 보고한다.
- 목록의 기존 만료 계약을 바꾸거나 `CAN_VIEW_ANSWERS_SQL` 차단 정책까지
  변경하면 이 테스트 계획 범위를 벗어나므로 `FAIL`이다.

## 12. Human approval

- Reviewer: User approval in chat
- Decision: Approved for implementation and test execution
- Approved at: 2026-08-10 (chat)
