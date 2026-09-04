/**
 * Created at: 2026-09-05T03:45:38+09:00
 * Source scenario: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-004 through INT-005
 */
package com.dnd.qello;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggingProfileIntegrationTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String PROBE_MESSAGE = "structured_logging_probe";
	private static final String PROBE_MAIN = "com.dnd.qello.StructuredLoggingProcessProbeApplication";
	private static final long CHILD_TIMEOUT_SECONDS = 20L;

	@Test
	@DisplayName("observability 프로필의 probe stdout 한 줄은 ECS JSON이다")
	void emitsEcsJsonOnlyWhenObservabilityProfileIsActive() throws Exception {
		ChildRun child = runProbe("observability");
		JsonNode ecs = OBJECT_MAPPER.readTree(child.probeLine());

		assertThat(ecs.path("message").asText()).isEqualTo(PROBE_MESSAGE);
		assertThat(ecsValue(ecs, "log.level").asText()).isEqualTo("INFO");
		assertThat(ecsValue(ecs, "service.name").asText()).isEqualTo("qello");
		assertThat(ecsValue(ecs, "service.version").asText()).isEqualTo("unknown");
		assertThat(ecsValue(ecs, "ecs.version").asText()).isNotBlank();
		assertThat(ecs.path("requestId").asText()).isEqualTo("profile-probe");
		assertThat(ecs.path("status").asInt()).isEqualTo(200);
	}

	@Test
	@DisplayName("default 프로필의 probe stdout은 ECS JSON이 아니며 프로세스는 성공한다")
	void keepsDefaultProfileConsoleOutputUnstructured() throws Exception {
		ChildRun child = runProbe("default");

		assertThat(child.exitCode()).isZero();
		assertThat(child.probeLine()).doesNotStartWith("{");
	}

	private static ChildRun runProbe(String profile) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Dfile.encoding=UTF-8",
				"-cp",
				childClasspath(),
				PROBE_MAIN,
				"--spring.main.banner-mode=off",
				"--spring.output.ansi.enabled=never",
				"--spring.profiles.active=" + profile);
		builder.redirectErrorStream(true);
		builder.environment().remove("QELLO_APP_VERSION");

		Process process = builder.start();
		try {
			if (!process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new AssertionError("child process did not exit within 20 seconds");
			}
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			int exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new AssertionError(
						"child process exited " + exitCode + " with output:" + System.lineSeparator() + output);
			}
			return new ChildRun(exitCode, probeLine(output));
		} finally {
			process.destroyForcibly();
		}
	}

	private static String probeLine(String output) {
		List<String> lines = output.lines()
				.filter(line -> line.contains(PROBE_MESSAGE))
				.toList();
		assertThat(lines)
				.as("exactly one child line containing %s", PROBE_MESSAGE)
				.hasSize(1);
		return lines.getFirst();
	}

	private static String childClasspath() throws URISyntaxException {
		LinkedHashSet<String> entries = new LinkedHashSet<>();
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		while (loader != null) {
			if (loader instanceof URLClassLoader urlClassLoader) {
				for (URL url : urlClassLoader.getURLs()) {
					if ("file".equals(url.getProtocol())) {
						entries.add(Path.of(url.toURI()).toString());
					}
				}
			}
			loader = loader.getParent();
		}
		String javaClassPath = System.getProperty("java.class.path");
		if (javaClassPath != null && !javaClassPath.isBlank()) {
			for (String entry : javaClassPath.split(File.pathSeparator, -1)) {
				if (!entry.isBlank()) {
					entries.add(entry);
				}
			}
		}
		if (entries.isEmpty()) {
			throw new AssertionError("child JVM classpath is empty");
		}
		return String.join(File.pathSeparator, entries);
	}

	private static JsonNode ecsValue(JsonNode root, String dottedName) {
		if (root.has(dottedName)) {
			return root.get(dottedName);
		}
		JsonNode current = root;
		for (String part : dottedName.split("\\.")) {
			if (current == null || current.isMissingNode() || !current.has(part)) {
				return MissingNode.getInstance();
			}
			current = current.get(part);
		}
		return current;
	}

	private record ChildRun(int exitCode, String probeLine) {
	}
}
