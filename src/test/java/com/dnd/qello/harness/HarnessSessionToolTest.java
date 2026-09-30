package com.dnd.qello.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <pre>
 * Created at: 2026-09-30T03:35:30+09:00
 * Source scenario: TEST-PLAN-GH-290-REPO-MAP-UNIT-006
 * Additional scenarios: UNIT-007, UNIT-008
 * </pre>
 */
class HarnessSessionToolTest {
	private static final Path TOOL = Path.of("scripts/repo-map/session.py").toAbsolutePath();
	private static final Path MAP = Path.of("scripts/repo-map/run.py").toAbsolutePath();
	private static final ObjectMapper JSON = new ObjectMapper();
	@TempDir
	Path temp;

	@Test
	@DisplayName("Prepare never starts the product and stable identity excludes run output paths")
	void preparesWithoutCallingProduct() throws Exception {
		Fixture f = fixture();
		Path first = temp.resolve("first"), second = temp.resolve("second");
		assertEquals(0, prepare(f, "A", first).code());
		assertEquals(0, prepare(f, "A", second).code());
		assertFalse(Files.exists(f.calls()));
		JsonNode a = manifest(first), b = manifest(second);
		assertEquals(a.get("config_digest"), b.get("config_digest"));
		assertFalse(Files.exists(first.resolve("overlay.txt")));
		assertEquals("unknown", a.at("/config/effective_global_settings").asText());
		assertFalse(Files.readString(first.resolve("manifest.json")).contains("class Sample"));
	}

	@Test
	@DisplayName("B alone receives bounded map instructions while common identities match A")
	void separatesConditions() throws Exception {
		Fixture f = fixture();
		Path index = f.root().resolve("build/repo-map/index.json");
		Result generated = run(temp, "python3", MAP.toString(), "generate", "--root", f.root().toString(), "--output",
				index.toString());
		assertEquals(0, generated.code(), generated.text());
		Path a = temp.resolve("a"), b = temp.resolve("b");
		assertEquals(0, prepare(f, "A", a).code());
		Result result = prepare(f, "B", b, "--index", index.toString(), "--map-tool", MAP.toString(),
				"--verify-version");
		assertEquals(0, result.code(), result.text());
		assertEquals(manifest(a).at("/config/common"), manifest(b).at("/config/common"));
		assertNotEquals(manifest(a).get("config_digest"), manifest(b).get("config_digest"));
		String overlay = Files.readString(b.resolve("overlay.txt"));
		assertTrue(overlay.contains(index.toString()));
		assertTrue(overlay.contains("query"));
		assertFalse(overlay.contains("class Sample"));
		assertFalse(Files.exists(f.calls()));
		Path task = temp.resolve("b-task.txt");
		Files.writeString(task, "Inspect the synthetic declaration.");
		assertEquals(0, launch(b, task, manifest(b).get("config_digest").asText()).code());
		String sent = JSON.readTree(Files.readString(f.calls())).get("stdin").asText();
		assertTrue(sent.contains(index.toString()));
		assertTrue(sent.endsWith("Inspect the synthetic declaration."));
		Files.writeString(index, "{}\n");
		assertNotEquals(0,
				prepare(f, "B", temp.resolve("stale"), "--index", index.toString(), "--map-tool", MAP.toString())
						.code());
	}

	@Test
	@DisplayName("Dirty input, wrong revision, invalid condition and reused outputs fail closed")
	void rejectsInvalidPreparation() throws Exception {
		Fixture f = fixture();
		Path out = temp.resolve("run");
		assertEquals(0, prepare(f, "A", out).code());
		String original = Files.readString(out.resolve("manifest.json"));
		assertNotEquals(0, prepare(f, "A", out).code());
		assertEquals(original, Files.readString(out.resolve("manifest.json")));
		assertNotEquals(0, prepare(f, "C", temp.resolve("invalid")).code());
		assertNotEquals(0, prepare(f, "A", temp.resolve("wrong"), "--expected-revision",
				"0000000000000000000000000000000000000000").code());
		Files.writeString(f.root().resolve("src/main/java/Sample.java"), "class Changed {}\n");
		assertNotEquals(0, prepare(f, "A", temp.resolve("dirty")).code());
		assertFalse(Files.exists(f.calls()));
	}

	@Test
	@DisplayName("Launch verifies identity and uses exactly one fresh exec with task on stdin")
	void launchesFreshAndRejectsDrift() throws Exception {
		Fixture f = fixture();
		Path out = temp.resolve("run"), prompt = temp.resolve("task.txt");
		Files.writeString(prompt, "Inspect the synthetic declaration.");
		assertEquals(0, prepare(f, "A", out, "--verify-version").code());
		String digest = manifest(out).get("config_digest").asText();
		assertNotEquals(0, launch(out, prompt, "bad").code());
		assertFalse(Files.exists(f.calls()));
		Result result = launch(out, prompt, digest);
		assertEquals(0, result.code(), result.text());
		JsonNode call = JSON.readTree(Files.readString(f.calls()));
		assertEquals(
				List.of("exec", "--json", "--sandbox", "read-only", "--model", "test-model", "-c",
						"model_reasoning_effort=\"high\"", "-c", "check_for_update_on_startup=false", "-"),
				JSON.convertValue(call.get("args"), List.class));
		assertTrue(call.get("stdin").asText()
				.contains("Read-only exploration only. Do not edit files or implement changes."));
		assertTrue(call.get("stdin").asText().endsWith("Inspect the synthetic declaration."));
		assertNotEquals(0, launch(out, prompt, digest).code());
		Path drift = temp.resolve("drift");
		assertEquals(0, prepare(f, "A", drift, "--verify-version").code());
		Files.writeString(f.product(), Files.readString(f.product()) + "\n# changed\n");
		assertNotEquals(0, launch(drift, prompt, manifest(drift).get("config_digest").asText()).code());
	}

