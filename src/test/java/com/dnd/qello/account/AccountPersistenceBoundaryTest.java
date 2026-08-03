package com.dnd.qello.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import com.dnd.qello.account.repository.jpa.AccountJpaEntity;

/**
 * Created at: 2026-08-03T18:13:05+09:00
 * Source scenario: TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-003, TEST-PLAN-GH-37-ACCOUNT-PERSISTENCE-UNIT-004
 */
class AccountPersistenceBoundaryTest {

	private static final Set<String> FORBIDDEN_ENTITY_ANNOTATIONS = Set.of(
		"ManyToOne",
		"OneToMany",
		"OneToOne",
		"ManyToMany",
		"Version"
	);

	@Test
	@DisplayName("Account domain과 repository port는 JPA 및 Spring Data에 의존하지 않는다")
	void domainAndPortRemainPersistenceIndependent() throws IOException {
		List<Path> contractSources = List.of(
			Path.of("src/main/java/com/dnd/qello/account/domain"),
			Path.of("src/main/java/com/dnd/qello/account/repository/AccountRepository.java")
		);

		for (Path source : contractSources) {
			try (Stream<Path> paths = Files.isDirectory(source)
				? Files.walk(source)
				: Stream.of(source)) {
				for (Path javaFile : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
					String content = Files.readString(javaFile);
					assertThat(content)
						.doesNotContain("jakarta.persistence")
						.doesNotContain("org.springframework.data");
				}
			}
		}
	}

	@Test
	@DisplayName("Account Entity는 region scalar만 매핑하고 관계 및 낙관적 잠금을 만들지 않는다")
	void entityUsesScalarRegionWithoutRelationsOrVersion() throws Exception {
		Field regionField = AccountJpaEntity.class.getDeclaredField("coarseRegionCode");
		List<String> annotationNames = Arrays.stream(AccountJpaEntity.class.getDeclaredFields())
			.flatMap(field -> Arrays.stream(field.getDeclaredAnnotations()))
			.map(Annotation::annotationType)
			.map(Class::getSimpleName)
			.toList();

		assertThat(regionField.getType()).isEqualTo(String.class);
		assertThat(annotationNames).doesNotContainAnyElementsOf(FORBIDDEN_ENTITY_ANNOTATIONS);
	}

	@Test
	@DisplayName("다른 feature는 Account JPA Entity와 Spring Data repository를 참조하지 않는다")
	void otherFeaturesDoNotReferenceAccountJpaImplementation() throws IOException {
		Path root = Path.of("src/main/java/com/dnd/qello");
		try (Stream<Path> paths = Files.walk(root)) {
			List<String> otherFeatureSources = paths
				.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.toString().contains("/account/"))
				.map(this::readSource)
				.toList();

			assertThat(otherFeatureSources)
				.allMatch(source -> !source.contains("account.repository.jpa"));
		}
	}

	@Test
	@DisplayName("Hibernate는 Flyway schema를 validate만 하고 web view까지 세션을 열지 않는다")
	void jpaSafetySettingsAreFixed() throws IOException {
		Properties properties = PropertiesLoaderUtils.loadProperties(
			new ClassPathResource("application.properties"));

		assertThat(properties)
			.containsEntry("spring.jpa.generate-ddl", "false")
			.containsEntry("spring.jpa.hibernate.ddl-auto", "validate")
			.containsEntry("spring.jpa.open-in-view", "false")
			.containsEntry("spring.jpa.properties.hibernate.jdbc.time_zone", "UTC");
	}

	private String readSource(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException("source를 읽을 수 없습니다: " + source, exception);
		}
	}

}
