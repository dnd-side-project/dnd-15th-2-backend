/**
 * Created at: 2026-11-24T17:42:01Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-009, INT-010
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.PostAudience;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import com.dnd.qello.direction.repository.PostAudienceRepository;
import com.dnd.qello.direction.security.AesGcmCoordinateProtector;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.security.CoordinateKeyRing;
import com.dnd.qello.direction.security.CoordinateProtectionException;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CoordinatePersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ActiveUserPresenceRepository presenceRepository;
	@Autowired
	private PostAudienceRepository audienceRepository;
	@Autowired
	private CoordinateProtector protector;

	@Test
	@DisplayName("INT-009: presence 암호문을 다른 사용자 행으로 옮기면 인증이 실패한다")
	void authenticatesPresenceRowIdentity() {
		long first = CoordinateEncryptionTestFixture.user(jdbc);
		long second = CoordinateEncryptionTestFixture.user(jdbc);
		presenceRepository.save(presence(first));
		presenceRepository.save(presence(second));
		byte[] envelope = ciphertext(first);
		assertThat(presenceRepository.findByUserId(first).orElseThrow().getLatitude())
				.isEqualByComparingTo("12.3456789123456");
		jdbc.update("UPDATE active_user_presence SET position_ciphertext = ? WHERE user_id = ?", envelope, second);

		assertThatThrownBy(() -> presenceRepository.findByUserId(second))
				.isInstanceOf(CoordinateProtectionException.class);
		assertThat(presenceRepository.findByUserId(first)).isPresent();
	}

	@Test
	@DisplayName("INT-009: audience 행 교체와 같은 숫자 ID의 다른 용도 암호문을 거절한다")
	void authenticatesAudienceRowAndColumnPurpose() {
		long user = CoordinateEncryptionTestFixture.user(jdbc);
		long first = CoordinateEncryptionTestFixture.post(jdbc, user, "MATCHING");
		long second = CoordinateEncryptionTestFixture.post(jdbc, user, "MATCHING");
		audienceRepository.save(audience(first));
		audienceRepository.save(audience(second));
		byte[] envelope = jdbc.queryForObject(
				"SELECT origin_position_ciphertext FROM post_audience WHERE post_id = ?", byte[].class, first);
		jdbc.update("UPDATE post_audience SET origin_position_ciphertext = ? WHERE post_id = ?", envelope, second);
		assertThatThrownBy(() -> audienceRepository.findByPostId(second))
				.isInstanceOf(CoordinateProtectionException.class);

		byte[] wrongPurpose = protector.protect(new Coordinate(BigDecimal.ONE, BigDecimal.TEN),
				CoordinatePurpose.ACTIVE_USER_PRESENCE, first);
		jdbc.update("UPDATE post_audience SET origin_position_ciphertext = ? WHERE post_id = ?", wrongPurpose, first);
		assertThatThrownBy(() -> audienceRepository.findByPostId(first))
				.isInstanceOf(CoordinateProtectionException.class);
	}

	@Test
	@DisplayName("INT-010: previous 행을 읽고 새 write는 current envelope로 저장하며 잘못된 키는 실패한다")
	void readsPreviousAndWritesCurrentWithoutPlaintextFallback() {
		long user = CoordinateEncryptionTestFixture.user(jdbc);
		presenceRepository.save(presence(user));
		byte[] previousKey = new byte[32];
		Arrays.fill(previousKey, (byte) 'd');
		var old = new AesGcmCoordinateProtector(new CoordinateKeyRing(
				"coordinate-fixture-previous", Map.of("coordinate-fixture-previous", previousKey)));
		Coordinate original = new Coordinate(presence(user).getLatitude(), presence(user).getLongitude());
		byte[] oldEnvelope = old.protect(original, CoordinatePurpose.ACTIVE_USER_PRESENCE, user);
		jdbc.update("UPDATE active_user_presence SET position_ciphertext = ? WHERE user_id = ?", oldEnvelope, user);
		assertThat(presenceRepository.findByUserId(user).orElseThrow().getLongitude())
				.isEqualByComparingTo(original.longitude());
		presenceRepository.save(presence(user));
		byte[] currentEnvelope = ciphertext(user);
		int idLength = Byte.toUnsignedInt(currentEnvelope[1]);
		assertThat(new String(currentEnvelope, 2, idLength, StandardCharsets.UTF_8))
				.isEqualTo("coordinate-fixture-current");
		var wrong = new AesGcmCoordinateProtector(new CoordinateKeyRing(
				"coordinate-fixture-current", Map.of("coordinate-fixture-current", new byte[32])));
		assertThatThrownBy(() -> wrong.decrypt(currentEnvelope, CoordinatePurpose.ACTIVE_USER_PRESENCE, user))
				.isInstanceOf(CoordinateProtectionException.class);
		assertThatThrownBy(() -> wrong.decrypt(oldEnvelope, CoordinatePurpose.ACTIVE_USER_PRESENCE, user))
				.isInstanceOf(CoordinateProtectionException.class);
	}

	private byte[] ciphertext(long user) {
		return jdbc.queryForObject("SELECT position_ciphertext FROM active_user_presence WHERE user_id = ?",
				byte[].class, user);
	}

	private static ActiveUserPresence presence(long user) {
		return ActiveUserPresence.create(user, new BigDecimal("12.3456789123456"),
				new BigDecimal("23.4567891234567"), null, "KR", BigDecimal.ONE, true,
				CoordinateEncryptionTestFixture.NOW, CoordinateEncryptionTestFixture.NOW.plusSeconds(86400));
	}

	private PostAudience audience(long post) {
		long scheme = jdbc.queryForObject(
				"SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'", Long.class);
		return PostAudience.create(post, scheme, "N", BigDecimal.ZERO, BigDecimal.valueOf(45), 0, 20100000,
				BigDecimal.TEN, BigDecimal.ONE, null, CoordinateEncryptionTestFixture.NOW);
	}
}
