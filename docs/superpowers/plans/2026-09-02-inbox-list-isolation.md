# 수신함 목록 REPEATABLE_READ isolation 결함 수정 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `InboxApplicationService.list()`가 목록과 direction chip을 하나의 `REPEATABLE_READ` snapshot에서 읽게 한다.

**Architecture:** 바깥 `list()`가 기본 `READ_COMMITTED`를 열면 안쪽 `InboxQueryService.list()`의 `REPEATABLE_READ`는 `REQUIRED` 참여로 무시된다. 고치는 위치는 application `list()`다. class-level `@Transactional(readOnly = true)`를 추가해 Wave 0 ratchet(`QELLO-JAVA-TX-001`)을 맞추고, `list()` method에 `isolation = REPEATABLE_READ`를 둔다. `InboxQueryService`와 HTTP/DB는 유지한다. `REQUIRES_NEW`로 안쪽 트랜잭션을 분리하지 않는다.

**Tech Stack:** Java 21, Spring `@Transactional`, PostgreSQL `REPEATABLE READ`, JUnit 5, Testcontainers, Mockito spy, `TransactionTemplate` `PROPAGATION_REQUIRES_NEW`.

**Spec:** GitHub Issue #212, `TASK.md`, `docs/test-plans/gh-212-TEST-PLAN-GH-212-INBOX-LIST-ISOLATION.md`

**Test plan:** `docs/test-plans/gh-212-TEST-PLAN-GH-212-INBOX-LIST-ISOLATION.md`

**Approval:** Human partner approved this implementation plan for build at `2026-09-02T19:28:00+09:00`.

## Global Constraints

- Java toolchain은 21이고 들여쓰기는 탭, 줄 길이는 120자다.
- HTTP 경로, 오류 코드, DB schema, migration, `InboxQuerySql`을 변경하지 않는다.
- feed application seam 분리를 이 PR에 섞지 않는다.
- `InboxQueryService` production 본문을 이 작업에서 리팩터링하지 않는다.
- 새 `LEGACY`를 추가하지 않는다. isolation을 검사하지 않는 ArchUnit 규칙에 dummy `JUSTIFIED_EXCEPTION`을 넣지 않는다.
- 모든 신규 JUnit class는 정확한 ISO 8601 timestamp, source scenario ID, 모든 method `@DisplayName`을 가진다. 아래 예시는 `2026-09-02T19:20:00+09:00`이며 실제 파일 생성 시각이 다르면 그 시각을 쓴다.
- 목록 동시성 테스트 entry point는 `InboxApplicationService.list()`만 허용한다. `InboxQueryService.list()` 직접 호출은 현재 코드에서도 GREEN이 될 수 있어 금지한다.
- spy 훅의 삽입은 `PROPAGATION_REQUIRES_NEW` 또는 Spring TX에 묶이지 않은 connection에서 commit한다. 목록 TX의 `JdbcTemplate` 참여를 쓰지 않는다.
- 테스트 클래스를 클래스 단위 `@Transactional`로 감싸지 않는다.
- 민감정보를 fixture, log, report에 기록하지 않는다.
- 커밋은 별도 사람 승인 게이트다. 아래 commit message는 승인 후 `/harness-commit`에서 사용할 제안이며 모두 `fix(feed): ... (#212)` 또는 문서/테스트 목적에 맞는 type을 쓴다. 브랜치 prefix는 `fix`이므로 훅이 허용하는 type만 사용한다.

## Approved decisions

- `DEC-212-001`: `REPEATABLE_READ`는 `InboxApplicationService.list()`에서 시작한다. `InboxQueryService.list()`에 `REQUIRES_NEW`를 두지 않는다.
- `DEC-212-002`: 이 파일을 고치므로 class-level `@Transactional(readOnly = true)`를 추가한다. write method는 기존 method-level `@Transactional`을 유지한다.
- `DEC-212-003`: `ProductionConventionRules`는 isolation을 보지 않는다. baseline에 isolation용 `JUSTIFIED_EXCEPTION`을 추가하지 않는다.
- `DEC-212-004`: feed seam 분리, HTTP/DB 계약 변경, `InboxQueryService` 구조 변경은 제외한다.

