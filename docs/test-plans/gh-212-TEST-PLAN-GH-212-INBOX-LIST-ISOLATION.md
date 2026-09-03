# Test Plan: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION

> Created at: `2026-09-02T19:12:00+09:00`
> GitHub Issue: `#212`
> Status: Approved

## 1. Objective

`InboxApplicationService.list()`가 바깥 `READ_COMMITTED` 트랜잭션을 연 뒤
`InboxQueryService.list()`의 `REPEATABLE_READ`에 참여해, 목록 SELECT와
direction chip 집계가 서로 다른 snapshot을 볼 수 있는 결함을 막는다.

실패 시 사용자는 같은 호출에서 카드 목록과 칩 숫자가 어긋난 수신함을 본다.
조회 도중 새로 매칭된 글이 칩에만 나타나거나, 필터를 연 칩과 실제 카드 건수가
불일치한다. 이 불일치는 HTTP 계약이 아니라 트랜잭션 경계 결함이다.

## 2. Scope

### Included

- `InboxApplicationService.list()` 진입점에서 실제 `REPEATABLE_READ`가 시작되는지
- 목록 SELECT 직후 다른 트랜잭션이 새 수신 항목을 commit해도 같은 호출의
  chip 집계가 그 항목을 보지 않는지
- 같은 `list()` 호출의 카드 목록과 chip count가 하나의 snapshot에서 일치하는지
- 방향 필터를 건 상태에서도 칩이 같은 snapshot의 카테고리 전체를 집계하는지
- 상세 열람에서 `OPENED` 전이 후 projection 조회 실패 시 전체 rollback
- 손대는 production `@Service`의 `QELLO-JAVA-TX-001`·`TX-002`·`TX-003`·
  `QELLO-JAVA-INJECTION-001` ratchet
- 기존 수신함 목록·칩·상세 단위/통합 회귀

### Excluded

- feed application seam 분리 (Wave 1B)
- `InboxQueryService.list()`를 단독 호출하는 기존 칩/목록 테스트의 재작성
- HTTP 경로, 오류 코드, 응답 스키마 변경 검증을 넘어선 API 재설계
- DB schema, migration, `InboxQuerySql` 쿼리 의미 변경
- baseline 51건 제거, 새 `LEGACY` 추가, 전체 production scan 전환
- isolation 속성을 검사하는 새 ArchUnit 규칙 도입
  (`ProductionConventionRules`는 현재 isolation을 보지 않는다)
