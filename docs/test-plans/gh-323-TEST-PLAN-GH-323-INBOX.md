# Test Plan: TEST-PLAN-GH-323-INBOX

> Created at: `2026-10-07T18:23:37+09:00`
> GitHub Issue: `#323`
> Status: Approved — 사람 승인 완료

## 1. Objective

프론트가 내게 온 질문의 미답변·답변 완료 항목을 한 번 받아 status로 구분한다.
category 없는 조회가 항목을 누락하거나 종료·타인·비노출 항목을 노출하지 않는지 검증한다.
방향 칩은 미답변만 집계하고 기존 snapshot 일관성과 페이지네이션 없는 반환을 유지한다.

## 2. Scope

### Included

- HTTP category 제거, 내부 ALL 합집합 조회, status 유지.
- AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING/ANSWERED 포함.
- 만료 경계, SKIPPED/EXPIRED/BLOCKED 및 기존 가시성·소유권 제외.
- 선택 방향 필터 유지, 칩은 방향 필터와 무관한 미답변 전체 집계.
- 최신순·동률 ID 정렬, 5개 초과 조회, REPEATABLE_READ 회귀.
- OpenAPI 생성 검증과 보낸 질문 계약 불변 확인.

### Excluded

- 보낸 질문의 필터·커서·반환 구조 변경.
- 답변 제출/공개, 수신 용량 정책, 만료·넘김 worker와 상세 열람 정책 변경.
- 신규 DB schema, 외부 API·배포·인프라·성능 벤치마크.
- 프론트 구현 및 실제 운영 데이터 사용.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #323 | category 없이 미답변·답변 완료 합집합, 미답변 chips, 만료 제외, 5개 초과 전체 반환 |
| 사용자 대화 및 화면 | 답변 완료 화면에 방향 필터가 없으며 프론트에서 status로 분류 |
| TASK.md / AGENTS.md | 작업 식별자·역할·허용 파일, 사람 테스트 계획 승인과 독립 검증 |
| InboxQuerySql / FeedScopeSql | 기존 소유권·공개·차단·신고 조건 및 expires_at > 조회 시각 유지 |
| InboxApplicationService.list | REPEATABLE_READ, 같은 Clock 조회 시각과 계정 자격 |
| InboxApiSpec / InboxListingResponse | 인증 요청, ApiResponse, cards.status와 개인정보 비노출 |
| src/test/AGENTS.md / src/integrationTest/AGENTS.md / test-policy | JUnit 5, DisplayName, 생성 시각·원본 시나리오 헤더와 테스트 보고서 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| ALL 조건을 무조건 조회로 구현해 종료·비노출 상태까지 포함 | 비공개 항목 노출 | 중간 | P0 | 명시적 허용 상태·가시성 통합 검사 |
| ANSWERED 누락 또는 답변 이후 유효 항목을 5개로 자름 | 화면 목록 누락 | 중간 | P0 | 혼합 상태 및 5개 초과 통합 검사 |
| 만료 시각과 같은 항목 포함 | 만료 질문 노출 | 중간 | P0 | 직전·동일·직후 Clock 경계 |
| 칩에 ANSWERED까지 집계 | 미답변 방향 수 오류 | 높음 | P0 | 같은 방향·다른 방향 혼합 상태 집계 |
| ALL 경로에서 목록·칩 snapshot 분리 | 동시 조회 불일치 | 중간 | P0 | 조회 사이 별도 transaction commit 검증 |
| 방향 필터·정렬·공통 응답 및 기존 category 경로 퇴행 | 화면 전환 오류 | 중간 | P1 | 필터·정렬·기존 테스트 회귀 |
| 보낸 질문까지 같이 변경 | 범위 위반 | 낮음 | P0 | 변경 파일·OpenAPI 경로 diff 독립 검토 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-323-INBOX-UNIT-001 | 인증 subject 및 AVAILABLE/ANSWERED 카드가 있는 서비스 결과 | category 없는 GET /api/v1/direction/inbox | 내부 ALL 조회 호출, 두 상태를 그대로 ApiResponse로 노출, 기존 개인정보 비노출 | P0 | inbox-executor |
| TEST-PLAN-GH-323-INBOX-UNIT-002 | 선택 방향 N / 방향 생략 요청 | 목록 HTTP 호출 | directionSegmentKey를 그대로 전달하며 category 요청 binding을 하지 않음 | P1 | inbox-executor |
| TEST-PLAN-GH-323-INBOX-UNIT-003 | 변경한 InboxApiSpec 및 기존 목록 응답 | 계약·MockMvc 회귀 검사 | category 파라미터 없음, 인증 숨김 및 선택 방향 유지, 기존 media·상세·명령 응답 계약 유지 | P1 | inbox-executor |

