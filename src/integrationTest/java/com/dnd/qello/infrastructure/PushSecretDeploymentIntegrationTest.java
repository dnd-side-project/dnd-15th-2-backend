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
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-001
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-002
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-003
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-004
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-005
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-006
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-007
 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-008
	 * Source scenario: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-009
 * </pre>
 */
class PushSecretDeploymentIntegrationTest {

	@TempDir
	Path temporary;

	// 별도 helper 파일 없이 실제 호스트 프로세스와 workflow 전달을 같은 격리 경계에서 실행한다.
	private static final String SANDBOX = """
			import ast, base64, copy, fcntl, gzip, json, os, shutil, stat, subprocess, sys, textwrap
			from pathlib import Path
			import yaml

			repository = Path.cwd()
			temporary = Path(sys.argv[1])
			template_path = repository / "infra/modules/app-server/user_data.sh.tftpl"
			template = template_path.read_text()
			runtime = template.split("# BEGIN QELLO RUNTIME\\n", 1)[1].split("# END QELLO RUNTIME\\n", 1)[0]
			namespace = {}
			exec(compile(ast.parse(runtime), "<runtime-definitions>", "exec"), namespace)
			keys = namespace["PUSH_KEYS"]
			suffixes = namespace["SUFFIXES"]
			override = os.environ.get("QELLO_TEST_COMPOSE")
			docker = shutil.which("docker")
			if not override and docker is None:
			    raise RuntimeError("Compose unavailable: set QELLO_TEST_COMPOSE or install PATH docker compose")
			compose_command = [override] if override else [
			    docker, "--config", str(Path.home() / ".docker"), "compose"]
			try:
			    subprocess.run([*compose_command, "version", "--short"], capture_output=True,
			                   text=True, timeout=10, check=True)
			except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
			    raise RuntimeError("Compose unavailable: QELLO_TEST_COMPOSE or PATH docker compose must run") from None
			workflow = yaml.safe_load((repository / ".github/workflows/deploy-test-server.yml").read_text())
			step = next(step for step in workflow["jobs"]["deploy"]["steps"]
			            if "SSM_PARAMETER_PATH_PREFIX" in step.get("env", {}))
			workflow_shell = step["run"]
			workflow_python = workflow_shell.split("<<'PY'\\n", 1)[1].rsplit("\\nPY", 1)[0]
			sentinel = "FIXTURE_LEAK_364_DO_NOT_PRINT"
			image_a = "fixture.invalid/app:" + "a" * 40
			image_b = "fixture.invalid/app:" + "b" * 40
			image_c = "fixture.invalid/app:" + "c" * 40
			image_d = "fixture.invalid/app:" + "d" * 40
			base_env = ("APP_IMAGE='" + image_a + "'\\nSPRING_PROFILES_ACTIVE=test\\n"
			            "QELLO_DB_NAME=qello\\nQELLO_DB_USERNAME=qello\\n"
			            "QELLO_DB_PASSWORD=fixture-db\\nQELLO_MEDIA_BUCKET=fixture-bucket\\n"
			            "QELLO_AUTH_ACCESS_TOKEN_SECRET=fixture-auth\\n"
			            "OTHER='first\\nQELLO_NOTIFICATION_PUSH_TOKEN_CURRENT_KEY_ID=inside-other\\nlast'\\n")

			# 이 프로세스의 권한/clock/rename만 대체하고 실제 배포·파일·Compose 코드는 유지한다.
			host_runner = '''
			import ast, json, os, sys, time
			from pathlib import Path
			root = Path(os.environ["QELLO_SANDBOX"])
			tree = ast.parse(sys.stdin.read())
			call = tree.body.pop()
			scope = {}
			exec(compile(tree, "<actual-host-definitions>", "exec"), scope)
			scope["ROOT"] = root
			scope["STATE"] = root / ".runtime"
			scope["CLEAN_ENV"].update({
			    "PATH": str(root / "bin") + os.pathsep + os.environ["PATH"],
			    "HOME": str(root), "QELLO_SANDBOX": str(root),
			    "QELLO_FIXTURE_COMPOSE_COMMAND": os.environ["QELLO_FIXTURE_COMPOSE_COMMAND"],
			    "AWS_CONFIG_FILE": "/dev/null", "AWS_SHARED_CREDENTIALS_FILE": "/dev/null",
			})
			os.geteuid = lambda: 0
			os.chown = lambda *args: None
			os.fchown = lambda *args: None
			time.sleep = lambda seconds: None
			real_replace = os.replace
			control = json.loads((root / "fixture.json").read_text())
			failures = list(control.get("rename_failures", []))
			def replace(source, destination):
			    source, destination = Path(source), Path(destination)
			    operation = (source.parent.name + "/" + destination.name
			                 if source.parent.name in ("next", "restore", "interrupted")
			                 else destination.name)
			    if operation in failures:
			        failures.remove(operation)
			        raise OSError("injected rename failure")
			    return real_replace(source, destination)
			os.replace = replace
			exec(compile(ast.Module(body=[call], type_ignores=[]), "<actual-host-entrypoint>", "exec"), scope)
			'''

			# Compose 해석은 실제 바이너리에 맡기고 네트워크와 daemon 효과만 격리한다.
			boundary = '''
			import json, os, subprocess, sys
			from pathlib import Path
			root = Path(os.environ["QELLO_SANDBOX"])
			control = json.loads((root / "fixture.json").read_text())
			args = sys.argv[1:]
			tool = Path(sys.argv[0]).name
			with (root / "effects.jsonl").open("a") as stream:
			    stream.write(json.dumps([tool, *args]) + chr(10))
			def finish(value, status=0):
			    print(json.dumps(value))
			    sys.exit(status)
			if tool == "aws":
			    if "get-parameter" in args:
			        if control.get("aws_failure"):
			            print(control["sentinel"], file=sys.stderr)
			            sys.exit(17)
			        if control.get("malformed"):
			            print(control["sentinel"])
			            sys.exit(0)
			        name = args[args.index("--name") + 1]
			        assert name.startswith("/fixture/")
			        assert args[args.index("--region") + 1] == "fixture-region"
			        assert "--with-decryption" in args
			        finish({"Parameter": {"Value": control["parameters"][name.rsplit("/", 1)[1]]}})
			    if "get-login-password" in args:
			        print(control["sentinel"])
			        sys.exit(0)
			    if "send-command" in args:
			        payload = args[args.index("--parameters") + 1]
			        (root / "payload.json").write_text(payload)
			        command = json.loads(payload)["commands"][0]
			        source = command.split("<<'PY_HOST'" + chr(10), 1)[1].rsplit("PY_HOST", 1)[0]
			        result = subprocess.run([sys.executable, str(root / "host.py")], input=source,
			                                capture_output=True, text=True, timeout=45)
			        (root / "remote-output").write_text(result.stdout + result.stderr)
			        (root / "invocation.json").write_text(json.dumps({
			            "Status": "Success" if result.returncode == 0 else "Failed",
			            "StandardOutputContent": result.stdout, "StandardErrorContent": result.stderr,
			        }))
			        finish({"Command": {"CommandId": "fixture-command"}})
			    if "wait" in args:
			        sys.exit(0)
			    if "get-command-invocation" in args:
			        finish(json.loads((root / "invocation.json").read_text()))
			    raise AssertionError("unexpected AWS operation")
			if tool == "docker":
			    if control.get("docker_failure") in args:
			        print(control["sentinel"], file=sys.stderr)
			        sys.exit(23)
			    if args[0] == "login":
			        assert sys.stdin.read().strip() == control["sentinel"]
			        sys.exit(0)
			    assert args[0] == "compose"
			    if "config" in args:
			        location = Path(args[args.index("--env-file") + 1]).parent
			        if (control.get("compose_failure") and location.name == "next"
			                or control.get("published_config_failure")
			                and location == root and (root / ".runtime/pending.json").exists()):
			            print(control["sentinel"], file=sys.stderr)
			            sys.exit(19)
			        result = subprocess.run([*json.loads(os.environ["QELLO_FIXTURE_COMPOSE_COMMAND"]), *args[1:]],
			                                capture_output=True, timeout=20)
			        sys.stdout.buffer.write(result.stdout)
			        sys.stderr.buffer.write(result.stderr)
			        sys.exit(result.returncode)
			    assert "pull" in args or "up" in args, args
			    sys.exit(0)
			if tool == "curl":
			    if control.get("health_failure"):
			        print(control["sentinel"], file=sys.stderr)
			        sys.exit(22)
			    print('{"status":"UP"}')
			    sys.exit(0)
			raise AssertionError("unexpected external tool")
			'''

			class Sandbox:
			    def __init__(self, name, existing=True):
			        self.root = temporary / name
			        self.root.mkdir(mode=0o700)
			        self.bin = self.root / "bin"
			        self.bin.mkdir()
			        self.control = {"sentinel": sentinel}
			        self.secrets("initial")
			        for tool in ("aws", "docker", "curl"):
			            executable = self.bin / tool
			            executable.write_text("#!" + sys.executable + "\\n" + textwrap.dedent(boundary))
			            executable.chmod(0o700)
			        (self.root / "host.py").write_text(textwrap.dedent(host_runner))
			        compose = template.split("<<'COMPOSEEOF'\\n", 1)[1].split("\\nCOMPOSEEOF", 1)[0]
			        (self.root / "compose.yaml").write_text(
			            compose.replace("$${", "${").replace("${app_port}", "8080"))
			        if existing:
			            (self.root / ".env").write_text(base_env)
			        self.env = {
			            "PATH": str(self.bin) + os.pathsep + os.environ["PATH"], "HOME": str(self.root),
			            "QELLO_SANDBOX": str(self.root),
			            "QELLO_FIXTURE_COMPOSE_COMMAND": json.dumps(compose_command),
			            "APP_IMAGE": "fixture.invalid/polluted:parent",
			            **{key: "polluted-parent" for key in keys},
			        }

			    def secrets(self, label):
			        credential = {
			            "type": "service_account", "project_id": "fixture-project",
			            "private_key_id": label, "private_key": sentinel + "\\nPEM$\\\\'\\\"\\nEND\\n",
			            "client_email": "fixture@example.invalid", "token_uri": "https://example.invalid/token",
			        }
			        self.values = dict(zip(keys, [
			            "fixture-" + label, base64.b64encode(bytes(range(32))).decode(),
			            base64.b64encode(bytes(range(32, 64))).decode(), "fixture-project",
			            json.dumps(credential, indent=2),
			        ]))
			        self.control["parameters"] = dict(zip(suffixes, self.values.values()))
			        self.control["parameters"].update({
			            "db-password": "fixture-db", "auth-token": "fixture-auth",
			        })
			        self.save()

			    def save(self):
			        (self.root / "fixture.json").write_text(json.dumps(self.control))

			    def settings(self, image=image_b, rollback=False):
			        return {"region": "fixture-region", "prefix": "/fixture", "image": image,
			                "rollback": rollback}

			    def deploy(self, image=image_b, rollback=False):
			        self.save()
			        source = runtime + "\\nsys.exit(deploy(" + repr(self.settings(image, rollback)) + "))\\n"
			        return self.host(source)

			    def host(self, source):
			        result = subprocess.run([sys.executable, str(self.root / "host.py")], input=source,
			                                capture_output=True, text=True, env=self.env, timeout=45)
			        self.safe_output(result)
			        return result

			    def workflow(self, image=image_b):
			        self.save()
			        env = {**self.env, "AWS_REGION": "fixture-region", "INSTANCE_ID": "fixture-instance",
			               "IMAGE_REF": image, "SSM_PARAMETER_PATH_PREFIX": "/fixture", "REQUESTED_IMAGE_SHA": ""}
			        result = subprocess.run([sys.executable, "-c", workflow_python], cwd=repository,
			                                capture_output=True, text=True, env=env, timeout=60)
			        self.safe_output(result)
			        self.safe_files()
			        return result

			    def pair(self):
			        return {name: (self.root / name).read_text() if (self.root / name).exists() else None
			                for name in (".env", "compose.push.yaml")}

			    def backup(self):
			        return json.loads((self.root / ".runtime/previous.json").read_text())

			    def effective(self):
			        env = {"PATH": os.environ["PATH"], "HOME": str(self.root)}
			        args = [*compose_command, "--project-directory", str(self.root),
			                "--env-file", str(self.root / ".env"), "-f", str(self.root / "compose.yaml")]
			        if (self.root / "compose.push.yaml").exists():
			            args += ["-f", str(self.root / "compose.push.yaml")]
			        result = subprocess.run(args + ["config", "--format", "json"], capture_output=True,
			                                text=True, env=env, timeout=20, check=True)
			        return json.loads(result.stdout.replace("$$", "$"))

			    def events(self):
			        path = self.root / "effects.jsonl"
			        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

			    def safe_output(self, result):
			        assert sentinel not in result.stdout + result.stderr

			    def safe_files(self):
			        allowed = {".env", "fixture.json", ".runtime/previous.json", ".runtime/pending.json"}
			        for file in self.root.rglob("*"):
			            if file.is_file() and file.relative_to(self.root).as_posix() not in allowed:
			                assert sentinel.encode() not in file.read_bytes(), str(file)

			    def assert_push(self):
			        actual = self.effective()["services"]["app"]["environment"]
			        for key in keys[:4]:
			            assert actual[key] == self.values[key], key
			        assert json.loads(actual[keys[4]]) == json.loads(self.values[keys[4]])

			def success(result):
			    assert result.returncode == 0, result.stdout + result.stderr

			def failure(result, stage):
			    assert result.returncode == 1, result.stdout + result.stderr
			    assert "[qello] runtime " + stage + " FAILED" in result.stderr, result.stderr

			def boot_source(sandbox):
			    variables = {
			        "aws_region": "fixture-region", "ecr_registry": "fixture.invalid",
			        "ecr_repository_name": "app", "app_image_tag": "b" * 40, "app_port": 8080,
			        "media_bucket_name": "fixture-bucket", "spring_profiles_active": "test",
			        "compose_version": "v2.29.7", "db_password_parameter_name": "/fixture/db-password",
			        "auth_token_secret_parameter_name": "/fixture/auth-token",
			    }
			    for name, suffix in zip((
			            "push_token_current_key_id_parameter_name",
			            "push_token_current_encryption_key_parameter_name",
			            "push_token_fingerprint_key_parameter_name",
			            "push_fcm_project_id_parameter_name", "push_fcm_credential_json_parameter_name"), suffixes):
			        variables[name] = "/fixture/" + suffix
			    expression = ("base64gzip(templatefile(" + json.dumps(str(template_path)) +
			                  ", jsondecode(" + json.dumps(json.dumps(variables)) + ")))")
			    result = subprocess.run(["terraform", "console", "-no-color"], input=expression,
			                            cwd=sandbox.root, capture_output=True, text=True, timeout=20, check=True)
			    compressed = base64.b64decode(json.loads(result.stdout))
			    assert len(compressed) <= 16384, len(compressed)
			    rendered = gzip.decompress(compressed).decode()
			    compose = rendered.split("<<'COMPOSEEOF'\\n", 1)[1].split("\\nCOMPOSEEOF", 1)[0]
			    (sandbox.root / "compose.yaml").write_text(compose)
			    python = rendered.split("<<'PY_RUNTIME'\\n", 1)[1].split("\\nPY_RUNTIME", 1)[0]
			    definitions = python.split("# BEGIN QELLO RUNTIME\\n", 1)[1].split("# END QELLO RUNTIME\\n", 1)[0]
			    assert ast.dump(ast.parse(definitions)) == ast.dump(ast.parse(runtime))
			    return python, compressed
			""";

