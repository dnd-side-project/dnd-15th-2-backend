package com.dnd.qello.account.repository.jdbc;

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

}