기존 단위 테스트는 불필요한 재작성 없이 해당 HTTP signature 및 ALL 위임 기대만 갱신한다.
컴파일에 불필요한 내부 category signature 변경은 하지 않는다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-323-INBOX-INT-001 | QueryService + JDBC + PostGIS DB | 본인 미답변 4개 상태, ANSWERED, SKIPPED/EXPIRED/BLOCKED, 타인 항목 | ALL 조회 | 허용 5개 상태만 반환, 타인·종료 제외 | 기존 fixture reset/transaction rollback |
| TEST-PLAN-GH-323-INBOX-INT-002 | 조회 SQL + Clock | 미답변·ANSWERED 각각 만료 직전·동일·직후 | 고정 시각 ALL 조회 | expires_at > at만 포함, 만료 worker 실행 없이 즉시 제외 | 기존 DB 격리 |
| TEST-PLAN-GH-323-INBOX-INT-003 | QueryService + 칩 집계 SQL | 같은 방향 미답변·ANSWERED와 다른 방향 ANSWERED | ALL 조회 | 카드에는 두 종류, chips는 미답변만 집계, 답변 완료만 있으면 chips 빈 배열 | 기존 fixture 정리 |
| TEST-PLAN-GH-323-INBOX-INT-004 | 카드·방향 SQL | 여러 방향에 두 종류 카드, 공백·알 수 없는 방향 | 방향 생략/선택/공백/미등록 키 조회 | 기존 방향 조건 유지, chips는 전체 미답변 기준, 생략/공백은 전체 방향 | 기존 fixture 정리 |
| TEST-PLAN-GH-323-INBOX-INT-005 | ALL 조회 + 정렬 | 미답변 5개 이하와 ANSWERED를 합쳐 5개 초과, 동일 matched_at fixture | ALL 조회 | 전체 반환, matched_at DESC/id DESC 순서, 임의 LIMIT 없음 | 기존 DB 격리 |
| TEST-PLAN-GH-323-INBOX-INT-006 | ApplicationService + JDBC + 2개 transaction | 최초 목록 후 새 미답변을 별도 transaction에 commit | ALL 목록 및 chips 조회 | 새 항목이 같은 호출의 chips에만 섞이지 않음, 기존 REPEATABLE_READ 보장 | fixture reset 및 spy reset |
| TEST-PLAN-GH-323-INBOX-INT-007 | 기존 보안·가시성 회귀 | 차단·신고·삭제·비공개·계정 자격 기존 fixture | 기존 테스트 + ALL 경로 대표 차단/비노출 fixture | 두 종류 모두 기존 비노출 조건과 계정 gate를 유지 | 기존 fixture 정리 |
| TEST-PLAN-GH-323-INBOX-INT-008 | Springdoc OpenAPI | 테스트 애플리케이션 및 기존 생성 테스트 | OpenApiSpecificationIntegrationTest | inbox category 없음, status/인증/방향 계약 일치, 보낸 질문 operation 계약 불변 | 테스트 컨테이너 종료 |

## 7. Cross-cutting scenarios

### Database and transactions

- 실제 PostgreSQL/PostGIS Testcontainers를 사용한다. H2로 DB 조건을 대체하지 않는다.
- ALL 조회는 허용 상태 합집합만 확장하고 SCOPE_FILTER·계정 gate·Clock을 유지한다.
- 기존 read-only REPEATABLE_READ를 보존하며 별도 transaction commit을 이용해 snapshot을 확인한다.
- schema·migration·write 동작 없음. 기존 상세 open rollback·권한 테스트는 회귀 실행한다.

### Concurrency and idempotency

- INT-006에서 목록과 칩의 동시성 일관성을 검증한다.
- 조회는 상태·슬롯을 변경하지 않는다. 새 멱등 쓰기나 대량 동시성 시험은 해당 없음.

### External APIs

- 외부 공급자 연동 변경 없음. 기존 미디어 mock/fixture만 사용한다.
- 실제 자격 증명이나 운영 API 호출 없음.

### Failure recovery and reconciliation

