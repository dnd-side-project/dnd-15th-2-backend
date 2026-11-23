/**
 * Created at: 2026-11-23T13:52:37Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-001
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-002
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-INT-003
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dnd.qello.direction.consent.LocationConsent;
import com.dnd.qello.direction.consent.LocationConsentAction;
import com.dnd.qello.direction.consent.LocationConsentRepository;
import com.dnd.qello.direction.consent.LocationConsentService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocationConsentPersistenceIntegrationTest.FixedTime.class)
class LocationConsentPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final Instant AT = Instant.parse("2026-11-23T12:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MockMvc mvc;
	@Autowired
	private LocationConsentRepository history;
	@Autowired
	private LocationConsentService service;

	private final java.util.ArrayList<Long> users = new java.util.ArrayList<>();
	private String schema;

	@AfterEach
	void cleanup() {
		for (long user : users) {
			jdbc.update("DELETE FROM location_consent_history WHERE user_id = ?", user);
			jdbc.update("DELETE FROM user_account WHERE id = ?", user);
		}
		users.clear();
		if (schema != null) {
			jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
			schema = null;
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("INT-001: 빈 schema와 V33 schema 모두 V34를 적용하고 이전 checksum을 보존한다")
	void migratesFreshAndV33WithoutChangingHistory(boolean upgrade) {
		schema = "consent381_" + SEQUENCE.incrementAndGet();
		List<Map<String, Object>> before = List.of();
		if (upgrade) {
			migration("33").migrate();
			before = jdbc.queryForList("SELECT version, checksum FROM " + schema
					+ ".flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank");
		}
		assertThat(migration(null).migrate().migrationsExecuted).isEqualTo(upgrade ? 1 : 34);
		assertThat(migration(null).validateWithResult().validationSuccessful).isTrue();
		assertThat(migration(null).migrate().migrationsExecuted).isZero();
		if (upgrade) {
			assertThat(jdbc.queryForList("SELECT version, checksum FROM " + schema
					+ ".flyway_schema_history WHERE success AND version::int <= 33 ORDER BY installed_rank"))
					.isEqualTo(before);
		}
		String index = jdbc.queryForObject(
				"SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
				String.class, schema, "ix_location_consent_history_user_latest");
		assertThat(index).contains("(user_id, id DESC)");
		long user = jdbc.queryForObject("""
				INSERT INTO %s.user_account (country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('KR', 'KR', 'ko-KR', 'UTC', 'migration381') RETURNING id
				""".formatted(schema), Long.class);
		jdbc.update("INSERT INTO " + schema + ".location_consent_history"
				+ " (user_id, terms_version, action, recorded_at) VALUES (?, 'test-v1', 'CONSENT', ?)",
				user, Timestamp.from(AT));
		assertSqlState("23503", "DELETE FROM " + schema + ".user_account WHERE id = ?", user);
		assertSqlState("23503", insertSql(), Long.MAX_VALUE, "test-v1", "CONSENT", Timestamp.from(AT));
		assertSqlState("23502", insertSql(), null, "test-v1", "CONSENT", Timestamp.from(AT));
		assertSqlState("23502", insertSql(), user, null, "CONSENT", Timestamp.from(AT));
		assertSqlState("23502", insertSql(), user, "test-v1", null, Timestamp.from(AT));
		assertSqlState("23502", insertSql(), user, "test-v1", "CONSENT", null);
		assertSqlState("23514", insertSql(), user, "test-v1", "UNKNOWN", Timestamp.from(AT));
		for (String version : List.of("", " ", "\t", " test-v1", "test-v1 ")) {
			assertSqlState("23514", insertSql(), user, version, "CONSENT", Timestamp.from(AT));
		}
		assertSqlState("22001", insertSql(), user, "x".repeat(101), "CONSENT", Timestamp.from(AT));
		jdbc.update(insertSql(), user, "x".repeat(100), "CONSENT", Timestamp.from(AT));
	}

	@Test
	@DisplayName("INT-002: 본인 동의·철회 성공마다 이력이 늘고 이전 행과 타인 이력 및 GET은 불변이다")
	void appendsEverySuccessfulRequestAndKeepsEarlierRows() throws Exception {
		long user = account();
		long other = account();
		history.append(new LocationConsent(null, other, "test-v2", LocationConsentAction.CONSENT, AT));
		List<Map<String, Object>> otherBefore = rows(other);
		mvc.perform(get("/api/v1/location-consents/current").with(auth(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.consented").value(false))
				.andExpect(jsonPath("$.data.lastAction").isEmpty());
		assertThat(rows(user)).isEmpty();
		List<Map<String, Object>> before = List.of();
		for (int request = 0; request < 4; request++) {
			String path = request < 2 ? "/api/v1/location-consents" : "/api/v1/location-consents/withdrawals";
			mvc.perform(post(path).with(auth(user)).contentType(MediaType.APPLICATION_JSON)
					.content(request < 2
							? "{\"termsVersion\":\"test-v1\",\"recordedAt\":\"1900-01-01T00:00:00Z\"}"
							: ""))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.consented").value(request < 2));
			List<Map<String, Object>> after = rows(user);
			assertThat(after).hasSize(request + 1);
			assertThat(after.subList(0, before.size())).isEqualTo(before);
			assertThat(((Timestamp) after.get(request).get("recorded_at")).toInstant())
					.isEqualTo(AT);
			before = after;
		}
		mvc.perform(get("/api/v1/location-consents/current").with(auth(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.lastAction").value("WITHDRAW"));
		assertThat(rows(user)).isEqualTo(before);
		assertThat(rows(other)).isEqualTo(otherBefore);
	}

	@Test
	@DisplayName("INT-003: 동일·역행 시각에서도 사용자 전체 최신 id만 현재 상태를 결정한다")
	void latestIdWinsOverVersionAndRecordedTime() {
		long user = account();
		history.append(new LocationConsent(null, user, "test-v2", LocationConsentAction.CONSENT, AT));
		assertThat(service.current(user).consented()).isFalse();
		history.append(new LocationConsent(null, user, "test-v1", LocationConsentAction.CONSENT, AT));
		assertThat(service.current(user).consented()).isTrue();
		LocationConsent withdrawn = history.append(new LocationConsent(null, user, "test-v2",
				LocationConsentAction.WITHDRAW, AT.minusSeconds(60)));
		assertThat(history.findLatestByUserId(user)).contains(withdrawn);
		assertThat(service.current(user).consented()).isFalse();
		assertThat(service.current(user).lastTermsVersion()).isEqualTo("test-v2");
	}

	@Test
	@DisplayName("INT-002: 무기록 철회는 현재 버전을, 구버전 이력 철회는 마지막 버전을 사용한다")
	void withdrawalChoosesLatestVersionOrCurrentWhenEmpty() {
		long empty = account();
		long old = account();
		history.append(new LocationConsent(null, old, "test-v2", LocationConsentAction.CONSENT, AT));
		assertThat(service.withdraw(empty).lastTermsVersion()).isEqualTo("test-v1");
		assertThat(service.withdraw(old).lastTermsVersion()).isEqualTo("test-v2");
	}

	private long account() {
		long user = jdbc.queryForObject("""
				INSERT INTO user_account (country_code, coarse_region_code, locale, timezone, nickname)
				VALUES ('KR', 'KR', 'ko-KR', 'UTC', ?) RETURNING id
				""", Long.class, "consent-persist-" + SEQUENCE.incrementAndGet());
		users.add(user);
		return user;
	}

	private List<Map<String, Object>> rows(long user) {
		return jdbc.queryForList("SELECT * FROM location_consent_history WHERE user_id = ? ORDER BY id", user);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor auth(long user) {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
				.jwt(token -> token.subject(Long.toString(user)));
	}

	private Flyway migration(String target) {
		var config = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true);
		return (target == null ? config : config.target(target)).load();
	}

	private String insertSql() {
		return "INSERT INTO " + schema
				+ ".location_consent_history (user_id, terms_version, action, recorded_at) VALUES (?, ?, ?, ?)";
	}

	private void assertSqlState(String state, String sql, Object... args) {
		assertThatThrownBy(() -> jdbc.update(sql, args)).rootCause()
				.isInstanceOf(java.sql.SQLException.class)
				.satisfies(failure -> assertThat(((java.sql.SQLException) failure).getSQLState()).isEqualTo(state));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock consentPersistenceClock() {
			return Clock.fixed(AT, ZoneOffset.UTC);
		}
	}
}
