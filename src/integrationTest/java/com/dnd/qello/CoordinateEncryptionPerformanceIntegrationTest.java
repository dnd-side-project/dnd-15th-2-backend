/**
 * Created at: 2026-11-24T17:48:42Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-PERF-001 through PERF-003
 */
package com.dnd.qello;

import java.util.Arrays;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.dnd.qello.direction.config.CoordinateProtectionProperties;
import com.dnd.qello.direction.security.CoordinateProtector;

@SpringBootTest
@ActiveProfiles("test")
@Import({DirectionMatchingE3TestClockConfiguration.class,
		CoordinatePerformanceRunner.LockObservationConfiguration.class})
class CoordinateEncryptionPerformanceIntegrationTest extends CoordinatePerformanceRunner {

	private StorageAdapter adapter;

	@Autowired
	void coordinateStorage(JdbcTemplate jdbc, CoordinateProtector protector, CoordinateProtectionProperties keys) {
		adapter = new CoordinateCiphertextPerformanceAdapter(jdbc, protector, keys);
	}

	@Override
	StorageAdapter storage() {
		return adapter;
	}

	@DynamicPropertySource
	static void fixtureKeys(DynamicPropertyRegistry registry) {
		byte[] current = new byte[32];
		byte[] previous = new byte[32];
		Arrays.fill(current, (byte) 'c');
		Arrays.fill(previous, (byte) 'p');
		registry.add("qello.direction.coordinate-protection.current-key-id", () -> "performance-current");
		registry.add("qello.direction.coordinate-protection.current-key-base64",
				() -> Base64.getEncoder().encodeToString(current));
		registry.add("qello.direction.coordinate-protection.previous-key-id", () -> "performance-previous");
		registry.add("qello.direction.coordinate-protection.previous-key-base64",
				() -> Base64.getEncoder().encodeToString(previous));
	}
}
