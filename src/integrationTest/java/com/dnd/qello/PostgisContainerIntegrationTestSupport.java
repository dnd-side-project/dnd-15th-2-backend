package com.dnd.qello;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Created at: 2026-08-03T16:09:35+09:00
 * Source scenario: TEST-PLAN-GH-31-POSTGIS-TESTCONTAINERS-SUPPORT
 */
@Testcontainers
abstract class PostgisContainerIntegrationTestSupport {

	private static final DockerImageName POSTGIS_IMAGE = DockerImageName
		// Testcontainers DockerImageName does not accept the Compose tag@digest form.
		.parse("postgis/postgis:16-3.5-alpine")
		.asCompatibleSubstituteFor("postgres");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
		.withDatabaseName("qello_test")
		.withUsername("qello_test")
		.withPassword("test-only")
		.withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));

}
