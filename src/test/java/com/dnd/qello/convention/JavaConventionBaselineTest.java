/*
 * Created at: 2026-09-02T00:38:56+09:00
 * Source scenario: TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-001 through UNIT-007
 */
package com.dnd.qello.convention;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class JavaConventionBaselineTest {

	@Test
	@DisplayName("baseline validator의 self-test가 통과한다")
	void validatorSelfTestPasses() throws IOException, InterruptedException {
		CommandResult result = run("--self-test");

		assertThat(result.exitCode()).isZero();
		assertThat(result.output()).contains("Java convention baseline self-test passed.");
	}

	@Test
	@DisplayName("wildcard baseline target은 QELLO-JAVA-BASELINE-003으로 거절한다")
	void rejectsWildcardTarget(@TempDir Path temporaryDirectory) throws IOException, InterruptedException {
		Path baseline = temporaryDirectory.resolve("baseline.json");
		Files.writeString(baseline, """
				{
				  "schemaVersion": 1,
				  "bootstrapIssue": 208,
				  "entries": [{
				    "id": "JAVA-CONV-0001",
				    "rule": "QELLO-JAVA-IMPORT-001",
				    "target": "com.dnd.qello.*",
				    "sourceSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
				    "classification": "LEGACY",
				    "reason": "fixture",
				    "trackingReference": "fixture",
				    "reviewBy": "2026-12-31"
				  }]
				}
				""");

		CommandResult result = run("--baseline", baseline.toString());

		assertThat(result.exitCode()).isNotZero();
		assertThat(result.output()).contains("QELLO-JAVA-BASELINE-003");
	}

	@Test
	@DisplayName("Gradle은 validateJavaConventionBaseline task를 제공한다")
	void registersBaselineValidationTask() throws IOException, InterruptedException {
		Process process = new ProcessBuilder("./gradlew", "tasks", "--all", "--console=plain")
				.redirectErrorStream(true)
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(process.waitFor()).isZero();
		assertThat(output).contains("validateJavaConventionBaseline");
	}

	@Test
	@DisplayName("승인되지 않은 decisionId의 예외는 QELLO-JAVA-BASELINE-005로 거절한다")
	void rejectsUnapprovedExceptionDecision(@TempDir Path temporaryDirectory) throws IOException, InterruptedException {
		Path baseline = temporaryDirectory.resolve("baseline.json");
		Files.writeString(baseline, """
				{
				  "schemaVersion": 1,
				  "bootstrapIssue": 208,
				  "entries": [{
				    "id": "JAVA-CONV-0002",
				    "rule": "QELLO-JAVA-TX-001",
				    "target": "com.dnd.qello.ExampleService",
				    "sourceSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
				    "classification": "JUSTIFIED_EXCEPTION",
				    "reason": "fixture",
				    "designReference": "docs/design.md",
				    "decisionId": "DEC-999-001"
				  }]
				}
				""");

		CommandResult result = run("--baseline", baseline.toString());

		assertThat(result.exitCode()).isNotZero();
		assertThat(result.output()).contains("QELLO-JAVA-BASELINE-005");
	}

	@Test
	@DisplayName("현재 Git blob과 다른 LEGACY hash는 QELLO-JAVA-BASELINE-006으로 거절한다")
	void rejectsStaleLegacyHash(@TempDir Path temporaryDirectory) throws IOException, InterruptedException {
		Path baseline = temporaryDirectory.resolve("baseline.json");
		Files.writeString(baseline, """
				{
				  "schemaVersion": 1,
				  "bootstrapIssue": 208,
				  "entries": [{
				    "id": "JAVA-CONV-0003",
				    "rule": "QELLO-JAVA-IMPORT-001",
				    "target": "com.dnd.qello.account.service.ProfileService",
				    "sourceSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
				    "classification": "LEGACY",
				    "reason": "fixture",
				    "trackingReference": "fixture",
				    "reviewBy": "2026-12-31"
				  }]
				}
				""");

		CommandResult result = run("--baseline", baseline.toString());

		assertThat(result.exitCode()).isNotZero();
		assertThat(result.output()).contains("QELLO-JAVA-BASELINE-006");
	}

	@Test
	@DisplayName("baseline validator는 Checkstyle suppression XML을 생성한다")
	void generatesSuppressionXml(@TempDir Path temporaryDirectory) throws IOException, InterruptedException {
		Path suppression = temporaryDirectory.resolve("baseline-suppressions.xml");

		CommandResult result = run("--suppression-output", suppression.toString());

		assertThat(result.exitCode()).isZero();
		assertThat(Files.readString(suppression)).contains("<suppressions");
	}

	@Test
	@DisplayName("validateJavaConventionBaseline task는 generated suppression을 만든다")
	void gradleBaselineTaskGeneratesSuppression() throws IOException, InterruptedException {
		Process process = new ProcessBuilder("./gradlew", "validateJavaConventionBaseline", "--console=plain")
				.redirectErrorStream(true)
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(process.waitFor()).isZero();
		assertThat(Path.of("build", "generated", "checkstyle", "baseline-suppressions.xml"))
				.exists();
	}

	private static CommandResult run(String... arguments) throws IOException, InterruptedException {
		String[] command = new String[arguments.length + 2];
		command[0] = "python3";
		command[1] = "scripts/validate-java-conventions.py";
		System.arraycopy(arguments, 0, command, 2, arguments.length);
		Process process = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		return new CommandResult(process.waitFor(), output);
	}

	private record CommandResult(int exitCode, String output) {
	}
}