	@Test
	@DisplayName("INT001: 기존 설정과 DB를 보존하고 다섯 secret과 파일 권한을 게시한다")
	void existingHostDeployment() throws Exception {
		run("""
				host = Sandbox("existing")
				before = host.effective()
				original = host.pair()
				success(host.deploy())
				host.assert_push()
				actual = host.effective()
				assert actual["services"]["db"] == before["services"]["db"]
				for key, value in before["services"]["app"]["environment"].items():
				    assert actual["services"]["app"]["environment"][key] == value
				assert host.backup() == original
				assert actual["services"]["app"]["image"] == image_b
				for path in (host.root, host.root / ".runtime"):
				    assert stat.S_IMODE(path.stat().st_mode) == 0o700
				for name in (".env", "compose.push.yaml", ".runtime/previous.json", ".runtime/lock"):
				    assert stat.S_IMODE((host.root / name).stat().st_mode) == 0o600
				effects = [event for event in host.events() if event[0] == "docker"
				           and ("pull" in event or "up" in event)]
				assert [event[event.index("pull"):] for event in effects if "pull" in event] == [["pull", "app"]]
				assert [event[event.index("up"):] for event in effects if "up" in event] == [
				    ["up", "-d", "--no-deps", "--force-recreate", "app"]]
				assert not any("down" in event or "volume" in event for event in host.events())
				assert not list((host.root / ".runtime").glob("stage-*"))
				assert not (host.root / ".runtime/pending.json").exists()
				host.safe_files()
				""");
	}