- DB 오류 응답·상세 rollback은 기존 회귀 테스트를 실행한다. 새 오류 복구 흐름은 없다.
- 검증 실패를 suppress하지 않으며 실패 명령·영향·재검증 방법을 보고한다.
- 복구는 기능 commit revert로 기존 category 계약을 되돌린다. DB 복구 불필요.

## 8. Test data and isolation

- Fixtures: 기존 synthetic account/post/recipient/answer 및 direction fixture를 재사용한다.
- Database isolation: 각 클래스의 기존 reset/transaction rollback 계약을 유지한다.
- Clock/randomness: 고정 Instant, 만료 경계 및 matched_at tie를 명시적으로 구성한다.
- External API doubles: 기존 media fixture/mock, 새 외부 API 없음.
- Cleanup: 기존 Testcontainers lifecycle, fixture reset, spy reset을 따른다.
- 실제 자격 증명·운영 식별자·.env 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 부모 오케스트레이터 | TASK.md, 이 계획 | 요구사항·파일·시나리오 계약 | Issue/branch/TASK 일치와 문서 공백 검사 |
| 2 | inbox-executor (승인 후 호출) | TASK의 production 6개, 아래 테스트 5개, 생성 OpenAPI, 테스트 보고서 | UNIT-001~003, INT-001~008 | 승인 후 기존 기준선·실패 테스트·구현·단위/통합 회귀, OpenAPI 생성 |
| 3 | inbox-verifier (구현 후 독립 호출) | 소스 수정 권한 없음 | 전체 시나리오·범위 | 실제 diff·JUnit 결과·필수 검사·보낸 질문 계약 불변 |

승인된 production 파일은 TASK.md의 목록에 한정한다.
테스트 소유 파일:
- src/test/java/com/dnd/qello/feed/web/InboxApiMockMvcTest.java
- src/test/java/com/dnd/qello/feed/web/InboxWebContractTest.java
- src/integrationTest/java/com/dnd/qello/InboxQueryIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/InboxDirectionChipIntegrationTest.java
- src/integrationTest/java/com/dnd/qello/InboxListIsolationIntegrationTest.java

변경 없는 기존 테스트와 OpenApiSpecificationIntegrationTest도 회귀 실행할 수 있다.
추가 파일 변경 필요 시 부모에게 근거를 보고하고 범위를 먼저 확인한다.
실행 에이전트는 혼자 작업하는 것이 아니며 다른 사람의 변경을 되돌리지 않는다.
새 테스트의 DisplayName 및 클래스의 ISO 8601 생성 시각·원본 시나리오 헤더를 정책에 맞춘다.

### Commands after approval

Java 21, Docker/Testcontainers 및 프로젝트 의존성이 필요하다.

```bash
./gradlew test
./gradlew integrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

전체 integrationTest가 생성 OpenAPI 검증을 포함한다.
검사 실패·필수 환경 부족은 FAIL/BLOCKED로 보고하며 미실행을 PASS로 표현하지 않는다.
독립 검증자는 실패를 통과시키려고 소스를 수정하지 않는다.

## 10. Completion criteria

- [x] 사람 테스트 계획 승인 후 테스트 작성·실행 및 production 구현
- [x] 모든 P0 시나리오 구현과 P1 기존 계약 회귀
- [x] 모든 테스트 메서드 DisplayName 및 클래스 timestamp/source scenario 검증
- [x] 단위·통합 테스트 및 필수 하네스 검사 통과
- [x] category 제거·혼합 상태·미답변 chips를 생성 OpenAPI와 코드로 확인
- [x] 보낸 질문 계약과 범위 밖 파일 불변 확인
- [x] 잠재 문제 분석 및 templates/test-report.md 기반 보고서 생성
- [x] 구현자와 독립 검증자의 증거 분리, 실패·차단 항목 없음

실행 결과는 `docs/reports/tests/gh-323-TEST-REPORT-GH-323-INBOX.md`에 기록했다.
전체 단위 1,225개·통합 794개 및 필수 검사 통과 후, 별도 검증자가 명세·코드 품질·실행 증거를 PASS로 확인했다.

## 11. Human approval

- Reviewer: 현재 부모 세션의 사용자
- Decision: APPROVED
- Approved at: `2026-10-07T19:33:51+09:00` (승인 기록 시각)
- Note: 현재 부모 세션에서 사용자가 “테스트 계획 승인할게”라고 명시적으로 승인했다. 승인 범위는 이 계획과 TASK.md의 허용 파일·시나리오이다.
