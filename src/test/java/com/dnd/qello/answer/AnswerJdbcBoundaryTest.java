package com.dnd.qello.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-07T04:40:00+09:00
 * Source scenario: TEST-PLAN-GH-70-MEDIA-ASSET-SERVICE-UNIT-008
 */
class AnswerJdbcBoundaryTest {

	@Test
	@DisplayName("answer domain과 repository port는 Spring/JPA 구현에 의존하지 않는다")
	void domainAndPortsRemainIndependent() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/answer/domain"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java")).map(this::read))
				.allMatch(source -> !source.contains("jakarta.persistence") && !source.contains("org.springframework"));
		}
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/answer/repository"))) {
			List<String> ports = paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/jdbc/"))
				.filter(path -> !path.toString().contains("/jpa/"))
				.map(this::read).toList();
			assertThat(ports).allMatch(source -> !source.contains("jakarta.persistence")
				&& !source.contains("org.springframework.data"));
		}
	}

	@Test
	@DisplayName("answer(media 포함)는 다른 feature의 JPA Entity와 JDBC 구현을 직접 참조하지 않는다")
	void answerDoesNotReachIntoOtherFeatureImplementations() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/answer"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java")).map(this::read))
				.allMatch(source -> !source.contains("direction.repository.jdbc")
					&& !source.contains("direction.repository.jpa")
					&& !source.contains("feed.repository.jdbc")
					&& !source.contains("feed.repository.jpa"));
		}
	}

	@Test
	@DisplayName("다른 feature는 answer(media 포함)의 JPA Entity와 JDBC 구현을 직접 참조하지 않는다")
	void otherFeaturesDoNotReferenceAnswerImplementation() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/answer/"))
				.map(this::read))
				.allMatch(source -> !source.contains("answer.repository.jdbc") && !source.contains("answer.repository.jpa"));
		}
	}

	private String read(Path path) {
		try { return Files.readString(path); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
}
