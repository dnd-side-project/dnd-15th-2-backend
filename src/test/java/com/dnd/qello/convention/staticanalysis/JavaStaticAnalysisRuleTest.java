/*
 * Created at: 2026-09-02T00:38:56+09:00
 * Source scenario: TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-015 through UNIT-019
 */
package com.dnd.qello.convention.staticanalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaStaticAnalysisRuleTest {

	@Test
	@DisplayName("formatter와 Checkstyle task가 Gradle에 등록된다")
	void registersJavaConventionTasks() throws IOException, InterruptedException {
		Process process = new ProcessBuilder("./gradlew", "tasks", "--all", "--console=plain")
				.redirectErrorStream(true)
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(process.waitFor()).isZero();
		assertThat(output)
				.contains("spotlessApply")
				.contains("spotlessJavaCheck")
				.contains("checkstyleMain");
	}

	@Test
	@DisplayName("Checkstyle fixture는 method length와 complexity rule ID를 반환한다")
	void reportsStaticAnalysisRuleIds() throws IOException, InterruptedException {
		assertThat(checkFixture("CompliantService.java").exitCode()).isZero();
		assertThat(checkFixture("MethodLengthBoundary.java").output())
				.contains("QELLO-JAVA-SIZE-001");
		assertThat(checkFixture("ComplexityBoundary.java").output())
				.contains("QELLO-JAVA-CPLX-001");
		assertThat(checkFixture("WildcardAndBypass.java").output())
				.contains("QELLO-JAVA-IMPORT-001")
				.contains("QELLO-JAVA-BYPASS-001");
	}

	private static CommandResult checkFixture(String name) throws IOException, InterruptedException {
		Path fixture = Path.of("src", "test", "resources", "java-conventions", "static-analysis", name)
				.toAbsolutePath();
		Process process = new ProcessBuilder(
				"./gradlew", "-PqelloStaticFixture=" + fixture, "checkstyleFixture", "--console=plain")
				.redirectErrorStream(true)
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		return new CommandResult(process.waitFor(), output);
	}

	private record CommandResult(int exitCode, String output) {
	}
}
