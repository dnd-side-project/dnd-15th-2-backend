package com.dnd.qello.direction.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.direction.domain.DirectionScheme;
import com.dnd.qello.direction.domain.DirectionSegment;

public interface DirectionSchemeRepository {
	DirectionScheme save(DirectionScheme scheme);
	Optional<DirectionScheme> findById(long id);
	Optional<DirectionScheme> findByCodeAndVersion(String code, int version);

	/** 설정된 code의 현재 ACTIVE scheme을 반환한다. */
	Optional<DirectionScheme> findActiveByCode(String code);
	List<DirectionSegment> findSegments(long schemeId);
	DirectionSegment saveSegment(DirectionSegment segment);
}
