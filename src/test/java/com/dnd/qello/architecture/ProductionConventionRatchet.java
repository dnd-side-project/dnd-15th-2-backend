/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-001 through INT-006
 */
package com.dnd.qello.architecture;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;

public final class ProductionConventionRatchet {

	private ProductionConventionRatchet() {
	}

	public static void check(JavaClasses classes, Set<String> changedTypeNames) {
		JavaClasses selected = classes.that(DescribedPredicate.describe("changed production services",
				(JavaClass javaClass) -> javaClass.isAnnotatedWith(Service.class)
						&& changedTypeNames.contains(javaClass.getName())));
		ProductionConventionRules.classReadOnlyRule().allowEmptyShould(true).check(selected);
		ProductionConventionRules.noInjectionRule().allowEmptyShould(true).check(selected);
		ProductionConventionRules.publicTransactionMethodsRule().allowEmptyShould(true).check(selected);
		ProductionConventionRules.noTransactionSelfInvocationRule().allowEmptyShould(true).check(selected);
	}
}
