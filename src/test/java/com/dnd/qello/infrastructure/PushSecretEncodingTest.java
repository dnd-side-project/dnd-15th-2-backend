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
 * Source scenario: TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-001
 * Source scenario: TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-002
 * Source scenario: TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-003
 * Source scenario: TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-004
 * Source scenario: TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-005
 * </pre>
 */
class PushSecretEncodingTest {

	@TempDir
	Path temporary;

	private static final String RUNTIME = """
			import ast, base64, io, json
			from contextlib import redirect_stdout, redirect_stderr
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
			values = {
			    "QELLO_NOTIFICATION_PUSH_TOKEN_CURRENT_KEY_ID": "fixture-key",
			    "QELLO_NOTIFICATION_PUSH_TOKEN_CURRENT_ENCRYPTION_KEY_BASE64":
			        base64.b64encode(bytes(range(32))).decode(),
			    "QELLO_NOTIFICATION_PUSH_TOKEN_FINGERPRINT_KEY_BASE64":
			        base64.b64encode(bytes(range(32, 64))).decode(),
			    "QELLO_NOTIFICATION_PUSH_FCM_PROJECT_ID": "fixture-project",
			    "QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON": json.dumps(credential, indent=2),
			}
			coordinate = {
			    "QELLO_COORDINATE_CURRENT_KEY_ID": "fixture-coordinate",
			    "QELLO_COORDINATE_CURRENT_KEY_BASE64": base64.b64encode(bytes(range(64, 96))).decode(),
			}
			coordinate_id = "QELLO_COORDINATE_CURRENT_KEY_ID"
			coordinate_key = "QELLO_COORDINATE_CURRENT_KEY_BASE64"

			def rejects_coordinate(candidate):
			    output = io.StringIO()
			    with redirect_stdout(output), redirect_stderr(output):
			        try:
			            runtime["validate_coordinate"](candidate)
			        except (ValueError, KeyError) as error:
			            assert all(value not in str(error) for value in candidate.values()
			                       if isinstance(value, str) and value.strip())
			        else:
			            raise AssertionError("invalid coordinate input accepted")
			    assert output.getvalue() == "", "coordinate validation exposed output"
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
				unrelated += ("OTHER_COORDINATE='first\\r\\n" + coordinate_id +
				              "=inside-coordinate\\r\\nlast'\\r\\n")
				original = unrelated + runtime["update_env"](base_env, values).replace("\\n", "\\r\\n")
				for key in coordinate:
				    original += key + "=obsolete\\r\\nexport " + key + "='duplicate'\\r\\n"
				replacement = {
				    coordinate_id: "fixture-coordinate-next$'id",
				    coordinate_key: base64.b64encode(bytes(range(96, 128))).decode(),
				}
				runtime["validate_coordinate"]({**values, **replacement})
				updated = runtime["update_env"](original, replacement)
				preserved = unrelated + runtime["update_env"](base_env, values).replace("\\n", "\\r\\n")
				assert updated == preserved + "".join(
				    key + "=" + runtime["dotenv"](value) + "\\n" for key, value in replacement.items())
				assert "obsolete" not in updated and "duplicate" not in updated
				assert updated.count(coordinate_id + "=") == 2
				assert updated.count(coordinate_key + "=") == 1
				assert runtime["update_env"](updated, replacement) == updated
				""");
	}

	@Test
	@DisplayName("D6 UNIT001: 좌표 key ID를 문자 수가 아닌 UTF-8 255-byte 경계로 검증한다")
	void coordinateIdUtf8Boundary() throws Exception {
		run("""
				for identifier in ("a", "a" * 255, "가" * 85, "가" * 84 + "abc"):
				    candidate = {**values, **coordinate, coordinate_id: identifier}
				    runtime["validate_coordinate"](candidate)
				    assert candidate[coordinate_id] == identifier
				    assert candidate[coordinate_key] == coordinate[coordinate_key]
				for identifier in ("a" * 256, "가" * 85 + "a", "가" * 86):
				    rejects_coordinate({**values, **coordinate, coordinate_id: identifier})
				""");
	}

	@Test
	@DisplayName("D6 UNIT002: 좌표 key는 canonical Base64 32 bytes만 허용하고 오류 원문을 노출하지 않는다")
	void coordinateCanonicalBase64() throws Exception {
		run("""
				runtime["validate_coordinate"]({**values, **coordinate})
				canonical = coordinate[coordinate_key]
				alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
				noncanonical = canonical[:-2] + alphabet[alphabet.index(canonical[-2]) + 1] + "="
				invalid = [base64.b64encode(bytes(range(size))).decode() for size in (0, 16, 31, 33, 64)]
				invalid += [
				    "!" + canonical[1:], canonical[:-1], canonical + "=", canonical[:-2] + "==",
				    canonical[:8] + "=" + canonical[9:], noncanonical,
				    " " + canonical, canonical + " ", canonical[:8] + " " + canonical[8:],
				    canonical + "\\n", canonical[:8] + "\\r\\n" + canonical[8:],
				    base64.urlsafe_b64encode(bytes([255]) * 32).decode(),
				]
				for encoded in invalid:
				    rejects_coordinate({**values, **coordinate, coordinate_key: encoded})
				""");
	}

	@Test
	@DisplayName("D6 UNIT003: 좌표 key와 push AES 및 fingerprint key의 목적별 분리를 검증한다")
	void coordinateKeySeparation() throws Exception {
		run("""
				runtime["validate_push"](values)
				runtime["validate_coordinate"]({**values, **coordinate})
				for push_key in (
				    "QELLO_NOTIFICATION_PUSH_TOKEN_CURRENT_ENCRYPTION_KEY_BASE64",
				    "QELLO_NOTIFICATION_PUSH_TOKEN_FINGERPRINT_KEY_BASE64",
				):
				    rejects_coordinate({**values, **coordinate, coordinate_key: values[push_key]})
				""");
	}

	@Test
	@DisplayName("D6 UNIT004: 두 좌표 필드의 누락·빈 값·공백·NUL·비문자열을 각각 거부한다")
	void coordinateRequiredFields() throws Exception {
		run("""
				runtime["validate_coordinate"]({**values, **coordinate})
				for key in (coordinate_id, coordinate_key):
				    candidate = {**values, **coordinate}
				    del candidate[key]
				    rejects_coordinate(candidate)
				    for invalid in ("", " \\t\\r\\n", chr(0), coordinate[key] + chr(0),
				                    None, 7, False, [], {}):
				        rejects_coordinate({**values, **coordinate, key: invalid})
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
