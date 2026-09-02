/*
 * Created at: 2026-09-02T00:38:56+09:00
 * Source scenario: TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-010 through UNIT-014
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-005 through UNIT-012
 * Source scenario: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION-INT-004
 */
package com.dnd.qello.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.architecture.fixture.ClassWriteTransactionalService;
import com.dnd.qello.architecture.fixture.CollaboratingTransactionalService;
import com.dnd.qello.architecture.fixture.ConstructorAutowiredService;
import com.dnd.qello.architecture.fixture.FieldInjectedService;
import com.dnd.qello.architecture.fixture.PrivateTransactionalService;
import com.dnd.qello.architecture.fixture.QueryLikeTransactionalService;
import com.dnd.qello.architecture.fixture.ReadOnlyTransactionalService;
import com.dnd.qello.architecture.fixture.SelfInvokingTransactionalService;
import com.dnd.qello.architecture.fixture.SetterInjectedService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaConventionArchitectureTest {

	@Test
	@DisplayName("클래스 단위 쓰기 Transactional은 QELLO-JAVA-TX-001로 거절한다")
	void rejectsClassWriteTransaction() {
		JavaClasses classes = new ClassFileImporter()
				.importClasses(ClassWriteTransactionalService.class);

		assertThatThrownBy(() -> ProductionConventionRules.classReadOnlyRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-TX-001");
	}

	@Test
	@DisplayName("클래스 readOnly Transactional은 허용한다")
	void acceptsClassReadOnlyTransaction() {
		JavaClasses classes = new ClassFileImporter()
				.importClasses(ReadOnlyTransactionalService.class);

		ProductionConventionRules.classReadOnlyRule().check(classes);
	}

	@Test
	@DisplayName("field injection은 QELLO-JAVA-INJECTION-001로 거절한다")
	void rejectsFieldInjection() {
		JavaClasses classes = new ClassFileImporter().importClasses(FieldInjectedService.class);

		assertThatThrownBy(() -> ProductionConventionRules.noInjectionRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-INJECTION-001");
	}

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

	@Test
	@DisplayName("private transaction method는 QELLO-JAVA-TX-002로 거절한다")
	void rejectsPrivateTransactionMethod() {
		JavaClasses classes = new ClassFileImporter().importClasses(PrivateTransactionalService.class);

		assertThatThrownBy(() -> ProductionConventionRules.publicTransactionMethodsRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-TX-002");
	}

	@Test
	@DisplayName("transaction method self-invocation은 QELLO-JAVA-TX-003으로 거절한다")
	void rejectsTransactionSelfInvocation() {
		JavaClasses classes = new ClassFileImporter().importClasses(SelfInvokingTransactionalService.class);

		assertThatThrownBy(() -> ProductionConventionRules.noTransactionSelfInvocationRule().check(classes))
				.hasMessageContaining("QELLO-JAVA-TX-003");
	}

	@Test
	@DisplayName("다른 Service의 트랜잭션 메서드 호출은 QELLO-JAVA-TX-003이 아니다")
	void acceptsCollaboratorTransactionCall() {
		JavaClasses classes = new ClassFileImporter().importClasses(
				CollaboratingTransactionalService.class, QueryLikeTransactionalService.class);

		ProductionConventionRules.noTransactionSelfInvocationRule().check(classes);
	}
}
