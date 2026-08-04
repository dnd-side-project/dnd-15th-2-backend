package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Created at: 2026-08-03T17:45:39+09:00
 * Source scenario: TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-001, TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-002
 */
class FlywayMigrationContractTest {

	private static final String ACCEPTED_DDL_SHA_256 =
		"cc93ba87aa5999bdd48589b63fa4da4e383270626fb36ecb7adac482ed3d95a7";
	private static final String ACCEPTED_V2_SHA_256 =
		"c8daf71f9ce75fb75b6c30ecb49a4b2d912b887a34d8f602617c2ca8a27f4e04";

	@Test
	@DisplayName("V1은 승인된 독립 DDL과 동일하고 V2는 승인된 delta와 동일하다")
	void migrationsMatchAcceptedContent() throws Exception {
		ClassPathResource v1 = new ClassPathResource(
			"db/migration/V1__create_direction_communication_schema.sql");
		ClassPathResource v2 = new ClassPathResource(
			"db/migration/V2__add_reactions_and_skip_pending.sql");
		Properties scriptConfiguration = PropertiesLoaderUtils.loadProperties(
			new ClassPathResource(
				"db/migration/V1__create_direction_communication_schema.sql.conf"));

		assertThat(sha256(v1)).isEqualTo(ACCEPTED_DDL_SHA_256);
		assertThat(sha256(v2)).isEqualTo(ACCEPTED_V2_SHA_256);
		assertThat(scriptConfiguration)
			.containsEntry("executeInTransaction", "false");
		assertThat(sqlMigrationNames()).containsExactly(
			"V1__create_direction_communication_schema.sql",
			"V2__add_reactions_and_skip_pending.sql");
	}

	@Test
	@DisplayName("V2는 script configuration 없이 기본 transaction 안에서 실행된다")
	void v2RunsInsideTheDefaultTransaction() throws IOException {
		assertThat(scriptConfigurationNames()).containsExactly(
			"V1__create_direction_communication_schema.sql.conf");
	}

	@Test
	@DisplayName("Flyway는 migration 이름을 검증하고 clean과 자동 baseline을 금지한다")
	void flywaySafetySettingsAreEnabled() throws IOException {
		Properties properties = PropertiesLoaderUtils.loadProperties(
			new ClassPathResource("application.properties"));

		assertThat(properties)
			.containsEntry("spring.flyway.enabled", "true")
			.containsEntry("spring.flyway.locations", "classpath:db/migration")
			.containsEntry("spring.flyway.clean-disabled", "true")
			.containsEntry("spring.flyway.baseline-on-migrate", "false")
			.containsEntry("spring.flyway.validate-migration-naming", "true");
	}

	private String sha256(ClassPathResource resource)
		throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = resource.getInputStream()) {
			return HexFormat.of().formatHex(digest.digest(input.readAllBytes()));
		}
	}

	private List<String> sqlMigrationNames() throws IOException {
		Path migrationDirectory = Path.of("src/main/resources/db/migration");
		try (var paths = Files.list(migrationDirectory)) {
			return paths
				.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".sql"))
				.sorted()
				.toList();
		}
	}

	private List<String> scriptConfigurationNames() throws IOException {
		Path migrationDirectory = Path.of("src/main/resources/db/migration");
		try (var paths = Files.list(migrationDirectory)) {
			return paths
				.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".conf"))
				.sorted()
				.toList();
		}
	}

}
