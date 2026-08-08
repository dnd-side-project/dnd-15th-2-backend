package com.dnd.qello.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-06T14:30:00+09:00
 * Source scenario: TEST-PLAN-GH-67-INBOX-SENT-POST-UNIT-006 through UNIT-008,
 * TEST-PLAN-GH-79-ANSWER-VISIBILITY-RECIPIENTS (2026-08-08 개정 반영)
 */
class FeedPersistenceBoundaryTest {

	@Test
	@DisplayName("feed view와 repository port는 Spring, JPA 구현에 의존하지 않는다")
	void viewsAndPortsRemainIndependent() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/feed/view"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java")).map(this::read))
				.allMatch(source -> !source.contains("jakarta.persistence") && !source.contains("org.springframework"));
		}
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/feed/repository"))) {
			List<String> ports = paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/jdbc/"))
				.map(this::read).toList();
			assertThat(ports).allMatch(source -> !source.contains("jakarta.persistence")
				&& !source.contains("org.springframework.data"));
		}
	}

	@Test
	@DisplayName("feed는 다른 feature의 JPA Entity와 JDBC 구현을 직접 참조하지 않는다")
	void feedDoesNotReachIntoOtherFeatureImplementations() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/feed"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java")).map(this::read))
				.allMatch(source -> !source.contains(".repository.jdbc.")
					&& !source.contains(".repository.jpa.")
					&& !source.contains("JpaEntity"));
		}
	}

	@Test
	@DisplayName("수신함 projection은 답변 수와 공감 수를 노출한다 — 2026-08-07 개정(ADR 0002)으로 답변이 수신 자격자 전원에게 공개됐다")
	void inboxProjectionsExposeAnswerAndReactionCounts() throws IOException {
		String source = read(Path.of("src/main/java/com/dnd/qello/feed/view/InboxCard.java"));

		assertThat(source).contains("answerCount").contains("reactionCount").contains("unreadAnswerCount");
	}

	private String read(Path path) {
		try { return Files.readString(path); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
}