- 인프라 apply, 배포, 프로덕션 변경
- 실제 credential, `.env`, token, 계정·서버 식별자

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #212 | 같은 `list()` 호출의 목록과 chip 집계가 하나의 `REPEATABLE_READ` snapshot을 사용한다 |
| GitHub Issue #212 | 첫 SELECT 이후 커밋된 새 항목이 같은 호출의 chip count에 섞이지 않는다 |
| GitHub Issue #212 | 상세 열람 `OPENED` 전이 후 projection 조회 실패 시 전체 rollback이 유지된다 |
| GitHub Issue #212 | HTTP 경로, 오류 코드, DB schema 불변. feed 모듈 분리와 같은 PR에 섞지 않는다 |
| GitHub Issue #212 | 변경된 production Service가 `javaConventionCheck`를 통과한다 |
| `TASK.md` | `InboxApplicationService.list()`에서 실제 `REPEATABLE_READ`가 시작되게 조정 |
| `InboxQueryService.list` 주석 | `findInbox`와 `countByDirection`은 별개 SELECT이며 `REPEATABLE_READ`로 시작 시점 snapshot을 고정한다 |
| Spring `REQUIRED` 기본값 | 안쪽 `@Transactional(isolation = REPEATABLE_READ)`는 이미 열린 바깥 트랜잭션에 참여하면 isolation을 바꾸지 못한다 |
| `docs/harness/JAVA_CONVENTIONS.md` | class read-only 기본, public TX method, self-invocation 금지, constructor injection |
| `ProductionConventionRules` | TX/injection 규칙은 isolation 속성을 검사하지 않는다. 없는 규칙에 대한 dummy `JUSTIFIED_EXCEPTION`을 넣지 않는다 |
| `AGENTS.md` §3 | JUnit 5, `@DisplayName`, ISO 8601 class header, unit/integration 분리 |
| `TASK.md` | 테스트·구현 계획 승인 전 production 구현 금지 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 바깥 `READ_COMMITTED`가 안쪽 `REPEATABLE_READ`를 삼킴 | 목록과 칩이 다른 snapshot | 높음 (현재 코드) | P0 | `InboxApplicationService.list()` 경로에서 첫 SELECT 이후 커밋이 칩에 안 보임 |
| `InboxQueryService.list()`만 검증 | 결함이 그대로 통과 | 높음 | P0 | 동시성 테스트 entry point가 `InboxApplicationService.list()` |
| 삽입이 같은 Spring TX에 참여 | 스냅샷 검증이 무의미 | 높음 | P0 | 삽입은 `PROPAGATION_REQUIRES_NEW` 또는 트랜잭션에 묶이지 않은 별도 connection |
| `JdbcTemplate`이 현재 TX에 bind | 새 항목이 같은 snapshot에 들어가 거짓 통과/실패 | 높음 | P0 | spy 콜백의 삽입이 목록 TX와 다른 connection에서 commit됨 |
| class-level `readOnly`만 추가하고 `list()` isolation을 안 올림 | TX-001은 통과하고 결함은 남음 | 중간 | P0 | `list()` method annotation에 `REPEATABLE_READ`가 있고 런타임 테스트가 통과 |
| `REQUIRES_NEW`로 안쪽 목록 TX를 분리 | 바깥과 안쪽이 다른 snapshot | 중간 | P0 | 구현이 `REQUIRED` 참여를 유지하고 바깥에서 isolation을 연다. 테스트는 application `list()` 한 호출만 본다 |
| 상세 `OPENED` 후 projection 실패가 commit됨 | 사용자에게 안 열린 글이 OPENED로 남음 | 중간 | P0 | 예외 후 DB status가 이전 값 |
| 기존 칩 의미(필터는 카드만, 칩은 카테고리 전체)를 깨뜨림 | 방향 전환 UX 회귀 | 중간 | P0 | 필터를 건 호출에서도 칩은 같은 snapshot의 카테고리 전체 |
| Wave 0 ratchet이 변경된 `InboxApplicationService`를 실패시킴 | CI 차단 | 높음 | P0 | class `readOnly` 기본, public TX, constructor injection, `javaConventionCheck` 통과 |
| 없는 isolation 규칙에 `JUSTIFIED_EXCEPTION` 추가 | baseline 오염 | 중간 | P1 | isolation을 검사하는 ArchUnit 규칙이 없으면 baseline에 넣지 않음 |
| 새 `LEGACY` 추가 | #208/#210 계약 위반 | 낮음 | P0 | baseline entry ID 집합에 새 `LEGACY` 없음 |
| HTTP/DB 계약을 같이 바꿈 | 범위 이탈 | 낮음 | P0 | controller·오류 코드·migration diff 없음 |
| 기존 `InboxApiIntegrationTest` 목록 회귀 | 카테고리 분리·칩 필터 의미 손상 | 낮음 | P1 | 기존 INT-001/INT-002 통과 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-001 | `InboxApplicationService.list` | method `@Transactional`을 읽는다 | `readOnly = true`이고 `isolation = REPEATABLE_READ`다 | P0 | Annotation Executor |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-002 | `InboxApplicationService` 클래스 | class `@Transactional`을 읽는다 | `readOnly = true`다. class write가 아니다 | P0 | Annotation Executor |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-003 | `list`·`detail`·`skip`·`revertSkip` | 가시성과 self-invocation을 검사한다 | 모두 public이고, 클래스 내부에서 `@Transactional` 메서드를 직접 호출하지 않는다 | P0 | Annotation Executor |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-004 | `InboxApplicationService` 필드·메서드 | `@Autowired`를 검사한다 | field/setter `@Autowired`가 없고 의존성은 생성자로만 주입된다 | P0 | Annotation Executor |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-005 | 자격 있는 수신자와 고정 Clock | `list()` | 서버 Clock의 한 시각만 `InboxQueryService.list`에 전달된다 | P0 | Application Unit Executor |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-006 | `queryService.detail`이 empty | `detail()` | `open()` 호출 뒤 `INBOX_ITEM_NOT_FOUND`를 던진다 | P0 | Application Unit Executor |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-007 | `AccountEligibilityGate`가 거부 | `list()` | query service를 호출하지 않는다 | P1 | Application Unit Executor |

