package com.dnd.qello.direction.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.domain.DirectionCandidate;

public interface ActiveUserPresenceRepository {
	ActiveUserPresence save(ActiveUserPresence presence);
	Optional<ActiveUserPresence> findByUserId(long userId);
	List<DirectionCandidate> findCandidates(long excludedUserId, double originLatitude, double originLongitude,
		long minDistanceMeters, long maxDistanceMeters, double sectorStartDegrees, double sectorEndDegrees,
		Instant at, String regionCode);
	List<DirectionSegmentCandidateCount> findCandidateCountsBySegment(long schemeId, long excludedUserId,
		double originLatitude, double originLongitude, long minDistanceMeters, long maxDistanceMeters,
		Instant at, String regionCode);

	record DirectionSegmentCandidateCount(String segmentKey, long count) {
		public DirectionSegmentCandidateCount {
			if (segmentKey == null || segmentKey.isBlank()) {
				throw new IllegalArgumentException("segmentKey must not be blank");
			}
			if (count < 0) {
				throw new IllegalArgumentException("count must not be negative");
			}
		}
	}
}
