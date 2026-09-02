# Java convention production 감사와 changed-file ratchet 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Issue #210에서 production `@Service`의 TX/injection 규칙을 실제로 감사하고, `origin/main`·staged 기준으로 변경된 Service에만 강제한다.

**Architecture:** fixture ArchUnit 조건을 `ProductionConventionRules`로 추출해 production 감사·ratchet과 공유한다. `ChangedJavaTypes`가 Git diff와 staged manifest에서 production Java type을 고르고, `ProductionConventionRatchetTest`가 그 type에만 같은 규칙을 적용한다. 미수정 위반은 inventory로만 남기고 `baseline.json`에는 넣지 않는다.

**Tech Stack:** Java 21, Gradle 8.14.3, ArchUnit 1.4.2, JUnit 5, Git.

**Spec:** `docs/superpowers/specs/2026-09-02-production-convention-ratchet-design.md`

**Test plan:** `docs/test-plans/gh-210-TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET.md`

**Approval:** Human partner approved this implementation plan for build at `2026-09-02T17:42:43+09:00`.

## Global Constraints

- Java toolchain은 21이고 Gradle wrapper는 8.14.3이다.
- ArchUnit은 이미 pin된 `com.tngtech.archunit:archunit-junit5:1.4.2`만 사용한다.
- production `src/main/java`, schema, migration, API, `config/java-conventions/baseline.json`을 수정하지 않는다.
- 새 `LEGACY` 추가와 hash-only 갱신을 하지 않는다.
- 모든 신규 JUnit class는 정확한 ISO 8601 timestamp, source scenario ID, 모든 method `@DisplayName`을 가진다. 아래 예시는 `2026-09-02T17:38:38+09:00`이며 실제 파일 생성 시각이 다르면 그 시각을 쓴다.
- Java source는 탭 indentation과 120자 줄 길이를 따른다.
- `origin/main` 누락은 skip이 아니라 configuration failure다.
- constructor `@Autowired`를 `QELLO-JAVA-INJECTION-001`로 승격하지 않는다.
- 민감정보를 fixture, log, report에 기록하지 않는다.
- 커밋은 별도 사람 승인 게이트다. 아래 commit message는 승인 후 사용할 제안이며 모두 `chore(...): ... (#210)`을 사용한다.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `src/test/java/com/dnd/qello/architecture/ProductionConventionRules.java` | 공유 ArchRule과 TX 분류 |
| `src/test/java/com/dnd/qello/architecture/ChangedJavaTypes.java` | origin/main·staged production type selector |
| `src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureTest.java` | fixture 계약. 공유 rules만 호출 |
| `src/test/java/com/dnd/qello/architecture/ProductionConventionAuditTest.java` | production inventory와 canary |
| `src/test/java/com/dnd/qello/architecture/ProductionConventionRatchetTest.java` | changed-file에만 규칙 적용 |
| `src/test/java/com/dnd/qello/architecture/ChangedJavaTypesTest.java` | selector unit/temp-git 계약 |
| `src/test/java/com/dnd/qello/architecture/fixture/SetterInjectedService.java` | setter `@Autowired` negative fixture |
| `src/test/java/com/dnd/qello/architecture/fixture/ConstructorAutowiredService.java` | constructor `@Autowired` positive fixture |
| `build.gradle` | architecture test filter와 manifest systemProperty |
| `docs/harness/JAVA_CONVENTIONS.md` | production ratchet 문서 |

---

## Task 1: Extract shared rules and complete injection fixtures

**Files:**

- Create: `src/test/java/com/dnd/qello/architecture/ProductionConventionRules.java`
- Create: `src/test/java/com/dnd/qello/architecture/fixture/SetterInjectedService.java`
- Create: `src/test/java/com/dnd/qello/architecture/fixture/ConstructorAutowiredService.java`
- Modify: `src/test/java/com/dnd/qello/architecture/JavaConventionArchitectureTest.java`

