/**
 * Created at: 2026-10-01T09:55:37+09:00
 * Source scenario: TEST-PLAN-GH-294-COUNTRY-SEED-INT-001 through INT-005
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.repository.CountryCatalogRepository;

@SpringBootTest
@ActiveProfiles({"test", "flyway-migration"})
class CountrySeedMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String SEED_PATH = "db/migration/V29__seed_country_region_code.sql";
	private static final String UPGRADE_SCHEMA = "country_seed_upgrade";
	private static final String TEST_REGION = "CSEED-JP-1";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CountryCatalogRepository countryCatalogRepository;

	private static Flyway flywayForSchema(String schema, String... targets) {
		var configuration = Flyway.configure()
			.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
			.schemas(schema)
			.defaultSchema(schema)
			.locations("classpath:db/migration")
			.cleanDisabled(true);
		if (targets.length > 0) {
			configuration = configuration.target(targets[0]);
		}
		return configuration.load();
	}

	@BeforeAll
	static void applyV1ThroughV28ToUpgradeSchema() {
		flywayForSchema(UPGRADE_SCHEMA, "28").migrate();
	}

	@AfterEach
	void cleanFixtures() {
		jdbcTemplate.update("DELETE FROM user_account WHERE coarse_region_code = ?", TEST_REGION);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", TEST_REGION);
		jdbcTemplate.update("UPDATE region_code SET display_name = '대한민국' WHERE code = 'KR'");
	}

	private String seedSql() throws Exception {
		return new String(new ClassPathResource(SEED_PATH).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("전체 마이그레이션 후 region_code에는 level이 COUNTRY인 국가 249개만 시드로 존재한다")
	void seedsAllCountriesAsRootRows() {
		Integer countries = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM region_code WHERE level = 'COUNTRY'", Integer.class);
		Integer otherLevels = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM region_code WHERE level <> 'COUNTRY'", Integer.class);
		Integer rootsWithParent = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM region_code WHERE level = 'COUNTRY' AND parent_code IS NOT NULL", Integer.class);
		Integer malformedCodes = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM region_code WHERE level = 'COUNTRY' AND code !~ '^[A-Z]{2}$'", Integer.class);
		String koreaName = jdbcTemplate.queryForObject(
			"SELECT display_name FROM region_code WHERE code = 'KR'", String.class);

		assertThat(countries).isEqualTo(249);
		assertThat(otherLevels).isZero();
		assertThat(rootsWithParent).isZero();
		assertThat(malformedCodes).isZero();
		assertThat(koreaName).isEqualTo("대한민국");
	}

	@Test
	@DisplayName("existsCountry는 시드된 국가에 true, 시드에 없는 코드와 소문자와 빈 문자열에 false를 반환한다")
	void existsCountryReflectsSeed() {
		assertThat(countryCatalogRepository.existsCountry("KR")).isTrue();
		assertThat(countryCatalogRepository.existsCountry("US")).isTrue();
		assertThat(countryCatalogRepository.existsCountry("ZZ")).isFalse();
		assertThat(countryCatalogRepository.existsCountry("kr")).isFalse();
		assertThat(countryCatalogRepository.existsCountry("")).isFalse();
	}

	@Test
	@DisplayName("시드 SQL을 다시 실행해도 행 수가 변하지 않고 이미 있는 행의 display_name을 덮어쓰지 않는다")
	void reapplyingSeedKeepsExistingRows() throws Exception {
		jdbcTemplate.update("UPDATE region_code SET display_name = 'Korea' WHERE code = 'KR'");
		Integer before = jdbcTemplate.queryForObject("SELECT count(*) FROM region_code", Integer.class);

		jdbcTemplate.execute(seedSql());
		jdbcTemplate.execute(seedSql());

		Integer after = jdbcTemplate.queryForObject("SELECT count(*) FROM region_code", Integer.class);
		String koreaName = jdbcTemplate.queryForObject(
			"SELECT display_name FROM region_code WHERE code = 'KR'", String.class);
		assertThat(after).isEqualTo(before);
		assertThat(koreaName).isEqualTo("Korea");
	}

	@Test
	@DisplayName("시드된 국가는 user_account.country_code가 참조할 수 있고 시드에 없는 코드는 FK로 거부된다")
	void userAccountReferencesSeededCountryOnly() {
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'JP', 'Country Seed Test Region', 'REGION')
			""", TEST_REGION);

		jdbcTemplate.update("""
			INSERT INTO user_account
				(role, country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'JP', ?, 'ja-JP', 'Asia/Tokyo', 'country-seed-jp')
			""", TEST_REGION);

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO user_account
				(role, country_code, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ZZ', ?, 'ja-JP', 'Asia/Tokyo', 'country-seed-zz')
			""", TEST_REGION)).isInstanceOf(DataIntegrityViolationException.class);
		Integer accounts = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM user_account WHERE coarse_region_code = ?", Integer.class, TEST_REGION);
		assertThat(accounts).isEqualTo(1);
	}

	@Test
	@DisplayName("V28까지 적용되어 KR과 하위 지역이 이미 있는 DB에 V29를 적용하면 기존 행을 보존하고 국가 249개가 된다")
	void upgradeKeepsPreExistingRows() {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			JdbcTemplate scoped = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
			scoped.execute("SET search_path TO " + UPGRADE_SCHEMA);

			scoped.update("INSERT INTO region_code (code, parent_code, display_name, level) "
				+ "VALUES ('KR', NULL, 'Korea', 'COUNTRY')");
			scoped.update("INSERT INTO region_code (code, parent_code, display_name, level) "
				+ "VALUES ('CSEED-KR-1', 'KR', 'Country Seed Existing Region', 'REGION')");

			flywayForSchema(UPGRADE_SCHEMA).migrate();

			Integer countries = scoped.queryForObject(
				"SELECT count(*) FROM region_code WHERE level = 'COUNTRY'", Integer.class);
			String koreaName = scoped.queryForObject(
				"SELECT display_name FROM region_code WHERE code = 'KR'", String.class);
			Integer existingRegion = scoped.queryForObject(
				"SELECT count(*) FROM region_code WHERE code = 'CSEED-KR-1' AND parent_code = 'KR'", Integer.class);

			assertThat(countries).isEqualTo(249);
			assertThat(koreaName).isEqualTo("Korea");
			assertThat(existingRegion).isEqualTo(1);
			return null;
		});
	}

}
