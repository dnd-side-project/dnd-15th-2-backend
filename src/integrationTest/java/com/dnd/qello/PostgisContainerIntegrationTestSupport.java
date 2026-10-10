/**
 * Created at: 2026-08-03T16:09:35+09:00
 * Source scenario: TEST-PLAN-GH-31-POSTGIS-TESTCONTAINERS-SUPPORT
 * Source scenario: TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-001 (컨테이너 하나·클래스마다 새 DB, added 2026-10-10T12:57:32+09:00)
 */
package com.dnd.qello;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// PostGIS 컨테이너는 JVM에서 한 번만 띄우고, 종료는 JVM이 끝날 때 Testcontainers Ryuk가 맡는다.
// 테스트 클래스마다 같은 이름의 DB를 template_postgis로 다시 만들어, 클래스마다 컨테이너를 띄우던 때처럼
// 빈 DB에서 Flyway가 돌게 한다. 한 DB를 이어 쓰면 각 클래스의 정리 코드가 앞 클래스가 남긴 자식 행에
// FK로 막힌다(TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS 보고서).
@ExtendWith(PostgisContainerIntegrationTestSupport.ClassDatabaseExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgisContainerIntegrationTestSupport {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName
			// Testcontainers DockerImageName does not accept the Compose tag@digest form.
			.parse("postgis/postgis:16-3.5-alpine")
			.asCompatibleSubstituteFor("postgres");

	private static final String PG_STAT_STATEMENTS_ENABLED = "qello.test.postgres.pg-stat-statements-enabled";

	private static final String DATABASE_NAME = "qello_test";

	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = postgresContainer();

	static {
		postgres.start();
	}

	private static PostgreSQLContainer<?> postgresContainer() {
		PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGIS_IMAGE)
				.withDatabaseName(DATABASE_NAME)
				.withUsername("qello_test")
				.withPassword("test-only")
				.withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));
		if (Boolean.getBoolean(PG_STAT_STATEMENTS_ENABLED)) {
			container.withCommand(
					"postgres",
					"-c",
					"shared_preload_libraries=pg_stat_statements");
		}
		return container;
	}

	// 상위 클래스의 @ExtendWith가 먼저 등록되므로 Spring 컨텍스트와 각 클래스의 @BeforeAll보다 먼저 실행된다.
	static final class ClassDatabaseExtension implements BeforeAllCallback {

		@Override
		public void beforeAll(ExtensionContext context) throws SQLException {
			// 지우는 DB에는 접속할 수 없으므로 관리용 postgres DB에 접속한다.
			String maintenanceUrl = "jdbc:postgresql://%s:%d/postgres".formatted(
					postgres.getHost(), postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
			try (Connection connection = DriverManager.getConnection(
					maintenanceUrl, postgres.getUsername(), postgres.getPassword());
					Statement statement = connection.createStatement()) {
				// 직전 클래스의 컨텍스트는 @DirtiesContext(AFTER_CLASS)로 이미 닫혔다. 남은 연결이 있어도 끊고 지운다.
				statement.execute("DROP DATABASE IF EXISTS " + DATABASE_NAME + " WITH (FORCE)");
				statement.execute("CREATE DATABASE " + DATABASE_NAME + " TEMPLATE template_postgis");
			}
		}

	}

}
