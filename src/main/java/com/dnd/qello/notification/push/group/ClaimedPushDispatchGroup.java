package com.dnd.qello.notification.push.group;

import java.time.Instant;

/** worker가 generation fence와 함께 받은 group claim 경계다. */
public record ClaimedPushDispatchGroup(long groupId, int generation, Instant leaseUntil) {

	public ClaimedPushDispatchGroup {
		if (groupId <= 0 || generation <= 0 || leaseUntil == null) {
			throw new IllegalArgumentException("group claim 값이 올바르지 않습니다");
		}
	}

	public boolean matchesGeneration(int currentGeneration) {
		return generation == currentGeneration;
	}
}
