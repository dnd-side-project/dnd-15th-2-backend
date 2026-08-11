package com.dnd.qello.filtering.repository;

import java.util.Optional;

import com.dnd.qello.filtering.domain.FilterRelease;

public interface FilterReleaseRepository {

	FilterRelease save(FilterRelease release);

	Optional<FilterRelease> findById(long id);
}
