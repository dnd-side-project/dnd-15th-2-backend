package com.dnd.qello.account.repository;

import java.util.List;

public interface CountryCatalogRepository {

	boolean existsCountry(String countryCode);

	List<String> findCountryAncestors(String coarseRegionCode);

}