## File Structure

| Path | Responsibility |
| --- | --- |
| `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTransactionBoundaryTest.java` | list isolation·class readOnly·public TX·constructor injection 계약 |
| `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTest.java` | empty projection 후 `INBOX_ITEM_NOT_FOUND` (UNIT-006). 기존 list/자격 회귀 유지 |
| `src/integrationTest/java/com/dnd/qello/InboxListIsolationIntegrationTest.java` | application `list()` snapshot 동시성, detail rollback |
| `src/main/java/com/dnd/qello/feed/service/InboxApplicationService.java` | class readOnly + `list()` `REPEATABLE_READ` |
| `TASK.md` | 결정 ID, 승인 증거, 완료 조건 |

---

## Task 1: Annotation unit tests

**Files:**

- Create: `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTransactionBoundaryTest.java`

**Interfaces:**

- Consumes UNIT-001 through UNIT-004.
- Produces reflection assertions on `InboxApplicationService`. No production type changes in this task.
- Expected on current `origin/main`: UNIT-001 and UNIT-002 FAIL (`list()` has no isolation, class has no `@Transactional`). UNIT-003 and UNIT-004 PASS.

- [ ] **Step 1: Add the transaction boundary test.**

~~~java
/**
 * Created at: 2026-09-02T19:20:00+09:00
 * Source scenario: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-001 through UNIT-004
 */
package com.dnd.qello.feed.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.feed.view.InboxCategory;

class InboxApplicationServiceTransactionBoundaryTest {

	@Test
	@DisplayName("UNIT-001 list()는 읽기 전용 REPEATABLE_READ 트랜잭션을 연다")
	void listStartsRepeatableRead() throws Exception {
		Method list = InboxApplicationService.class.getMethod(
			"list", long.class, InboxCategory.class, String.class);
		Transactional transactional = list.getAnnotation(Transactional.class);

		assertThat(transactional).isNotNull();
		assertThat(transactional.readOnly()).isTrue();
		assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
	}

	@Test
	@DisplayName("UNIT-002 클래스는 read-only 트랜잭션 기본값을 가진다")
	void classIsReadOnlyDefault() {
		Transactional transactional = InboxApplicationService.class.getAnnotation(Transactional.class);

		assertThat(transactional).isNotNull();
		assertThat(transactional.readOnly()).isTrue();
	}

	@Test
	@DisplayName("UNIT-003 트랜잭션 메서드는 public이고 클래스 내부에서 호출하지 않는다")
	void transactionalMethodsArePublicAndNotSelfInvoked() {
		for (Method method : InboxApplicationService.class.getDeclaredMethods()) {
			if (method.getAnnotation(Transactional.class) == null) {
				continue;
			}
			assertThat(Modifier.isPublic(method.getModifiers()))
				.as(method.getName())
				.isTrue();
		}
	}

	@Test
	@DisplayName("UNIT-004 field/setter Autowired가 없고 생성자 주입만 쓴다")
	void usesConstructorInjectionOnly() {
		assertThat(InboxApplicationService.class.getDeclaredFields())
			.allSatisfy(field -> assertThat(field.getAnnotation(Autowired.class)).isNull());
		assertThat(InboxApplicationService.class.getDeclaredMethods())
			.allSatisfy(method -> assertThat(method.getAnnotation(Autowired.class)).isNull());
	}
}
~~~

- [ ] **Step 2: Run the annotation tests and confirm current RED for isolation/class TX.**

Run:

```bash
./gradlew test --tests '*InboxApplicationServiceTransactionBoundaryTest'
```

Expected: UNIT-001 FAIL (`isolation` is `DEFAULT`). UNIT-002 FAIL (class annotation null). UNIT-003/004 PASS. Do not change production code in this task.