	@Test
	@DisplayName("INT002: 조회·파싱·Compose·각 rename 실패와 동시 잠금이 기존 일치 세트를 보존한다")
	void prepublicationAndRenameFailures() throws Exception {
		run("""
				cases = [
				    ({"aws_failure": True}, "secrets"), ({"malformed": True}, "secrets"),
				    ({"compose_failure": True}, "validate"),
				    ({"docker_failure": "login"}, "pull"),
				    ({"docker_failure": "pull"}, "pull"),
				    ({"docker_failure": "up"}, "start"),
				    ({"rename_failures": ["pending.json"]}, "publish"),
				    ({"rename_failures": ["next/.env"]}, "publish"),
				    ({"rename_failures": ["next/compose.push.yaml"]}, "publish"),
				    ({"published_config_failure": True}, "publish"),
				    ({"rename_failures": ["previous.json"]}, "backup"),
				]
				for index, (controls, stage) in enumerate(cases):
				    host = Sandbox("failure-" + str(index))
				    success(host.deploy())
				    before, backup = host.pair(), host.backup()
				    host.secrets("changed")
				    host.control.update(controls)
				    failure(host.deploy(image_c), stage)
				    assert host.pair() == before, controls
				    assert host.backup() == backup, controls
				    host.safe_files()
				host = Sandbox("locked")
				success(host.deploy())
				before = host.pair()
				with (host.root / ".runtime/lock").open("a") as lock:
				    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
				    failure(host.deploy(image_c), "prepare")
				    assert host.pair() == before
				""");
	}

