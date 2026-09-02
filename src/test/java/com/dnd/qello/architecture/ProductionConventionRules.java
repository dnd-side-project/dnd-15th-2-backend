/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-005 through UNIT-012
 */
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
		READ_ONLY, CLASS_WRITE, MISSING_READ_ONLY
	}

	public static TransactionShape classifyTransaction(JavaClass item) {
		if (!item.isAnnotatedWith(Transactional.class)) {
			return TransactionShape.MISSING_READ_ONLY;
		}
		Transactional annotation = item.getAnnotationOfType(Transactional.class);
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
							if (!call.getTarget().isAnnotatedWith(Transactional.class)) {
								continue;
							}
							if (!call.getOrigin().getOwner().getName()
									.equals(call.getTarget().getOwner().getName())) {
								continue;
							}
							events.add(SimpleConditionEvent.violated(call,
									"QELLO-JAVA-TX-003: " + call.getOrigin().getFullName()
											+ " -> " + call.getTarget().getFullName()));
						}
					}
				});
	}
}