- [ ] **Step 3: Prepare commit 1 for review after the production fix lands.**

Do not commit a permanently red test. Keep this file uncommitted until Task 4 makes UNIT-001/002 green, then include it in the same review-purpose commit as the production fix. Suggested message is in Task 4.

---

## Task 2: Empty-projection unit regression

**Files:**

- Modify: `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTest.java`

**Interfaces:**

- Consumes UNIT-006. UNIT-005 and UNIT-007 already exist (`listUsesSingleServerInstant`, `rejectsUnknownAccount` / `rejectsIneligibleAccount`) and must stay.
- `detail(long, long)` already calls `postRecipientService.open` then `queryService.detail`. Empty optional already throws `INBOX_ITEM_NOT_FOUND`.
- This test is expected GREEN on current main. It locks the call order before class-level TX changes.

- [ ] **Step 1: Add UNIT-006 to `InboxApplicationServiceTest`.**

Insert after `detailOpensAndQueriesAtSameInstant()`:

~~~java
	@Test
	@DisplayName("UNIT-006 상세 projection이 없으면 OPENED 위임 뒤 INBOX_ITEM_NOT_FOUND를 던진다")
	void detailThrowsWhenProjectionMissingAfterOpen() {
		when(queryService.detail(RECIPIENT_ID, POST_RECIPIENT_ID, NOW)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detail(RECIPIENT_ID, POST_RECIPIENT_ID))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ITEM_NOT_FOUND);
		verify(postRecipientService).open(RECIPIENT_ID, POST_RECIPIENT_ID, NOW);
	}
~~~

Header source scenario comment: append `TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-UNIT-006`. Keep existing GH-124 IDs.

- [ ] **Step 2: Run unit tests.**

```bash
./gradlew test --tests '*InboxApplicationServiceTest'
```

Expected: PASS, including the new method.

---

## Task 3: Isolation integration tests (INT-001 red on main)

**Files:**

- Create: `src/integrationTest/java/com/dnd/qello/InboxListIsolationIntegrationTest.java`

**Interfaces:**

- Consumes INT-001, INT-002, INT-003.
- Entry point: `InboxApplicationService.list` / `detail` only.
- Spy: `JdbcInboxQueryRepository` (`@MockitoSpyBean`).
- Concurrent insert: `TransactionTemplate` + `PROPAGATION_REQUIRES_NEW` calling `Inbox124IntegrationFixtures.post` / `available`.
- Clock: `@Import(Inbox124TestClockConfiguration.class)` so `at` is `2026-08-16T06:00:00.123456Z`.
- INT-001/002 expected RED on current `origin/main`. INT-003 expected GREEN (existing write `@Transactional` already rolls back).

- [ ] **Step 1: Add the integration test class.**

