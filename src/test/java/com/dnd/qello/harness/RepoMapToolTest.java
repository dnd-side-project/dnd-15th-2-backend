/**
 * Created at: 2026-11-24T19:04:51+09:00
 * Source scenario: TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-003, TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-004
 */
package com.dnd.qello.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * <pre>
 * Created at: 2026-09-30T03:35:08+09:00
 * Source scenario: TEST-PLAN-GH-290-REPO-MAP-UNIT-001
 * Additional scenarios: UNIT-002, UNIT-003, UNIT-004, UNIT-005, UNIT-009, UNIT-010, UNIT-011
 * Windows execution: TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-006 through UNIT-007
 * </pre>
 */
class RepoMapToolTest {
	private static final Path TOOL = Path.of("scripts/repo-map/run.py").toAbsolutePath();
	private static final ObjectMapper JSON = new ObjectMapper();
	private Path executableTool = TOOL;
	private String javaHome = System.getProperty("java.home");
	private Map<String, String> inheritedEnvironment = Map.of();
	@TempDir
	Path root;

	@Test
	@DisplayName("UNIT-001 AST declarations remain deterministic without body or annotation literals")
	void syntaxOnly() throws Exception {
		init();
		source("Sample.java", """
				package sample;
				import java.util.List;
				@Deprecated(since="PRIVATE_LITERAL")
				class Sample<T extends Number> extends Base implements Runnable {
				  String text = "class Fake {}";
				  public void run() { class Local {} }
				  public int find(@Mark(value="TYPE_LITERAL") String text) { return 9; }
				  public int find(int count) { return 8; }
				  record Nested(String value) {}
				}
				""");
		generate();
		String first = Files.readString(index());
		generate();
		assertThat(Files.readString(index())).isEqualTo(first);
		assertThat(first).contains("Sample.Nested", "find(String)", "find(int)", "java.util.List", "Base")
				.doesNotContain("PRIVATE_LITERAL", "TYPE_LITERAL", "Fake", "Local", "return 9");
		JsonNode rows = JSON.readTree(first).get("rows");
		assertThat(rows.size()).isEqualTo(6);
		assertThat(rows.get(1).get("kind").asText()).isEqualTo("class");
		assertThat(rows.get(1).get("line").asInt()).isEqualTo(3);
		assertThat(rows.get(2).get("symbol").asText()).isEqualTo("run");
		assertThat(rows.get(2).get("line").asInt()).isEqualTo(6);
		assertThat(rows.get(5).get("kind").asText()).isEqualTo("record");
		assertThat(rows.get(5).get("line").asInt()).isEqualTo(9);
	}

	@Test
	@DisplayName("UNIT-002 added edited deleted sources and mismatched metadata invalidate check and query")
	void staleInputs() throws Exception {
		init();
		source("One.java", "class One {}");
		generate();
		source("Two.java", "class Two {}");
		assertThat(run("check").code()).isNotZero();
		assertThat(run("query", "--symbol", "One", "--limit", "2").code()).isNotZero();
		generate();
		source("Two.java", "class Two { void edit() {} }");
		assertThat(run("check").code()).isNotZero();
		generate();
		Files.delete(root.resolve("src/main/java/Two.java"));
		assertThat(run("check").code()).isNotZero();
		generate();
		String valid = Files.readString(index());
		for (String field : List.of("runtime", "tool", "head", "schema")) {
			var data = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(valid);
			data.put(field, "different");
			Files.writeString(index(), data.toString());
			assertThat(run("check").code()).as(field).isNotZero();
		}
	}

	@Test
	@DisplayName("UNIT-002 actual tool bytes changes invalidate unchanged index")
	void actualToolChanges() throws Exception {
		init();
		source("One.java", "class One {}");
		Path copiedTools = Files.createDirectories(root.resolve("build/copied-tools"));
		executableTool = Files.copy(TOOL, copiedTools.resolve("run.py"));
		Path helper = Files.copy(TOOL.resolveSibling("RepoMap.java"), copiedTools.resolve("RepoMap.java"));
		generate();
		String original = Files.readString(helper);
		Files.writeString(helper, original + "\n// changed tool fixture\n");
		assertThat(run("check").code()).isNotZero();
		assertThat(run("query", "--symbol", "One").code()).isNotZero();
		Files.writeString(helper, original);
		assertThat(run("check").code()).isZero();
	}

