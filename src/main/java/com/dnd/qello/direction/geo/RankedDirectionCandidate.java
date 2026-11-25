package com.dnd.qello.direction.geo;

import java.time.Instant;
import java.util.Comparator;

import com.dnd.qello.direction.domain.DirectionCandidate;

public record RankedDirectionCandidate(DirectionCandidate candidate, long recentReceivedCount, Instant lastReceivedAt) {

	public static final Comparator<RankedDirectionCandidate> FAIRNESS_ORDER = Comparator
			.comparingLong(RankedDirectionCandidate::recentReceivedCount)
			.thenComparing(RankedDirectionCandidate::lastReceivedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(value -> value.candidate().distanceMeters())
			.thenComparing(value -> value.candidate().userId());
}
