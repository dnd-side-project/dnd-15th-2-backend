/*
 * Created at: 2026-10-06T12:10:00+09:00
 * Source scenario: TEST-PLAN-GH-312-COUNTRY-ONLY-REGISTRATION-UNIT-004 through UNIT-005
 */
package com.dnd.qello.harness;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCommandsTest {

	@Test
	@DisplayName("UNIT-004: 고른 Python 명령은 실제로 Python 3 코드를 실행한다")
	void resolvesRunnablePython3() throws Exception {
		List<String> command = PlatformCommands.python("-c", "import sys; print(sys.version_info[0])");

		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(process.waitFor()).isZero();
		assertThat(output.strip()).isEqualTo("3");
	}

	@Test
	@DisplayName("UNIT-005: Gradle wrapper 명령은 현재 OS에서 실행할 수 있는 wrapper 파일을 가리킨다")
	void resolvesGradleWrapperForCurrentOs() {
		List<String> command = PlatformCommands.gradlew("--version");

		Path wrapper = Path.of(command.getFirst());
		assertThat(wrapper).exists();
		assertThat(wrapper.getFileName().toString())
				.isEqualTo(PlatformCommands.isWindows() ? "gradlew.bat" : "gradlew");
		assertThat(command).endsWith("--version");
	}
}