	@Test
	@DisplayName("UNIT-002 reported runtime changes invalidate unchanged index")
	void reportedRuntimeChanges() throws Exception {
		// 가짜 java는 shebang Python 스크립트다. Windows는 확장자 없는 스크립트를 java 실행 파일로 실행하지 못한다.
		assumeFalse(PlatformCommands.isWindows(), "script-based java fixture needs a POSIX shebang");
		init();
		source("One.java", "class One {}");
		generate();
		Path bin = Files.createDirectories(root.resolve("build/runtime/bin"));
		Path java = bin.resolve("java");
		String wrapper = "#!/usr/bin/env python3\nimport os, sys\n"
				+ "if sys.argv[1:] == ['-version']:\n"
				+ " print('openjdk version \"21-fixture-one\"', file=sys.stderr)\n sys.exit(0)\n"
				+ "real = " + JSON.writeValueAsString(Path.of(javaHome, "bin/java").toString()) + "\n"
				+ "os.execv(real, [real] + sys.argv[1:])\n";
		Files.writeString(java, wrapper);
		assertThat(java.toFile().setExecutable(true)).isTrue();
		javaHome = bin.getParent().toString();
		generate();
		assertThat(run("check").code()).isZero();
		Files.writeString(java, wrapper.replace("fixture-one", "fixture-two"));
		assertThat(run("check").code()).isNotZero();
		assertThat(run("query", "--symbol", "One").code()).isNotZero();
	}

	@Test
	@DisplayName("UNIT-001 UNIT-005 reverse file creation order is stable and duplicate names retain ownership")
	void creationOrderAndDuplicateNames() throws Exception {
		init();
		Path main = root.resolve("src/main/java/alpha/Same.java");
		Path test = root.resolve("src/test/java/beta/Same.java");
		Files.createDirectories(main.getParent());
		Files.createDirectories(test.getParent());
		Files.writeString(main, "package alpha; class Same {}\n");
		Files.writeString(test, "package beta; class Same {}\n");
		generate();
		String first = Files.readString(index());
		Files.delete(main);
		Files.delete(test);
		Files.writeString(test, "package beta; class Same {}\n");
		Files.writeString(main, "package alpha; class Same {}\n");
		generate();
		assertThat(Files.readString(index())).isEqualTo(first);
		Result query = run("query", "--symbol", "Same", "--limit", "20");
		assertThat(query.code()).isZero();
		JsonNode rows = JSON.readTree(query.text()).get("rows");
		assertThat(rows.size()).isEqualTo(4);
		assertThat(rows.get(0).get("type").asText()).isEqualTo("alpha.Same");
		assertThat(rows.get(0).get("source_root").asText()).isEqualTo("src/main/java");
		assertThat(rows.get(0).get("source_path").asText()).isEqualTo("alpha/Same.java");
		assertThat(rows.get(2).get("type").asText()).isEqualTo("beta.Same");
		assertThat(rows.get(2).get("source_root").asText()).isEqualTo("src/test/java");
		assertThat(rows.get(2).get("source_path").asText()).isEqualTo("beta/Same.java");
	}

	@Test
	@DisplayName("UNIT-003 parse failure preserves old index and never logs source text")
	void parseFailure() throws Exception {
		init();
		source("One.java", "class One {}");
		generate();
		String old = Files.readString(index());
		source("One.java", "class One { PRIVATE_LITERAL broken = ; }");
		Result result = run("generate");
		assertThat(result.code()).isNotZero();
		assertThat(result.text()).doesNotContain("PRIVATE_LITERAL");
		assertThat(Files.readString(index())).isEqualTo(old);
	}