	@Test
	@DisplayName("INT003: health 실패 뒤 이전 이미지와 파일을 SSM 재조회 없이 수동 복구한다")
	void healthFailureAndManualRollback() throws Exception {
		run("""
				host = Sandbox("health")
				success(host.deploy(image_a))
				host.secrets("B")
				success(host.deploy(image_b))
				before, backup = host.pair(), host.backup()
				host.control["health_failure"] = True
				failure(host.deploy(image_c), "health")
				assert host.pair() == before and host.backup() == backup
				assert (host.root / ".runtime/pending.json").exists()
				reads = len([event for event in host.events() if "get-parameter" in event])
				host.control["health_failure"] = False
				host.control["aws_failure"] = True
				success(host.deploy(image_b, rollback=True))
				assert host.pair() == before
				assert host.backup() == backup
				assert len([event for event in host.events() if "get-parameter" in event]) == reads
				assert host.effective()["services"]["app"]["image"] == image_b
				assert not (host.root / ".runtime/pending.json").exists()
				""");
	}

	@Test
	@DisplayName("INT004: 성공 A·B와 실패 C·성공 D 및 재실행이 직전 성공 백업 한 세트만 유지한다")
	void previousSuccessfulPairLifecycle() throws Exception {
		run("""
				host = Sandbox("lifecycle")
				success(host.deploy(image_a))
				pair_a = host.pair()
				host.secrets("B")
				success(host.deploy(image_b))
				pair_b = host.pair()
				assert host.backup() == pair_a
				host.secrets("C")
				host.control["health_failure"] = True
				failure(host.deploy(image_c), "health")
				assert host.pair() == pair_b and host.backup() == pair_a
				host.control["health_failure"] = False
				success(host.deploy(image_b, rollback=True))
				assert host.backup() == pair_a
				host.secrets("D")
				success(host.deploy(image_d))
				assert host.backup() == pair_b
				pair_d = host.pair()
				success(host.deploy(image_d))
				assert host.pair() == pair_d and host.backup() == pair_b
				assert sorted(path.name for path in (host.root / ".runtime").iterdir()) == ["lock", "previous.json"]
				assert (host.root / ".env").read_text().count(keys[0] + "=") == 2
				""");
	}

