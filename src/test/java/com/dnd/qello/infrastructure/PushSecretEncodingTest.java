package com.dnd.qello.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <pre>
 * Created at: 2026-11-20T11:03:20Z
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-001
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-002
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-003
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-004
 * </pre>
 */
class PushSecretEncodingTest {

	@TempDir
	Path temporary;

	private static final String RUNTIME = """
			import ast, base64, json
			from pathlib import Path

			template = Path("infra/modules/app-server/user_data.sh.tftpl").read_text()
			source = template.split("# BEGIN QELLO RUNTIME\\n", 1)[1].split("# END QELLO RUNTIME\\n", 1)[0]
			runtime = {}
			exec(compile(ast.parse(source), "<actual-qello-runtime>", "exec"), runtime)
			keys = runtime["PUSH_KEYS"]
			credential = {
			    "type": "service_account", "project_id": "fixture-project",
			    "private_key_id": "fixture-key", "private_key": "FAKE PEM\\nline$\\\\'\\\"\\nEND\\n",
			    "client_email": "fixture@example.invalid", "token_uri": "https://example.invalid/token",
			}
			values = dict(zip(keys, [
			    "fixture-key", base64.b64encode(bytes(range(32))).decode(),
			    base64.b64encode(bytes(range(32, 64))).decode(),
			    "fixture-project", json.dumps(credential, indent=2),
			]))
			base_env = ("APP_IMAGE='fixture.invalid/app:old'\\nSPRING_PROFILES_ACTIVE=test\\n"
			            "QELLO_DB_NAME=qello\\nQELLO_DB_USERNAME=fixture\\n"
			            "QELLO_DB_PASSWORD=fixture-db\\nQELLO_MEDIA_BUCKET=fixture-bucket\\n"
			            "QELLO_AUTH_ACCESS_TOKEN_SECRET=fixture-auth\\n")
			""";

	@Test
	@DisplayName("UNIT001: credential JSON을 필드 손상 없이 물리 한 줄로 정규화한다")
	void credentialRoundTrip() throws Exception {
		run("""
				runtime["validate_push"](values)
				assert json.loads(values[keys[4]]) == credential
				assert "\\n" not in values[keys[4]]
				assignments = runtime["update_env"](base_env, values).splitlines()
				assert len([line for line in assignments if line.startswith(keys[4] + "=")]) == 1
				assert len(assignments) == len(base_env.splitlines()) + 5
				""");
	}

	@Test
	@DisplayName("UNIT002: 일반 dotenv 값의 dollar와 따옴표를 literal로 인코딩한다")
	void literalDotenvEncoding() throws Exception {
		run("""
				assert runtime["dotenv"]("") == "''"
				assert runtime["dotenv"]("${UNSET} $$") == "'${UNSET} $$'"
				assert runtime["dotenv"]("a'b") == "'a\\\\'b'"
				""");
	}

	@Test
	@DisplayName("UNIT003: 닫는 따옴표 앞 backslash가 있는 값을 JSON escape로 보존한다")
	void escapedDotenvEncoding() throws Exception {
		run("""
				for value in ("tail" + chr(92), "a" + chr(92) + "'$"):
				    encoded = runtime["dotenv"](value)
				    assert encoded.startswith('"') and encoded.endswith('"')
				    assert json.loads(encoded.replace("$$", "$")) == value
				""");
	}

	@Test
	@DisplayName("UNIT004: CRLF와 무관한 여러 줄 설정을 보존하고 중복 할당을 멱등 갱신한다")
	void multilinePreservation() throws Exception {
		run("""
				runtime["validate_push"](values)
				unrelated = ("# fixture\\r\\nOTHER='first\\r\\n" + keys[0] +
				             "=inside-unrelated\\r\\nlast'\\r\\n")
				original = unrelated + keys[0] + "=obsolete\\r\\nexport " + keys[0] + "='duplicate'\\r\\n"
				updated = runtime["update_env"](original, values)
				assert updated.startswith(unrelated)
				assert "obsolete" not in updated and "duplicate" not in updated
				assert updated.count(keys[0] + "=") == 2
				assert runtime["update_env"](updated, values) == updated
				""");
	}

	private void run(String scenario) throws Exception {
		Path output = temporary.resolve("python-output");
		ProcessBuilder builder = new ProcessBuilder("python3", "-c", RUNTIME + scenario);
		String path = builder.environment().get("PATH");
		builder.environment().clear();
		builder.environment().put("PATH", path);
		builder.environment().put("HOME", temporary.toString());
		Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
		try {
			assertEquals(0, process.onExit().get(90, TimeUnit.SECONDS).exitValue(), () -> {
				try {
					return Files.readString(output);
				} catch (java.io.IOException exception) {
					throw new java.io.UncheckedIOException(exception);
				}
			});
		} finally {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
		}
	}
}
