package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;

public record FilterReleaseResponse(
	long id,
	String normalizationRef,
	String localRulesetRef,
	String categoryMappingRef,
	String modelSnapshot,
	FilterReleaseStatus status,
	Instant promotedAt,
	Instant createdAt
) {

	public static FilterReleaseResponse from(FilterRelease release) {
		return new FilterReleaseResponse(
			release.id(),
			release.normalizationRef(),
			release.localRulesetRef(),
			release.categoryMappingRef(),
			release.modelSnapshot(),
			release.status(),
			release.promotedAt(),
			release.createdAt()
		);
	}
}