	@Test
	@DisplayName("INT005: 실제 부팅과 workflow SSM 전달이 같은 다섯 환경 값을 생성하고 누출하지 않는다")
	void bootAndWorkflowConverge() throws Exception {
		run("""
				boot = Sandbox("boot", existing=False)
				existing = Sandbox("workflow")
				source, compressed = boot_source(boot)
				success(boot.host(source))
				success(existing.workflow())
				boot.assert_push()
				existing.assert_push()
				boot_environment = boot.effective()["services"]["app"]["environment"]
				host_environment = existing.effective()["services"]["app"]["environment"]
				assert {key: boot_environment[key] for key in keys} == {
				    key: host_environment[key] for key in keys}
				assert boot.effective()["services"]["app"]["image"] == image_b
				boot.safe_files()
				existing.safe_files()
				""");
	}

	@Test
	@DisplayName("INT006: AWS·JSON·Compose 실패가 출력과 진단 파일 및 SSM payload에 값을 남기지 않는다")
	void diagnosticAndPayloadRedaction() throws Exception {
		run("""
				for index, controls in enumerate((
				        {"aws_failure": True}, {"malformed": True}, {"compose_failure": True})):
				    host = Sandbox("redaction-" + str(index))
				    before = host.pair()
				    host.control.update(controls)
				    result = host.workflow()
				    assert result.returncode == 1, result.stdout + result.stderr
				    assert host.pair() == before
				    remote = (host.root / "remote-output").read_text()
				    assert "FAILED" in remote and sentinel not in remote
				    host.safe_files()
				    payload = json.loads((host.root / "payload.json").read_text())
				    assert len(payload["commands"]) == 1
				    assert sentinel not in json.dumps(payload)
				""");
	}

