package com.dnd.qello.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.question.repository.jpa.ApprovedQuestionJpaEntity;
import com.dnd.qello.question.repository.jpa.QuestionAssignmentCycleJpaEntity;
import com.dnd.qello.question.repository.jpa.QuestionAssignmentJpaEntity;
import com.dnd.qello.question.repository.jpa.QuestionProposalJpaEntity;
import com.dnd.qello.question.repository.jpa.QuestionProposalReviewJpaEntity;

/**
 * Created at: 2026-08-03T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-38-QUESTION-PERSISTENCE-UNIT-005
 */
class QuestionPersistenceBoundaryTest {

	private static final List<Class<?>> ENTITIES = List.of(
		QuestionProposalJpaEntity.class,
		QuestionProposalReviewJpaEntity.class,
		ApprovedQuestionJpaEntity.class,
		QuestionAssignmentCycleJpaEntity.class,
		QuestionAssignmentJpaEntity.class
	);

	@Test
	@DisplayName("Question domain과 repository port는 JPA 및 Spring Data에 의존하지 않는다")
	void domainAndPortsRemainPersistenceIndependent() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello/question/domain"))) {
			assertThat(paths.filter(path -> path.toString().endsWith(".java"))
				.map(this::readSource)
				.allMatch(source -> !source.contains("jakarta.persistence")
					&& !source.contains("org.springframework"))).isTrue();
		}
		for (String port : List.of(
			"src/main/java/com/dnd/qello/question/repository/QuestionProposalRepository.java",
			"src/main/java/com/dnd/qello/question/repository/QuestionProposalReviewRepository.java",
			"src/main/java/com/dnd/qello/question/repository/ApprovedQuestionRepository.java",
			"src/main/java/com/dnd/qello/question/repository/QuestionAssignmentCycleRepository.java",
			"src/main/java/com/dnd/qello/question/repository/QuestionAssignmentRepository.java")) {
			String source = Files.readString(Path.of(port));
			assertThat(source).doesNotContain("jakarta.persistence").doesNotContain("org.springframework.data");
		}
	}

	@Test
	@DisplayName("5개 Question Entity는 관계 매핑과 version column 없이 scalar ID만 사용한다")
	void entitiesUseScalarIdsWithoutRelationsOrVersion() {
		for (Class<?> entity : ENTITIES) {
			List<String> annotations = Arrays.stream(entity.getDeclaredFields())
				.flatMap(field -> Arrays.stream(field.getDeclaredAnnotations()))
				.map(Annotation::annotationType)
				.map(Class::getSimpleName).toList();
			assertThat(annotations).doesNotContain("ManyToOne", "OneToMany", "OneToOne", "ManyToMany", "Version");
		}
		assertThat(Arrays.stream(ApprovedQuestionJpaEntity.class.getDeclaredFields())
			.filter(field -> field.getName().endsWith("Id"))
			.map(Field::getType).toList()).allMatch(type -> type == Long.class);
	}

	@Test
	@DisplayName("다른 feature는 Question JPA 구현을 직접 참조하지 않는다")
	void otherFeaturesDoNotReferenceQuestionJpaImplementation() throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/dnd/qello"))) {
			List<String> otherSources = paths.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/question/"))
				.map(this::readSource).toList();
			assertThat(otherSources).allMatch(source -> !source.contains("question.repository.jpa"));
		}
	}

	private String readSource(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException exception) {
			throw new IllegalStateException("source를 읽을 수 없습니다: " + path, exception);
		}
	}
}
