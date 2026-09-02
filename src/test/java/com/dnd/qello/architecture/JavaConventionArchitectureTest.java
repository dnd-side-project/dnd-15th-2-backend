/*
 * Created at: 2026-09-02T00:38:56+09:00
 * Source scenario: TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-010 through UNIT-014
 */
package com.dnd.qello.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.architecture.fixture.ClassWriteTransactionalService;
import com.dnd.qello.architecture.fixture.FieldInjectedService;
import com.dnd.qello.architecture.fixture.PrivateTransactionalService;
import com.dnd.qello.architecture.fixture.ReadOnlyTransactionalService;
import com.dnd.qello.architecture.fixture.SelfInvokingTransactionalService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaConventionArchitectureTest {

	@Test
	@DisplayName("클래스 단위 쓰기 Transactional은 QELLO-JAVA-TX-001로 거절한다")
	void rejectsClassWriteTransaction() {
		JavaClasses classes = new ClassFileImporter()
				.importClasses(ClassWriteTransactionalService.class);

		assertThatThrownBy(() -> classReadOnlyRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-TX-001");
	}

	@Test
	@DisplayName("클래스 readOnly Transactional은 허용한다")
	void acceptsClassReadOnlyTransaction() {
		JavaClasses classes = new ClassFileImporter()
				.importClasses(ReadOnlyTransactionalService.class);

		classReadOnlyRule().check(classes);
	}

	@Test
	@DisplayName("field injection은 QELLO-JAVA-INJECTION-001로 거절한다")
	void rejectsFieldInjection() {
		JavaClasses classes = new ClassFileImporter().importClasses(FieldInjectedService.class);

		assertThatThrownBy(() -> noFieldInjectionRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-INJECTION-001");
	}

	@Test
	@DisplayName("private transaction method는 QELLO-JAVA-TX-002로 거절한다")
	void rejectsPrivateTransactionMethod() {
		JavaClasses classes = new ClassFileImporter().importClasses(PrivateTransactionalService.class);

		assertThatThrownBy(() -> publicTransactionMethodsRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-TX-002");
	}

	@Test
	@DisplayName("transaction method self-invocation은 QELLO-JAVA-TX-003으로 거절한다")
	void rejectsTransactionSelfInvocation() {
		JavaClasses classes = new ClassFileImporter().importClasses(SelfInvokingTransactionalService.class);

		assertThatThrownBy(() -> noTransactionSelfInvocationRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-TX-003");
	}

	private static ArchRule classReadOnlyRule() {
		return classes()
				.that().areAnnotatedWith("org.springframework.stereotype.Service")
				.should(new ArchCondition<>("have class read-only transaction") {
					@Override
					public void check(JavaClass item, ConditionEvents events) {
						Transactional annotation = item.getAnnotationOfType(Transactional.class);
						if (annotation == null || !annotation.readOnly()) {
							events.add(SimpleConditionEvent.violated(item, "QELLO-JAVA-TX-001: " + item.getName()));
						}
					}
				});
	}

	private static ArchRule noFieldInjectionRule() {
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
					}
				});
	}

	private static ArchRule publicTransactionMethodsRule() {
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

	private static ArchRule noTransactionSelfInvocationRule() {
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
