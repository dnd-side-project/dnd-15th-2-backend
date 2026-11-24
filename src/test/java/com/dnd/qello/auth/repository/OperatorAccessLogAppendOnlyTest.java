/*
 * Created at: 2026-11-22T22:02:56+09:00
 * Source scenario: TEST-PLAN-GH-375-OPERATOR-ACCESS-LOG-UNIT-017
 */
package com.dnd.qello.auth.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// operator_access_log는 append-only 원장이다(#375). DB 수준 차단은 I4 범위라, 애플리케이션에
// 수정·삭제 경로가 생기지 않는지를 저장소 계약과 소스 검사로 고정한다.
class OperatorAccessLogAppendOnlyTest {

	private static final Path PRODUCTION_SOURCES = Path.of("src/main");
	private static final Pattern MUTATING_STATEMENT = Pattern.compile(
			"(update\\s+operator_access_log\\b|delete\\s+from\\s+operator_access_log\\b"
					+ "|truncate\\s+(table\\s+)?operator_access_log\\b)");

	@Test
	@DisplayName("접근 기록 저장소는 append만 제공하고 수정·삭제 메서드를 두지 않는다")
	void repositoryExposesAppendOnly() {
		List<String> methodNames = Arrays.stream(OperatorAccessLogRepository.class.getDeclaredMethods())
				.map(Method::getName)
				.toList();

		assertThat(methodNames).containsExactly("append");
		assertThat(methodNames).noneMatch(name -> {
			String lower = name.toLowerCase(Locale.ROOT);
			return lower.contains("update") || lower.contains("delete")
					|| lower.contains("remove") || lower.contains("modify");
		});
	}

	@Test
	@DisplayName("production 소스와 migration에 operator_access_log를 수정·삭제하는 SQL이 없다")
	void productionSourcesHaveNoMutatingStatement() throws IOException {
		try (Stream<Path> files = Files.walk(PRODUCTION_SOURCES)) {
			List<Path> offenders = files
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
					.filter(this::containsMutatingStatement)
					.toList();

			assertThat(offenders).isEmpty();
		}
	}

	private boolean containsMutatingStatement(Path path) {
		try {
			String normalized = Files.readString(path).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
			return MUTATING_STATEMENT.matcher(normalized).find();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