**Interfaces:**

- Consumes UNIT-005 through UNIT-012.
- Produces `ProductionConventionRules.classReadOnlyRule()`, `noInjectionRule()`, `publicTransactionMethodsRule()`, `noTransactionSelfInvocationRule()`, `classifyTransaction(JavaClass)`.
- `noInjectionRule()` fails field `@Autowired` and method `@Autowired`. Constructors are not methods, so constructor `@Autowired` is not this rule.
- `classifyTransaction` returns `READ_ONLY`, `CLASS_WRITE`, or `MISSING_READ_ONLY`.

- [ ] **Step 1: Add setter and constructor fixtures.**

`SetterInjectedService.java`:

~~~java
/*
 * Created at: 2026-09-02T17:38:38+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-006
 */
package com.dnd.qello.architecture.fixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SetterInjectedService {

	private Object dependency;

	@Autowired
	public void setDependency(Object dependency) {
		this.dependency = dependency;
	}
}
~~~

`ConstructorAutowiredService.java`:

~~~java
/*
 * Created at: 2026-09-02T17:38:38+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-007
 */
package com.dnd.qello.architecture.fixture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConstructorAutowiredService {

	private final Object dependency;

	@Autowired
	ConstructorAutowiredService(Object dependency) {
		this.dependency = dependency;
	}
}
~~~

- [ ] **Step 2: Write failing fixture tests for setter and constructor.**

Add to `JavaConventionArchitectureTest`:

~~~java
@Test
@DisplayName("setter injection은 QELLO-JAVA-INJECTION-001로 거절한다")
void rejectsSetterInjection() {
	JavaClasses classes = new ClassFileImporter().importClasses(SetterInjectedService.class);

	assertThatThrownBy(() -> ProductionConventionRules.noInjectionRule().check(classes))
			.hasMessageContaining("QELLO-JAVA-INJECTION-001");
}

@Test
@DisplayName("constructor Autowired는 QELLO-JAVA-INJECTION-001이 아니다")
void acceptsConstructorAutowired() {
	JavaClasses classes = new ClassFileImporter().importClasses(ConstructorAutowiredService.class);

	ProductionConventionRules.noInjectionRule().check(classes);
}
~~~

Existing tests must call `ProductionConventionRules` instead of private methods. Until the class exists this will not compile.

- [ ] **Step 3: Run the architecture tests.**

~~~bash
./gradlew test --tests '*JavaConventionArchitectureTest'
~~~

Expected: compilation FAIL because `ProductionConventionRules` does not exist, or setter test FAIL because the current field-only condition does not see `setDependency`.

- [ ] **Step 4: Implement `ProductionConventionRules`.**

~~~java
package com.dnd.qello.architecture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

public final class ProductionConventionRules {

	private ProductionConventionRules() {
	}

	public enum TransactionShape {
		READ_ONLY,
		CLASS_WRITE,
		MISSING_READ_ONLY
	}

	public static TransactionShape classifyTransaction(JavaClass item) {
		Transactional annotation = item.getAnnotationOfType(Transactional.class);
		if (annotation == null) {
			return TransactionShape.MISSING_READ_ONLY;
		}
		if (!annotation.readOnly()) {
			return TransactionShape.CLASS_WRITE;
		}
		return TransactionShape.READ_ONLY;
	}

	public static ArchRule classReadOnlyRule() {
		return classes()
				.that().areAnnotatedWith("org.springframework.stereotype.Service")
				.should(new ArchCondition<>("have class read-only transaction") {
					@Override
					public void check(JavaClass item, ConditionEvents events) {
						if (classifyTransaction(item) != TransactionShape.READ_ONLY) {
							events.add(SimpleConditionEvent.violated(item, "QELLO-JAVA-TX-001: " + item.getName()));
						}
					}
				});
	}

