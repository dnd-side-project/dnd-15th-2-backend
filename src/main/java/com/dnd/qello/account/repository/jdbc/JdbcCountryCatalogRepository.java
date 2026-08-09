package com.dnd.qello.account.repository.jdbc;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dnd.qello.account.repository.CountryCatalogRepository;

@Repository
public class JdbcCountryCatalogRepository implements CountryCatalogRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public JdbcCountryCatalogRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean existsCountry(String countryCode) {
		Integer count = jdbc.queryForObject("""
			SELECT count(*)
			FROM region_code
			WHERE code = :countryCode AND level = 'COUNTRY'
			""", new MapSqlParameterSource("countryCode", countryCode), Integer.class);
		return count != null && count == 1;
	}

	@Override
	public List<String> findCountryAncestors(String coarseRegionCode) {
		return jdbc.queryForList("""
			WITH RECURSIVE region_hierarchy (code, parent_code, level, path) AS (
			SELECT code, parent_code, level, ARRAY[code]::VARCHAR(100)[]
				FROM region_code
				WHERE code = :coarseRegionCode
				UNION ALL
			SELECT parent.code, parent.parent_code, parent.level,
				(hierarchy.path || parent.code::VARCHAR(100))::VARCHAR(100)[]
				FROM region_code parent
				JOIN region_hierarchy hierarchy ON parent.code = hierarchy.parent_code
				WHERE NOT parent.code = ANY(hierarchy.path)
			)
			SELECT code
			FROM region_hierarchy
			WHERE level = 'COUNTRY'
			ORDER BY code
			""", new MapSqlParameterSource("coarseRegionCode", coarseRegionCode), String.class);
	}

}