	@Test
	@DisplayName("UNIT-004 source output targets are rejected without modification")
	void unsafeOutputTarget() throws Exception {
		init();
		Path file = source("One.java", "class One {}");
		generate();
		Result unsafe = command(PlatformCommands.python(TOOL.toString(), "generate", "--root", root.toString(),
				"--output", file.toString()).toArray(String[]::new));
		assertThat(unsafe.code()).isNotZero();
		assertThat(Files.readString(file)).isEqualTo("class One {}");
	}

	@Test
	@DisplayName("UNIT-004 symlink escape is rejected without modification")
	void symlinkEscape() throws Exception {
		assumeTrue(PlatformCommands.canCreateSymbolicLinks(root),
				"symbolic links are not permitted (Windows needs Developer Mode)");
		init();
		source("One.java", "class One {}");
		generate();
		String old = Files.readString(index());
		Files.createSymbolicLink(root.resolve("src/main/java/Escape.java"), TOOL);
		assertThat(run("generate").code()).isNotZero();
		assertThat(Files.readString(index())).isEqualTo(old);
	}

	@Test
	@DisplayName("UNIT-005 queries disambiguate overloads and honestly bound matches")
	void boundedQuery() throws Exception {
		init();
		source("One.java", "class One { void find(int n) {} void find(String s) {} }");
		generate();
		Result result = run("query", "--symbol", "find", "--limit", "1");
		assertThat(result.code()).isZero();
		JsonNode query = JSON.readTree(result.text());
		assertThat(query.get("total").asInt()).isEqualTo(2);
		assertThat(query.get("truncated").asBoolean()).isTrue();
		assertThat(query.get("rows").size()).isEqualTo(1);
		assertThat(query.get("rows").get(0).get("file").asText()).isEqualTo("src/main/java/One.java");
		assertThat(query.get("rows").get(0).get("source_root").asText()).isEqualTo("src/main/java");
		assertThat(query.get("rows").get(0).get("source_path").asText()).isEqualTo("One.java");
		assertThat(JSON.readTree(run("query", "--symbol", "absent", "--limit", "1").text())
				.get("total").asInt()).isZero();
		assertThat(run("query", "--symbol", "find", "--limit", "0").code()).isNotZero();
	}

	@Test
	@DisplayName("UNIT-009 grouped symbols and declared dependencies exclude body and annotation content")
	void groupedDeclarations() throws Exception {
		init();
		source("Service.java",
				"""
						package demo;
						class Service<T> {
						  @Mark("PRIVATE_LITERAL") java.util.Map<@TypeMark("TYPE_PRIVATE_LITERAL") String, ? extends Repo[]> field = null;
						  static Cache shared = new InitializerOnly();
						  int count; T generic;
						  <C> Service(Port port, C unknown) { new BodyOnly(); }
						  void save() {}
						  void save(int n) {}
						  ReturnOnly load(MethodOnly ignored) { return null; }
						  class Nested<U> { T outer; U own; java.util.List<? super Value[]> kept; }
						}
						""");
		generate();
		JsonNode result = classes("Port", 1);
		assertThat(result.get("total").asInt()).isEqualTo(1);
		JsonNode group = result.get("classes").get(0);
		assertThat(group.get("type").asText()).isEqualTo("demo.Service");
		assertThat(group.get("path").asText()).isEqualTo("Service.java");
		assertThat(group.get("source_root").asText()).isEqualTo("src/main/java");
		assertThat(group.get("symbols")).isEqualTo(JSON.readTree("[\"load\",\"save\"]"));
		assertThat(group.get("dependencies")).isEqualTo(JSON.readTree(
				"[\"Cache\",\"Port\",\"Repo\",\"String\",\"java.util.Map\"]"));
		assertThat(group.get("dependency_evidence")).isEqualTo(JSON.readTree("""
				[{"dependency":"Cache","source":"field","name":"shared","line":4},
				 {"dependency":"Port","source":"constructor_parameter","name":"port","line":6},
				 {"dependency":"Repo","source":"field","name":"field","line":3},
				 {"dependency":"String","source":"field","name":"field","line":3},
				 {"dependency":"java.util.Map","source":"field","name":"field","line":3}]
				"""));
		assertThat(result.toString()).doesNotContain("PRIVATE_LITERAL", "InitializerOnly", "BodyOnly", "ReturnOnly",
				"MethodOnly");
		JsonNode nested = classes("Nested", 10).get("classes").get(0);
		assertThat(nested.get("dependencies")).isEqualTo(JSON.readTree("[\"Value\",\"java.util.List\"]"));
	}