UNIT-005와 UNIT-007은 기존 `InboxApplicationServiceTest` 시나리오의 회귀다.
구현 시 기존 테스트를 삭제하지 않고 유지한다. UNIT-006은 현재 클래스에
empty-projection 단언이 없으므로 같은 파일에 추가한다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-001 | `InboxApplicationService`, `InboxQueryRepository` spy, PostgreSQL | 수신자에게 N 방향 AVAILABLE 1건. spy는 `findInbox` 실호출 직후 `REQUIRES_NEW`로 같은 수신자·같은 N 방향 새 항목을 commit | `inbox.list(recipientId, UNANSWERED, null)` | 반환 카드는 기존 1건만. N 칩 count는 1. 새 항목 id는 카드·칩 어디에도 없다. 이 테스트는 현재 `origin/main`에서 RED여야 한다 | JDBC fixture delete, spy reset |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-002 | 동일 | N 1건 + 필터 `"N"`. spy는 `findInbox` 직후 S 방향 새 항목을 commit | `inbox.list(recipientId, UNANSWERED, "N")` | 카드는 N 1건. 칩에 S가 없고 N count는 1. 칩은 카드 필터와 무관하게 같은 snapshot의 카테고리 전체다 | JDBC fixture delete, spy reset |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-003 | `InboxApplicationService.detail`, repository spy | AVAILABLE 1건. spy는 `findDetail`을 empty로 돌려 전이 후 projection 실패를 재현 | `inbox.detail(recipientId, postRecipientId)` | `INBOX_ITEM_NOT_FOUND`. DB status는 `AVAILABLE`이고 `opened_at`은 null. OPENED가 commit되지 않는다 | JDBC fixture delete, spy reset |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-004 | Wave 0 ratchet | `InboxApplicationService`가 변경된 작업 트리 | `./gradlew javaConventionCheck` | TX-001/002/003·INJECTION-001로 실패하지 않는다. 새 `LEGACY` 없음. isolation을 안 보는 규칙에 dummy `JUSTIFIED_EXCEPTION`을 추가하지 않는다 | Gradle report |
| TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-005 | 기존 수신함 통합 테스트 | 구현 후 | `InboxApiIntegrationTest`의 목록·칩 시나리오와 `InboxApplicationServiceTest` | 카테고리 분리, 칩은 필터와 무관한 카테고리 집계, 자격 거부 회귀 없음 | suite lifecycle |

INT-001 실패 조건(현재 결함): 바깥 `READ_COMMITTED`면 두 번째 SELECT인
`countByDirection`이 커밋된 새 N 항목을 봐 칩 count가 2가 된다.

INT-001/002의 삽입은 `JdbcTemplate` 기본 참여를 쓰면 안 된다. 목록 트랜잭션에
bind되면 두 SELECT가 같은 커밋되지 않은 삽입을 보게 되어 isolation 검증이 아니다.

## 7. Cross-cutting scenarios

### Database and transactions

- schema, migration, `InboxQuerySql`을 바꾸지 않는다. 검증 대상은 트랜잭션
  시작 위치와 isolation이다.
- 목록 트랜잭션은 읽기 전용 `REPEATABLE_READ`여야 한다. PostgreSQL에서
  읽기 전용 repeatable read는 serialization failure를 만들지 않는다고
  `InboxQueryService` 주석이 전제한다. 이 테스트는 write conflict를 만들지 않는다.
- 상세 `detail()`은 쓰기 트랜잭션이다. class-level `readOnly = true`를 넣더라도
  method-level `@Transactional` write가 `OPENED` 전이를 커밋/롤백할 수 있어야 한다.
- 테스트 클래스를 클래스 단위 `@Transactional`로 감싸지 않는다. 기존
  `InboxApiIntegrationTest`처럼 JDBC cleanup을 쓴다.

### Concurrency and idempotency

- INT-001/002는 두 스레드 레이스가 아니라 **한 호출 안의 두 SELECT 사이**에
  다른 트랜잭션이 commit하는 시나리오다. `InboxCommandConcurrencyIntegrationTest`
  의 skip/open 레이스를 재사용하지 않는다.
- 훅은 `InboxQueryRepository.findInbox` 실호출 이후, `countByDirection` 이전이다.
- 멱등 키, 중복 탭, skip 확정은 이 계획 밖이다.