	public static ArchRule noInjectionRule() {
		return classes()
				.that().areAnnotatedWith("org.springframework.stereotype.Service")
				.should(new ArchCondition<>("use constructor injection") {
					@Override
					public void check(JavaClass item, ConditionEvents events) {
						for (JavaField field : item.getFields()) {
							if (field.isAnnotatedWith(Autowired.class)) {
								events.add(SimpleConditionEvent.violated(field,
										"QELLO-JAVA-INJECTION-001: " + field.getFullName()));
							}
						}
						for (JavaMethod method : item.getMethods()) {
							if (method.isAnnotatedWith(Autowired.class)) {
								events.add(SimpleConditionEvent.violated(method,
										"QELLO-JAVA-INJECTION-001: " + method.getFullName()));
							}
						}
					}
				});
	}

	public static ArchRule publicTransactionMethodsRule() {
		return classes()
				.that().areAnnotatedWith("org.springframework.stereotype.Service")
				.should(new ArchCondition<>("expose transaction methods publicly") {
					@Override
					public void check(JavaClass item, ConditionEvents events) {
						for (JavaMethod method : item.getMethods()) {
							if (method.isAnnotatedWith(Transactional.class)
									&& !method.getModifiers().contains(JavaModifier.PUBLIC)) {
								events.add(SimpleConditionEvent.violated(method,
										"QELLO-JAVA-TX-002: " + method.getFullName()));
							}
						}
					}
				});
	}

	public static ArchRule noTransactionSelfInvocationRule() {
		return classes()
				.that().areAnnotatedWith("org.springframework.stereotype.Service")
				.should(new ArchCondition<>("call transaction methods through a proxy") {
					@Override
					public void check(JavaClass item, ConditionEvents events) {
						for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
							if (call.getTarget().isAnnotatedWith(Transactional.class)) {
								events.add(SimpleConditionEvent.violated(call,
										"QELLO-JAVA-TX-003: " + call.getOrigin().getFullName()
												+ " -> " + call.getTarget().getFullName()));
							}
						}
					}
				});
	}
}
~~~

Delete the private rule methods from `JavaConventionArchitectureTest` and call these four methods. Keep existing `@DisplayName` and scenario headers. Rename the field-injection test helper from `noFieldInjectionRule()` to `noInjectionRule()`.

- [ ] **Step 5: Re-run fixture tests.**

~~~bash
./gradlew test --tests '*JavaConventionArchitectureTest'
~~~

Expected: PASS. Setter fixture fails the rule inside the assertion. Constructor fixture passes. Existing TX fixtures still pass.

- [ ] **Step 6: Prepare commit 1 for review.**

Proposed paths: `ProductionConventionRules.java`, two fixtures, `JavaConventionArchitectureTest.java`.

Suggested message:

~~~text
chore(conventions): share ArchUnit rules and reject setter injection (#210)
~~~

---

## Task 2: Select changed production Java types

**Files:**

- Create: `src/test/java/com/dnd/qello/architecture/ChangedJavaTypes.java`
- Create: `src/test/java/com/dnd/qello/architecture/ChangedJavaTypesTest.java`

**Interfaces:**

- Consumes UNIT-013 through UNIT-017.
- Produces `ChangedJavaTypes.productionJavaTypesFromOriginMain(Path)`, `productionJavaTypesFromManifest(List<String>)`, `toTypeNames(Collection<String>)`.
- Git command union: `git diff --name-only origin/main -- src/main/java` and `git diff --name-only --cached origin/main -- src/main/java`.
- Missing `origin/main` throws `IllegalStateException` whose message contains `origin/main` and does not contain `QELLO-JAVA-TX-001`.
- Only paths under `src/main/java/` ending in `.java` become type names. Test and resource paths are ignored.

- [ ] **Step 1: Write selector tests against a temp Git repo.**

~~~java
@Test
@DisplayName("origin/main과 동일한 production Java는 ratchet 대상에서 빠진다")
void excludesUnchangedProductionJava() throws Exception {
	Path repo = gitRepoWithOriginMain("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java", "class DeviceTokenService {}");

	assertThat(ChangedJavaTypes.productionJavaTypesFromOriginMain(repo)).isEmpty();
}

@Test
@DisplayName("origin/main 대비 수정된 production Java는 ratchet 대상이다")
void includesModifiedProductionJava() throws Exception {
	Path repo = gitRepoWithOriginMain("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java", "class DeviceTokenService {}");
	Files.writeString(repo.resolve("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java"), "class DeviceTokenService { int x; }");

	assertThat(ChangedJavaTypes.productionJavaTypesFromOriginMain(repo))
			.containsExactly("com.dnd.qello.auth.service.DeviceTokenService");
}

@Test
@DisplayName("origin/main에 없는 새 production Java는 ratchet 대상이다")
void includesNewProductionJava() throws Exception {
	Path repo = gitRepoWithOriginMain("src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java", "class DeviceTokenService {}");
	Path added = repo.resolve("src/main/java/com/dnd/qello/feed/service/NewFeedService.java");
	Files.createDirectories(added.getParent());
	Files.writeString(added, "class NewFeedService {}");

	assertThat(ChangedJavaTypes.productionJavaTypesFromOriginMain(repo))
			.contains("com.dnd.qello.feed.service.NewFeedService");
}

@Test
@DisplayName("같은 경로 목록이면 staged selector와 origin/main selector 결과가 같다")
void stagedAndOriginMainAgreeForSamePaths() {
	List<String> paths = List.of("src/main/java/com/dnd/qello/feed/service/InboxApplicationService.java");

	assertThat(ChangedJavaTypes.productionJavaTypesFromManifest(paths))
			.isEqualTo(ChangedJavaTypes.toTypeNames(paths));
}

@Test
@DisplayName("origin/main이 없으면 configuration failure로 실패한다")
void missingOriginMainIsConfigurationFailure() throws Exception {
	Path repo = Files.createTempDirectory("no-origin-main");
	git(repo, "init");

	assertThatThrownBy(() -> ChangedJavaTypes.productionJavaTypesFromOriginMain(repo))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("origin/main")
			.hasMessageNotContaining("QELLO-JAVA-TX-001");
}
~~~

Helper `gitRepoWithOriginMain` must `git init`, write the file, `git add`/`commit`, then `git update-ref refs/remotes/origin/main HEAD`. Set `user.email` and `user.name` locally in that repo only. Do not touch the real repository git config.

- [ ] **Step 2: Run selector tests before the class exists.**

~~~bash
./gradlew test --tests '*ChangedJavaTypesTest'
~~~

Expected: FAIL because `ChangedJavaTypes` does not exist.

- [ ] **Step 3: Implement `ChangedJavaTypes`.**

~~~java
public final class ChangedJavaTypes {

	private ChangedJavaTypes() {
	}

	public static Set<String> productionJavaTypesFromOriginMain(Path repositoryRoot) {
		ensureOriginMain(repositoryRoot);
		Set<String> names = new TreeSet<>();
		names.addAll(gitLines(repositoryRoot, "diff", "--name-only", "origin/main", "--", "src/main/java"));
		names.addAll(gitLines(repositoryRoot, "diff", "--name-only", "--cached", "origin/main", "--", "src/main/java"));
		return toTypeNames(names);
	}

	public static Set<String> productionJavaTypesFromManifest(Collection<String> relativePaths) {
		return toTypeNames(relativePaths);
	}

	public static Set<String> toTypeNames(Collection<String> relativePaths) {
		Set<String> types = new TreeSet<>();
		for (String path : relativePaths) {
			String normalized = path.replace('\\', '/');
			if (!normalized.startsWith("src/main/java/") || !normalized.endsWith(".java")) {
				continue;
			}
			types.add(normalized.substring("src/main/java/".length(), normalized.length() - ".java".length())
					.replace('/', '.'));
		}
		return types;
	}

	static void ensureOriginMain(Path repositoryRoot) {
		if (gitExit(repositoryRoot, "rev-parse", "--verify", "origin/main") != 0) {
			throw new IllegalStateException(
					"origin/main is missing; fetch origin main before running convention checks");
		}
	}
}
~~~

`gitLines` / `gitExit` run `git` with `directory(repositoryRoot)` and throw on unexpected IO. Empty diff is success with empty set.

- [ ] **Step 4: Re-run selector tests.**

~~~bash
./gradlew test --tests '*ChangedJavaTypesTest'
~~~

Expected: PASS.

- [ ] **Step 5: Prepare commit 2 for review.**

Suggested message:

~~~text
chore(conventions): select changed production Java types (#210)
~~~

---

## Task 3: Production audit inventory

**Files:**

- Create: `src/test/java/com/dnd/qello/architecture/ProductionConventionAuditTest.java`

**Interfaces:**

- Consumes UNIT-001 through UNIT-004 and UNIT-018.
- Reads `build/classes/java/main` with `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`.
- Does not write `baseline.json`.
- Canary: `com.dnd.qello.auth.service.DeviceTokenService` is `CLASS_WRITE`.
- `com.dnd.qello.notification.service.PushDeviceService` is absent from injection findings.
- `MISSING_READ_ONLY` and `CLASS_WRITE` sets are disjoint and `MISSING_READ_ONLY` is non-empty.

- [ ] **Step 1: Write the audit tests.**

~~~java
@Test
@DisplayName("production Service bytecode를 읽어 DeviceTokenService class write를 보고한다")
void reportsDeviceTokenServiceClassWrite() {
	AuditReport report = ProductionConventionAudit.scanMainClasses();

	assertThat(report.serviceNames()).contains("com.dnd.qello.auth.service.DeviceTokenService");
	assertThat(report.classWrite()).contains("com.dnd.qello.auth.service.DeviceTokenService");
}

@Test
@DisplayName("missing readOnly와 class write inventory는 겹치지 않는다")
void splitsMissingReadOnlyAndClassWrite() {
	AuditReport report = ProductionConventionAudit.scanMainClasses();

	assertThat(report.missingReadOnly()).isNotEmpty();
	assertThat(report.missingReadOnly()).doesNotContainAnyElementsOf(report.classWrite());
}

@Test
@DisplayName("감사는 baseline.json을 변경하지 않는다")
void auditDoesNotMutateBaseline() throws IOException {
	Path baseline = Path.of("config/java-conventions/baseline.json");
	String before = Files.readString(baseline);
	ProductionConventionAudit.scanMainClasses();
	assertThat(Files.readString(baseline)).isEqualTo(before);
}

@Test
@DisplayName("PushDeviceService constructor Autowired는 INJECTION-001 inventory에 없다")
void doesNotClassifyConstructorAutowiredAsInjection() {
	AuditReport report = ProductionConventionAudit.scanMainClasses();

	assertThat(report.injectionTargets())
			.noneMatch(target -> target.contains("PushDeviceService"));
}
~~~

Keep `AuditReport` in the same test class or as a package-private type in `ProductionConventionAuditTest` until Task 4 needs it. Do not add a production source type.

- [ ] **Step 2: Run the audit tests before the scanner exists.**

~~~bash
./gradlew test --tests '*ProductionConventionAuditTest'
~~~

Expected: FAIL because `ProductionConventionAudit` / `scanMainClasses` does not exist.

- [ ] **Step 3: Implement the scanner using shared rules.**

~~~java
JavaClasses classes = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPath(Path.of("build/classes/java/main"));

for (JavaClass type : classes) {
	if (!type.isAnnotatedWith("org.springframework.stereotype.Service")) {
		continue;
	}
	if (!type.getPackageName().startsWith("com.dnd.qello")) {
		continue;
	}
	report.serviceNames.add(type.getName());
	switch (ProductionConventionRules.classifyTransaction(type)) {
		case CLASS_WRITE -> report.classWrite.add(type.getName());
		case MISSING_READ_ONLY -> report.missingReadOnly.add(type.getName());
		case READ_ONLY -> {
		}
	}
}
ProductionConventionRules.noInjectionRule().evaluate(classes);
~~~

Collect injection findings from `EvaluationResult.getFailureReport().getDetails()` or by repeating the field/method `@Autowired` loops on `@Service` types. Fixture classes must not appear because tests are excluded from `build/classes/java/main`.

- [ ] **Step 4: Re-run audit tests.**

~~~bash
./gradlew test --tests '*ProductionConventionAuditTest'
~~~

Expected: PASS on current `main` production bytecode. If `DeviceTokenService` is no longer class write, stop and update the canary with evidence; do not weaken the scanner.

- [ ] **Step 5: Prepare commit 3 for review.**

Suggested message:

~~~text
chore(conventions): audit production transaction and injection inventory (#210)
~~~

---

## Task 4: Apply rules only to changed production Services

**Files:**

- Create: `src/test/java/com/dnd/qello/architecture/ProductionConventionRatchetTest.java`
- Modify: `build.gradle` `javaConventionArchitectureTest` filter and Test systemProperty

**Interfaces:**

- Consumes INT-001 through INT-006.
- `changedTypes()` uses `qelloConventionManifest` system property when present, otherwise `ChangedJavaTypes.productionJavaTypesFromOriginMain(Path.of("."))`.
- Rules run only on `@Service` types whose name is in `changedTypes()`.
- Empty changed set is success.
- Gradle task `javaConventionArchitectureTest` executes `*JavaConventionArchitectureTest`, `*ProductionConvention*`, `*ChangedJavaTypesTest`.

- [ ] **Step 1: Write ratchet tests with fixture classes and explicit type sets.**

~~~java
@Test
@DisplayName("변경되지 않은 violating Service는 ratchet을 막지 않는다")
void untouchedViolationDoesNotFail() {
	ProductionConventionRatchet.check(
			new ClassFileImporter().importClasses(ClassWriteTransactionalService.class),
			Set.of());
}

@Test
@DisplayName("변경된 class write Service는 QELLO-JAVA-TX-001로 실패한다")
void changedClassWriteFails() {
	assertThatThrownBy(() -> ProductionConventionRatchet.check(
			new ClassFileImporter().importClasses(ClassWriteTransactionalService.class),
			Set.of(ClassWriteTransactionalService.class.getName())))
			.hasMessageContaining("QELLO-JAVA-TX-001")
			.hasMessageContaining(ClassWriteTransactionalService.class.getName());
}

@Test
@DisplayName("변경된 read-only Service는 통과한다")
void changedCompliantPasses() {
	ProductionConventionRatchet.check(
			new ClassFileImporter().importClasses(ReadOnlyTransactionalService.class),
			Set.of(ReadOnlyTransactionalService.class.getName()));
}

@Test
@DisplayName("새 class write Service는 QELLO-JAVA-TX-001로 실패한다")
void newClassWriteFails() {
	assertThatThrownBy(() -> ProductionConventionRatchet.check(
			new ClassFileImporter().importClasses(ClassWriteTransactionalService.class),
			Set.of(ClassWriteTransactionalService.class.getName())))
			.hasMessageContaining("QELLO-JAVA-TX-001");
}

@Test
@DisplayName("staged 대상이 아닌 violating Service는 결과에 없다")
void unstagedViolationIsIgnored() {
	ProductionConventionRatchet.check(
			new ClassFileImporter().importClasses(
					ReadOnlyTransactionalService.class, ClassWriteTransactionalService.class),
			Set.of(ReadOnlyTransactionalService.class.getName()));
}
~~~

`ProductionConventionRatchet.check(JavaClasses, Set<String>)` filters then applies all four shared rules. INT-001/002 Git identity is covered by Task 2 selector plus this filter; do not compile production sources inside `@TempDir`.

- [ ] **Step 2: Run ratchet tests before implementation.**

~~~bash
./gradlew test --tests '*ProductionConventionRatchetTest'
~~~

Expected: FAIL because `ProductionConventionRatchet` does not exist.

- [ ] **Step 3: Implement the ratchet and a repository-level test.**

~~~java
public final class ProductionConventionRatchet {

	private ProductionConventionRatchet() {
	}

	public static void check(JavaClasses classes, Set<String> changedTypeNames) {
		JavaClasses selected = classes.that(new DescribedPredicate<>("changed production services") {
			@Override
			public boolean test(JavaClass javaClass) {
				return javaClass.isAnnotatedWith("org.springframework.stereotype.Service")
						&& changedTypeNames.contains(javaClass.getName());
			}
		});
		ProductionConventionRules.classReadOnlyRule().check(selected);
		ProductionConventionRules.noInjectionRule().check(selected);
		ProductionConventionRules.publicTransactionMethodsRule().check(selected);
		ProductionConventionRules.noTransactionSelfInvocationRule().check(selected);
	}
}
~~~

Add a test that imports `build/classes/java/main` and uses `ChangedJavaTypes.productionJavaTypesFromOriginMain(Path.of("."))`. On this branch, if no production Service file changed, it must pass. This is INT-007's ratchet half.

- [ ] **Step 4: Widen the Gradle architecture test filter and pass the staged manifest.**

In `build.gradle` `javaConventionArchitectureTest`:

~~~groovy
filter {
	includeTestsMatching '*JavaConventionArchitectureTest'
	includeTestsMatching '*ProductionConvention*'
	includeTestsMatching '*ChangedJavaTypesTest'
}
def manifest = providers.gradleProperty('qelloConventionManifest')
if (manifest.isPresent()) {
	systemProperty 'qelloConventionManifest', manifest.get()
}
~~~

`ProductionConventionRatchetTest` repository test reads `System.getProperty("qelloConventionManifest")`. If set, parse JSON `paths` like `build.gradle` `conventionManifestFiles` and call `productionJavaTypesFromManifest`. If unset, use origin/main.

- [ ] **Step 5: Prove the Gradle task runs the new tests.**

~~~bash
./gradlew javaConventionArchitectureTest --console=plain
~~~

Expected: output includes `JavaConventionArchitectureTest`, `ProductionConventionAuditTest`, `ProductionConventionRatchetTest`, `ChangedJavaTypesTest`, and the task succeeds on this branch.

- [ ] **Step 6: Prepare commit 4 for review.**

Suggested message:

~~~text
chore(conventions): ratchet ArchUnit rules on changed production services (#210)
~~~

---

## Task 5: Document the production ratchet

**Files:**

- Modify: `docs/harness/JAVA_CONVENTIONS.md`
- Modify: `TASK.md` existing-user-owned-changes if needed

**Interfaces:**

- Documents changed-file production TX/injection enforcement.
- States that inventory is not baseline.
- States constructor `@Autowired` is not `QELLO-JAVA-INJECTION-001`.

- [ ] **Step 1: Add a production scan section after Service 트랜잭션.**

Exact text to insert:

~~~markdown
## production scan과 changed-file ratchet

ArchUnit TX/injection 규칙은 production `@Service`를 대상으로 한다.

- 감사 inventory는 전체 production Service를 읽지만 `javaConventionCheck`를 실패시키지 않는다.
- `javaConventionCheck`는 `origin/main` 대비 변경된 production Java의 `@Service`에만 규칙을 강제한다.
- `javaConventionStagedCheck`는 `qelloConventionManifest` 경로 집합에 같은 규칙을 적용한다.
- 새 production Service 파일은 즉시 강제된다. 미수정 legacy는 Project draft로 추적한다.
- 기존 위반을 `LEGACY`로 추가하거나 hash만 갱신하지 않는다.
- field와 setter `@Autowired`는 `QELLO-JAVA-INJECTION-001`이다. constructor `@Autowired`는
  이 규칙의 대상이 아니다.
- `origin/main`이 없으면 검사를 건너뛰지 않고 configuration failure로 종료한다.
~~~

- [ ] **Step 2: Prepare commit 5 for review.**

Suggested message:

~~~text
chore(conventions): document production ArchUnit ratchet (#210)
~~~

---

## Task 6: Regression evidence and test report

**Files:**

- Create: `docs/test-reports/gh-210-TEST-REPORT-GH-210-PRODUCTION-CONVENTION-RATCHET.md` via `./harness test-run`
- Modify: `TASK.md` completion checkboxes only after observed results

**Interfaces:**

- Consumes UNIT-019, INT-007 through INT-009.
- Does not modify production source or baseline.

- [ ] **Step 1: Run focused convention tests.**

~~~bash
./gradlew test --tests '*JavaConventionArchitectureTest' --tests '*ChangedJavaTypesTest' --tests '*ProductionConvention*'
./gradlew javaConventionArchitectureTest
./gradlew javaConventionCheck
./gradlew test --tests '*JavaConventionBaselineTest'
~~~

Expected: all PASS. Baseline lifecycle still rejects new `LEGACY` and hash-only updates.

- [ ] **Step 2: Confirm no production or baseline mutation.**

~~~bash
git diff --name-only origin/main
git diff --check
~~~

Expected: `src/main/java`, `src/main/resources/db/migration`, API spec, `config/java-conventions/baseline.json` are absent from the diff. Allowed: test source, `build.gradle`, `docs/`, `TASK.md`.

- [ ] **Step 3: Run broader verification after focused tests pass.**

~~~bash
./gradlew test --tests '*JavaConvention*'
./gradlew check
./harness test-run --id TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET
./harness check
./harness pr-ready --project-tests
~~~

If an environment command cannot run, record command, error, scenario IDs and residual risk as `BLOCKED`. Do not mark unrun checks as PASS.

- [ ] **Step 4: Fill the test report from `templates/test-report.md`.**

Record actual counts, command outcomes, path-diff evidence, and application/DB/concurrency/transaction/external API/failure-recovery analysis. ArchUnit did not open PostgreSQL transactions. Residual risk: later `DeviceTokenService` cleanup must update the audit canary; constructor `@Autowired` remains deferred to the notification package draft.

- [ ] **Step 5: Prepare commit 6 for review.**

Suggested message:

~~~text
chore(conventions): record production ratchet verification evidence (#210)
~~~

---

## Spec coverage

| Requirement | Task |
| --- | --- |
| DEC-210-001 production 실제 스캔 | Task 3 |
| DEC-210-002 baseline에 기존 위반을 넣지 않음 | Task 3, Task 6 |
| DEC-210-003 origin/main·staged ratchet | Task 2, Task 4 |
| DEC-210-004 isolation/feed 비범위 | Global Constraints |
| DEC-210-005 공유 rules | Task 1 |
| DEC-210-006 setter 포함, constructor 비승격 | Task 1, Task 3 |
| DEC-210-007 inventory는 gate가 아님 | Task 3 vs Task 4 |
| UNIT-005~012 | Task 1 |
| UNIT-013~017 | Task 2 |
| UNIT-001~004, UNIT-018 | Task 3 |
| UNIT-019 | Task 6 |
| INT-001~006 | Task 2 + Task 4 |
| INT-007~009 | Task 6 |
| JAVA_CONVENTIONS 문서 | Task 5 |

## Placeholder scan

없음. 테스트 클래스 헤더 timestamp는 구현 시각이 예시를 지나면 그 시각으로 바꾼다.