	@Test
	@DisplayName("UNIT-009 record components and static fields count while enum constants are not type evidence")
	void groupedRecordAndEnum() throws Exception {
		init();
		source("Shapes.java", """
				record Shape(Value item) { Shape { } static Cache cache; void draw() {} }
				enum Mode { FIRST; private Port port; void use() {} }
				""");
		generate();
		JsonNode shape = classes("draw", 10).get("classes").get(0);
		assertThat(shape.get("dependencies")).isEqualTo(JSON.readTree("[\"Cache\",\"Value\"]"));
		assertThat(shape.get("symbols")).isEqualTo(JSON.readTree("[\"draw\"]"));
		assertThat(shape.get("dependency_evidence").size()).isEqualTo(2);
		JsonNode mode = classes("Mode", 10).get("classes").get(0);
		assertThat(mode.get("dependencies")).isEqualTo(JSON.readTree("[\"Port\"]"));
	}

	@Test
	@DisplayName("UNIT-009 member types shadow outer generics before declaration while constructor generics remain excluded")
	void groupedLexicalTypeShadowing() throws Exception {
		init();
		source("Outer.java", """
				class Outer<T> {
				  class Inner {
				    T value;
				    Inner(T value) {}
				    <T> Inner(T generic, int unused) {}
				    class T { T self; }
				  }
				}
				""");
		generate();
		JsonNode group = classes("Outer.Inner", 10).get("classes").get(0);
		assertThat(group.get("type").asText()).isEqualTo("Outer.Inner");
		assertThat(group.get("dependencies")).isEqualTo(JSON.readTree("[\"T\"]"));
		assertThat(group.get("dependency_evidence")).isEqualTo(JSON.readTree("""
				[{"dependency":"T","source":"constructor_parameter","name":"value","line":4},
				 {"dependency":"T","source":"field","name":"value","line":3}]
				"""));
		JsonNode nested = classes("Outer.Inner.T", 10).get("classes").get(0);
		assertThat(nested.get("dependencies")).isEqualTo(JSON.readTree("[\"T\"]"));
	}

	@Test
	@DisplayName("UNIT-010 grouped limits preserve complete collision-safe classes across source roots")
	void groupedCollisions() throws Exception {
		init();
		source("Same.java", "package demo; class Same { void alpha() {} void beta() {} }");
		Path test = root.resolve("src/test/java/Same.java");
		Files.createDirectories(test.getParent());
		Files.writeString(test, "package demo; class Same { void gamma() {} }");
		generate();
		JsonNode limited = classes("Same", 1);
		assertThat(limited.get("total").asInt()).isEqualTo(2);
		assertThat(limited.get("truncated").asBoolean()).isTrue();
		assertThat(limited.get("classes").size()).isEqualTo(1);
		assertThat(limited.get("classes").get(0).get("symbols")).isEqualTo(JSON.readTree("[\"alpha\",\"beta\"]"));
		JsonNode all = classes("Same", 10).get("classes");
		assertThat(all.get(0).get("type")).isEqualTo(all.get(1).get("type"));
		assertThat(all.get(0).get("source_root").asText()).isEqualTo("src/main/java");
		assertThat(all.get(1).get("source_root").asText()).isEqualTo("src/test/java");
		assertThat(classes("beta", 10).get("classes").get(0).get("symbols").size()).isEqualTo(2);
		assertThat(classes("src/test/java", 10).get("total").asInt()).isEqualTo(1);
		JsonNode empty = classes("Absent", 10);
		assertThat(empty.get("total").asInt()).isZero();
		assertThat(empty.get("truncated").asBoolean()).isFalse();
		assertThat(empty.get("classes").isEmpty()).isTrue();
	}

