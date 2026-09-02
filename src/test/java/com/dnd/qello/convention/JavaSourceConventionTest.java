/*
 * Created at: 2026-09-02T00:38:56+09:00
 * Source scenario: TEST-PLAN-GH-208-JAVA-CONVENTION-GATES-UNIT-015 through UNIT-019
 */
package com.dnd.qello.convention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceConventionTest {

	static {
		StaticJavaParser.getParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
	}

	@Test
	@DisplayName("단순 명시적 생성자는 QELLO-JAVA-CTOR-001로 보고한다")
	void reportsSimpleExplicitConstructor() throws IOException {
		Path source = Path.of("src", "test", "resources", "java-conventions", "source",
				"ExplicitConstructorService.java");

		assertThat(inspect(source))
				.singleElement()
				.isEqualTo("QELLO-JAVA-CTOR-001");
	}

	@Test
	@DisplayName("RequiredArgsConstructor Service는 constructor rule을 통과한다")
	void acceptsRequiredArgsConstructorService() throws IOException {
		Path source = Path.of("src", "test", "resources", "java-conventions", "source", "RequiredArgsService.java");

		assertThat(inspect(source)).isEmpty();
	}

	@Test
	@DisplayName("production Service의 명시적 생성자는 RequiredArgs 또는 exact baseline으로 관리된다")
	void acceptsBaselineBackedProductionServices() throws IOException {
		Set<String> baselineTargets = new HashSet<>();
		new ObjectMapper().readTree(Path.of("config", "java-conventions", "baseline.json").toFile())
				.path("entries")
				.forEach(entry -> {
					if ("QELLO-JAVA-CTOR-001".equals(entry.path("rule").asText())) {
						baselineTargets.add(entry.path("target").asText());
					}
				});

		List<String> violations = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
			for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
				for (String rule : inspect(path)) {
					if ("QELLO-JAVA-CTOR-001".equals(rule) && !baselineTargets.contains(typeName(path))) {
						violations.add(typeName(path));
					}
				}
			}
		}

		assertThat(violations).describedAs("unmanaged constructor targets").isEmpty();
	}

	private static String typeName(Path source) {
		String relative = Path.of("src", "main", "java").relativize(source).toString();
		return relative.substring(0, relative.length() - ".java".length()).replace('/', '.').replace('\\', '.');
	}

	private static List<String> inspect(Path source) throws IOException {
		ClassOrInterfaceDeclaration declaration = StaticJavaParser.parse(source)
				.findFirst(ClassOrInterfaceDeclaration.class, type -> type.getAnnotationByName("Service").isPresent())
				.orElse(null);
		if (declaration == null) {
			return List.of();
		}
		if (declaration.getConstructors().stream().anyMatch(JavaSourceConventionTest::isSimpleConstructor)
				&& declaration.getAnnotationByName("RequiredArgsConstructor").isEmpty()) {
			return List.of("QELLO-JAVA-CTOR-001");
		}
		return List.of();
	}

	private static boolean isSimpleConstructor(ConstructorDeclaration constructor) {
		return !constructor.getBody().getStatements().isEmpty()
				&& constructor.getBody().getStatements().stream().allMatch(statement -> statement.isExpressionStmt()
						&& statement.asExpressionStmt().getExpression().isAssignExpr()
						&& isThisFieldAssignment(statement.asExpressionStmt().getExpression().asAssignExpr()));
	}

	private static boolean isThisFieldAssignment(AssignExpr assignment) {
		return assignment.getOperator() == AssignExpr.Operator.ASSIGN
				&& assignment.getTarget().isFieldAccessExpr()
				&& assignment.getTarget().asFieldAccessExpr().getScope().isThisExpr()
				&& assignment.getValue().isNameExpr()
				&& assignment.getTarget().asFieldAccessExpr().getNameAsString()
						.equals(assignment.getValue().asNameExpr().getNameAsString());
	}
}