	@Test
	@DisplayName("INT007: 동일 SHA 롤백은 이전 성공 또는 pending을 우선하고 복구 실패 시 원본을 보존한다")
	void sameImageRollbackAndInterruptedRecovery() throws Exception {
		run(legacyRollbackScenario() + """
				host = Sandbox("same-sha")
				success(host.deploy(image_a))
				pair_a = host.pair()
				host.secrets("rotated")
				success(host.deploy(image_a))
				pair_b = host.pair()
				assert pair_a != pair_b and host.backup() == pair_a
				reads = len([event for event in host.events() if "get-parameter" in event])
				host.control["aws_failure"] = True
				success(host.deploy(image_a, rollback=True))
				assert host.pair() == pair_a and host.backup() == pair_b
				assert len([event for event in host.events() if "get-parameter" in event]) == reads
				interrupted = Sandbox("interrupted")
				success(interrupted.deploy(image_a))
				pair_a = interrupted.pair()
				interrupted.secrets("rotated")
				success(interrupted.deploy(image_a))
				pair_b = interrupted.pair()
				interrupted.secrets("broken")
				interrupted.control["rename_failures"] = ["next/compose.push.yaml", "restore/.env"]
				result = interrupted.deploy(image_c)
				failure(result, "publish")
				assert "runtime recovery REQUIRED" in result.stderr
				pending = interrupted.root / ".runtime/pending.json"
				assert json.loads(pending.read_text()) == pair_b
				assert interrupted.backup() == pair_a
				interrupted.control["aws_failure"] = True
				interrupted.control["rename_failures"] = ["interrupted/.env"]
				failure(interrupted.deploy(image_a, rollback=True), "recovery")
				assert json.loads(pending.read_text()) == pair_b
				assert interrupted.backup() == pair_a
				interrupted.control["rename_failures"] = []
				reads = len([event for event in interrupted.events() if "get-parameter" in event])
				success(interrupted.deploy(image_a, rollback=True))
				assert interrupted.pair() == pair_b
				assert interrupted.backup() == pair_a
				assert len([event for event in interrupted.events() if "get-parameter" in event]) == reads
				assert not pending.exists()
				""");
	}

