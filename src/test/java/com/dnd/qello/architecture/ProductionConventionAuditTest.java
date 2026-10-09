/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-UNIT-001 through UNIT-004, UNIT-018
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-022 (added 2026-10-09T18:14:46+09:00;
 * DeviceTokenService가 method 단위 쓰기 트랜잭션으로 바뀌어 class write 단언을 반대로 바꿨다, A7.
 * 그 결과 class write inventory가 비어 겹침 검사를 교집합 비교로 바꿨다)
 */
package com.dnd.qello.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConventionAuditTest {

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-022: production Service bytecode를 읽고 method 단위 쓰기로 바꾼 DeviceTokenService는 class write로 보고하지 않는다")
	void reportsDeviceTokenServiceAsClassReadOnly() {
		AuditReport report = scanMainClasses();

		assertThat(report.serviceNames()).contains("com.dnd.qello.auth.service.DeviceTokenService");
		assertThat(report.classWrite()).doesNotContain("com.dnd.qello.auth.service.DeviceTokenService");
		assertThat(report.missingReadOnly()).doesNotContain("com.dnd.qello.auth.service.DeviceTokenService");
	}

	@Test
	@DisplayName("missing readOnly와 class write inventory는 겹치지 않는다")
	void splitsMissingReadOnlyAndClassWrite() {
		AuditReport report = scanMainClasses();

		assertThat(report.missingReadOnly()).isNotEmpty();
		// class write Service가 하나도 없을 수 있다(#337). doesNotContainAnyElementsOf는 빈 목록을
		// 거절하므로 교집합으로 본다.
		Set<String> overlap = new TreeSet<>(report.missingReadOnly());
		overlap.retainAll(report.classWrite());
		assertThat(overlap).isEmpty();
	}

	@Test
	@DisplayName("감사는 baseline.json을 변경하지 않는다")
	void auditDoesNotMutateBaseline() throws IOException {
		Path baseline = Path.of("config/java-conventions/baseline.json");
		String before = Files.readString(baseline);
		scanMainClasses();
		assertThat(Files.readString(baseline)).isEqualTo(before);
	}

	@Test
	@DisplayName("PushDeviceService constructor Autowired는 INJECTION-001 inventory에 없다")
	void doesNotClassifyConstructorAutowiredAsInjection() {
		AuditReport report = scanMainClasses();

		assertThat(report.injectionTargets())
				.noneMatch(target -> target.contains("PushDeviceService"));
	}

	static AuditReport scanMainClasses() {
		JavaClasses classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPath(Path.of("build/classes/java/main"));
		Set<String> serviceNames = new TreeSet<>();
		Set<String> classWrite = new TreeSet<>();
		Set<String> missingReadOnly = new TreeSet<>();
		Set<String> injectionTargets = new TreeSet<>();
		for (JavaClass type : classes) {
			if (!type.isAnnotatedWith(Service.class)) {
				continue;
			}
			if (!type.getPackageName().startsWith("com.dnd.qello")) {
				continue;
			}
			serviceNames.add(type.getName());
			switch (ProductionConventionRules.classifyTransaction(type)) {
				case CLASS_WRITE -> classWrite.add(type.getName());
				case MISSING_READ_ONLY -> missingReadOnly.add(type.getName());
				case READ_ONLY -> {
				}
			}
			for (JavaField field : type.getFields()) {
				if (field.isAnnotatedWith(Autowired.class)) {
					injectionTargets.add(field.getFullName());
				}
			}
			for (JavaMethod method : type.getMethods()) {
				if (method.isAnnotatedWith(Autowired.class)) {
					injectionTargets.add(method.getFullName());
				}
			}
		}
		return new AuditReport(serviceNames, classWrite, missingReadOnly, injectionTargets);
	}

	record AuditReport(
			Set<String> serviceNames,
			Set<String> classWrite,
			Set<String> missingReadOnly,
			Set<String> injectionTargets) {
	}
}