### External APIs

- FCM, OAuth, S3, moderation, GitHub API를 호출하지 않는다.
- HTTP MockMvc 재작성은 하지 않는다. controller는 계속
  `InboxApplicationService.list`에 위임한다.

### Failure recovery and reconciliation

- INT-003: projection 실패는 `OPENED`를 남기지 않고 전체 rollback한다.
- spy/latch 실패 시 테스트 타임아웃은 환경 실패로 기록하고, isolation 통과로
  위장하지 않는다.
- `javaConventionCheck` 실패 메시지는 새 `LEGACY`가 아니라 Service 수정을 안내해야 한다.

## 8. Test data and isolation

- Fixtures: 기존 `Inbox124IntegrationFixtures` 패턴(계정·post·AVAILABLE
  `post_recipient`)을 새 통합 테스트에서 재사용하거나 동일 JDBC insert를
  로컬 helper로 복제한다. production fixture package를 바꾸지 않는다.
- Database isolation: `PostgisContainerIntegrationTestSupport` 공유 PostgreSQL.
  메서드마다 해당 테스트 region/account row를 삭제한다. 클래스
  `@Transactional` rollback을 쓰지 않는다.
- Clock/randomness: 기존 inbox 통합과 같이 고정 `Instant`와 test Clock.
  동시 삽입 시각은 목록 조회 `at`보다 이전이어도 된다. 만료 시각은 조회 시점
  이후여야 목록에 들어갈 자격이 있다.
- External API doubles: 없음. repository spy만 트랜잭션 사이에 commit 훅을 넣는다.
- Cleanup: spy stub reset, JDBC delete, executor/latch가 있으면 shutdown.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

Spy 훅 제약:

- `@MockitoSpyBean InboxQueryRepository` (저장소 기존 패턴).
- 삽입 트랜잭션은 `TransactionTemplate` + `PROPAGATION_REQUIRES_NEW`, 또는
  Spring TX에 참여하지 않는 raw `DataSource` connection.
- `InboxQueryService.list()`를 직접 호출하면 현재 코드에서도 GREEN이 될 수
  있다. 금지. entry point는 `InboxApplicationService.list()`만 허용한다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Annotation Executor | `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTransactionBoundaryTest.java` | UNIT-001, UNIT-002, UNIT-003, UNIT-004 | `./gradlew test --tests '*InboxApplicationServiceTransactionBoundaryTest'` |
| 2 | Application Unit Executor | `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTest.java` | UNIT-005, UNIT-006, UNIT-007 | `./gradlew test --tests '*InboxApplicationServiceTest'` |
| 3 | Isolation Integration Executor | `src/integrationTest/java/com/dnd/qello/InboxListIsolationIntegrationTest.java` | INT-001, INT-002, INT-003 | `./gradlew integrationTest --tests '*InboxListIsolationIntegrationTest'` |
| 4 | Convention Executor | production `InboxApplicationService.java`와 필요 시 `baseline.json`만. 새 ArchUnit 규칙 파일 없음 | INT-004 | `./gradlew javaConventionCheck` |
| 5 | Regression Executor | 기존 파일 수정 없음 | INT-005 | `./gradlew test --tests '*InboxApplicationServiceTest' --tests '*InboxApiMockMvcTest'` 및 `./gradlew integrationTest --tests '*InboxApiIntegrationTest'` |

실행 에이전트는 위 소유 파일만 수정한다. `InboxQueryService.java` 테스트 재작성,
`InboxCommandConcurrencyIntegrationTest`, `InboxDirectionChipIntegrationTest`,
`InboxQueryIntegrationTest`는 이 계획의 소유가 아니다.

구현 전에 INT-001이 현재 코드에서 RED인지는 Isolation Integration Executor가
수정 없이 재현하거나, 구현 계획에서 동일 시나리오를 실패 증거로 남긴다.

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 통합 테스트 통과 (`InboxListIsolationIntegrationTest`, 기존 수신함 목록 회귀)
- [x] `./gradlew javaConventionCheck` 통과
- [x] HTTP 경로·오류 코드·migration 무변경
- [x] 잠재 문제 분석
- [x] 테스트 보고서 생성 (`templates/test-report.md`)

## 11. Human approval

- Reviewer: Human partner
- Decision: Approved for implementation plan
- Approved at: `2026-09-02T19:20:00+09:00`
