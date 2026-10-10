/**
 * Created at: 2026-08-03T16:09:35+09:00
 * Source scenario: TEST-PLAN-GH-31-POSTGIS-TESTCONTAINERS-SUPPORT
 * Source scenario: TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-001 (컨테이너 하나·클래스마다 새 DB, added 2026-10-10T12:57:32+09:00)
 * Source scenario: TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-001 (컨텍스트 공유·클래스 시작 전 DB 초기화, added 2026-10-10T15:19:46+09:00)
 */
package com.dnd.qello;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.zaxxer.hikari.HikariDataSource;

// PostGIS 컨테이너와 DB는 JVM에서 하나만 쓰고, 종료는 JVM이 끝날 때 Testcontainers Ryuk가 맡는다.
// Spring 컨텍스트는 설정이 같은 클래스끼리 캐시해 공유한다. 각 클래스의 정리 코드는 빈 DB에서 시작한다는 전제로
// 자기 테이블만 지우므로, 클래스 시작 전에 DB를 Flyway를 막 적용한 상태로 되돌린다
// (TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT D1).
@ExtendWith(PostgisContainerIntegrationTestSupport.SharedDatabaseResetExtension.class)
abstract class PostgisContainerIntegrationTestSupport {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName
			// Testcontainers DockerImageName does not accept the Compose tag@digest form.
			.parse("postgis/postgis:16-3.5-alpine")
			.asCompatibleSubstituteFor("postgres");

	private static final String PG_STAT_STATEMENTS_ENABLED = "qello.test.postgres.pg-stat-statements-enabled";

	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = postgresContainer();

	static {
		postgres.start();
	}

	private static PostgreSQLContainer<?> postgresContainer() {
		PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGIS_IMAGE)
				.withDatabaseName("qello_test")
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

	static final class SharedDatabaseResetExtension implements BeforeAllCallback {

		// 마이그레이션이 넣는 기준 데이터(V1 OCTANT, V29 국가 코드)다. 일부 테스트가 지우거나 바꾸므로 비운 뒤 다시 넣는다.
		// 부모 테이블이 먼저 와야 한다.
		private static final List<String> REFERENCE_TABLES = List.of("region_code", "direction_scheme",
				"direction_segment");

		private static final List<String> IDENTITY_REFERENCE_TABLES = List.of("direction_scheme", "direction_segment");

		private static final Set<String> RETAINED_TABLES = Set.of("flyway_schema_history", "spatial_ref_sys");

		private static Baseline baseline;

		@Override
		public void beforeAll(ExtensionContext context) throws SQLException {
			// 컨텍스트를 먼저 얻는다. 처음 뜨는 컨텍스트의 Flyway가 스키마를 만든 뒤에 초기화가 돌아야 한다.
			ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
			// 마이그레이션 테스트가 풀의 커넥션에 남긴 SET search_path 같은 세션 상태가 다음 클래스로 이어지지 않게
			// 이 컨텍스트의 커넥션을 새로 받게 한다. 초기화가 지우는 별도 스키마를 가리킨 커넥션도 함께 버려진다.
			DataSource dataSource = applicationContext.getBean(DataSource.class);
			if (dataSource.isWrapperFor(HikariDataSource.class)) {
				dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().softEvictConnections();
			}
			try (Connection connection = DriverManager.getConnection(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
				connection.setAutoCommit(false);
				try (Statement statement = connection.createStatement()) {
					// 캐시된 다른 컨텍스트에 트랜잭션이 남아 TRUNCATE가 기다리면 멈추지 않고 실패로 드러나게 한다.
					statement.execute("SET LOCAL lock_timeout = '10s'");
				}
				if (baseline == null) {
					// 첫 클래스의 테스트가 돌기 전이라 DB는 Flyway를 막 적용한 상태다.
					baseline = Baseline.capture(connection);
				} else {
					baseline.restore(connection);
				}
				connection.commit();
			}
		}

		// 스냅샷은 DB에 테이블로 남기지 않는다. 남기면 스키마 조건 없는 카탈로그 조회에 같은 이름 객체로 잡힌다.
		private record Baseline(Set<String> schemas, Map<String, String> referenceRows) {

			static Baseline capture(Connection connection) throws SQLException {
				Map<String, String> rows = new LinkedHashMap<>();
				for (String table : REFERENCE_TABLES) {
					rows.put(table, queryString(connection,
							"SELECT coalesce(json_agg(t), '[]')::text FROM public." + table + " t"));
				}
				return new Baseline(querySchemas(connection), rows);
			}

			void restore(Connection connection) throws SQLException {
				try (Statement statement = connection.createStatement()) {
					statement.execute("TRUNCATE TABLE " + String.join(", ", publicTables(connection))
							+ " RESTART IDENTITY CASCADE");
					// TRUNCATE는 앞 클래스가 ANALYZE로 남긴 열 통계를 지우지 않는다. 남으면 다음 클래스의 실행 계획이
					// 지워진 데이터의 분포를 따른다. 막 마이그레이션한 DB처럼 통계가 없게 한다(컨테이너 계정은 superuser다).
					statement.execute("""
							DELETE FROM pg_catalog.pg_statistic s
							USING pg_catalog.pg_class c
							JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
							WHERE s.starelid = c.oid AND n.nspname = 'public'
								AND c.relname NOT IN ('flyway_schema_history', 'spatial_ref_sys')
							""");
					for (String schema : querySchemas(connection)) {
						// 마이그레이션 테스트가 고정 이름으로 만든 별도 스키마. pg_temp 계열은 다른 세션 것이라 건드리지 않는다.
						if (!schemas.contains(schema) && !schema.startsWith("pg_")) {
							statement.execute("DROP SCHEMA \"" + schema + "\" CASCADE");
						}
					}
				}
				for (Map.Entry<String, String> entry : referenceRows.entrySet()) {
					try (PreparedStatement insert = connection.prepareStatement("INSERT INTO public." + entry.getKey()
							+ " SELECT * FROM json_populate_recordset(null::public." + entry.getKey() + ", ?::json)")) {
						insert.setString(1, entry.getValue());
						insert.executeUpdate();
					}
				}
				// 원래 id로 다시 넣었으므로 RESTART IDENTITY로 처음으로 돌아간 시퀀스를 최대 id 뒤로 옮긴다.
				for (String table : IDENTITY_REFERENCE_TABLES) {
					queryString(connection, "SELECT setval(pg_get_serial_sequence('public." + table + "', 'id'),"
							+ " coalesce(max(id), 1), count(*) > 0)::text FROM public." + table);
				}
			}

			private static List<String> publicTables(Connection connection) throws SQLException {
				List<String> tables = new ArrayList<>();
				try (Statement statement = connection.createStatement();
						ResultSet result = statement.executeQuery(
								"SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename")) {
					while (result.next()) {
						String table = result.getString(1);
						if (!RETAINED_TABLES.contains(table)) {
							tables.add("public.\"" + table + "\"");
						}
					}
				}
				return tables;
			}

			private static Set<String> querySchemas(Connection connection) throws SQLException {
				Set<String> names = new HashSet<>();
				try (Statement statement = connection.createStatement();
						ResultSet result = statement.executeQuery("SELECT nspname FROM pg_namespace")) {
					while (result.next()) {
						names.add(result.getString(1));
					}
				}
				return names;
			}

			private static String queryString(Connection connection, String sql) throws SQLException {
				try (Statement statement = connection.createStatement();
						ResultSet result = statement.executeQuery(sql)) {
					result.next();
					return result.getString(1);
				}
			}

		}

	}

}