	@Test
	@DisplayName("Launch refuses changed user config entrypoints and unverified product version")
	void rejectsConfigDriftAndUnverifiedVersion() throws Exception {
		Fixture f = fixture();
		Path home = temp.resolve("codex-home");
		Files.createDirectories(home);
		Files.writeString(home.resolve("config.toml"), "model = \"fixture-one\"\n");
		Path out = temp.resolve("config-run"), unverified = temp.resolve("unverified"), task = temp.resolve("task.txt");
		Files.writeString(task, "Inspect fixture.");
		assertEquals(0, prepare(f, "A", unverified).code());
		assertNotEquals(0, launch(unverified, task, manifest(unverified).get("config_digest").asText()).code());
		assertEquals(0, prepare(f, "A", out, "--verify-version").code());
		assertFalse(manifest(out).at("/config/config_entrypoints/config.toml").isMissingNode());
		assertFalse(Files.readString(out.resolve("manifest.json")).contains("fixture-one"));
		Files.writeString(home.resolve("config.toml"), "model = \"fixture-two\"\n");
		assertNotEquals(0, launch(out, task, manifest(out).get("config_digest").asText()).code());
		assertFalse(Files.exists(f.calls()));
	}

	@Test
	@DisplayName("Clean detached baseline supports exploration without claiming implementation authorization")
	void permitsReadOnlyBaselineExploration() throws Exception {
		Fixture f = fixture();
		assertEquals(0, run(f.root(), "git", "checkout", "--detach").code());
		Path out = temp.resolve("exploration");
		Result result = prepare(f, "A", out, "--task-id", "EXPLORATION-001");
		assertEquals(0, result.code(), result.text());
		assertFalse(manifest(out).at("/config/implementation_authorized").asBoolean(true));
		assertEquals("detached", manifest(out).at("/run/observed_branch").asText());
	}

	private Result launch(Path output, Path task, String digest) throws Exception {
		return run(temp, "python3", TOOL.toString(), "launch", "--output", output.toString(), "--expected-config",
				digest, "--task-file", task.toString());
	}

	private Fixture fixture() throws Exception {
		Path root = temp.resolve("workspace");
		Files.createDirectories(root.resolve("src/main/java"));
		root = root.toRealPath();
		Files.writeString(root.resolve("src/main/java/Sample.java"), "class Sample { void inspect() {} }\n");
		Files.writeString(root.resolve("AGENTS.md"), "Read-only exploration; follow the task.\n");
		Files.writeString(root.resolve("TASK.md"),
				"- GitHub Issue: #290\n- Branch: chore/gh-290-fixture\n- TASK-ID: GH-290-FIXTURE\n");
		Files.writeString(root.resolve(".gitignore"), "build/\n");
		assertEquals(0, run(root, "git", "init", "-b", "chore/gh-290-fixture").code());
		assertEquals(0, run(root, "git", "add", ".").code());
		assertEquals(0, run(root, "git", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
				"commit", "-m", "fixture").code());
		String revision = run(root, "git", "rev-parse", "HEAD").text().trim();
		Path product = temp.resolve("fake-product"), calls = temp.resolve("calls.json");
		Files.writeString(product,
				"#!/usr/bin/env python3\nimport json,sys,pathlib\nif sys.argv[1:] == ['--version']:\n print('codex-cli 0.158.0')\nelse:\n pathlib.Path("
						+ JSON.writeValueAsString(calls.toString())
						+ ").write_text(json.dumps({'args':sys.argv[1:],'stdin':sys.stdin.read()}))\n");
		assertTrue(product.toFile().setExecutable(true));
		return new Fixture(root, revision, product, calls);
	}

	private Result prepare(Fixture f, String condition, Path output, String... extra) throws Exception {
		List<String> args = new ArrayList<>(List.of("python3", TOOL.toString(), "prepare", "--condition", condition,
				"--workspace", f.root().toString(), "--expected-revision", f.revision(), "--output", output.toString(),
				"--product", f.product().toString(), "--model", "test-model", "--effort", "high", "--task-id",
				"GH-290-FIXTURE"));
		args.addAll(List.of(extra));
		return run(temp, args.toArray(String[]::new));
	}

	private JsonNode manifest(Path output) throws Exception {
		return JSON.readTree(output.resolve("manifest.json").toFile());
	}

	private Result run(Path cwd, String... args) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(args).directory(cwd.toFile()).redirectErrorStream(true);
		builder.environment().put("CODEX_HOME", temp.resolve("codex-home").toString());
		builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
		Process process = builder.start();
		String text = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		return new Result(process.waitFor(), text);
	}

	private record Fixture(Path root, String revision, Path product, Path calls) {
	}
	private record Result(int code, String text) {
	}
}
