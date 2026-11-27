/**
 * Created at: 2026-11-25T04:38:55Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-PERF-001 through PERF-003
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import com.dnd.qello.direction.config.CoordinateProtectionProperties;
import com.dnd.qello.direction.security.AesGcmCoordinateProtector;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.security.CoordinateKeyRing;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import static org.assertj.core.api.Assertions.assertThat;

final class CoordinateCiphertextPerformanceAdapter implements CoordinatePerformanceRunner.StorageAdapter {

	private final JdbcTemplate jdbc;
	private final CoordinateProtector current;
	private final CoordinateProtector previous;

	CoordinateCiphertextPerformanceAdapter(JdbcTemplate jdbc, CoordinateProtector current,
			CoordinateProtectionProperties keys) {
		this.jdbc = jdbc;
		this.current = current;
		this.previous = new AesGcmCoordinateProtector(new CoordinateKeyRing(keys.previousKeyId(),
				Map.of(keys.previousKeyId(), Base64.getDecoder().decode(keys.previousKeyBase64()))));
	}

	@Override
	public String variant() {
		return "ENCRYPTED_APP_FULL_FLOW";
	}

	@Override
	public void seedPresence(List<CoordinatePerformanceRunner.Presence> rows) {
		List<Object[]> encrypted = rows.stream().map(row -> new Object[]{
				row.userId(), (row.previousKey() ? previous : current).protect(
						coordinate(row.location()), CoordinatePurpose.ACTIVE_USER_PRESENCE, row.userId()),
				row.region(), row.receiveAllowed(), Timestamp.from(row.locationAt()), Timestamp.from(row.expiresAt())})
				.toList();
		jdbc.batchUpdate(
				"""
						INSERT INTO active_user_presence
							(user_id, position_ciphertext, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
						VALUES (?, ?, ?, 1, ?, ?, ?)
						""",
				encrypted);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM active_user_presence
				WHERE coarse_region_code = ? AND receive_allowed AND position_ciphertext IS NOT NULL
				""", Long.class, rows.getFirst().region()))
				.isEqualTo(rows.stream().filter(CoordinatePerformanceRunner.Presence::receiveAllowed).count());
	}

	@Override
	public void prepareAudience(long postId, CoordinatePerformanceRunner.Location origin, boolean previousKey) {
		if (previousKey) {
			jdbc.update("UPDATE post_audience SET origin_position_ciphertext = ? WHERE post_id = ?",
					previous.protect(coordinate(origin), CoordinatePurpose.POST_AUDIENCE, postId), postId);
		}
	}

	private static Coordinate coordinate(CoordinatePerformanceRunner.Location location) {
		return new Coordinate(location.latitude(), location.longitude());
	}
}
