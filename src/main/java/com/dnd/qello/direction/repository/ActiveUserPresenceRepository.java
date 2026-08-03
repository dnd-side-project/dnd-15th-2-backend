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
}
