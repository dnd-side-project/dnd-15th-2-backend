package com.dnd.qello.filtering.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterRelease;

public interface FilterReleaseRepository {

	FilterRelease save(FilterRelease release);

	Optional<FilterRelease> findById(long id);

	/** 현재 PROMOTED release 조회. DB 유일성 제약이 결과가 최대 하나임을 보장한다. */
	Optional<FilterRelease> findCurrentlyPromoted();

	List<FilterRelease> findAll();
}