~~~java
/**
 * Created at: 2026-09-02T19:20:00+09:00
 * Source scenario: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-001 through INT-003
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;
import com.dnd.qello.feed.repository.jdbc.JdbcInboxQueryRepository;
import com.dnd.qello.feed.service.InboxApplicationService;
import com.dnd.qello.feed.view.DirectionChip;
import com.dnd.qello.feed.view.InboxCard;
import com.dnd.qello.feed.view.InboxCategory;
import com.dnd.qello.feed.view.InboxListing;

@SpringBootTest
@ActiveProfiles("test")
@Import(Inbox124TestClockConfiguration.class)
class InboxListIsolationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Instant NOW = Instant.parse("2026-08-16T06:00:00.123456Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private InboxApplicationService inbox;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoSpyBean
	private JdbcInboxQueryRepository queryRepository;

	private Inbox124IntegrationFixtures fixtures;
	private long senderId;
	private long recipientId;

	@BeforeEach
	void resetFixtures() {
		reset(queryRepository);
		fixtures = new Inbox124IntegrationFixtures(jdbc, NOW);
		fixtures.reset();
		senderId = fixtures.account("inbox212-sender");
		recipientId = fixtures.account("inbox212-recipient");
	}

	@Test
	@DisplayName("INT-001 목록 SELECT 이후 커밋된 같은 방향 항목은 같은 호출의 칩에 섞이지 않는다")
	void chipCountIgnoresRowsCommittedAfterFindInbox() {
		long existingPost = fixtures.post(senderId, "int001-existing", NOW.plusSeconds(3600), "ACTIVE", null);
		long existingRecipient = fixtures.available(existingPost, recipientId, NOW.minusSeconds(10), 0);
		commitNewItemAfterFindInbox(0);

		InboxListing listing = inbox.list(recipientId, InboxCategory.UNANSWERED, null);

		assertThat(listing.cards()).extracting(InboxCard::postRecipientId).containsExactly(existingRecipient);
		assertThat(nChipCount(listing)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-002 방향 필터를 건 호출의 칩도 같은 snapshot만 보고 새 S 항목을 넣지 않는다")
	void filteredListChipsStayOnTheSameSnapshot() {
		long northPost = fixtures.post(senderId, "int002-north", NOW.plusSeconds(3600), "ACTIVE", null);
		long northRecipient = fixtures.available(northPost, recipientId, NOW.minusSeconds(10), 0);
		commitNewItemAfterFindInbox(180);

		InboxListing listing = inbox.list(recipientId, InboxCategory.UNANSWERED, "N");

		assertThat(listing.cards()).extracting(InboxCard::postRecipientId).containsExactly(northRecipient);
		assertThat(listing.chips()).extracting(DirectionChip::segmentKey).containsExactly("N");
		assertThat(nChipCount(listing)).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-003 상세 projection 실패는 OPENED 전이를 롤백한다")
	void detailProjectionFailureRollsBackOpened() {
		long postId = fixtures.post(senderId, "int003-open", NOW.plusSeconds(3600), "ACTIVE", null);
		long postRecipientId = fixtures.available(postId, recipientId, NOW.minusSeconds(10), 0);
		doReturn(Optional.empty()).when(queryRepository)
			.findDetail(eq(recipientId), eq(postRecipientId), any());

		assertThatThrownBy(() -> inbox.detail(recipientId, postRecipientId))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INBOX_ITEM_NOT_FOUND);
		assertThat(fixtures.status(postRecipientId)).isEqualTo("AVAILABLE");
	}

	private void commitNewItemAfterFindInbox(int inboundBearing) {
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			List<InboxCard> cards = (List<InboxCard>) invocation.callRealMethod();
			commitInSeparateTransaction(() -> {
				long postId = fixtures.post(
					senderId, "after-snapshot-" + inboundBearing, NOW.plusSeconds(3600), "ACTIVE", null);
				fixtures.available(postId, recipientId, NOW.minusSeconds(1), inboundBearing);
			});
			return cards;
		}).when(queryRepository).findInbox(eq(recipientId), eq(InboxCategory.UNANSWERED), any(), any());
	}

	private void commitInSeparateTransaction(Runnable work) {
		TransactionTemplate separate = new TransactionTemplate(transactionManager);
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		separate.executeWithoutResult(status -> work.run());
	}

	private long nChipCount(InboxListing listing) {
		return listing.chips().stream()
			.filter(chip -> "N".equals(chip.segmentKey()))
			.mapToLong(DirectionChip::count)
			.findFirst()
			.orElse(0L);
	}
}
~~~

If `Inbox124TestClockConfiguration` import fails because the class is package-private, keep the new test in `com.dnd.qello` — it already is. Do not move the clock config.

If chip keys in the test DB scheme are not exactly `"N"`/`"S"`, read the active scheme once in a helper and assert with that key. Do not hard-fail on a renamed scheme without checking `direction_scheme`. Current `InboxApiIntegrationTest` INT-002 uses `"N"` and `"S"`.

- [ ] **Step 2: Run INT-001/002/003 against current production code.**

```bash
./gradlew integrationTest --tests '*InboxListIsolationIntegrationTest'
```

Expected: INT-001 FAIL (chip count 2). INT-002 FAIL (S chip present or N count changed). INT-003 PASS. Record this RED as evidence that the test is not going through `InboxQueryService.list()` directly.

If INT-001 unexpectedly PASSES on current main, stop. The spy insert is probably joining the list transaction. Fix the test hook (`REQUIRES_NEW` / raw connection) before any production change.

- [ ] **Step 3: Do not commit red isolation tests alone.**

Same as Task 1: land with the production fix in Task 4.

---

## Task 4: Start REPEATABLE_READ on application list()

**Files:**

- Modify: `src/main/java/com/dnd/qello/feed/service/InboxApplicationService.java`

**Interfaces:**

- Consumes DEC-212-001, DEC-212-002.
- Produces:

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboxApplicationService {
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public InboxListing list(long recipientId, InboxCategory category, String directionSegmentKey);

	@Transactional
	public InboxDetail detail(...);

	@Transactional
	public PostRecipient skip(...);

	@Transactional
	public PostRecipient revertSkip(...);
}
```

- Spring method-level `@Transactional` replaces class-level attributes. `detail`/`skip`/`revertSkip` remain writes (`readOnly` default false).
- `InboxQueryService.list()` stays `REQUIRED` + `REPEATABLE_READ` and joins the outer snapshot. Do not add `REQUIRES_NEW`.

- [ ] **Step 1: Update `InboxApplicationService`.**

Add imports:

~~~java
import org.springframework.transaction.annotation.Isolation;
~~~

Class and `list()`:

~~~java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboxApplicationService {

	private final AccountEligibilityGate accountEligibilityGate;
	private final InboxQueryService queryService;
	private final PostRecipientService postRecipientService;
	private final Clock clock;
	private final SkipConfirmationProperties skipConfirmationProperties;

	/**
	 * 목록과 칩 집계가 같은 snapshot을 보게 REPEATABLE_READ를 여기서 연다.
	 * 바깥 기본 READ_COMMITTED에 참여하면 InboxQueryService의 isolation은 무시된다.
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public InboxListing list(long recipientId, InboxCategory category, String directionSegmentKey) {
		accountEligibilityGate.require(recipientId);
		Instant at = clock.instant();
		return queryService.list(recipientId, category, directionSegmentKey, at);
	}
~~~

Keep `detail` / `skip` / `revertSkip` / `revertibleUntil` / `mapCommandException` bodies unchanged. Keep `@RequiredArgsConstructor`. Do not add field/setter `@Autowired`. Do not make `list()` private. Do not call `list()` from another method in this class.

- [ ] **Step 2: Re-run the tests that were red.**

```bash
./gradlew test --tests '*InboxApplicationServiceTransactionBoundaryTest' --tests '*InboxApplicationServiceTest'
./gradlew integrationTest --tests '*InboxListIsolationIntegrationTest'
```

Expected: all PASS. INT-001 cards remain the pre-insert row, N chip count is 1. INT-002 has no S chip. INT-003 status stays `AVAILABLE`.

- [ ] **Step 3: Run convention ratchet on the changed Service.**

```bash
./gradlew javaConventionCheck
```

Expected: PASS. `InboxApplicationService` has class read-only, public TX methods, constructor injection. No new `LEGACY`. No baseline.json edit.

If TX-001 still fails, the class annotation is missing or `readOnly` is false. Fix the annotation; do not register an exception.

- [ ] **Step 4: Prepare the production+test commit for `/harness-commit`.**

Suggested message:

```text
fix(feed): start inbox list REPEATABLE_READ on the application boundary (#212)
```

Include:

- `src/main/java/com/dnd/qello/feed/service/InboxApplicationService.java`
- `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTransactionBoundaryTest.java`
- `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTest.java`
- `src/integrationTest/java/com/dnd/qello/InboxListIsolationIntegrationTest.java`

Do not commit until the human approves `/harness-commit`.

---

## Task 5: Regression, contract, and report

**Files:**

- Modify: `TASK.md`
- Modify: `docs/test-plans/gh-212-TEST-PLAN-GH-212-INBOX-LIST-ISOLATION.md` only if scenario IDs/paths did not drift
- Create: `docs/reports/tests/gh-212-TEST-PLAN-GH-212-INBOX-LIST-ISOLATION.md` via `./harness test-run` after tests pass, or fill `templates/test-report.md`

**Interfaces:**

- Consumes INT-004, INT-005, completion criteria.
- Must not change controller, error codes, or migrations.

- [ ] **Step 1: Run regression commands.**

```bash
./gradlew test --tests '*InboxApplicationServiceTest' --tests '*InboxApiMockMvcTest'
./gradlew integrationTest --tests '*InboxApiIntegrationTest'
git diff --name-only origin/main
```

Expected: existing list/chip/detail HTTP unit tests pass. `InboxApiIntegrationTest` INT-001/INT-002 category and chip-filter meaning unchanged. Diff contains `InboxApplicationService.java` plus tests/docs/`TASK.md` only. No `src/main/resources/db/migration`, no controller, no `InboxQueryService.java`.

- [ ] **Step 2: Update `TASK.md` decisions and completion checkboxes** after evidence exists.

Record:

```markdown
- `DEC-212-001`: REPEATABLE_READ starts on InboxApplicationService.list()
- `DEC-212-002`: class-level readOnly default for TX-001
- `DEC-212-003`: no isolation JUSTIFIED_EXCEPTION
- `DEC-212-004`: no feed seam / HTTP / schema change
```

Mark completion criteria only when the corresponding command output exists.

- [ ] **Step 3: Fill the test report.**

Use `templates/test-report.md`. Record RED-then-GREEN for INT-001/002, GREEN for INT-003 before and after, `javaConventionCheck` output, and residual risk: `InboxQueryService.list()` still documents REPEATABLE_READ for direct callers; Wave 1B must not reintroduce an outer READ_COMMITTED wrapper.

- [ ] **Step 4: Suggested follow-up commit for docs/evidence.**

```text
fix(feed): record inbox list isolation verification evidence (#212)
```

If the harness commit hook requires type `fix` on this branch, keep `fix`. Do not invent a `test`/`docs` type that the branch prefix will reject.

---

## Spec coverage

| Requirement | Task |
| --- | --- |
| Issue: 같은 list() 호출이 하나의 REPEATABLE_READ snapshot | Task 3, Task 4 |
| Issue: 첫 SELECT 이후 커밋이 칩에 안 섞임 | Task 3 INT-001/002, Task 4 |
| Issue: OPENED 후 projection 실패 rollback | Task 2 UNIT-006, Task 3 INT-003 |
| Issue: HTTP/DB/feed seam 제외 | Global Constraints, Task 5 path diff |
| Issue: javaConventionCheck | Task 4 Step 3, Task 5 |
| DEC-212-001 application에서 isolation 시작 | Task 4 |
| DEC-212-002 class readOnly | Task 1 UNIT-002, Task 4 |
| DEC-212-003 baseline 예외 없음 | Task 4 Step 3 |
| DEC-212-004 QueryService/HTTP/schema 유지 | Task 4, Task 5 |
| UNIT-001~004 | Task 1 |
| UNIT-005, UNIT-007 | existing tests, Task 2/5 regression |
| UNIT-006 | Task 2 |
| INT-001~003 | Task 3 |
| INT-004~005 | Task 4–5 |

## Placeholder scan

없음. 테스트 클래스 헤더 timestamp는 구현 시각이 예시를 지나면 그 시각으로 바꾼다.
`Inbox124*` helper가 패키지 접근으로 실패하면 같은 패키지 `com.dnd.qello`를 유지하는 것이 수정이고, 새 public fixture 모듈을 만들지 않는다.
