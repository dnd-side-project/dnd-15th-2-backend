/**
 * Created at: 2026-08-14T01:12:00+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-INT-012
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.dnd.qello.direction.config.DirectionPresenceProperties;
import com.dnd.qello.direction.service.DirectionPresenceService;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
	"qello.direction.presence.ttl=PT12H",
	"qello.direction.presence.max-accuracy-meters=75.5",
	"qello.direction.presence.max-future-skew=PT10S",
	"qello.direction.presence.max-observation-age=PT2M"
})
class ActiveUserPresenceConfigurationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-PRESENCE-CONFIG-121";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DirectionPresenceProperties properties;

	@Autowired
	private DirectionPresenceService presenceService;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Presence Config Region', 'REGION')
			""", REGION);
	}

	@Test
	@DisplayName("Spring override가 service TTL·정확도·관측 시각 정책에 독립적으로 적용된다")
	void appliesOverriddenPoliciesToPresenceUpdate() {
		long userId = jdbc.queryForObject("""
			INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('KR', ?, 'ko-KR', 'Asia/Seoul', 'config-override') RETURNING id
			""", Long.class, REGION);
		Instant observedAt = Instant.now().minusSeconds(30);

		assertThat(presenceService.update(userId, new DirectionPresenceService.UpdateCommand(
			new BigDecimal("37.5"), new BigDecimal("127.0"), new BigDecimal("75.5"), true, observedAt)))
			.isTrue();

		assertThat(properties.ttl()).isEqualTo(java.time.Duration.ofHours(12));
		assertThat(properties.maxAccuracyMeters()).isEqualByComparingTo("75.5");
		assertThat(properties.maxFutureSkew()).isEqualTo(java.time.Duration.ofSeconds(10));
		assertThat(properties.maxObservationAge()).isEqualTo(java.time.Duration.ofMinutes(2));
		assertThat(jdbc.queryForObject("SELECT expires_at FROM active_user_presence WHERE user_id = ?",
			Timestamp.class, userId).toInstant()).isEqualTo(observedAt.plus(properties.ttl()));
	}
}