	private String legacyRollbackScenario() {
		return """
				legacy = Sandbox("legacy-rollback")
				original = legacy.pair()
				success(legacy.deploy(image_b))
				reads = len([event for event in legacy.events() if "get-parameter" in event])
				legacy.control["aws_failure"] = True
				success(legacy.deploy(image_a, rollback=True))
				assert legacy.pair() == original
				assert not (legacy.root / "compose.push.yaml").exists()
				assert len([event for event in legacy.events() if "get-parameter" in event]) == reads
				""";
	}

	@Test
	@DisplayName("INT009: 순수 입력 검증과 실제 Compose round-trip이 잘못된 입력과 부모 환경 오염을 구별한다")
	void validationAndComposeRoundTrip() throws Exception {
		run("""
				host = Sandbox("round-trip")
				before = host.pair()
				valid = copy.deepcopy(host.values)
				credential = json.loads(valid[keys[4]])
				rejected = []
				for key in keys:
				    missing = copy.deepcopy(valid)
				    del missing[key]
				    rejected.append(missing)
				    for empty in ("", "  ", "\\0"):
				        rejected.append({**valid, key: empty})
				for key in keys[1:3]:
				    for size in (0, 16, 31, 33, 64):
				        rejected.append({**valid, key: base64.b64encode(bytes(range(size))).decode()})
				for malformed in ("%%%", "A" * 42 + "B=", valid[keys[1]][:-2] + "B="):
				    rejected.append({**valid, keys[1]: malformed})
				rejected.append({**valid, keys[2]: valid[keys[1]]})
				for bad in ("not-json", "[]", json.dumps({**credential, "type": "user"}),
				            json.dumps({**credential, "project_id": "different"}),
				            json.dumps({**credential, "private_key": ""})):
				    rejected.append({**valid, keys[4]: bad})
				for candidate in rejected:
				    try:
				        namespace["validate_push"](candidate)
				    except (ValueError, KeyError):
				        pass
				    else:
				        raise AssertionError("invalid push input accepted")
				host.control["parameters"][suffixes[1]] = "invalid-base64"
				failure(host.deploy(), "secrets")
				assert host.pair() == before
				host.secrets("round-trip")
				success(host.deploy())
				host.assert_push()
				actual = host.effective()["services"]["app"]
				assert actual["image"] == image_b
				assert json.loads(actual["environment"][keys[4]]) == credential | {"private_key_id": "round-trip"}
				""");
	}

	@Test
	@DisplayName("INT008: 실제 Terraform gzip 부팅 payload와 workflow 호스트 payload가 동일 런타임을 전달한다")
	void terraformGzipAndBothDeliveryPaths() throws Exception {
		run("""
				boot = Sandbox("rendered-boot", existing=False)
				existing = Sandbox("delivered-host")
				source, compressed = boot_source(boot)
				assert len(compressed) <= 16384
				assert sentinel.encode() not in gzip.decompress(compressed)
				success(boot.host(source))
				success(existing.workflow())
				command = json.loads((existing.root / "payload.json").read_text())["commands"][0]
				delivered = command.split("<<'PY_HOST'\\n", 1)[1].rsplit("PY_HOST", 1)[0]
				tree = ast.parse(delivered)
				tree.body.pop()
				assert ast.dump(tree) == ast.dump(ast.parse(runtime))
				boot.assert_push()
				existing.assert_push()
				boot_effects = [event for event in boot.events() if event[0] == "docker" and "up" in event]
				assert [event[event.index("up"):] for event in boot_effects] == [
				    ["up", "-d", "--wait", "db"], ["up", "-d", "--no-deps", "--force-recreate", "app"]]
				""");
	}

	private void run(String scenario) throws Exception {
		Path output = temporary.resolve("python-output");
		ProcessBuilder builder = new ProcessBuilder("python3", "-c", SANDBOX + scenario, temporary.toString());
		String path = builder.environment().get("PATH");
		String compose = builder.environment().get("QELLO_TEST_COMPOSE");
		builder.environment().clear();
		builder.environment().put("PATH", path);
		builder.environment().put("HOME", System.getProperty("user.home"));
		if (compose != null) {
			builder.environment().put("QELLO_TEST_COMPOSE", compose);
		}
		Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
		try {
			assertEquals(0, process.onExit().get(180, TimeUnit.SECONDS).exitValue(), () -> {
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
