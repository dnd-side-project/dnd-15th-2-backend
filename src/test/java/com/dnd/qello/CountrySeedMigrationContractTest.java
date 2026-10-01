/*
 * Created at: 2026-10-01T09:55:37+09:00
 * Source scenario: TEST-PLAN-GH-294-COUNTRY-SEED-UNIT-001 through UNIT-004
 */
package com.dnd.qello;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CountrySeedMigrationContractTest {

	private static final String SEED_PATH = "db/migration/V29__seed_country_region_code.sql";
	private static final Pattern ROW = Pattern.compile(
		"^\\s*\\('([^']*)', (NULL|'[^']*'), '([^']*)', '([^']*)'\\)[,]?\\s*$", Pattern.MULTILINE);
	private static final Pattern ROW_START = Pattern.compile("^\\s*\\('", Pattern.MULTILINE);

	private static String sql;
	private static List<SeedRow> rows;

	private record SeedRow(String code, String parentCode, String displayName, String level) {
	}

	@BeforeAll
	static void loadSeed() throws IOException {
		try (InputStream input = new ClassPathResource(SEED_PATH).getInputStream()) {
			sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		rows = new ArrayList<>();
		Matcher matcher = ROW.matcher(sql);
		while (matcher.find()) {
			rows.add(new SeedRow(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
		}
	}

	@Test
	@DisplayName("시드는 ISO 3166-1 alpha-2 형식의 중복 없는 국가 249개를 담고 이름이 모두 채워져 있다")
	void seedHasUniqueWellFormedCountries() {
		Matcher rowStarts = ROW_START.matcher(sql);
		int rowStartCount = 0;
		while (rowStarts.find()) {
			rowStartCount++;
		}
		Set<String> codes = new HashSet<>();
		rows.forEach(row -> codes.add(row.code()));

		assertThat(rows).hasSize(249);
		assertThat(rowStartCount).as("파싱하지 못한 행이 없어야 한다").isEqualTo(rows.size());
		assertThat(codes).hasSize(rows.size());
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.code()).matches("[A-Z]{2}");
			assertThat(row.displayName()).isNotBlank();
		});
	}

	@Test
	@DisplayName("시드의 모든 행은 level이 COUNTRY이고 parent_code가 NULL이다")
	void seedRowsAreRootCountries() {
		assertThat(rows).isNotEmpty().allSatisfy(row -> {
			assertThat(row.level()).isEqualTo("COUNTRY");
			assertThat(row.parentCode()).isEqualTo("NULL");
		});
	}

	@Test
	@DisplayName("시드는 ON CONFLICT DO NOTHING으로 재실행에 안전하고 기존 행을 삭제하거나 수정하지 않는다")
	void seedIsIdempotentAndNonDestructive() {
		assertThat(sql).contains("ON CONFLICT (code) DO NOTHING");
		assertThat(sql.toUpperCase()).doesNotContain("DELETE", "UPDATE", "TRUNCATE", "DROP");
	}

	@Test
	@DisplayName("시드는 대표 국가 KR, US, JP를 포함하고 ISO에 없는 ZZ는 포함하지 않는다")
	void seedContainsRepresentativeCodes() {
		Set<String> codes = new HashSet<>();
		rows.forEach(row -> codes.add(row.code()));

		assertThat(codes).contains("KR", "US", "JP").doesNotContain("ZZ");
	}

}
