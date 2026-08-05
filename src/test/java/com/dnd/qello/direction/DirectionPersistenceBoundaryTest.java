package com.dnd.qello.direction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-03T20:30:00+09:00
 * Source scenario: TEST-PLAN-GH-39-DIRECTION-POSTGIS-PERSISTENCE-UNIT-007
 */
class DirectionPersistenceBoundaryTest {

	@Test
	@DisplayName("Direction domain과 repository port는 Spring/JPA 구현에 의존하지 않는다")
	void domainAndPortsRemainIndependent() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/direction/domain"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java"))
				.map(this::read).allMatch(source -> !source.contains("jakarta.persistence") && !source.contains("org.springframework"))).isTrue();
		}
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/direction/repository"))) {
			List<String> ports = paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/jdbc/"))
				.filter(path -> !path.toString().contains("/jpa/"))
				.map(this::read).toList();
			assertThat(ports).allMatch(source -> !source.contains("jakarta.persistence") && !source.contains("org.springframework.data"));
		}
	}

	@Test
	@DisplayName("Direction 구현은 다른 feature의 JPA Entity와 Repository를 직접 참조하지 않는다")
	void otherFeaturesDoNotReferenceDirectionImplementation() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().toString().contains("/direction/"))
				.map(this::read).allMatch(source -> !source.contains("direction.repository.jdbc") && !source.contains("direction.repository.jpa"))).isTrue();
		}
	}

	private String read(Path path) {
		try { return Files.readString(path); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
}
