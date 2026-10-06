/*
 * Created at: 2026-10-06T12:10:00+09:00
 * Source scenario: TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-004 through UNIT-005
 */
package com.dnd.qello.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// 테스트가 하위 프로세스로 Python과 Gradle wrapper를 실행할 때 OS에 맞는 명령을 고른다.
// Python 선택 순서는 scripts/find-python.sh와 같다. Windows의 python3는 Microsoft Store 안내만 여는
// 실행 별칭이라 존재 여부가 아니라 실제 실행 결과로 후보를 고른다.
public final class PlatformCommands {

	private static final boolean WINDOWS = System.getProperty("os.name", "")
			.toLowerCase(Locale.ROOT)
			.contains("windows");
	private static final String PROBE = "import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)";

	private static List<String> python;

	private PlatformCommands() {
	}

	public static boolean isWindows() {
		return WINDOWS;
	}

	public static synchronized List<String> python() {
		if (python == null) {
			python = resolvePython();
		}
		return python;
	}

	public static List<String> python(String... arguments) {
		List<String> command = new ArrayList<>(python());
		command.addAll(List.of(arguments));
		return command;
	}

	// Windows는 셸 스크립트인 ./gradlew를 실행하지 못하므로 gradlew.bat을 절대 경로로 실행한다.
	public static List<String> gradlew(String... arguments) {
		List<String> command = new ArrayList<>();
		command.add(WINDOWS ? Path.of("gradlew.bat").toAbsolutePath().toString() : "./gradlew");
		command.addAll(List.of(arguments));
		return command;
	}

	// 개발자 모드가 꺼진 Windows는 일반 사용자의 심볼릭 링크 생성을 막는다.
	public static boolean canCreateSymbolicLinks(Path directory) {
		Path link = directory.resolve(".symlink-probe");
		try {
			Files.createSymbolicLink(link, directory);
			Files.delete(link);
			return true;
		} catch (IOException | UnsupportedOperationException exception) {
			return false;
		}
	}

	private static List<String> resolvePython() {
		String override = System.getenv("QELLO_PYTHON");
		if (override != null && !override.isBlank()) {
			if (runsPython3(List.of(override))) {
				return List.of(override);
			}
			throw new IllegalStateException("QELLO_PYTHON does not point to a working Python 3 interpreter");
		}
		for (List<String> candidate : List.of(List.of("python3"), List.of("python"), List.of("py", "-3"))) {
			if (runsPython3(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Python 3 is required. Install it or set QELLO_PYTHON.");
	}

	private static boolean runsPython3(List<String> candidate) {
		List<String> command = new ArrayList<>(candidate);
		command.add("-c");
		command.add(PROBE);
		try {
			Process process = new ProcessBuilder(command)
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			if (!process.waitFor(30, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (IOException exception) {
			return false;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