	@Test
	@DisplayName("UNIT-011 old schema rejects both queries and regeneration preserves default row counts")
	void groupedSchemaCompatibility() throws Exception {
		init();
		source("One.java", "class One { Port port; void find() {} }");
		generate();
		var data = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(Files.readString(index()));
		assertThat(data.get("rows").size()).isEqualTo(3);
		data.put("schema", 1);
		Files.writeString(index(), data.toString());
		assertThat(run("check").code()).isNotZero();
		assertThat(run("query", "--symbol", "One", "--format", "classes").code()).isNotZero();
		generate();
		JsonNode legacy = JSON.readTree(run("query", "--symbol", "find").text());
		assertThat(legacy.get("total").asInt()).isEqualTo(1);
		assertThat(legacy.get("rows").get(0).get("signature").asText()).isEqualTo("find():void");
		assertThat(classes("find", 10).get("total").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("GH-386 UNIT-003 foreign Git environments never redirect fixture writes")
	void foreignGitEnvironment() throws Exception {
		Path fixtures = root.toRealPath();
		Map<String, String> config = Map.of("HOME", fixtures.toString(), "GIT_CONFIG_NOSYSTEM", "1",
				"GIT_CONFIG_GLOBAL", fixtures.resolve("global.config").toString(),
				"GIT_CONFIG_SYSTEM", fixtures.resolve("system.config").toString());
		inheritedEnvironment = config;
		Path sentinel = sentinel(fixtures);
		Map<String, String> before = gitState(sentinel);
		Map<String, String> poison = foreignEnvironment(sentinel);
		List<Map<String, String>> cases = new java.util.ArrayList<>();
		poison.forEach((key, value) -> cases.add(Map.of(key, value)));
		cases.add(poison);
		for (int i = 0; i < cases.size(); i++) {
			root = Files.createDirectory(fixtures.resolve("target-" + i));
			inheritedEnvironment = new TreeMap<>(config);
			inheritedEnvironment.putAll(cases.get(i));
			init();
			assertThat(command("git", "rev-parse", "--show-toplevel").text().strip())
					.isEqualTo(root.toString());
			assertThat(command("git", "log", "-1", "--format=%s").text().strip()).isEqualTo("fixture");
			assertThat(gitState(sentinel)).as("sentinel hashes for case %s", i).isEqualTo(before);
		}
	}

	@Test
	@DisplayName("GH-386 UNIT-004 Python uses fixture HEAD and preserves user config and authentication environment")
	void foreignPythonEnvironment() throws Exception {
		Path fixtures = root.toRealPath();
		inheritedEnvironment = Map.of("HOME", fixtures.toString(), "GIT_CONFIG_NOSYSTEM", "1",
				"GIT_CONFIG_GLOBAL", fixtures.resolve("global.config").toString(),
				"GIT_CONFIG_SYSTEM", fixtures.resolve("system.config").toString());
		Path sentinel = sentinel(fixtures);
		Map<String, String> before = gitState(sentinel);
		Path global = Files.writeString(fixtures.resolve("global.config"), "[fixture]\n\tmarker = user-config\n");
		Path system = Files.writeString(fixtures.resolve("system.config"), "");
		Map<String, String> inherited = new TreeMap<>(foreignEnvironment(sentinel));
		inherited.putAll(Map.of("GIT_CONFIG_GLOBAL", global.toString(), "GIT_CONFIG_SYSTEM", system.toString(),
				"GIT_CONFIG_NOSYSTEM", "1", "GIT_SSH_COMMAND", "fixture-ssh", "GIT_ASKPASS", "fixture-askpass",
				"GIT_TERMINAL_PROMPT", "0", "FIXTURE_MARKER", "non-git"));
		inherited.put("HOME", fixtures.toString());
		root = Files.createDirectory(fixtures.resolve("target"));
		init();
		inheritedEnvironment = inherited;
		source("Fixture.java", "class Fixture {}");
		generate();
		assertThat(run("check").code()).isZero();
		JsonNode query = JSON.readTree(run("query", "--symbol", "Fixture").text());
		assertThat(query.get("total").asInt()).isEqualTo(2);
		assertThat(JSON.readTree(Files.readString(index())).get("head").asText())
				.isEqualTo(command("git", "rev-parse", "HEAD").text().strip());
		assertPythonEnvironment(sentinel, inherited);
		String valid = Files.readString(index());
		source("Fixture.java", "class Fixture { PRIVATE_LITERAL broken = ; }");
		Result failed = run("generate");
		assertThat(failed.code()).isNotZero();
		assertThat(failed.text()).doesNotContain("PRIVATE_LITERAL");
		assertThat(Files.readString(index())).isEqualTo(valid);
		assertThat(gitState(sentinel)).isEqualTo(before);
	}

	private void assertPythonEnvironment(Path sentinel, Map<String, String> inherited) throws Exception {
		String probe = """
				import json, os, sys
				keys = ['GIT_CONFIG_GLOBAL', 'GIT_CONFIG_SYSTEM', 'GIT_CONFIG_NOSYSTEM',
				        'GIT_SSH_COMMAND', 'GIT_ASKPASS', 'GIT_TERMINAL_PROMPT', 'FIXTURE_MARKER', 'JAVA_HOME', 'HOME']
				keys.extend(sys.argv[1:])
				print(json.dumps({key: os.environ.get(key) for key in keys}))
				""";
		List<String> probeCommand = PlatformCommands.python("-c", probe);
		probeCommand.addAll(foreignEnvironment(sentinel).keySet());
		Result observed = command(probeCommand.toArray(String[]::new));
		assertThat(observed.code()).isZero();
		JsonNode environment = JSON.readTree(observed.text());
		for (String key : List.of("GIT_CONFIG_GLOBAL", "GIT_CONFIG_SYSTEM", "GIT_CONFIG_NOSYSTEM",
				"GIT_SSH_COMMAND", "GIT_ASKPASS", "GIT_TERMINAL_PROMPT", "FIXTURE_MARKER", "HOME")) {
			assertThat(environment.get(key).asText()).as(key).isEqualTo(inherited.get(key));
		}
		assertThat(environment.get("JAVA_HOME").asText()).isEqualTo(javaHome);
		for (String key : foreignEnvironment(sentinel).keySet()) {
			assertThat(environment.get(key).isNull()).as(key).isTrue();
		}
		assertThat(command("git", "config", "fixture.marker").text().strip()).isEqualTo("user-config");
	}

	private Path sentinel(Path fixtures) throws Exception {
		root = Files.createDirectory(fixtures.resolve("sentinel"));
		init();
		Files.writeString(root.resolve("sentinel.txt"), "sentinel");
		assertThat(command("git", "add", "sentinel.txt").code()).isZero();
		assertThat(command("git", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
				"-c", "maintenance.auto=false", "-c", "gc.auto=0",
				"commit", "-qm", "sentinel").code()).isZero();
		return root;
	}

	private Map<String, String> foreignEnvironment(Path sentinel) {
		Path git = sentinel.resolve(".git");
		return Map.ofEntries(
				Map.entry("GIT_DIR", git.toString()), Map.entry("GIT_WORK_TREE", sentinel.toString()),
				Map.entry("GIT_COMMON_DIR", git.toString()),
				Map.entry("GIT_INDEX_FILE", git.resolve("index").toString()),
				Map.entry("GIT_OBJECT_DIRECTORY", git.resolve("objects").toString()),
				Map.entry("GIT_ALTERNATE_OBJECT_DIRECTORIES", git.resolve("objects").toString()),
				Map.entry("GIT_IMPLICIT_WORK_TREE", "0"), Map.entry("GIT_GRAFT_FILE", git.resolve("grafts").toString()),
				Map.entry("GIT_NO_REPLACE_OBJECTS", "1"), Map.entry("GIT_REPLACE_REF_BASE", "refs/fixture/"),
				Map.entry("GIT_PREFIX", "foreign/"), Map.entry("GIT_SHALLOW_FILE", git.resolve("shallow").toString()),
				Map.entry("GIT_CONFIG", git.resolve("config").toString()),
				Map.entry("GIT_CONFIG_PARAMETERS", "'core.bare=true'"), Map.entry("GIT_CONFIG_COUNT", "1"),
				Map.entry("GIT_CONFIG_KEY_0", "core.bare"), Map.entry("GIT_CONFIG_VALUE_0", "true"),
				Map.entry("GIT_CEILING_DIRECTORIES", sentinel.getParent().toString()),
				Map.entry("GIT_DISCOVERY_ACROSS_FILESYSTEM", "0"));
	}

	private Map<String, String> gitState(Path repository) throws Exception {
		Map<String, String> hashes = new TreeMap<>();
		try (var files = Files.walk(repository.resolve(".git"))) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				hashes.put(repository.relativize(file).toString(),
						HexFormat.of()
								.formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
			}
		}
		return hashes;
	}

	private JsonNode classes(String symbol, int limit) throws Exception {
		Result result = run("query", "--format", "classes", "--symbol", symbol, "--limit", Integer.toString(limit));
		assertThat(result.code()).as(result.text()).isZero();
		return JSON.readTree(result.text());
	}

	private void init() throws Exception {
		root = root.toRealPath();
		assertThat(command("git", "init", "-q").code()).isZero();
		Files.writeString(root.resolve(".gitignore"), "build/\n");
		assertThat(command("git", "add", ".gitignore").code()).isZero();
		assertThat(command("git", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
				"-c", "maintenance.auto=false", "-c", "gc.auto=0",
				"commit", "-qm", "fixture").code()).isZero();
	}

	private Path source(String name, String content) throws Exception {
		Path file = root.resolve("src/main/java/" + name);
		Files.createDirectories(file.getParent());
		return Files.writeString(file, content);
	}

	private void generate() throws Exception {
		Result result = run("generate");
		assertThat(result.code()).as(result.text()).isZero();
	}

	private Path index() {
		return root.resolve("build/repo-map/index.json");
	}

	private Result run(String action, String... args) throws Exception {
		List<String> command = PlatformCommands.python(executableTool.toString(), action,
				"--root", root.toString(), "--output", index().toString());
		command.addAll(List.of(args));
		return command(command.toArray(String[]::new));
	}

	private Result command(String... args) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(args).directory(root.toFile()).redirectErrorStream(true);
		builder.environment().putAll(inheritedEnvironment);
		builder.environment().keySet().removeIf(key -> List.of(
				"GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE",
				"GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_IMPLICIT_WORK_TREE",
				"GIT_GRAFT_FILE", "GIT_NO_REPLACE_OBJECTS", "GIT_REPLACE_REF_BASE", "GIT_PREFIX",
				"GIT_SHALLOW_FILE", "GIT_CONFIG", "GIT_CONFIG_PARAMETERS", "GIT_CONFIG_COUNT",
				"GIT_CEILING_DIRECTORIES", "GIT_DISCOVERY_ACROSS_FILESYSTEM").contains(key)
				|| key.matches("GIT_CONFIG_(KEY|VALUE)_\\d+"));
		builder.environment().put("JAVA_HOME", javaHome);
		Path output = Files.createTempFile(root, ".command-", ".log");
		try {
			Process process = builder.redirectOutput(output.toFile()).start();
			boolean exited = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
			if (!exited) {
				process.descendants().forEach(ProcessHandle::destroyForcibly);
				process.destroyForcibly().waitFor();
			}
			assertThat(exited).as("fixture command completed within timeout").isTrue();
			return new Result(process.exitValue(), Files.readString(output));
		} finally {
			Files.delete(output);
		}
	}

	private record Result(int code, String text) {
	}
}
