/*
 * Created at: 2026-09-02T17:42:43+09:00
 * Source scenario: TEST-PLAN-GH-210-PRODUCTION-CONVENTION-RATCHET-INT-001 through INT-007
 */
package com.dnd.qello.architecture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.architecture.fixture.ClassWriteTransactionalService;
import com.dnd.qello.architecture.fixture.ReadOnlyTransactionalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConventionRatchetTest {

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

	@Test
	@DisplayName("현재 저장소에서 변경되지 않은 production Service는 ratchet을 통과한다")
	void currentRepositoryUntouchedProductionPasses() throws IOException {
		JavaClasses classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPath(Path.of("build/classes/java/main"));
		ProductionConventionRatchet.check(classes, changedTypes());
	}

	private static Set<String> changedTypes() throws IOException {
		String manifest = System.getProperty("qelloConventionManifest");
		if (manifest != null && !manifest.isBlank()) {
			JsonNode paths = new ObjectMapper().readTree(Path.of(manifest).toFile()).path("paths");
			List<String> relative = new ArrayList<>();
			paths.forEach(node -> relative.add(node.asText()));
			return ChangedJavaTypes.productionJavaTypesFromManifest(relative);
		}
		return ChangedJavaTypes.productionJavaTypesFromOriginMain(Path.of("."));
	}
}
